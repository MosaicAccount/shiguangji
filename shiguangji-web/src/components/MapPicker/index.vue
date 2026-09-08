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
        <el-button type="primary" :disabled="lat == null || lng == null" @click="confirmPick">确认选择</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="MapPicker">
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const props = defineProps<{
  modelValue: boolean
  /** 打开时回显的纬度（可选） */
  latitude?: number | null
  /** 打开时回显的经度（可选） */
  longitude?: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm', latitude: number, longitude: number): void
}>()

const mapRef = ref<HTMLElement | null>(null)
const lat = ref<number | null>(null)
const lng = ref<number | null>(null)
let map: L.Map | null = null
let marker: L.Marker | null = null

/** 地点搜索（Nominatim，景点/城市均可） */
interface SearchResult {
  label: string
  lat: number
  lng: number
}
const keyword = ref('')
const searching = ref(false)
const searchResults = ref<SearchResult[]>([])
const searchTip = ref('')

/** 读取主题 CSS 变量（选点标记跟随亮/暗主题色） */
function themeColor(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#A85F52'
}

// 选点暂时限定在中国：接入世界地图时移除 CHINA_BOUNDS/minZoom/搜索 viewbox 与定位回退即可
const CHINA_BOUNDS = L.latLngBounds([15, 73], [54, 136])
/** 未授权定位时的默认视角：故宫 */
const DEFAULT_CENTER: [number, number] = [39.91634, 116.3972]

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
  // 最小层级=省级（6 级），配合硬边界基本看不到外国；有已选坐标时定位街道级
  map = L.map(mapRef.value, {
    minZoom: 6,
    maxBounds: CHINA_BOUNDS,
    maxBoundsViscosity: 1.0
  }).setView(lat.value != null && lng.value != null ? [lat.value, lng.value] : DEFAULT_CENTER, 13)
  // ponytail: OSM 瓦片（WGS-84，与表单存储坐标系一致）；国内访问偏慢是已知瓶颈，
  // 如不可接受可换高德瓦片，但需整体做 GCJ-02 坐标转换
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(map)
  map.on('click', (e: L.LeafletMouseEvent) => setPoint(e.latlng.lat, e.latlng.lng))
  if (lat.value != null && lng.value != null) {
    renderMarker()
  } else {
    locateUser()
  }
}

/** 尝试定位到用户当前位置；拒绝授权、失败或定位在国外时保持默认视角 */
function locateUser(): void {
  if (!('geolocation' in navigator)) return
  navigator.geolocation.getCurrentPosition(
    position => {
      const { latitude, longitude } = position.coords
      // 用户已手动选点则不打扰视角；定位点在中国范围外也不采用
      if (lat.value == null && lng.value == null && CHINA_BOUNDS.contains([latitude, longitude])) {
        map?.setView([latitude, longitude], 13)
      }
    },
    () => {},
    { timeout: 8000 }
  )
}

function destroyMap(): void {
  marker = null
  map?.remove()
  map = null
}

/** divIcon 圆点标记，避免 leaflet 默认图片图标在打包后 404 */
function renderMarker(): void {
  if (!map || lat.value == null || lng.value == null) return
  if (marker) {
    marker.setLatLng([lat.value, lng.value])
    return
  }
  const icon = L.divIcon({
    className: '',
    html: `<span style="display:block;width:14px;height:14px;border-radius:50%;background:${themeColor('--sgj-primary')};border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4)"></span>`,
    iconSize: [14, 14],
    iconAnchor: [7, 7]
  })
  marker = L.marker([lat.value, lng.value], { icon }).addTo(map)
}

function setPoint(latitude: number, longitude: number): void {
  // 表单经纬度精度统一 6 位
  lat.value = Number(latitude.toFixed(6))
  lng.value = Number(longitude.toFixed(6))
  renderMarker()
}

async function search(): Promise<void> {
  const q = keyword.value.trim()
  if (!q || searching.value) return
  searching.value = true
  searchTip.value = ''
  try {
    const res = await fetch(
      `https://nominatim.openstreetmap.org/search?format=json&limit=5&accept-language=zh-CN&viewbox=73,54,136,15&bounded=1&q=${encodeURIComponent(q)}`
    )
    if (!res.ok) throw new Error(String(res.status))
    const rows = (await res.json()) as Array<{ display_name: string; lat: string; lon: string }>
    searchResults.value = rows.map(r => ({ label: r.display_name, lat: Number(r.lat), lng: Number(r.lon) }))
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

function chooseResult(result: SearchResult): void {
  setPoint(result.lat, result.lng)
  map?.flyTo([result.lat, result.lng], 15)
  searchResults.value = []
  searchTip.value = ''
}

function confirmPick(): void {
  if (lat.value == null || lng.value == null) return
  emit('confirm', lat.value, lng.value)
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
