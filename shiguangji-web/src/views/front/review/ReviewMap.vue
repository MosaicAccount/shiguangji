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

            <div v-if="inset" class="hd-inset"
                 :style="{ left: inset.x + '%', top: inset.y + '%', width: inset.w + '%', height: inset.h + '%' }">
              <span class="cap">境外 · 单独示意</span>
            </div>

            <svg v-if="trail" class="trail-b" :viewBox="`0 0 ${mapW} ${mapH}`" preserveAspectRatio="none" aria-hidden="true">
              <path :d="trail" fill="none" stroke="var(--hd-line)" stroke-width="2.4" stroke-linecap="round" stroke-dasharray="9 8"/>
            </svg>

            <!-- 逐点档：时间序编号，标签侧由路线方向决定（mapLayout.planLabels） -->
            <button v-for="(c, i) in clusters" :key="'p' + i" type="button"
                    class="hd-cl" :class="['side-' + c.side, c.dense && 'dense']"
                    :style="{ left: c.x.toFixed(1) + '%', top: c.y.toFixed(1) + '%' }"
                    :title="clusterTitle(c)" @click="onClusterClick(c)">
              <span class="c">
                <svg viewBox="0 0 46 46"><ellipse cx="23" cy="23" rx="21" ry="20"/></svg><b>{{ i + 1 }}</b>
                <span v-if="!c.dense" class="t">{{ clusterLabelText(c) }}</span>
              </span>
            </button>

            <!-- 聚合档：按省/大区计数，点气泡进该组的真实地图 -->
            <button v-for="g in bubbles" :key="g.key" type="button" class="hd-cl"
                    :style="{ left: g.x.toFixed(1) + '%', top: g.y.toFixed(1) + '%' }"
                    @click="selectPlaces(g.places)">
              <span class="c">
                <svg viewBox="0 0 46 46"><ellipse cx="23" cy="23" rx="21" ry="20"/></svg><b>{{ g.places.length }}</b>
                <span v-if="!g.dense" class="t">{{ g.label }}</span>
              </span>
            </button>

            <span class="fnote">{{ usePins ? '手绘示意 · 非比例地图' : `手绘示意 · 非比例 · 按${aggLevel}计数` }}</span>
            <span v-if="usePins && noCoordText" class="hd-nocoord">{{ noCoordText }} 没填坐标，没落在图上</span>
          </div>
        </div>

        <!-- 这一年去过的地方：按月份升序，一行一条；点行看详情（图上点击只会选中这里） -->
        <div v-if="usePins || props.places.length" class="hd-mapkey shown">
          <div class="kh">这一年走过的 {{ props.places.length }} 个地方 · 按月份</div>
          <div v-for="(g, gi) in legendGroups" :key="g.title + gi" class="mk-group">
            <div class="mk-month">{{ g.title }}</div>
            <button class="k" type="button" v-for="(r, i) in g.rows" :key="r.title + i"
                    :class="{ sel: selected.includes(r.id) }"
                    @click="emit('openPlace', [placeById(r.id)])">
              <b v-if="r.badge">{{ r.badge }}</b>
              <span>{{ r.title }}<em v-if="r.meta"> · {{ r.meta }}</em></span>
              <i aria-hidden="true">›</i>
            </button>
          </div>
        </div>

        <p v-if="noteText" class="hd-note">
          <svg class="nicon" viewBox="0 0 20 20" aria-hidden="true">
            <path d="M3 15 Q6 8 10 11 T17 5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-dasharray="3 2.4"/>
            <circle cx="3" cy="15" r="2.1" fill="none" stroke="currentColor" stroke-width="1.6"/>
            <circle cx="17" cy="5" r="2.1" fill="none" stroke="currentColor" stroke-width="1.6"/>
          </svg>
          <span>{{ noteText }}</span>
        </p>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { ReviewPlace } from '@/types/api/front/review'
import {
  aggregateBubbles,
  clusterLabelText,
  fitBoxFor,
  layoutPins,
  monthSpan,
  pinClusterLimit,
  planLabelBoxes,
  shouldShowLabels,
  trailPath
} from './mapLayout'
import type { PinCluster } from './mapLayout'

