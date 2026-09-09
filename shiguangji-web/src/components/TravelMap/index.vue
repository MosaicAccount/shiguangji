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
import { loadAMap } from '@/utils/map'
import { wgs84ToGcj02 } from '@/utils/coord'
import { photoUrl } from '@/utils/sgj'
import type { TravelPoint } from '@/types/api/front/travel'

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

/** 聚合半径：坐标距离小于该值（千米）的地点照片聚合为一个扇牌 */
const CLUSTER_KM = 120

interface ClusterNode {
  lng: number
  lat: number
  points: (MapPoint & { gLng: number; gLat: number })[]
}

/** 两点间球面距离（千米） */
function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const rad = Math.PI / 180
  const dLat = (lat2 - lat1) * rad
  const dLng = (lng2 - lng1) * rad
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

/**
 * 贪心就近聚合：依次取点，落入已有簇心 120km 内则并入，否则自立新簇。
 * ponytail: O(n²) 逐点比对 + 簇心均值，个人足迹量级（数百点）足够；
 * 城市级聚合需求出现时再升级网格聚类。
 */
function buildClusters(): ClusterNode[] {
  const clusters: ClusterNode[] = []
  // 转换 GCJ-02 并附加到点（wgs84ToGcj02 返回 [纬度, 经度]），簇心均值统一用 GCJ
  const tagged: (MapPoint & { gLng: number; gLat: number })[] = []
  for (const p of [
    ...props.visited.map(p => ({ ...p, status: 'DONE' as const })),
    ...props.wish.map(p => ({ ...p, status: 'WANT' as const }))
  ]) {
    if (p.latitude == null || p.longitude == null) continue
    const [gLat, gLng] = wgs84ToGcj02(p.latitude, p.longitude)
    tagged.push({ ...p, gLng, gLat })
  }
  for (const p of tagged) {
    const hit = clusters.find(c => haversineKm(c.lat, c.lng, p.gLat, p.gLng) <= CLUSTER_KM)
    if (hit) {
      hit.points.push(p)
      hit.lng = hit.points.reduce((s, x) => s + x.gLng, 0) / hit.points.length
      hit.lat = hit.points.reduce((s, x) => s + x.gLat, 0) / hit.points.length
    } else {
      clusters.push({ lng: p.gLng, lat: p.gLat, points: [p] })
    }
  }
  return clusters
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

/** 聚合牌标签：单地点用地名；同城用城市；跨城用「XX等N地」 */
function clusterLabel(points: TravelPoint[]): string {
  if (points.length === 1) return points[0].title || '地点'
  const cities = Array.from(new Set(points.map(p => p.city).filter(Boolean))) as string[]
  if (cities.length === 1) return cities[0]
  return `${cities[0] || points[0].title}等${points.length}地`
}

/* 扇牌/圆点标记的 HTML（样式见 <style>，非 scoped 以作用于 marker 内容） */
function deckHtml(points: MapPoint[]): string {
  const { count, covers } = clusterPhotos(points)
  const cards = covers.length
    ? `<span class="tm-deck${covers.length > 1 ? '' : ' single'}">${covers
        .map(u => `<i style="background-image:url('${u}')"></i>`)
        .join('')}</span>`
    : `<span class="tm-plain-dot ${points.some(p => p.status === 'DONE') ? 'visited' : 'want'}"></span>`
  const pillText = count > 0 ? `${clusterLabel(points)} · ${count}张` : `${clusterLabel(points)} · 想去`
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
          <span class="tm-pop-name"><b>${p.title}</b><span>${count} 张 →</span></span>
          <span class="tm-pop-thumbs">
            <i style="background-image:url('${p.cover ? photoUrl(p.cover) : ''}')" aria-hidden="true"></i>
            ${extra > 0 ? `<i class="more">+${extra}</i>` : ''}
          </span>
        </button>`
    })
    .join('')
  wrap.innerHTML = `
    <div class="tm-pop-head">
      <div><div class="tm-pop-title">${clusterLabel(cluster.points)}的照片</div><div class="tm-pop-sub">附近 ${cluster.points.length} 个地点 · 共 ${clusterPhotos(cluster.points).count} 张</div></div>
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

function renderOverlays(): void {
  overlays.value.forEach(o => map.remove(o))
  overlays.value = []
  infoWindow?.close()

  // 聚合的是"照片"：有照片的簇合成扇牌；无照片的地点保持独立圆点（避免丢点）
  for (const cluster of buildClusters()) {
    const hasPhotos = clusterPhotos(cluster.points).count > 0
    if (hasPhotos) {
      addMarker(cluster.lng, cluster.lat, deckHtml(cluster.points), () => {
        infoWindow?.setContent(popoverDom(cluster))
        infoWindow?.open(map, [cluster.lng, cluster.lat])
      })
    } else {
      for (const point of cluster.points) {
        addMarker(point.gLng, point.gLat, dotHtml(point), () => emit('select', point.itemId!))
      }
    }
  }

}

async function initMap(): Promise<void> {
  if (!mapRef.value || map) return
  AMap = await loadAMap()
  map = new AMap.Map(mapRef.value, {
    zoom: 4.2,
    center: [104.5, 36.5],
    mapStyle: 'amap://styles/whitesmoke',
    viewMode: '2D',
    zooms: [3.5, 14]
  })
  infoWindow = new AMap.InfoWindow({ isCustom: true, anchor: 'bottom-center', offset: new AMap.Pixel(0, -46) })
  // 点击底图关闭照片弹卡
  map.on('click', () => infoWindow?.close())
  renderOverlays()
  window.addEventListener('resize', onResize)
}

function onResize(): void {
  map?.resize?.()
}

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
  background: rgba(255, 255, 255, 0.88);
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
  background: rgba(255, 255, 255, 0.88);
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
    border: 3px solid #fff;
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
  background: #fff;
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
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 10px 28px rgba(23, 27, 26, 0.16);
  border: 1px solid var(--sgj-border-card);
  padding: 12px;
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
    background: var(--sgj-bg-card);
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

  &:hover {
    background: var(--sgj-bg-card);
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
      background: var(--sgj-bg-card);
      color: var(--sgj-text-2);
      font-size: 10px;
      border: 1px dashed var(--sgj-border-card);
      box-shadow: none;
      font-style: normal;
    }
  }
}
</style>
