<template>
  <div class="schematic-map">
    <div v-if="!withCoords.length" class="sm-empty">还没有能上图的足迹 —— 去地点卡里给去过的地方补个坐标，或先记一笔。</div>
    <template v-else>
      <svg class="sm-art" viewBox="0 0 720 440" role="img" aria-label="手绘足迹示意图：方位真实、距离非比例">
        <defs>
          <pattern id="sm-dots" width="15" height="15" patternUnits="userSpaceOnUse">
            <circle cx="2" cy="2" r="1.1" />
          </pattern>
        </defs>

        <rect width="720" height="440" fill="url(#sm-dots)" opacity=".5" />
        <g class="sm-grid" aria-hidden="true">
          <path v-for="x in gridX" :key="'gx' + x" :d="`M${x} 24 V416`" />
          <path v-for="y in gridY" :key="'gy' + y" :d="`M30 ${y} H690`" />
        </g>
        <path class="sm-deco" d="M40 78 q14 -18 28 0 q12 -14 24 2" aria-hidden="true" />
        <path class="sm-deco sm-wave" d="M600 380 q12 6 24 0 t24 0" aria-hidden="true" />
        <path class="sm-deco sm-wave" d="M640 400 q10 5 20 0 t20 0" aria-hidden="true" />

        <!-- 路径：按日期串联（经过组心），每段中点带方向箭标 -->
        <path v-if="routeD" class="sm-route" :d="routeD" />
        <path
          v-for="(ar, i) in arrows"
          :key="'ar' + i"
          class="sm-arrow"
          d="M-4.5 -4.5 L2.5 0 L-4.5 4.5"
          :transform="`translate(${ar.x} ${ar.y}) rotate(${ar.deg})`"
          aria-hidden="true"
        />

        <!-- 单点针：编号 = 全年到访顺序 -->
        <g
          v-for="p in singles"
          :key="'p' + (p.item.itemId ?? p.x)"
          class="sm-pin"
          :transform="`translate(${p.x},${p.y})`"
          role="button"
          tabindex="0"
          :aria-label="`查看 ${p.item.title}`"
          @click="emit('select', p.item)"
          @keydown.enter="emit('select', p.item)"
        >
          <circle class="bubble" r="13" />
          <text y="4.5" text-anchor="middle">{{ p.no }}</text>
        </g>

        <!-- 城市叠针：单泡数量 + 虚线外环 + 城市小签 -->
        <g
          v-for="(c, i) in clusters"
          :key="'c' + i"
          class="sm-cluster"
          :transform="`translate(${c.cx},${c.cy})`"
          role="button"
          tabindex="0"
          :aria-label="`查看 ${cityOf(c)} 的 ${c.items.length} 处足迹`"
          @click="emit('cluster', c.items)"
          @keydown.enter="emit('cluster', c.items)"
        >
          <circle class="ring" r="19" />
          <circle class="bubble" r="15" />
          <text class="cnt" y="5" text-anchor="middle">{{ c.items.length }}</text>
          <text class="cap" y="36" text-anchor="middle">{{ cityOf(c) }} · {{ c.items.length }} 处</text>
        </g>
      </svg>

      <div class="sm-foot">
        <span class="sm-legend"><i class="lg" aria-hidden="true" />去过（编号 = 到访顺序）</span>
        <span class="sm-legend"><i class="lg cluster" aria-hidden="true" />城市针 = 同城多处</span>
        <span class="sm-note">示意位置 · 非比例</span>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts" name="SchematicMap">
import type { TravelPoint } from '@/types/api/front/travel'

/**
 * 手绘足迹图（0.0.6 终版，设计图 docs/design/mockups/handdrawn-0.0.6/travel-map.html）：
 * - 真实坐标等距圆柱相对投影（方位真实、距离非比例）；投影框按全部足迹计算，月份筛选不改变它；
 * - 编号针 = 全年到访顺序；路径按日期串联，每段中点带方向箭标；
 * - 防重叠：先按真实投影距离把同城点聚成组（不伪造间隔），散点再对簇心微位移（≤26px）；
 * - 同城多点 → 城市叠针（数量 + 虚线环 + 城市小签），点击交给上层弹「这一片」纸卡；
 * - 想去不上图（足迹图只画走过的路）。
 */
const props = defineProps<{
  /** 全部去过的地点（投影框与全年编号的依据；坐标缺失的自动过滤） */
  places: TravelPoint[]
  /** 月份筛选：'all' 或 '01'..'12'（按 finishDate 归月），只影响针与路径 */
  month?: string
}>()

const emit = defineEmits<{
  (e: 'select', point: TravelPoint): void
  (e: 'cluster', items: TravelPoint[]): void
}>()

