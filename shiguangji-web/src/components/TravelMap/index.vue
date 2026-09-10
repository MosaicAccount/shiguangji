<template>
  <div class="travel-map">
    <div class="map-tip">📷 照片按就近区域聚合 · 点击聚合牌翻看回忆</div>
    <div class="map-legend">
      <span><i class="legend-dot visited"></i>去过</span>
      <span><i class="legend-dot want"></i>想去</span>
      <span>🂠 照片聚合</span>
    </div>
    <div ref="mapRef" class="map-chart"></div>
  </div>
</template>

<script setup lang="ts">
import { useDark } from '@vueuse/core'
import { loadAMap } from '@/utils/map'
import { wgs84ToGcj02 } from '@/utils/coord'
import { photoUrl } from '@/utils/sgj'
import type { TravelPoint } from '@/types/api/front/travel'

/** 夜间模式：底图切暗色瓦片，标记/弹卡保持固定高对比配色 */
const isDark = useDark()
const mapStyle = computed(() => (isDark.value ? 'amap://styles/dark' : 'amap://styles/whitesmoke'))

const props = defineProps<{
  /** 去过地点（按时间排序） */
  visited: TravelPoint[]
  /** 想去地点 */
  wish: TravelPoint[]
}>()

const emit = defineEmits<{
  (e: 'select', itemId: number): void
}>()

/** 带展示状态的轨迹点（status 由所在数组推断：visited=DONE / wish=WANT） */
type MapPoint = TravelPoint & { status: 'DONE' | 'WANT' }

const mapRef = ref<HTMLElement | null>(null)
// JS API 2.0 官方 loader 未带类型，按 any 使用
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let AMap: any = null
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let map: any = null
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let infoWindow: any = null
const overlays = ref<any[]>([])

/**
 * 缩放驱动的分层聚合：
 * - 低缩放（<= 7.5）按省聚合，省级缺失（国外）回退国家；
 * - 中缩放（7.5 ~ 10）按市聚合；
 * - 高缩放（> 10）逐点显示（点少不显乱，保留地名与去过/想去标签）。
 */
const PROVINCE_MAX_ZOOM = 7.5
const CITY_MAX_ZOOM = 10

interface ClusterNode {
  lng: number
  lat: number
  /** 聚合显示名（省名/市名） */
  label: string
  points: (MapPoint & { gLng: number; gLat: number })[]
}

/** 行政区名规范化聚合键：去掉最常见的「市/省」尾缀，避免「北京/北京市」分裂 */
function regionKey(name?: string): string {
  return (name || '').trim().replace(/(市|省)$/, '')
}

/** 两点间球面距离（千米），省归属判定用 */
function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const rad = Math.PI / 180
  const dLat = (lat2 - lat1) * rad
  const dLng = (lng2 - lng1) * rad
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

/** 34 个省级行政区中心（WGS-84 粗略值，聚合归属足够） */
const PROVINCE_CENTERS: [string, number, number][] = [
  ['北京', 39.9, 116.4], ['天津', 39.08, 117.2], ['河北', 38.04, 114.51],
  ['山西', 37.87, 112.55], ['内蒙古', 40.82, 111.65], ['辽宁', 41.8, 123.43],
  ['吉林', 43.88, 125.32], ['黑龙江', 45.75, 126.65], ['上海', 31.23, 121.47],
  ['江苏', 32.06, 118.78], ['浙江', 30.27, 120.15], ['安徽', 31.86, 117.28],
  ['福建', 26.08, 119.3], ['江西', 28.68, 115.86], ['山东', 36.65, 117.12],
  ['河南', 34.75, 113.62], ['湖北', 30.59, 114.31], ['湖南', 28.23, 112.94],
  ['广东', 23.13, 113.26], ['广西', 22.82, 108.32], ['海南', 20.02, 110.35],
  ['重庆', 29.56, 106.55], ['四川', 30.57, 104.07], ['贵州', 26.65, 106.63],
  ['云南', 25.04, 102.71], ['西藏', 29.65, 91.13], ['陕西', 34.34, 108.94],
  ['甘肃', 36.06, 103.83], ['青海', 36.62, 101.78], ['宁夏', 38.47, 106.27],
  ['新疆', 43.79, 87.62], ['香港', 22.32, 114.17], ['澳门', 22.2, 113.55],
  ['台湾', 25.03, 121.57]
]

