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
      <div ref="mapRef" class="picker-map"></div>
    </div>
    <template #footer>
      <div class="picker-footer">
        <span class="picker-coord">
          {{ lat != null && lng != null ? `已选：纬度 ${lat}，经度 ${lng}` : '点击地图选择地点，滚轮可放大到街道级' }}
        </span>
        <el-button type="primary" :disabled="lat == null || lng == null" :loading="confirming" @click="confirmPick">确认选择</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="MapPicker">
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { ElMessage } from 'element-plus'
import { wgs84ToGcj02, gcj02ToWgs84 } from '@/utils/coord'
import { isInChina, loadChinaPolygons, reverseGeocode, searchPlaces } from '@/utils/map'
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

const mapRef = ref<HTMLElement | null>(null)
const lat = ref<number | null>(null)
const lng = ref<number | null>(null)
let map: L.Map | null = null
let marker: L.Marker | null = null
let locateMarker: L.Marker | null = null
/** 用户（或搜索）已操作过地图时，定位结果不再抢跳视角 */
let userMoved = false
/** 打开时定位授权已授予：此时用户拖动视为主动避开定位；首次授权流程点「允许」后必须就位 */
let grantedAtOpen = false

/** 地点搜索（Nominatim，景点/城市均可） */
const keyword = ref('')
const searching = ref(false)
const searchResults = ref<PlaceResult[]>([])
const searchTip = ref('')

/** 读取主题 CSS 变量（选点标记跟随亮/暗主题色） */
function themeColor(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#A85F52'
}

// 选点暂时限定在中国：接入世界地图时移除 CHINA_BOUNDS/minZoom/搜索 viewbox 与定位回退即可
const CHINA_BOUNDS = L.latLngBounds([15, 73], [54, 136])
/** 未授权定位时的默认视角：故宫（WGS-84） */
const DEFAULT_CENTER: [number, number] = [39.91634, 116.3972]
/** 会话内缓存的上次定位（WGS-84）：再次打开选点立即就位，避免每次等数秒定位 */
let lastKnownLocation: [number, number] | null = null

watch(
  () => props.modelValue,
  value => {
    if (value) {
      lat.value = props.latitude ?? null
      lng.value = props.longitude ?? null
      keyword.value = ''
      searchResults.value = []
      searchTip.value = ''
      initMap()
    } else {
      destroyMap()
    }
  }
)

async function initMap(): Promise<void> {
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
  // 最小层级=省级（6 级），配合硬边界基本看不到外国；有已选坐标时定位街道级
  // ponytail: 高德瓦片是 GCJ-02 显示空间，表单/库存是 WGS-84，出入显示层各转一次
  // （utils/coord）；将来接世界地图（MapLibre + WGS-84 瓦片）时删转换调用即可
  map = L.map(mapRef.value, {
    minZoom: 6,
    maxBounds: CHINA_BOUNDS,
    maxBoundsViscosity: 1.0
  }).setView(
    lat.value != null && lng.value != null ? wgs84ToGcj02(lat.value, lng.value) : wgs84ToGcj02(...DEFAULT_CENTER),
    13
  )
  // 中国轮廓用于选点是否在国内的判定；加载失败降级放行，不阻塞选点
  await loadChinaPolygons().catch(() => {})
  L.tileLayer('https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}', {
    subdomains: '1234',
    maxZoom: 19,
    attribution: '© <a href="https://www.amap.com/">高德地图</a>'
  }).addTo(map)
  map.on('click', (e: L.LeafletMouseEvent) => setPoint(e.latlng.lat, e.latlng.lng))
  map.on('dragstart', onUserMove)
  map.on('zoomstart', onUserMove)
  if (lat.value != null && lng.value != null) {
    renderMarker()
  } else {
    // 有会话内缓存定位：直接就位（后台再刷新），不让用户对着默认视角等定位
    if (lastKnownLocation) {
      map.setView(wgs84ToGcj02(...lastKnownLocation), 13)
      showLocateMarker(lastKnownLocation[0], lastKnownLocation[1])
    }
    locateUser()
  }
}

/** 用户拖动/缩放过地图后，定位结果不再抢跳视角 */
function onUserMove(): void {
  userMoved = true
}

