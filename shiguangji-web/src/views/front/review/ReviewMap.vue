<template>
  <section class="hd-sec">
    <div class="sheet">
      <div class="paper">
        <span class="tape"></span>
        <div class="lines"><span class="frame"></span></div>
        <div class="g-head">
          <h2 class="g-title">这一年走过的<svg viewBox="0 0 100 9" preserveAspectRatio="none"><path d="M2 5 Q12 1 24 4 T48 4 T74 3 T98 5" stroke="currentColor" stroke-width="2.4" fill="none" stroke-linecap="round" opacity=".85"/></svg></h2>
          <span class="g-count">{{ countText }}</span>
        </div>

        <div class="hd-map" ref="mapEl">
          <div class="hd-frame">
            <div class="dotgrid"></div>
            <span class="tap">点任一点 → 高德看真实位置</span>
            <span class="hd-compass" aria-hidden="true"><svg viewBox="0 0 22 28"><path d="M11 26 L11 13" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><path d="M5 15 L11 4 L17 15" fill="none" stroke="var(--hd-red)" stroke-width="2" stroke-linejoin="round"/></svg>北</span>

            <svg v-if="trail" class="trail-b" :viewBox="`0 0 ${mapW} ${mapH}`" preserveAspectRatio="none" aria-hidden="true">
              <path :d="trail" fill="none" stroke="var(--hd-line)" stroke-width="2.4" stroke-linecap="round" stroke-dasharray="9 8"/>
            </svg>

            <!-- 逐点档：时间序编号，标签侧由路线方向决定（mapLayout.planLabels） -->
            <button v-for="(c, i) in clusters" :key="'p' + i" type="button"
                    class="hd-cl" :class="[c.up && 'up', c.edge]"
                    :style="{ left: c.x.toFixed(1) + '%', top: c.y.toFixed(1) + '%' }"
                    :title="clusterTitle(c)" @click="$emit('openPlace', c.places)">
              <span class="c"><svg viewBox="0 0 46 46"><ellipse cx="23" cy="23" rx="21" ry="20"/></svg><b>{{ i + 1 }}</b></span>
              <span class="t">{{ clusterLabel(c) }}</span>
            </button>

            <!-- 聚合档：按省/大区计数，点气泡进该组的真实地图 -->
            <button v-for="g in bubbles" :key="g.key" type="button" class="hd-cl"
                    :style="{ left: g.x.toFixed(1) + '%', top: g.y.toFixed(1) + '%' }"
                    @click="$emit('openGroup', { key: g.key, level: agg!.level, places: g.places })">
              <span class="c"><svg viewBox="0 0 46 46"><ellipse cx="23" cy="23" rx="21" ry="20"/></svg><b>{{ g.places.length }}</b></span>
              <span class="t">{{ g.key }}</span>
            </button>

            <span class="fnote">{{ usePins ? '手绘示意 · 非比例地图' : `手绘示意 · 非比例 · 按${agg?.level}计数` }}</span>
            <span v-if="usePins && noCoordText" class="hd-nocoord">{{ noCoordText }} 没填坐标，没落在图上</span>
          </div>
        </div>

        <!-- 窄屏图例：地图上只留编号，名字进这里（桌面端图上直接写了名字） -->
        <div class="hd-mapkey">
          <div class="kh">{{ usePins ? '图例 · 按去过的先后' : `图例 · 每个${agg?.level}的地点数` }}</div>
          <div class="k" v-for="r in mapkeyRows" :key="r.n"><b>{{ r.n }}</b><span>{{ r.text }}</span></div>
        </div>

        <p class="hd-note">{{ noteText }}</p>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { ReviewPlace } from '@/types/api/front/review'
import {
  aggregatePlaces,
  layoutPins,
  REGION_POS,
  shouldUsePins,
  trailPath
} from './mapLayout'
import type { PinCluster } from './mapLayout'

const props = defineProps<{ places: ReviewPlace[] }>()
defineEmits<{
  (e: 'openPlace', places: ReviewPlace[]): void
  (e: 'openGroup', group: { key: string; level: '省' | '大区'; places: ReviewPlace[] }): void
}>()