const W = 720
const H = 440
const PAD = 52
const MIN_GAP = 52
const PUSH_MAX = 26
const SAME_SPOT = 34
const NEAR_CLUSTER = 30

const gridX = [140, 280, 420, 560]
const gridY = [110, 220, 330]

const withCoords = computed(() =>
  (props.places || []).filter(p => p.latitude != null && p.longitude != null)
)

/** 投影框：全部有坐标的足迹（月份切换不移动地图） */
const box = computed(() => {
  if (!withCoords.value.length) return null
  const xs = withCoords.value.map(p => p.longitude as number)
  const ys = withCoords.value.map(p => p.latitude as number)
  return {
    x0: Math.min(...xs), x1: Math.max(...xs),
    y0: Math.min(...ys), y1: Math.max(...ys),
    w: Math.max(...xs) - Math.min(...xs) || 0.01,
    h: Math.max(...ys) - Math.min(...ys) || 0.01
  }
})

function projX(p: TravelPoint): number {
  return PAD + (((p.longitude as number) - box.value!.x0) / box.value!.w) * (W - PAD * 2)
}

function projY(p: TravelPoint): number {
  return PAD + ((box.value!.y1 - (p.latitude as number)) / box.value!.h) * (H - PAD * 2)
}

interface Group { items: TravelPoint[]; cx: number; cy: number; absorbed?: boolean }

/** 防重叠 + 聚簇（口径与设计图一致：先聚簇、散点微位移、贴簇并入） */
function decluster(list: TravelPoint[]): Group[] {
  const groups: Group[] = []
  list.forEach(p => {
    const x = projX(p), y = projY(p)
    const g = groups.find(G => G.items.some(q => Math.hypot(x - projX(q), y - projY(q)) < SAME_SPOT))
    if (g) g.items.push(p)
    else groups.push({ items: [p], cx: x, cy: y })
  })
  groups.forEach(G => {
    G.cx = G.items.reduce((s, q) => s + projX(q), 0) / G.items.length
    G.cy = G.items.reduce((s, q) => s + projY(q), 0) / G.items.length
  })
  const fixed = groups.filter(G => G.items.length > 1)
  const singles = groups.filter(G => G.items.length === 1)
  const placed: { x: number; y: number }[] = []
  singles.forEach(G => {
    const it = G.items[0]
    const x0 = projX(it), y0 = projY(it)
    let mx = x0, my = y0
    const obs = fixed.map(F => ({ x: F.cx, y: F.cy })).concat(placed)
    for (let i = 0; i < 40; i++) {
      let moved = false
      obs.forEach(q => {
        const dx = mx - q.x, dy = my - q.y, d = Math.hypot(dx, dy)
        if (d < MIN_GAP) {
          const ang = Math.atan2(dy || 0.01, dx || 0.01), step = (MIN_GAP - d) / 2
          mx += Math.cos(ang) * step; my += Math.sin(ang) * step; moved = true
        }
      })
      const ddx = mx - x0, ddy = my - y0, dd = Math.hypot(ddx, ddy)
      if (dd > PUSH_MAX) { mx = x0 + (ddx / dd) * PUSH_MAX; my = y0 + (ddy / dd) * PUSH_MAX }
      if (!moved) break
    }
    const near = fixed.find(F => Math.hypot(mx - F.cx, my - F.cy) < NEAR_CLUSTER)
    if (near) {
      near.cx = (near.cx * near.items.length + mx) / (near.items.length + 1)
      near.cy = (near.cy * near.items.length + my) / (near.items.length + 1)
      near.items.push(it)
      G.absorbed = true
      return
    }
    G.cx = mx; G.cy = my
    placed.push({ x: mx, y: my })
  })
  return groups.filter(G => !G.absorbed)
}

const byDate = (a: TravelPoint, b: TravelPoint) =>
  String(a.finishDate || '').localeCompare(String(b.finishDate || ''))

/** 全年到访序号（月份切换不重编） */
const orderMap = computed(() => {
  const m: Record<number, number> = {}
  withCoords.value.slice().sort(byDate).forEach((p, i) => {
    if (p.itemId != null) m[p.itemId] = i + 1
  })
  return m
})

/** 可见集合：月份筛选 */
const visible = computed(() => {
  const list = withCoords.value.slice().sort(byDate)
  if (!props.month || props.month === 'all') return list
  return list.filter(p => String(p.finishDate || '').slice(5, 7) === props.month)
})

const groups = computed(() => (visible.value.length ? decluster(visible.value) : []))

const singles = computed(() =>
  groups.value
    .filter(G => G.items.length === 1)
    .map(G => ({ item: G.items[0], x: G.cx, y: G.cy, no: orderMap.value[G.items[0].itemId as number] || 1 }))
)