/** 距最近省中心超过该值视为境外地点 */
const PROVINCE_MAX_KM = 1100

/**
 * 省归属：数据带省名直接用；历史数据多为空，按最近省中心归属
 * （ponytail: 最近中心归属在省界凹凸处可能归错省，个人游记可接受；
 * 出现归错反馈再引入区划边界库）。
 */
function provinceOf(p: MapPoint & { gLat: number; gLng: number }): string {
  if (p.province) return regionKey(p.province)
  let best = ''
  let bestD = Infinity
  for (const [name, plat, plng] of PROVINCE_CENTERS) {
    const d = haversineKm(p.gLat, p.gLng, plat, plng)
    if (d < bestD) {
      bestD = d
      best = name
    }
  }
  return bestD <= PROVINCE_MAX_KM ? best : ''
}

/**
 * 按行政区分组（省层或市层）。
 * 市层：市名归一为 key，缺失市名的地点各自独立。
 */
function groupByRegion(points: (MapPoint & { gLng: number; gLat: number })[], level: 'prov' | 'city'): ClusterNode[] {
  const groups = new Map<string, ClusterNode>()
  for (const p of points) {
    let key: string
    let label: string
    if (level === 'prov') {
      const prov = provinceOf(p)
      key = prov || regionKey(p.country) || `#${p.itemId}`
      label = p.province || prov || p.country || p.title || '地点'
    } else {
      key = regionKey(p.city) || `#${p.itemId}`
      label = p.city || p.title || '地点'
    }
    const hit = groups.get(key)
    if (hit) {
      hit.points.push(p)
      hit.lng = hit.points.reduce((s, x) => s + x.gLng, 0) / hit.points.length
      hit.lat = hit.points.reduce((s, x) => s + x.gLat, 0) / hit.points.length
    } else {
      groups.set(key, { lng: p.gLng, lat: p.gLat, label, points: [p] })
    }
  }
  return Array.from(groups.values())
}

/** 转换 GCJ-02 并附加到点（wgs84ToGcj02 返回 [纬度, 经度]） */
function toMapPoints(): (MapPoint & { gLng: number; gLat: number })[] {
  const tagged: (MapPoint & { gLng: number; gLat: number })[] = []
  for (const p of [
    ...props.visited.map(p => ({ ...p, status: 'DONE' as const })),
    ...props.wish.map(p => ({ ...p, status: 'WANT' as const }))
  ]) {
    if (p.latitude == null || p.longitude == null) continue
    const [gLat, gLng] = wgs84ToGcj02(p.latitude, p.longitude)
    tagged.push({ ...p, gLng, gLat })
  }
  return tagged
}

function clusterPhotos(points: TravelPoint[]): { count: number; covers: string[] } {
  const covers: string[] = []
  let count = 0
  for (const p of points) {
    count += p.photoCount || 0
    if (p.cover && covers.length < 3) covers.push(photoUrl(p.cover))
  }
  return { count, covers }
}

/* 聚合牌 HTML（样式见 <style>，非 scoped 以作用于 marker 内容）：有照片用扇牌，无照片用数字圆片 */
function clusterHtml(cluster: ClusterNode): string {
  const { count, covers } = clusterPhotos(cluster.points)
  const cards = covers.length
    ? `<span class="tm-deck${covers.length > 1 ? '' : ' single'}">${covers
        .map(u => `<i style="background-image:url('${u}')"></i>`)
        .join('')}</span>`
    : `<span class="tm-coin ${cluster.points.some(p => p.status === 'DONE') ? 'visited' : 'want'}">${cluster.points.length}</span>`
  const pillText = count > 0
    ? `${cluster.label} · ${cluster.points.length}地 · ${count}张`
    : `${cluster.label} · ${cluster.points.length}地`
  return `<span class="tm-marker">${cards}<span class="tm-pill${count > 0 ? '' : ' muted'}">${pillText}</span></span>`
}