/** 尝试定位到用户当前位置；拒绝授权、失败或定位在国外时不处理 */
function locateUser(): void {
  if (!('geolocation' in navigator)) return
  navigator.geolocation.getCurrentPosition(
    position => {
      const { latitude, longitude } = position.coords
      // 定位点在中国范围外不采用
      if (!map || !CHINA_BOUNDS.contains([latitude, longitude])) {
        return
      }
      lastKnownLocation = [latitude, longitude]
      showLocateMarker(latitude, longitude)
      // 用户已选点或已操作视角时不打扰（打开时未授权的首次流程除外：点「允许」即明确要求定位到当前位置）；
      // maximumAge 让浏览器可复用近期定位，返回更快
      if (lat.value == null && lng.value == null && (!grantedAtOpen || !userMoved)) {
        map.setView(wgs84ToGcj02(latitude, longitude), 13)
      }
    },
    () => {},
    { timeout: 8000, maximumAge: 30000 }
  )
}

/** 当前位置蓝点标记（区别于主题色选点标记），入参 WGS-84 */
function showLocateMarker(latitude: number, longitude: number): void {
  if (!map) return
  const [gLat, gLng] = wgs84ToGcj02(latitude, longitude)
  if (locateMarker) {
    locateMarker.setLatLng([gLat, gLng])
    return
  }
  locateMarker = L.marker([gLat, gLng], {
    icon: L.divIcon({
      className: '',
      html: '<span style="display:block;width:12px;height:12px;border-radius:50%;background:#1E6FFF;border:2px solid #fff;box-shadow:0 0 0 6px rgba(30,111,255,.2)"></span>',
      iconSize: [12, 12],
      iconAnchor: [6, 6]
    })
  }).addTo(map)
}

function destroyMap(): void {
  marker = null
  locateMarker = null
  map?.remove()
  map = null
}

/** divIcon 圆点标记，避免 leaflet 默认图片图标在打包后 404；lat/lng 为表单值（WGS-84） */
function renderMarker(): void {
  if (!map || lat.value == null || lng.value == null) return
  const [gLat, gLng] = wgs84ToGcj02(lat.value, lng.value)
  if (marker) {
    marker.setLatLng([gLat, gLng])
    return
  }
  const icon = L.divIcon({
    className: '',
    html: `<span style="display:block;width:14px;height:14px;border-radius:50%;background:${themeColor('--sgj-primary')};border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4)"></span>`,
    iconSize: [14, 14],
    iconAnchor: [7, 7]
  })
  marker = L.marker([gLat, gLng], { icon }).addTo(map)
}

function setPoint(latitude: number, longitude: number): void {
  // 中国轮廓判定（GCJ-02 显示空间），界外（含矩形边界四角的外国领土）不允许选
  if (!isInChina(longitude, latitude)) {
    ElMessage.warning('只能选择中国范围内的地点')
    return
  }
  // 地图点击坐标是高德瓦片的 GCJ-02，转回 WGS-84 存表单，精度统一 6 位
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
      searchTip.value = '未找到中国范围内的地点，换个关键词试试'
    }
  } catch (e) {
    searchResults.value = []
    searchTip.value = '搜索失败，请检查网络后重试，也可直接点击地图选点'
  } finally {
    searching.value = false
  }
}

function chooseResult(result: PlaceResult): void {
  // Nominatim 结果是 WGS-84，落点/视角转为高德瓦片的 GCJ-02
  const [gLat, gLng] = wgs84ToGcj02(result.latitude, result.longitude)
  lat.value = Number(result.latitude.toFixed(6))
  lng.value = Number(result.longitude.toFixed(6))
  renderMarker()
  map?.flyTo([gLat, gLng], 15)
  searchResults.value = []
  searchTip.value = ''
}

async function confirmPick(): Promise<void> {
  if (lat.value == null || lng.value == null || confirming.value) return
  confirming.value = true
  // 确认时逆地理编码解析名称/地址/城市/国家，失败则只回填坐标
  let place: PickedPlace = { latitude: lat.value, longitude: lng.value }
  try {
    place = { ...place, ...(await reverseGeocode(lat.value, lng.value)) }
  } catch (e) {
    // ignore
  }
  confirming.value = false
  emit('confirm', place)
  emit('update:modelValue', false)
}

onBeforeUnmount(destroyMap)
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

  .picker-map {
    height: 420px;
    border-radius: 12px;
    overflow: hidden;
    background: var(--sgj-bg-card, #fff);
    z-index: 0;
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
</style>
