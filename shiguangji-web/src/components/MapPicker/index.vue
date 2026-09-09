<template>
  <el-dialog
    :model-value="modelValue"
    title="地图选点"
    width="640px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="picker-wrap">
      <div class="picker-search">
        <el-input
          v-model="keyword"
          placeholder="搜索景点、城市名称，如：故宫、杭州"
          clearable
          @keyup.enter="search"
        >
          <template #append>
            <el-button :loading="searching" @click="search">搜索</el-button>
          </template>
        </el-input>
        <ul v-if="searchResults.length" class="picker-results">
          <li v-for="(r, i) in searchResults" :key="i" @click="chooseResult(r)">{{ r.label }}</li>
        </ul>
        <div v-if="searchTip" class="picker-search-tip">{{ searchTip }}</div>
      </div>
      <div class="picker-map-wrap">
        <div
          ref="mapRef"
          class="picker-map"
          v-loading="locating"
          element-loading-text="正在定位当前位置，请稍候…"
        ></div>
        <el-button
          v-if="hasLocated"
          class="locate-btn"
          :icon="Aim"
          circle
          title="回到当前位置"
          @click="backToLocate"
        />
      </div>
    </div>
    <template #footer>
      <div class="picker-footer">
        <span class="picker-coord">
          {{ coordText }}
        </span>
        <el-button type="primary" :disabled="lat == null || lng == null" :loading="confirming" @click="confirmPick">确认选择</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="MapPicker">
import { ElMessage } from 'element-plus'
import { Aim } from '@element-plus/icons-vue'
import { wgs84ToGcj02, gcj02ToWgs84 } from '@/utils/coord'
import { isInChina, loadChinaPolygons, loadAMap, reverseGeocode, searchPlaces } from '@/utils/map'
import type { PickedPlace, PlaceResult } from '@/utils/map'

const props = defineProps<{
  modelValue: boolean
  /** 打开时回显的纬度（可选） */
  latitude?: number | null
  /** 打开时回显的经度（可选） */
  longitude?: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm', place: PickedPlace): void
}>()

const confirming = ref(false)
/** 首次定位进行中：地图遮罩等待，避免定位慢期间用户操作地图后视角被跳转打断 */
const locating = ref(false)
/** 本次会话已定位成功：控制「回到当前位置」按钮显隐（与 lastKnownLocation 同步） */
const hasLocated = ref(false)

const mapRef = ref<HTMLElement | null>(null)
const lat = ref<number | null>(null)
const lng = ref<number | null>(null)
/** 选中的底图地点名称（POI 热点/搜索命中时有值），展示并在确认时优先作为名称 */
const pickedName = ref('')
// JS API 2.0 命名空间官方 loader 未带类型，按 any 使用
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let AMap: any = null
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let map: any = null
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let marker: any = null
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let locateMarker: any = null
/** initMap 代际号：加载 JS API/轮廓期间弹窗被关闭或重开时，放弃过期的初始化 */
let initSeq = 0
/** 用户（或搜索）已操作过地图时，定位结果不再抢跳视角 */
let userMoved = false
/** 打开时定位授权已授予：此时用户拖动视为主动避开定位；首次授权流程点「允许」后必须就位 */
let grantedAtOpen = false
/** 最近一次 POI 热点点击时刻：热点点击后紧随的 map click 不再覆盖选点 */
let lastHotspotAt = 0

/** 地点搜索（高德 PlaceSearch，仅中国范围数据） */
const keyword = ref('')
const searching = ref(false)
const searchResults = ref<PlaceResult[]>([])
const searchTip = ref('')

// 选点暂时限定在中国：接入世界地图时移除边界常量/搜索数据源与定位回退即可
/** 未授权定位时的默认视角：故宫（WGS-84） */
const DEFAULT_CENTER: [number, number] = [39.91634, 116.3972]
/** 视角硬边界（近似中国矩形，WGS-84 与 GCJ-02 差异对视角钳制可忽略） */
const CHINA_MIN_LAT = 15
const CHINA_MAX_LAT = 54
const CHINA_MIN_LNG = 73
const CHINA_MAX_LNG = 136
/** 会话内缓存的上次定位（WGS-84）：再次打开选点立即就位，避免每次等数秒定位 */
let lastKnownLocation: [number, number] | null = null