function dotHtml(point: MapPoint): string {
  return `<span class="tm-marker"><span class="tm-plain-dot ${point.status === 'DONE' ? 'visited' : 'want'}"></span><span class="tm-pill muted">${point.title} · ${point.status === 'DONE' ? '去过' : '想去'}</span></span>`
}

/** 分组照片弹卡内容（InfoWindow 自定义 DOM，可直接绑定点击） */
function popoverDom(cluster: ClusterNode): HTMLElement {
  const wrap = document.createElement('div')
  wrap.className = 'tm-pop'
  const groups = cluster.points
    .map(p => {
      const count = p.photoCount || 0
      const extra = Math.max(0, count - 5)
      return `
        <button class="tm-pop-group" data-item="${p.itemId}">
          <span class="tm-pop-name"><b>${p.title}<i class="tm-pop-tag ${p.status === 'DONE' ? 'done' : 'want'}">${p.status === 'DONE' ? '去过' : '想去'}</i></b><span>${count} 张 →</span></span>
          <span class="tm-pop-thumbs">
            <i class="${p.cover ? '' : 'empty'}" ${p.cover ? `style="background-image:url('${photoUrl(p.cover)}')"` : ''} aria-hidden="true">${p.cover ? '' : '🖼'}</i>
            ${extra > 0 ? `<i class="more">+${extra}</i>` : ''}
          </span>
        </button>`
    })
    .join('')
  wrap.innerHTML = `
    <div class="tm-pop-head">
      <div><div class="tm-pop-title">${cluster.label}的照片</div><div class="tm-pop-sub">${cluster.points.length} 个地点 · 共 ${clusterPhotos(cluster.points).count} 张</div></div>
      <button class="tm-pop-close" aria-label="关闭">✕</button>
    </div>
    ${groups}`
  wrap.querySelector('.tm-pop-close')?.addEventListener('click', () => infoWindow?.close())
  wrap.querySelectorAll<HTMLButtonElement>('.tm-pop-group').forEach(b =>
    b.addEventListener('click', () => {
      infoWindow?.close()
      emit('select', Number(b.dataset.item))
    }))
  return wrap
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function addMarker(lng: number, lat: number, html: string, onClick: () => void): any {
  const el = document.createElement('button')
  el.className = 'tm-marker-btn'
  el.setAttribute('aria-label', html.match(/tm-pill[^>]*>([^<]*)<\/span>/)?.[1] || '地图标记')
  el.innerHTML = html
  el.addEventListener('click', (e: Event) => {
    e.stopPropagation()
    onClick()
  })
  const marker = new AMap.Marker({ position: [lng, lat], content: el, anchor: 'bottom-center' })
  map.add(marker)
  overlays.value.push(marker)
  return marker
}

/** 按当前缩放层级渲染：省聚合 → 市聚合 → 逐点 */
function renderOverlays(): void {
  overlays.value.forEach(o => map.remove(o))
  overlays.value = []
  infoWindow?.close()

  const points = toMapPoints()
  const zoom = map.getZoom()

  // 高缩放：逐点显示（点少，地名与去过/想去标签不乱）
  if (zoom > CITY_MAX_ZOOM) {
    for (const point of points) {
      const hasPhotos = (point.photoCount || 0) > 0
      if (hasPhotos) {
        const cluster: ClusterNode = { lng: point.gLng, lat: point.gLat, label: point.title || '地点', points: [point] }
        addMarker(point.gLng, point.gLat, clusterHtml(cluster), () => {
          infoWindow?.setContent(popoverDom(cluster))
          infoWindow?.open(map, [point.gLng, point.gLat])
        })
      } else {
        addMarker(point.gLng, point.gLat, dotHtml(point), () => emit('select', point.itemId!))
      }
    }
    return
  }

  // 低/中缩放：行政区聚合（照片多寡都聚合，避免满屏标签）
  const level = zoom <= PROVINCE_MAX_ZOOM ? 'prov' : 'city'
  for (const cluster of groupByRegion(points, level)) {
    addMarker(cluster.lng, cluster.lat, clusterHtml(cluster), () => {
      // 单点簇直接进详情；多点簇展开分组弹卡
      if (cluster.points.length === 1) {
        emit('select', cluster.points[0].itemId!)
        return
      }
      infoWindow?.setContent(popoverDom(cluster))
      infoWindow?.open(map, [cluster.lng, cluster.lat])
    })
  }
}

async function initMap(): Promise<void> {
  if (!mapRef.value || map) return
  AMap = await loadAMap()
  map = new AMap.Map(mapRef.value, {
    zoom: 4.2,
    center: [104.5, 36.5],
    mapStyle: mapStyle.value,
    viewMode: '2D',
    zooms: [3.5, 14]
  })
  infoWindow = new AMap.InfoWindow({ isCustom: true, anchor: 'bottom-center', offset: new AMap.Pixel(0, -46) })
  // 点击底图关闭照片弹卡；缩放结束后按新层级重绘聚合
  map.on('click', () => infoWindow?.close())
  map.on('zoomend', () => renderOverlays())
  renderOverlays()
  window.addEventListener('resize', onResize)
}

function onResize(): void {
  map?.resize?.()
}

// 夜间模式切换：换底图瓦片（标记为固定高对比配色，无需重绘）
watch(isDark, () => {
  map?.setMapStyle(mapStyle.value)
})

watch(
  () => [props.visited, props.wish],
  () => {
    if (map) renderOverlays()
  },
  { deep: true }
)

onMounted(() => {
  initMap().catch(() => {})
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  overlays.value.forEach(o => map?.remove(o))
  map?.destroy?.()
  map = null
})
</script>

<style scoped lang="scss">
.travel-map {
  position: relative;
}

.map-chart {
  height: 540px;
  background: var(--sgj-bg-card);
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(23, 27, 26, 0.06);
  overflow: hidden;
}

.map-tip {
  position: absolute;
  top: 14px;
  right: 14px;
  z-index: 170;
  font-size: 12px;
  color: var(--sgj-text-2);
  background: var(--sgj-bg-card);
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--sgj-border-card);
  pointer-events: none;
}