const clusters = computed(() => groups.value.filter(G => G.items.length > 1))

function cityOf(G: Group): string {
  return G.items[0]?.city || G.items[0]?.title || '同城'
}

/** 路径与箭标：可见集合按日期串联（经过组心） */
const routePts = computed(() =>
  visible.value.map(p => {
    const G = groups.value.find(G => G.items.includes(p))
    return G ? [G.cx, G.cy] : [projX(p), projY(p)]
  })
)

const routeD = computed(() =>
  routePts.value.length > 1
    ? 'M' + routePts.value.map(q => q[0].toFixed(1) + ',' + q[1].toFixed(1)).join(' L')
    : ''
)

const arrows = computed(() => {
  const out: { x: number; y: number; deg: number }[] = []
  for (let i = 0; i < routePts.value.length - 1; i++) {
    const [x1, y1] = routePts.value[i]
    const [x2, y2] = routePts.value[i + 1]
    out.push({
      x: x1 + (x2 - x1) * 0.55,
      y: y1 + (y2 - y1) * 0.55,
      deg: (Math.atan2(y2 - y1, x2 - x1) * 180) / Math.PI
    })
  }
  return out
})
</script>

<style scoped>
/* 手绘足迹图：颜色只用 --hd-* 令牌（.hd-app 作用域内） */
.sm-art { display: block; width: 100%; height: 100% }
.sm-art pattern circle { fill: var(--hd-ink-faint); opacity: .38 }
.sm-grid path { fill: none; stroke: var(--hd-line-soft); stroke-width: 1.1; stroke-dasharray: 3 9; opacity: .8 }
.sm-deco { stroke: var(--hd-ink-faint); stroke-width: 2; stroke-linecap: round; opacity: .5; fill: none }
.sm-wave { stroke: var(--hd-olive); opacity: .45; stroke-width: 1.8 }
.sm-route { fill: none; stroke: var(--hd-ochre); stroke-width: 2.4; stroke-dasharray: 8 7; stroke-linecap: round; opacity: .8 }
.sm-arrow { stroke: var(--hd-ochre); stroke-width: 2.2; fill: none; stroke-linecap: round; stroke-linejoin: round }

.sm-pin { cursor: pointer }
.sm-pin .bubble { fill: var(--hd-paper); stroke: var(--hd-amber-ink); stroke-width: 2 }
.sm-pin text { fill: var(--hd-amber-ink); font-size: 13px; font-family: var(--hd-hand) }
.sm-pin:hover .bubble, .sm-pin:focus-visible .bubble { stroke: var(--hd-red) }
.sm-pin:hover text, .sm-pin:focus-visible text { fill: var(--hd-red) }

.sm-cluster { cursor: pointer }
.sm-cluster .bubble { fill: var(--hd-paper); stroke: var(--hd-amber-ink); stroke-width: 2.2 }
.sm-cluster .ring { fill: none; stroke: var(--hd-amber-ink); stroke-width: 1.5; stroke-dasharray: 3 4; opacity: .75 }
.sm-cluster .cnt { fill: var(--hd-amber-ink); font-size: 13.5px; font-family: var(--hd-hand) }
.sm-cluster .cap {
  fill: var(--hd-ink-soft); font-size: 12.5px; font-family: var(--hd-hand);
  paint-order: stroke; stroke: var(--hd-paper); stroke-width: 3.5px;
}
.sm-cluster:hover .bubble, .sm-cluster:hover .ring,
.sm-cluster:focus-visible .bubble, .sm-cluster:focus-visible .ring { stroke: var(--hd-red) }
.sm-cluster:hover .cnt, .sm-cluster:hover .cap,
.sm-cluster:focus-visible .cnt, .sm-cluster:focus-visible .cap { fill: var(--hd-red) }

.sm-empty {
  display: grid; place-items: center; height: 260px;
  color: var(--hd-ink-soft); font-size: 14px; text-align: center; padding: 0 24px;
}

.sm-foot {
  display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
  margin-top: 8px; padding: 0 4px;
  font-size: 13px; color: var(--hd-ink-soft);
}
.sm-legend { display: inline-flex; align-items: center; gap: 6px }
.sm-legend .lg {
  width: 11px; height: 11px; display: inline-block;
  background: var(--hd-paper); border: 1.8px solid var(--hd-amber-ink);
  border-radius: 50% 46% 52% 48% / 48% 52% 46% 54%;
}
.sm-legend .lg.cluster {
  border: 1.5px dashed var(--hd-amber-ink);
  box-shadow: 0 0 0 2.5px var(--hd-paper), 0 0 0 4px var(--hd-amber-ink);
}
.sm-note { margin-left: auto; color: var(--hd-ink-faint) }
</style>
