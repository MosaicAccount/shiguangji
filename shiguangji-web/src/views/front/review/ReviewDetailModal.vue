<template>
  <div v-if="open" class="hd-layer" @click.self="$emit('close')">
    <!-- 点详情：手绘卡片弹性展开 -->
    <div v-if="mode === 'place' && place" class="hd-card">
      <button class="x" type="button" aria-label="关闭" @click="$emit('close')">×</button>
      <div class="hd-photo">
        <span class="tf"></span>
        <img v-if="place.coverUrl" :src="place.coverUrl" :alt="place.title || ''" />
        <span v-else class="g">{{ (place.title || '').slice(0, 1) }}</span>
      </div>
      <h3>{{ place.title }}</h3>
      <p class="sub">
        {{ year }} 年 {{ monthOf(place) }}<template v-if="regionOf(place)"> · {{ regionOf(place) }}</template>
        <template v-if="place.rating != null"> · ★ {{ place.rating }}</template>
      </p>
      <p v-if="place.comment" class="quote">{{ place.comment }}</p>
      <div v-if="tags.length" class="rowm">
        <span v-for="t in tags" :key="t" class="hd-chip">{{ t }}</span>
      </div>
      <div class="acts">
        <button class="hdbtn go" type="button" @click="locate(place)">在地图上定位 →</button>
        <button class="hdbtn" type="button" @click="viewRecord">看这条记录 →</button>
      </div>
    </div>

    <!-- 组地图：高德瓦片裱手绘框，SDK 首次打开才加载；点图钉下钻到点详情 -->
    <div v-else-if="groupView" class="hd-card">
      <button class="x" type="button" aria-label="关闭" @click="$emit('close')">×</button>
      <h3>{{ groupView.key }} · {{ groupView.places.length }} 个地方</h3>
      <p class="sub">{{ year }} 年<template v-if="groupView.level"> · 按{{ groupView.level }}</template> · 点图钉看这条记录</p>
      <div class="hd-provmap">
        <div v-show="mapState === 'ready'" ref="amapEl" class="amap"></div>
        <div v-if="mapState === 'loading'" class="hd-provempty">地图加载中…</div>
        <div v-else-if="mapState === 'error'" class="hd-provempty">地图加载失败，以下是该组地点清单</div>
        <span class="fnote">高德瓦片 · 手绘裱框 · 首次打开才加载</span>
      </div>
      <div class="hd-provlist">
        <div class="k" v-for="(p, i) in groupView.places" :key="p.itemId ?? i">
          <b>{{ i + 1 }}</b>
          <span>{{ p.title }} · {{ monthOf(p) }}{{ hasCoord(p) ? '' : '（没填坐标，没落图）' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { loadAMap } from '@/utils/map'
import type { ReviewPlace } from '@/types/api/front/review'

const props = defineProps<{
  mode: 'place' | 'group' | null
  place: ReviewPlace | null
  group: { key: string; level: '省' | '大区'; places: ReviewPlace[] } | null
  year: number
}>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'openPlace', place: ReviewPlace): void }>()
const router = useRouter()

const open = computed(() => props.mode !== null)

/** 「在地图上定位」：同一弹层切成单点组地图（聚焦该点） */
const locating = ref<ReviewPlace | null>(null)
const groupView = computed(() => {
  if (props.mode === 'group' && props.group) return props.group
  if (locating.value) return { key: locating.value.title || '定位', level: null as unknown as '省', places: [locating.value] }
  return null
})

const tags = computed(() =>
  (props.place?.tags || '').split(',').map((t) => t.trim()).filter(Boolean)
)

function locate(p: ReviewPlace) {
  locating.value = p
}
function viewRecord() {
  // 地点条目的详情在足迹页（本期不建直达锚点，进列表即达）
  router.push('/travel')
}
function monthOf(p: ReviewPlace): string {
  const m = p.finishDate ? Number(p.finishDate.slice(5, 7)) : 0
  return m > 0 ? `${m} 月` : ''
}
function regionOf(p: ReviewPlace): string {
  return p.province || p.country || ''
}
function hasCoord(p: ReviewPlace): boolean {
  return p.latitude != null && p.longitude != null
}

watch(
  () => [props.mode, props.place, props.group],
  () => {
    if (props.mode !== 'place') locating.value = null
  }
)

/* 高德按需加载（D-49）：首屏不引入 SDK，首次打开组地图才 loadAMap（进程内单例） */
const amapEl = ref<HTMLElement>()
const mapState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let map: any = null
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const markers: any[] = []

async function ensureMap() {
  if (map) return
  mapState.value = 'loading'
  try {
    const AMap = await loadAMap()
    map = new AMap.Map(amapEl.value, { zoom: 5, viewMode: '2D' })
    mapState.value = 'ready'
  } catch {
    mapState.value = 'error'
  }
}

