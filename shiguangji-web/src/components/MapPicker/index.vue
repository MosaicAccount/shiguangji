<template>
  <el-dialog
    :model-value="modelValue"
    title="地图选点"
    width="640px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="picker-wrap">
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

/** 读取主题 CSS 变量（选点标记跟随亮/暗主题色） */
function themeColor(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#A85F52'
}

watch(
  () => props.modelValue,
  value => {
    if (value) {
      lat.value = props.latitude ?? null
      lng.value = props.longitude ?? null
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
  // 有已选坐标时定位到街道级，否则给中国全境视角
  map = L.map(mapRef.value).setView([lat.value ?? 35, lng.value ?? 105], lat.value != null ? 13 : 4)
  // ponytail: OSM 瓦片（WGS-84，与表单存储坐标系一致）；国内访问偏慢是已知瓶颈，
  // 如不可接受可换高德瓦片，但需整体做 GCJ-02 坐标转换
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(map)
  map.on('click', (e: L.LeafletMouseEvent) => setPoint(e.latlng.lat, e.latlng.lng))
  if (lat.value != null && lng.value != null) {
    renderMarker()
  }
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

function confirmPick(): void {
  if (lat.value == null || lng.value == null) return
  emit('confirm', lat.value, lng.value)
  emit('update:modelValue', false)
}

onBeforeUnmount(destroyMap)
</script>

<style scoped lang="scss">
.picker-wrap {
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