const coordText = computed(() => {
  if (lat.value == null || lng.value == null) return '点击地图或底图上的地点图标选择，滚轮可放大到街道级'
  return `已选：${pickedName.value ? pickedName.value + '，' : ''}纬度 ${lat.value}，经度 ${lng.value}`
})

watch(
  () => props.modelValue,
  value => {
    if (value) {
      lat.value = props.latitude ?? null
      lng.value = props.longitude ?? null
      pickedName.value = ''
      keyword.value = ''
      searchResults.value = []
      searchTip.value = ''
      initMap()
    } else {
      initSeq++
      destroyMap()
    }
  }
)

async function initMap(): Promise<void> {
  const seq = ++initSeq
  await nextTick()
  if (!mapRef.value) return
  destroyMap()
  userMoved = false
  // 老浏览器无 permissions API 时按未授予处理（catch 兜底）：同意授权后定位必跳转
  try {
    grantedAtOpen = (await navigator.permissions.query({ name: 'geolocation' })).state === 'granted'
  } catch {
    grantedAtOpen = false
  }
  try {
    AMap = await loadAMap()
  } catch (e) {
    if (seq === initSeq) ElMessage.error('地图加载失败，请检查网络后重试')
    return
  }
  if (seq !== initSeq) return
  // 中国轮廓用于选点是否在国内的判定；加载失败降级放行，不阻塞选点
  await loadChinaPolygons().catch(() => {})
  if (seq !== initSeq || !mapRef.value) return
  // 最小层级=省级（6 级），配合硬边界基本看不到外国；有已选坐标或缓存定位时直接就位
  // ponytail: 高德 JS API 显示空间是 GCJ-02，表单/库存是 WGS-84，出入显示层各转一次
  // （utils/coord）；将来接世界地图时删转换调用即可
  const center = wgs84ToGcj02(
    lat.value != null && lng.value != null
      ? lat.value
      : lastKnownLocation
        ? lastKnownLocation[0]
        : DEFAULT_CENTER[0],
    lat.value != null && lng.value != null
      ? lng.value
      : lastKnownLocation
        ? lastKnownLocation[1]
        : DEFAULT_CENTER[1]
  )
  map = new AMap.Map(mapRef.value, {
    zoom: 13,
    zooms: [6, 19],
    // 开启底图 POI 热点：hotspotclick 需要此选项才会触发（官方 demo 要求）
    isHotspot: true,
    center: [center[1], center[0]]
  })
  map.setLimitBounds(new AMap.Bounds([CHINA_MIN_LNG, CHINA_MIN_LAT], [CHINA_MAX_LNG, CHINA_MAX_LAT]))
  map.on('click', onMapClick)
  map.on('hotspotclick', onHotspotClick)
  map.on('dragstart', onUserMove)
  map.on('zoomstart', onUserMove)
  if (lat.value != null && lng.value != null) {
    renderMarker()
  } else {
    // 有会话内缓存定位：视角已就位，蓝点补上（后台刷新不遮罩）；
    // 无缓存则遮罩等待首次定位（常含授权弹窗等待），期间不响应地图操作
    if (lastKnownLocation) {
      showLocateMarker(lastKnownLocation[0], lastKnownLocation[1])
      hasLocated.value = true
      locateUser(false)
    } else {
      locateUser(true)
    }
  }
}

/** 用户拖动/缩放过地图后，定位结果不再抢跳视角 */
function onUserMove(): void {
  userMoved = true
}

function inChinaBounds(latitude: number, longitude: number): boolean {
  return (
    latitude >= CHINA_MIN_LAT &&
    latitude <= CHINA_MAX_LAT &&
    longitude >= CHINA_MIN_LNG &&
    longitude <= CHINA_MAX_LNG
  )
}