function renderMarkers(places: ReviewPlace[]) {
  if (!map) return
  markers.splice(0).forEach((m) => map.remove(m))
  places.filter(hasCoord).forEach((p) => {
    const marker = new map.Marker({ position: [p.longitude, p.latitude], title: p.title })
    marker.on('click', () => emit('openPlace', p))
    map.add(marker)
    markers.push(marker)
  })
  if (markers.length) map.setFitView(markers)
}

watch(
  groupView,
  async (g) => {
    if (!g) return
    await nextTick()
    await ensureMap()
    renderMarkers(g.places)
  },
  { immediate: true }
)
</script>

<style scoped>
@import '../plan/handdrawn.css';

/* 弹层盖满视口；自带 plan-page 令牌作用域外的兜底不需要——组件渲染在 .plan-page 内 */
.hd-layer { position: fixed; inset: 0; z-index: 2000; display: flex; align-items: center; justify-content: center;
  padding: 26px; background: rgba(59, 52, 44, .3) }
.hd-card { position: relative; width: 100%; max-width: 520px; max-height: 92%; overflow: auto; background: var(--hd-paper);
  border-radius: 14px 5px 16px 6px/6px 16px 5px 14px; padding: 18px 20px 20px;
  box-shadow: 5px 7px 0 var(--hd-line-soft); animation: hdspring .44s cubic-bezier(.2, 1.45, .4, 1) both }
@keyframes hdspring { 0% { transform: translateY(24px) scale(.955); opacity: 0 } 62% { transform: translateY(-5px) scale(1.012) } 100% { transform: none; opacity: 1 } }
.hd-card::after { content: ''; position: absolute; inset: 0; border: 2.4px solid var(--hd-line); border-radius: inherit; filter: url(#rough); pointer-events: none }
.hd-card .x { position: absolute; right: 12px; top: 10px; width: 32px; height: 32px; border: 2px solid var(--hd-line);
  background: var(--hd-paper); border-radius: 50% 46% 52% 48%/48% 52% 46% 54%; cursor: pointer; font-size: 16px;
  line-height: 1; filter: url(#rough); z-index: 2 }

/* 照片：贴进手账（胶带 + 手绘裱框；无封面时衬线占位字） */
.hd-photo { position: relative; height: 168px; margin: 6px 0 14px; background: var(--hd-red-soft);
  border-radius: 8px 3px 9px 4px/4px 9px 3px 8px; display: flex; align-items: center; justify-content: center; overflow: hidden }
.hd-photo::after { content: ''; position: absolute; inset: 0; border: 2px solid var(--hd-line); border-radius: inherit; filter: url(#rough) }
.hd-photo img { width: 100%; height: 100%; object-fit: cover }
.hd-photo .g { font-size: 40px; color: var(--hd-red); opacity: .55 }
.hd-photo .tf { position: absolute; left: 50%; top: -11px; transform: translateX(-50%) rotate(-2deg); width: 86px; height: 22px;
  background: var(--hd-tape); border-radius: 2px; z-index: 1 }
.hd-card h3 { margin: 0 0 4px; font-size: 24px; font-weight: 400; letter-spacing: 1px }
.hd-card .sub { font-size: 14px; color: var(--hd-ink-soft); margin: 0 0 12px }
.hd-card .quote { margin: 0 0 14px; font-size: 16px; line-height: 1.7; background: var(--hd-amber-soft);
  border-radius: 10px 4px 11px 5px/5px 11px 4px 10px; padding: 10px 12px }
.hd-card .rowm { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 14px }
.hd-card .acts { display: flex; gap: 10px; flex-wrap: wrap }

/* 组地图：高德瓦片区（按需加载），无坐标/失败时清单兜底 */
.hd-provmap { position: relative; height: 250px; margin: 8px 0 12px; background: var(--hd-paper-2);
  border-radius: 12px 5px 14px 6px/6px 14px 5px 12px }
.hd-provmap::after { content: ''; position: absolute; inset: 0; border: 2.4px solid var(--hd-line); border-radius: inherit; filter: url(#rough); pointer-events: none }
.hd-provmap .amap { position: absolute; inset: 0 }
.hd-provmap .hd-provempty { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  font-size: 14px; color: var(--hd-ink-faint) }
.hd-provmap .fnote { position: absolute; left: 16px; bottom: 14px; font-size: 12.5px; color: var(--hd-ink-faint);
  background: var(--hd-paper); padding: 0 8px; border-radius: 8px 3px 8px 3px/3px 8px 3px 8px; z-index: 1 }
.hd-provlist { margin-top: 6px; border-top: 2px dashed var(--hd-line-soft); padding-top: 10px }
.hd-provlist .k { display: grid; grid-template-columns: 20px minmax(0, 1fr); gap: 8px; font-size: 14.5px; line-height: 1.85 }
.hd-provlist .k b { font-weight: 400; color: var(--hd-red) }

@media (max-width: 768px) {
  .hd-layer { align-items: flex-end; padding: 0 }
  .hd-card { max-width: none; max-height: 86vh; border-radius: 16px 16px 0 0/16px 16px 0 0 }
  .hd-photo { height: 140px }
}
</style>