.map-legend {
  position: absolute;
  left: 14px;
  bottom: 14px;
  z-index: 170;
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--sgj-text-2);
  background: var(--sgj-bg-card);
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--sgj-border-card);
  pointer-events: none;

  .legend-dot {
    display: inline-block;
    width: 9px;
    height: 9px;
    border-radius: 50%;
    margin-right: 5px;
    vertical-align: 1px;

    &.visited {
      background: var(--sgj-moss);
    }

    &.want {
      background: var(--sgj-amber);
    }
  }
}

@media (max-width: 768px) {
  .map-chart {
    height: 420px;
  }
}
</style>

<!-- 高德 marker/InfoWindow 内容挂在地图容器下，不在 scoped 作用域内，需全局样式 -->
<style lang="scss">
/* 聚合扇牌标记 */
.tm-marker-btn {
  padding: 0;
  background: none;
  border: 0;
  cursor: pointer;
  display: block;
  color: #2e3331;
}

.tm-marker {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  transform: translateY(6px);
  transition: transform 0.18s ease;

  .tm-marker-btn:hover &,
  .tm-marker-btn:focus-visible & {
    transform: translateY(0);
  }
}

.tm-deck {
  position: relative;
  display: block;
  width: 46px;
  height: 52px;

  i {
    position: absolute;
    bottom: 0;
    left: 50%;
    width: 34px;
    height: 44px;
    border: 3px solid var(--sgj-bg-card);
    border-radius: 7px;
    box-shadow: 0 2px 6px rgba(23, 27, 26, 0.25);
    transform: translateX(-50%) rotate(-16deg);
    transform-origin: 50% 115%;
    background-size: cover;
    background-position: center;
    display: block;

    &:nth-child(2) {
      transform: translateX(-50%) rotate(-3deg);
      z-index: 1;
    }

    &:nth-child(3) {
      transform: translateX(-50%) rotate(11deg);
      z-index: 2;
    }
  }

  &.single i {
    width: 40px;
    height: 50px;
    transform: translateX(-50%) rotate(-4deg);
  }
}