/** 尝试定位到用户当前位置；拒绝授权、失败或定位在国外时不处理 */
function locateUser(blockWhileLocating: boolean): void {
  if (!('geolocation' in navigator)) return
  locating.value = blockWhileLocating
  navigator.geolocation.getCurrentPosition(
    position => {
      locating.value = false
      const { latitude, longitude } = position.coords
      // 定位点在中国范围外不采用
      if (!map || !inChinaBounds(latitude, longitude)) {
        return
      }
      lastKnownLocation = [latitude, longitude]
      hasLocated.value = true
      showLocateMarker(latitude, longitude)
      // 用户已选点或已操作视角时不打扰（打开时未授权的首次流程除外：点「允许」即明确要求定位到当前位置）；
      // maximumAge 让浏览器可复用近期定位，返回更快
      if (lat.value == null && lng.value == null && (!grantedAtOpen || !userMoved)) {
        const [gLat, gLng] = wgs84ToGcj02(latitude, longitude)
        map.setZoomAndCenter(13, [gLng, gLat])
      }
    },
    () => {
      locating.value = false
    },
    { timeout: 8000, maximumAge: 30000 }
  )
}

/** 一键回到最近一次定位点（视角 13 级，与自动定位一致） */
function backToLocate(): void {
  if (!map || !lastKnownLocation) return
  const [gLat, gLng] = wgs84ToGcj02(...lastKnownLocation)
  map.setZoomAndCenter(13, [gLng, gLat])
}

/** 当前位置蓝点标记（区别于主题色选点标记），入参 WGS-84 */
function showLocateMarker(latitude: number, longitude: number): void {
  if (!map) return
  const [gLat, gLng] = wgs84ToGcj02(latitude, longitude)
  if (locateMarker) {
    locateMarker.setPosition([gLng, gLat])
    return
  }
  locateMarker = new AMap.Marker({
    position: [gLng, gLat],
    anchor: 'center',
    content:
      '<span style="display:block;width:12px;height:12px;border-radius:50%;background:#1E6FFF;border:2px solid #fff;box-shadow:0 0 0 6px rgba(30,111,255,.2)"></span>'
  })
  map.add(locateMarker)
}

function destroyMap(): void {
  marker = null
  locateMarker = null
  locating.value = false
  map?.destroy()
  map = null
}

/** 读取主题 CSS 变量（选点标记跟随亮/暗主题色） */
function themeColor(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#A85F52'
}

/** divIcon 圆点标记样式；lat/lng 为表单值（WGS-84）。
 *  每次落点重建标记（而非 setPosition 挪位），重放 pick-pop 弹跳动画让用户看清本次选中处 */
function renderMarker(): void {
  if (!map || lat.value == null || lng.value == null) return
  const [gLat, gLng] = wgs84ToGcj02(lat.value, lng.value)
  if (marker) {
    map.remove(marker)
  }
  marker = new AMap.Marker({
    position: [gLng, gLat],
    anchor: 'center',
    content: `<span class="pick-pin" style="display:block;width:14px;height:14px;border-radius:50%;background:${themeColor('--sgj-primary')};border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4)"></span>`
  })
  map.add(marker)
}

/** 普通点击选点；POI 热点点击后紧随的 click 不再覆盖（坐标与名称以 POI 为准） */
function onMapClick(e: any): void {
  if (Date.now() - lastHotspotAt < 200) return
  pickedName.value = ''
  pickAt(e.lnglat.lat, e.lnglat.lng)
}

/** 点击底图 POI 图标（景点/酒店/医院等）：以 POI 坐标选点、标记弹跳放大并记住名称 */
function onHotspotClick(e: any): void {
  lastHotspotAt = Date.now()
  pickedName.value = e.name || ''
  pickAt(e.lnglat.lat, e.lnglat.lng)
}

function pickAt(latitude: number, longitude: number): void {
  // 中国轮廓判定（GCJ-02 显示空间），界外（含边界矩形四角的外国领土）不允许选
  if (!isInChina(longitude, latitude)) {
    ElMessage.warning('只能选择中国范围内的地点')
    return
  }
  // 地图/POI 坐标是高德的 GCJ-02，转回 WGS-84 存表单，精度统一 6 位
  const [wLat, wLng] = gcj02ToWgs84(latitude, longitude)
  lat.value = Number(wLat.toFixed(6))
  lng.value = Number(wLng.toFixed(6))
  renderMarker()
}