const props = defineProps<{ places: ReviewPlace[] }>()
const emit = defineEmits<{
  /** 列表行点开详情（唯一详情入口） */
  (e: 'openPlace', places: ReviewPlace[]): void
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

/** 逐点布局总是先算出来（要用它数「图上会有几个点」），点数超限才退回聚合 */
const pinLayout = computed(() => layoutPins(props.places, mapW.value, mapH.value))

const usePins = computed(
  () =>
    pinLayout.value.clusters.length > 0 &&
    pinLayout.value.clusters.length <= pinClusterLimit(mapW.value)
)

const layout = computed(() =>
  usePins.value ? pinLayout.value : { clusters: [], inset: null }
)

const clusters = computed(() => {
  const list = layout.value.clusters
  if (!list.length) return list
  if (shouldShowLabels(list.length)) {
    planLabelBoxes(list, fitBoxFor(mapW.value, mapH.value))
  } else {
    // 点多时不铺标签：全部只留编号，名字进图下按月清单（否则标签必然互相压）
    list.forEach((c) => {
      c.side = 'none'
      c.dense = true
    })
  }
  return list
})

/** 境外插图区（有境外点时才出现）：境内按自身范围铺开，境外单独放进右侧小框 */
const inset = computed(() => layout.value.inset)

/** 是否有簇被合并/退成编号——有就在图下列清单，名字不能只靠图上标签 */
const needKey = computed(
  () =>
    !usePins.value ||
    clusters.value.some((c) => c.dense) ||
    clusters.value.length !== props.places.filter((p) => p.latitude != null && p.longitude != null).length
)

const trail = computed(() =>
  clusters.value.length > 1 ? trailPath(clusters.value, mapW.value, mapH.value) : ''
)

/** 聚合档：按组内真实坐标求中心拟合，气泡本身也跑同一套标签避让 */
const aggResult = computed(() =>
  usePins.value ? null : aggregateBubbles(props.places, fitBoxFor(mapW.value, mapH.value))
)

const bubbles = computed(() => {
  const res = aggResult.value
  if (!res) return []
  // 借用逐点档的簇结构跑避让：气泡共用同一套「下/上/右/左」规则
  const pseudo = res.bubbles.map((b) => {
    const span = monthSpan(b.places)
    return {
      x: b.x,
      y: b.y,
      side: 'down' as const,
      dense: false,
      label: span ? `${b.key} · ${span}` : b.key,
      places: b.places
    }
  })
  planLabelBoxes(pseudo, fitBoxFor(mapW.value, mapH.value))
  return res.bubbles.map((b, i) => ({
    ...b,
    label: pseudo[i].label,
    side: pseudo[i].side,
    dense: pseudo[i].dense
  }))
})

/** 聚合粒度文案用 */
const aggLevel = computed(() => aggResult.value?.level ?? '省')

const noCoordText = computed(() =>
  props.places
    .filter((p) => p.latitude == null || p.longitude == null)
    .map((p) => `${p.title}（${monthOf(p.finishDate)}）`)
    .join('、')
)

const countText = computed(() => {
  if (usePins.value) {
    const total = props.places.length
    const shown = clusters.value.length
    return shown === total
      ? `${total} 个地方 · ${shown} 个点`
      : `${total} 个地方 · 合为 ${shown} 个点`
  }
  return `${props.places.length} 个地方 · 按${aggLevel.value} ${bubbles.value.length} 个`
})

const noteText = computed(() =>
  usePins.value
    ? `虚线按去过的先后连，不是路线图 · 底图示意、不按比例${inset.value ? ' · 境外单独示意' : ''} · 点一下在清单里定位`
    : '点气泡 → 在下方清单里定位该档的地点'
)

/** 图下清单：永远按完成月份升序分组，一条地点一行（点行 = 看详情）。
 *  逐点档带上图上点号，聚合档不带号（没有单点可对应）。 */
interface LegendRow {
  /** itemId，用于选中高亮与打开详情（与徽标无关） */
  id: string
  badge: string
  title: string
  meta: string
}
interface LegendGroup {
  title: string
  rows: LegendRow[]
}

const legendGroups = computed<LegendGroup[]>(() => {
  // 点号表：逐点档才有意义（同一地点的行共用点号，因为可能被并簇）
  const numberByPlace = new Map<ReviewPlace, string>()
  clusters.value.forEach((c, i) => c.places.forEach((p) => numberByPlace.set(p, String(i + 1))))

  const byMonth = new Map<number, LegendRow[]>()
  props.places
    .filter((p) => p.title)
    .forEach((p) => {
      const mi = p.finishDate ? Number(String(p.finishDate).slice(5, 7)) : 0
      const list = byMonth.get(mi) || []
      list.push({
        id: String(p.itemId ?? ''),
        badge: numberByPlace.get(p) ?? '',
        title: p.title || '',
        meta: p.province || p.country || ''
      })
      byMonth.set(mi, list)
    })

  return [...byMonth.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([mi, rows]) => ({ title: mi > 0 ? `${mi} 月` : '月份未填', rows }))
})

/** 清单行回找地点（列表按标题展示，itemId 用来选中与打开详情） */
function placeById(id: string): ReviewPlace {
  return props.places.find((p) => String(p.itemId ?? '') === id) ?? ({} as ReviewPlace)
}

/** 图上选中：把对应清单行标出来并滚过去（详情只在列表点开，避免两条入口） */
const selected = ref<string[]>([])

function selectPlaces(places: ReviewPlace[]) {
  selected.value = places.map((p) => String(p.itemId ?? ''))
  nextTick(() => {
    const el = document.querySelector<HTMLElement>('.hd-mapkey .k.sel')
    el?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  })
}

function onClusterClick(c: PinCluster<ReviewPlace>) {
  selectPlaces(c.places)
}

function clusterTitle(c: PinCluster): string {
  return c.places.map((p) => `${p.title}（${monthOf(p.finishDate)}）`).join('、')
}

function monthOf(finishDate?: string | null): string {
  const m = finishDate ? Number(finishDate.slice(5, 7)) : 0
  return m > 0 ? `${m} 月` : ''
}

</script>

<style scoped>

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
.hd-inset { position: absolute; border: 2px dashed var(--hd-line-soft); border-radius: 8px 3px 9px 4px/4px 9px 3px 8px;
  pointer-events: none }
.hd-inset .cap { position: absolute; left: 8px; top: -10px; font-size: 12px; color: var(--hd-ink-faint);
  background: var(--hd-paper); padding: 0 6px; border-radius: 7px 3px 7px 3px/3px 7px 3px 7px }
.hd-nocoord { position: absolute; right: 22px; bottom: 20px; max-width: 268px; text-align: right; font-size: 13px; color: var(--hd-ink-soft);
  background: var(--hd-paper); padding: 1px 9px; border-radius: 9px 3px 9px 3px/3px 9px 3px 9px }

/* 图钉 / 气泡：按钮盒就是圆本身（46×46），标签绝对定位——
   之前按钮里塞了标签，translate(-50%,-50%) 居中的是「圆+标签」整体，
   圆心会偏移 ±(标签高/2)，导致避让规划算的位置和渲染对不上。 */
.hd-cl { position: absolute; width: 46px; height: 46px; transform: translate(-50%, -50%);
  border: 0; background: none; padding: 0; cursor: pointer; font: inherit; color: var(--hd-red) }
.hd-cl .c { position: relative; width: 46px; height: 46px; display: flex; align-items: center; justify-content: center }
.hd-cl .c > svg { position: absolute; inset: 0; filter: url(#rough) }
.hd-cl .c > svg ellipse { fill: var(--hd-amber-soft); stroke: currentColor; stroke-width: 2.2 }
.hd-cl .c > b { position: relative; font-size: 17px }

/* 标签：四向贴圆，距离与 mapLayout 的 LABEL_GAP 一致（7px） */
.hd-cl .t { position: absolute; font-size: 14px; line-height: 1; color: var(--hd-ink); background: var(--hd-paper);
  padding: 4px 8px; border-radius: 9px 3px 9px 3px/3px 9px 3px 9px; white-space: nowrap;
  box-shadow: 0 0 0 5px var(--hd-paper) }
.hd-cl.side-down .t { top: calc(100% + 7px); left: 50%; transform: translateX(-50%) }
.hd-cl.side-up .t { bottom: calc(100% + 7px); left: 50%; transform: translateX(-50%) }
.hd-cl.side-right .t { left: calc(100% + 7px); top: 50%; transform: translateY(-50%) }
.hd-cl.side-left .t { right: calc(100% + 7px); top: 50%; transform: translateY(-50%) }
.hd-cl:hover .c > svg ellipse { stroke-width: 3 }

/* 窄屏图例（桌面隐藏） */
.hd-mapkey { display: none }
.hd-mapkey.shown { display: block; margin-top: 12px }
.hd-mapkey .kh { font-size: 13px; color: var(--hd-ink-faint); margin-bottom: 6px }
.hd-mapkey .mk-group + .mk-group { margin-top: 10px }
.hd-mapkey .mk-month { font-size: 13px; color: var(--hd-ink-soft); margin-bottom: 2px }
/* 一行一条：徽标（点号）+ 地点 + 地区 + 箭头；徽标列按内容自适应，不会把「4 月」挤成两行 */
.hd-mapkey .k { display: flex; align-items: baseline; gap: 10px; width: 100%; font: inherit;
  font-size: 14.5px; line-height: 1.9; text-align: left; color: inherit; background: none;
  border: 0; border-radius: 6px; padding: 1px 6px; cursor: pointer }
.hd-mapkey .k:hover { background: var(--hd-amber-soft) }
.hd-mapkey .k.sel { background: var(--hd-amber-soft); box-shadow: inset 2px 0 0 var(--hd-red) }
.hd-mapkey .k b { flex: none; min-width: 16px; font-weight: 400; color: var(--hd-red) }
.hd-mapkey .k span { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.hd-mapkey .k i { flex: none; font-style: normal; color: var(--hd-ink-faint) }
.hd-mapkey .k em { font-style: normal; color: var(--hd-ink-faint) }
.hd-note { font-size: 13.5px; color: var(--hd-ink-soft); margin: 16px 0 0;
  display: flex; align-items: flex-start; gap: 7px }
.hd-note .nicon { width: 18px; height: 18px; flex: none; margin-top: 1px; color: var(--hd-ink-faint) }

@media (max-width: 768px) {
  .hd-map { height: 300px }
  .hd-compass { display: none }
  .hd-cl .t { display: none }
  .hd-mapkey { display: block; margin-top: 12px }
  .hd-frame .fnote { font-size: 12px; max-width: 150px }
  .hd-inset { position: absolute; border: 2px dashed var(--hd-line-soft); border-radius: 8px 3px 9px 4px/4px 9px 3px 8px;
  pointer-events: none }
.hd-inset .cap { position: absolute; left: 8px; top: -10px; font-size: 12px; color: var(--hd-ink-faint);
  background: var(--hd-paper); padding: 0 6px; border-radius: 7px 3px 7px 3px/3px 7px 3px 7px }
.hd-nocoord { display: none }
}
</style>