const mapEl = ref<HTMLElement>()
const mapW = ref(816)
const mapH = ref(352)

/** 容器实际像素：连线 viewBox 与收边距离都以它为准（1 单位 = 1px） */
function measure() {
  const el = mapEl.value
  if (el && el.clientWidth > 0) {
    mapW.value = el.clientWidth
    mapH.value = el.clientHeight
  }
}

let ro: ResizeObserver | undefined
onMounted(() => {
  measure()
  ro = new ResizeObserver(measure)
  if (mapEl.value) ro.observe(mapEl.value)
})
onBeforeUnmount(() => ro?.disconnect())

const usePins = computed(() => shouldUsePins(props.places.length, mapW.value))

const clusters = computed(() =>
  usePins.value ? layoutPins(props.places, mapW.value) : []
)

const trail = computed(() =>
  clusters.value.length > 1 ? trailPath(clusters.value, mapW.value, mapH.value) : ''
)

/** 聚合档：位置优先取组内真实坐标的拟合中心，无坐标组用大区示意位兜底 */
const bubbles = computed<{ key: string; x: number; y: number; places: ReviewPlace[] }[]>(() => {
  if (usePins.value) return []
  const groups = aggregatePlaces(props.places).groups
  return groups.map((g) => {
    if (g.points.length > 0) {
      return { key: g.key, x: avg(g.points.map((p) => p.x)), y: avg(g.points.map((p) => p.y)), places: g.places }
    }
    const pos = REGION_POS[g.key] || [50, 50]
    return { key: g.key, x: pos[0], y: pos[1], places: g.places }
  })
})

const agg = computed(() => (usePins.value ? null : aggregatePlaces(props.places)))

const noCoordText = computed(() =>
  props.places
    .filter((p) => p.latitude == null || p.longitude == null)
    .map((p) => `${p.title}（${monthOf(p.finishDate)}）`)
    .join('、')
)

const countText = computed(() => {
  if (usePins.value) {
    return `${props.places.length} 个地方 · ${clusters.value.length} 个点落在图上`
  }
  return `${props.places.length} 个地方 · 按${agg.value?.level} ${agg.value?.groups.length} 个`
})

const noteText = computed(() =>
  usePins.value
    ? '按去过的先后串起虚线，不是路线图——两点之间没有画「怎么走」。底是手绘示意（不铺地图瓦片、不按比例），精确位置点开后交给高德；靠得近的地点会并成一个点。'
    : `点数一多就不再逐点画，改成按${agg.value?.level}计数——想看某一组，点它的气泡进真实地图。`
)

/** 窄屏图例行（桌面端隐藏，CSS 控制） */
const mapkeyRows = computed(() => {
  if (usePins.value) {
    return clusters.value.map((c, i) => ({
      n: String(i + 1),
      text: c.places.map((p) => `${p.title} · ${monthOf(p.finishDate)}`).join(' / ')
    }))
  }
  return (agg.value?.groups ?? []).map((g) => ({
    n: String(g.places.length),
    text: g.key
  }))
})

function clusterLabel(c: PinCluster): string {
  return c.places.map((p) => p.title).join(' · ')
}
function clusterTitle(c: PinCluster): string {
  return c.places.map((p) => `${p.title}（${monthOf(p.finishDate)}）`).join('、')
}

function monthOf(finishDate?: string | null): string {
  const m = finishDate ? Number(finishDate.slice(5, 7)) : 0
  return m > 0 ? `${m} 月` : ''
}

function avg(nums: number[]): number {
  return nums.reduce((s, n) => s + n, 0) / nums.length
}
</script>

<style scoped>
@import '../plan/handdrawn.css';

.hd-sec { margin-top: 30px }