async function search(): Promise<void> {
  const q = keyword.value.trim()
  if (!q || searching.value) return
  searching.value = true
  searchTip.value = ''
  try {
    searchResults.value = await searchPlaces(q)
    if (!searchResults.value.length) {
      searchTip.value = '未找到相关地点，换个关键词试试'
    }
  } catch (e) {
    searchResults.value = []
    searchTip.value = '搜索失败，请检查网络后重试，也可直接点击地图选点'
  } finally {
    searching.value = false
  }
}

function chooseResult(result: PlaceResult): void {
  // 搜索结果坐标已转 WGS-84 存表单，落点/视角转回高德的 GCJ-02
  const [gLat, gLng] = wgs84ToGcj02(result.latitude, result.longitude)
  lat.value = Number(result.latitude.toFixed(6))
  lng.value = Number(result.longitude.toFixed(6))
  pickedName.value = result.title
  renderMarker()
  map?.setZoomAndCenter(15, [gLng, gLat])
  searchResults.value = []
  searchTip.value = ''
}

async function confirmPick(): Promise<void> {
  if (lat.value == null || lng.value == null || confirming.value) return
  confirming.value = true
  // 确认时逆地理编码解析名称/地址/城市/国家，失败则只回填坐标；
  // 点击 POI 图标/搜索命中时已拿到准确名称，优先于逆地理结果
  let place: PickedPlace = { latitude: lat.value, longitude: lng.value }
  try {
    place = { ...place, ...(await reverseGeocode(lat.value, lng.value)) }
  } catch (e) {
    // ignore
  }
  if (pickedName.value) place.title = pickedName.value
  confirming.value = false
  emit('confirm', place)
  emit('update:modelValue', false)
}

onBeforeUnmount(() => {
  initSeq++
  destroyMap()
})
</script>

<style scoped lang="scss">
.picker-wrap {
  position: relative;

  .picker-search {
    position: relative;
    margin-bottom: 12px;
    z-index: 500;

    .picker-results {
      position: absolute;
      top: 42px;
      left: 0;
      right: 0;
      margin: 0;
      padding: 4px 0;
      list-style: none;
      background: var(--sgj-bg-card, #fff);
      border: 1px solid var(--sgj-border-card, #e5e5e5);
      border-radius: 8px;
      box-shadow: 0 4px 16px rgba(23, 27, 26, 0.12);
      max-height: 180px;
      overflow-y: auto;
      z-index: 2000;

      li {
        padding: 8px 14px;
        font-size: 13px;
        color: var(--sgj-text-2, #666);
        cursor: pointer;

        &:hover {
          background: var(--sgj-primary-soft, #f7f0ec);
          color: var(--sgj-primary, #A85F52);
        }
      }
    }

    .picker-search-tip {
      margin-top: 6px;
      font-size: 12px;
      color: var(--sgj-text-4, #999);
    }
  }

  .picker-map-wrap {
    position: relative;

    .picker-map {
      height: 420px;
      border-radius: 12px;
      overflow: hidden;
      background: var(--sgj-bg-card, #fff);
      z-index: 0;

      // 选点标记落点弹跳放大，提示用户选中位置（Marker DOM 由地图动态注入，需 :deep 穿透）
      :deep(.pick-pin) {
        animation: pick-pop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
      }
    }

    .locate-btn {
      position: absolute;
      top: 12px;
      right: 12px;
      z-index: 800;
      box-shadow: 0 2px 8px rgba(23, 27, 26, 0.18);
    }
  }
}

.picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .picker-coord {
    font-size: 13px;
    color: var(--sgj-text-2, #666);
  }
}

@keyframes pick-pop {
  0% {
    transform: scale(0.3);
  }

  60% {
    transform: scale(1.4);
  }

  100% {
    transform: scale(1);
  }
}
</style>