.tm-coin {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 2.5px solid #fff;
  box-shadow: 0 2px 6px rgba(23, 27, 26, 0.25);
  color: #fff;
  font-size: 12.5px;
  font-weight: 700;

  &.visited {
    background: var(--sgj-moss);
  }

  &.want {
    background: var(--sgj-amber);
  }
}

.tm-plain-dot {
  display: block;
  width: 13px;
  height: 13px;
  border-radius: 50%;
  border: 2.5px solid #fff;
  box-shadow: 0 1px 5px rgba(23, 27, 26, 0.35);

  &.visited {
    background: var(--sgj-moss);
  }

  &.want {
    background: var(--sgj-amber);
  }
}

.tm-pill {
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-primary);
  color: var(--sgj-primary);
  box-shadow: 0 2px 6px rgba(23, 27, 26, 0.12);

  &.muted {
    border-color: var(--sgj-border-card);
    color: var(--sgj-text-2);
    box-shadow: none;
  }
}

/* 分组照片弹卡（InfoWindow isCustom 内容） */
.tm-pop {
  width: 268px;
  max-height: 320px;
  overflow-y: auto;
  background: var(--sgj-bg-card);
  border-radius: 14px;
  box-shadow: 0 10px 28px rgba(23, 27, 26, 0.16);
  border: 1px solid var(--sgj-border-card);
  padding: 12px;
  color: var(--sgj-text);
  font-family: var(--sgj-font, inherit);
}

.tm-pop-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 8px;
}

.tm-pop-title {
  font: 700 14px/1.3 var(--sgj-font-serif);
}

.tm-pop-sub {
  font-size: 11px;
  color: var(--sgj-text-4);
  margin-top: 2px;
}

.tm-pop-close {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: var(--sgj-text-2);
  font-size: 14px;
  line-height: 1;
  background: none;
  border: 0;
  cursor: pointer;

  &:hover {
    background: var(--sgj-bg-deep);
  }
}

.tm-pop-group {
  width: 100%;
  text-align: left;
  border-radius: 10px;
  padding: 8px;
  display: block;
  background: none;
  border: 0;
  cursor: pointer;
  /* button 不继承弹卡颜色，显式声明墨色（高德暗色容器下发白） */
  color: var(--sgj-text);

  &:hover {
    background: var(--sgj-bg-deep);
  }

  & + & {
    margin-top: 2px;
  }
}

.tm-pop-name {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  width: 100%;

  b {
    font-size: 13px;
  }

  span {
    font-size: 11px;
    color: var(--sgj-text-4);
  }
}

.tm-pop-tag {
  font-style: normal;
  font-size: 10px;
  font-weight: 400;
  padding: 1px 7px;
  border-radius: 999px;
  margin-left: 6px;
  vertical-align: 1px;

  &.done {
    background: var(--sgj-moss-soft);
    color: var(--sgj-moss);
  }

  &.want {
    background: var(--sgj-amber-soft);
    color: var(--sgj-amber);
  }
}

.tm-pop-thumbs {
  display: flex;
  gap: 5px;
  margin-top: 6px;

  i {
    width: 34px;
    height: 34px;
    border-radius: 7px;
    border: 2px solid #fff;
    box-shadow: 0 1px 4px rgba(23, 27, 26, 0.2);
    background-size: cover;
    background-position: center;
    display: block;

    &.more {
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--sgj-bg-deep);
      color: var(--sgj-text-3);
      font-size: 10px;
      border: 1px dashed var(--sgj-border-card);
      box-shadow: none;
      font-style: normal;
    }

    &.empty {
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--sgj-bg-deep);
      font-size: 14px;
    }
  }
}
</style>