/* 手绘示意底（手账点阵纸 + 指北针）裱进手绘边框：不承担地理精度，页面明写「非比例」 */
.hd-map { position: relative; height: 352px; margin-top: 6px }
.hd-frame { position: absolute; inset: 0; background: var(--hd-paper); border-radius: 12px 5px 14px 6px/6px 14px 5px 12px }
.hd-frame::after { content: ''; position: absolute; inset: 0; border: 2.6px solid var(--hd-line); border-radius: inherit; filter: url(#rough); pointer-events: none }
.hd-frame .dotgrid { position: absolute; inset: 14px; border-radius: 6px; opacity: .75;
  background-image: radial-gradient(var(--hd-line-soft) 1.3px, transparent 1.4px);
  background-size: 22px 22px; background-position: 6px 6px }
.hd-frame .tap { position: absolute; left: 22px; top: 18px; font-size: 13px; color: var(--hd-red);
  background: var(--hd-paper); padding: 1px 9px; border-radius: 9px 3px 9px 3px/3px 9px 3px 9px }
.hd-frame .trail-b { position: absolute; inset: 0; width: 100%; height: 100%; filter: url(#rough) }
.hd-frame .fnote { position: absolute; left: 22px; bottom: 20px; font-size: 13px; color: var(--hd-ink-faint);
  background: var(--hd-paper); padding: 1px 9px; border-radius: 9px 3px 9px 3px/3px 9px 3px 9px }
.hd-compass { position: absolute; right: 22px; top: 18px; display: flex; flex-direction: column; align-items: center; gap: 3px;
  color: var(--hd-ink-soft); font-size: 12px; line-height: 1 }
.hd-compass svg { width: 22px; height: 28px; filter: url(#rough) }
.hd-nocoord { position: absolute; right: 22px; bottom: 20px; max-width: 268px; text-align: right; font-size: 13px; color: var(--hd-ink-soft);
  background: var(--hd-paper); padding: 1px 9px; border-radius: 9px 3px 9px 3px/3px 9px 3px 9px }

/* 图钉 / 气泡：标签带纸色光晕，虚线经过时被干净地让开 */
.hd-cl { position: absolute; transform: translate(-50%, -50%); display: flex; flex-direction: column; align-items: center; gap: 4px;
  color: var(--hd-red); border: 0; background: none; padding: 0; cursor: pointer; font: inherit }
.hd-cl.up { flex-direction: column-reverse }
.hd-cl.l :deep(.t) { align-self: flex-start }
.hd-cl.r :deep(.t) { align-self: flex-end }
.hd-cl .c { position: relative; width: 46px; height: 46px; display: flex; align-items: center; justify-content: center }
.hd-cl .c svg { position: absolute; inset: 0; filter: url(#rough) }
.hd-cl .c svg ellipse { fill: var(--hd-amber-soft); stroke: currentColor; stroke-width: 2.2 }
.hd-cl .c b { position: relative; font-size: 17px }
.hd-cl .t { font-size: 14px; color: var(--hd-ink); background: var(--hd-paper); padding: 0 8px;
  border-radius: 9px 3px 9px 3px/3px 9px 3px 9px; white-space: nowrap;
  box-shadow: 0 0 0 5px var(--hd-paper) }
.hd-cl:hover .c svg ellipse { stroke-width: 3 }

/* 窄屏图例（桌面隐藏） */
.hd-mapkey { display: none }
.hd-mapkey .k { display: grid; grid-template-columns: 20px minmax(0, 1fr); gap: 8px; font-size: 14px; line-height: 1.85 }
.hd-mapkey .k b { font-weight: 400; color: var(--hd-red) }
.hd-mapkey .kh { font-size: 13px; color: var(--hd-ink-faint); margin-bottom: 4px }
.hd-note { font-size: 13.5px; color: var(--hd-ink-soft); margin: 16px 0 0 }

@media (max-width: 768px) {
  .hd-map { height: 300px }
  .hd-compass { display: none }
  .hd-cl .t { display: none }
  .hd-mapkey { display: block; margin-top: 12px }
  .hd-frame .fnote { font-size: 12px; max-width: 150px }
  .hd-nocoord { display: none }
}
</style>
