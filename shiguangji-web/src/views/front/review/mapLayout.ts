/**
 * 足迹示意地图的绘制规则（纯函数，供 ReviewMap.vue 消费、vitest 单测钉住）。
 *
 * 规则在原型（docs/design/mockups/review-0.0.4/review-page.html）上逐条验证过：
 * 分档 → 等比投影 → 自动缩放 → 按屏幕距离并簇 → 标签侧由路线方向决定 → 连线在圈边收住；
 * 聚合档按「省 → 大区」自适应阶梯（境外按国家成泡，大区档并入「境外」）。
 * 坐标一律为容器百分比（0~100），像素只出现在连线收边处（viewBox 与容器等尺寸）。
 */

/** 回顾地点里与绘图有关的字段（ReviewPlace 的子集） */
export interface MappablePlace {
  title?: string | null
  province?: string | null
  country?: string | null
  latitude?: number | null
  longitude?: number | null
  finishDate?: string | null
}

/** 有坐标的点（投影后，x/y 为容器百分比） */
export interface PinPoint<T = MappablePlace> {
  x: number
  y: number
  place: T
}

/** 标签方向（由避让结果决定；none 表示放不下，退成编号进图下清单） */
export type LabelSide = 'down' | 'up' | 'left' | 'right' | 'none'

/** 并簇结果：places 保持时间序，side/dense 由标签规划写入 */
export interface PinCluster<T extends MappablePlace = MappablePlace> {
  x: number
  y: number
  /** 标签方向：四向择优，避让其他标签与图钉 */
  side: LabelSide
  /** 放不下（四向都被占）：图上只留编号，名字进图下清单 */
  dense: boolean
  places: T[]
}

/** 聚合分组 */
export interface AggGroup<T extends MappablePlace = MappablePlace> {
  key: string
  places: T[]
  /** 组内可用坐标的成员（无坐标成员在清单里交代，不参与定位） */
  points: PinPoint<T>[]
}

/** 聚合结果：level 写明所用粒度（页面须标注） */
export interface AggResult<T extends MappablePlace = MappablePlace> {
  level: '省' | '大区'
  groups: AggGroup<T>[]
}

/** 逐点档的**点数**上限：超过就聚合（连线一多必然到处穿，实测 9 个点已看不清） */
export const PIN_MAX_CLUSTERS = 6
export const PIN_MAX_CLUSTERS_MOBILE = 4
export const MOBILE_WIDTH = 768
/** 图钉圆直径（px）：并簇阈值与连线收边都以它为基准 */
export const PIN_DIAMETER_PX = 46
/** 图上最多同时铺多少个文字标签：再多就只留编号，名字交给图下按月清单 */
export const LABEL_MAX_CLUSTERS = 6

/** 当前簇数是否还适合在图上铺标签 */
export function shouldShowLabels(clusterCount: number): boolean {
  return clusterCount <= LABEL_MAX_CLUSTERS
}

export function pinClusterLimit(containerWidth: number): number {
  return containerWidth < MOBILE_WIDTH ? PIN_MAX_CLUSTERS_MOBILE : PIN_MAX_CLUSTERS
}

/** 等比投影：x=经度、y=-纬度（不拉伸，南半球同样落框） */
export function projectPlaces<T extends MappablePlace>(places: T[]): PinPoint<T>[] {
  return places
    .filter((p) => p.latitude != null && p.longitude != null)
    .map((p) => ({ x: p.longitude as number, y: -(p.latitude as number), place: p }))
}

/** 拟合安全区：容器像素尺寸 + 留给图钉圆与文字标签的像素边距 */
export interface FitBox {
  width: number
  height: number
  padX: number
  padY: number
}

/** 圆半径 23 + 标签一行 → 上下要留出标签的空间，否则贴边的点会被框裁掉 */
export function fitBoxFor(containerWidth: number, containerHeight: number): FitBox {
  const mobile = containerWidth < MOBILE_WIDTH
  return mobile
    ? { width: containerWidth, height: containerHeight, padX: 34, padY: 34 }
    : { width: containerWidth, height: containerHeight, padX: 56, padY: 62 }
}

/**
 * 自动缩放（真地图的自动缩放同理）：bbox 等比铺进容器，可选按像素安全区内缩（贴边的点不被裁掉），
 * 单点居中。传入 box 时以「像素」为尺度等比缩放（经纬度 1:1，不拉伸）；
 * 不传时退回百分比拟合（14%~86%），供无容器尺寸的场景使用。
 * ponytail: span 下限 0.5°——同一点重复出现时防除零；更近的两点在该缩放下必然并簇。
 */
export function fitPoints<P>(points: PinPoint<P>[], box?: FitBox): PinPoint<P>[] {
  if (points.length === 0) return []
  if (points.length === 1) return [{ ...points[0], x: 50, y: 50 }]
  const xs = points.map((p) => p.x)
  const ys = points.map((p) => p.y)
  const x0 = Math.min(...xs)
  const x1 = Math.max(...xs)
  const y0 = Math.min(...ys)
  const y1 = Math.max(...ys)
  const cx = (x0 + x1) / 2
  const cy = (y0 + y1) / 2

  if (!box) {
    const k = Math.min(72 / Math.max(x1 - x0, 0.5), 72 / Math.max(y1 - y0, 0.5))
    return points.map((p) => ({ ...p, x: 50 + (p.x - cx) * k, y: 50 + (p.y - cy) * k }))
  }

  const usableW = Math.max(1, box.width - box.padX * 2)
  const usableH = Math.max(1, box.height - box.padY * 2)
  const k = Math.min(usableW / Math.max(x1 - x0, 0.5), usableH / Math.max(y1 - y0, 0.5))
  return points.map((p) => ({
    ...p,
    x: ((box.width / 2 + (p.x - cx) * k) / box.width) * 100,
    y: ((box.height / 2 + (p.y - cy) * k) / box.height) * 100
  }))
}

/** 投影 + 缩放一步到位（逐点档用） */
export function layoutPoints<T extends MappablePlace>(places: T[], box?: FitBox): PinPoint<T>[] {
  return fitPoints(projectPlaces(places), box)
}

/**
 * 按屏幕距离并簇（真地图在某个缩放下也这么干）：贪心，命中簇取位置均值。
 * gap 单位是百分比——「屏幕距离」在横竖两个轴上不是同一把尺，故分开给。
 */
export function clusterPoints<T extends MappablePlace>(
  points: PinPoint<T>[],
  gapX: number,
  gapY: number
): PinCluster<T>[] {
  const clusters: PinCluster<T>[] = []
  for (const p of points) {
    const hit = clusters.find(
      (c) => Math.abs(c.x - p.x) < gapX && Math.abs(c.y - p.y) < gapY
    )
    if (hit) {
      hit.x = (hit.x * hit.places.length + p.x) / (hit.places.length + 1)
      hit.y = (hit.y * hit.places.length + p.y) / (hit.places.length + 1)
      hit.places.push(p.place)
    } else {
      clusters.push({ x: p.x, y: p.y, side: 'down', dense: false, places: [p.place] })
    }
  }
  return clusters
}

/** 标签盒（px，左上角） */
export interface LabelBox {
  x: number
  y: number
  w: number
  h: number
}

const LABEL_H = 24   // 14px 字号 × 1.6 行高 + 余量
const LABEL_GAP = 7
const LABEL_PAD = 8

/** 估算标签宽度（中文按字号、西文按 0.55 字号；加左右内边距） */
export function labelWidth(text: string, fontPx = 14): number {
  let w = 0
  for (const ch of text) {
    w += /[\u4e00-\u9fa5·]/.test(ch) ? fontPx : fontPx * 0.55
  }
  return Math.round(w + 18)
}

/** 簇标签文案：单点用全名；多点收成「首个 +n」（全名在图下清单与悬停里） */
export function clusterLabelText<T extends MappablePlace>(c: PinCluster<T>): string {
  const first = c.places[0]?.title || ''
  return c.places.length === 1 ? first : `${first} +${c.places.length - 1}`
}

function rectsOverlap(a: LabelBox, b: LabelBox): boolean {
  return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
}

function rectHitsCircle(r: LabelBox, cx: number, cy: number, radius: number): boolean {
  const nx = Math.max(r.x, Math.min(cx, r.x + r.w))
  const ny = Math.max(r.y, Math.min(cy, r.y + r.h))
  return Math.hypot(nx - cx, ny - cy) < radius
}

/**
 * 标签避让：每个簇依次尝试 下 / 上 / 右 / 左 四个方向，选第一个既不越框、
 * 不压其他标签、也不压其他图钉的位置；四向都放不下就退成编号（dense），
 * 名字搬到图下清单。按构造顺序贪心，纯函数、可单测。
 */
export function planLabelBoxes<T extends MappablePlace>(clusters: PinCluster<T>[], box: FitBox): void {
  const radius = PIN_DIAMETER_PX / 2
  const placed: LabelBox[] = []
  const circles = clusters.map((c) => ({ x: (c.x / 100) * box.width, y: (c.y / 100) * box.height }))

  clusters.forEach((c, i) => {
    const cx = circles[i].x
    const cy = circles[i].y
    const text = clusterLabelText(c)
    const w = labelWidth(text)
    const h = LABEL_H
    const candidates: { side: Exclude<LabelSide, 'none'>; x: number; y: number }[] = [
      { side: 'down', x: cx - w / 2, y: cy + radius + LABEL_GAP },
      { side: 'up', x: cx - w / 2, y: cy - radius - LABEL_GAP - h },
      { side: 'right', x: cx + radius + LABEL_GAP, y: cy - h / 2 },
      { side: 'left', x: cx - radius - LABEL_GAP - w, y: cy - h / 2 }
    ]
    const free = candidates.find((cd) => {
      const rect = { x: cd.x, y: cd.y, w, h }
      const inside =
        cd.x >= LABEL_PAD &&
        cd.y >= LABEL_PAD &&
        cd.x + w <= box.width - LABEL_PAD &&
        cd.y + h <= box.height - LABEL_PAD
      if (!inside) return false
      if (placed.some((p) => rectsOverlap(p, rect))) return false
      return !circles.some((cc, j) => j !== i && rectHitsCircle(rect, cc.x, cc.y, radius + 6))
    })
    if (free) {
      c.side = free.side
      c.dense = false
      placed.push({ x: free.x, y: free.y, w, h })
    } else {
      c.side = 'none'
      c.dense = true
    }
  })
}

/** 境外插图区：容器百分比矩形（画虚线框与「境外」标注用） */
export interface InsetRect {
  x: number
  y: number
  w: number
  h: number
}

/** 有境外点时，右侧留给插图区的宽度比例 */
export const ABROAD_RATIO = 0.34

/** 逐点档完整布局：境内 / 境外分开拟合（境外进右侧插图区）→ 各自并簇 → 时间序合并 */
export function fitSplit<P extends MappablePlace>(
  points: PinPoint<P>[],
  containerWidth: number,
  containerHeight: number
): { points: PinPoint<P>[]; inset: InsetRect | null } {
  const box = fitBoxFor(containerWidth, containerHeight)
  const cn = points.filter((p) => isChina(p.place))
  const abroad = points.filter((p) => !isChina(p.place))

  if (abroad.length === 0) {
    return { points: fitPoints(points, box), inset: null }
  }

  const mainW = containerWidth * (1 - ABROAD_RATIO)
  const mainBox: FitBox = { width: mainW, height: containerHeight, padX: box.padX, padY: box.padY }
  const insetPx = {
    x: mainW + 6,
    y: containerHeight * 0.52,
    w: containerWidth * ABROAD_RATIO - 16,
    h: containerHeight * 0.4
  }

  // 主区拟合出的是「相对主区宽」的百分比，必须换算回整幅容器（否则点会跑到插图区里）
  const cnPts = fitPoints(cn, mainBox).map((pt) => ({
    ...pt,
    x: ((pt.x / 100) * mainW / containerWidth) * 100
  }))
  // 境外点只在插图区内拟合：先按小框缩放，再平移到插图区原点
  const abPts = fitPoints(abroad, {
    width: insetPx.w,
    height: insetPx.h,
    padX: 22,
    padY: 22
  }).map((pt) => ({
    ...pt,
    x: ((insetPx.x + (pt.x / 100) * insetPx.w) / containerWidth) * 100,
    y: ((insetPx.y + (pt.y / 100) * insetPx.h) / containerHeight) * 100
  }))

  return {
    points: [...cnPts, ...abPts],
    inset: {
      x: (insetPx.x / containerWidth) * 100,
      y: (insetPx.y / containerHeight) * 100,
      w: (insetPx.w / containerWidth) * 100,
      h: (insetPx.h / containerHeight) * 100
    }
  }
}

/**
 * 把过近的点推开：像素域迭代，成对沿连线方向各推一半，最后夹回容器安全区。
 * 聚合气泡用（不同省份可能真的挨着，但气泡不能叠），确定性、无随机。
 */
export function spreadPoints<P>(points: PinPoint<P>[], minPx: number, box: FitBox, iterations = 120): PinPoint<P>[] {
  const w = box.width
  const h = box.height
  const padX = Math.max(box.padX, minPx / 2)
  const padY = Math.max(box.padY, minPx / 2)
  const px = points.map((p) => ({
    ...p,
    px: (p.x / 100) * w,
    py: (p.y / 100) * h
  }))

  for (let it = 0; it < iterations; it++) {
    let moved = false
    for (let i = 0; i < px.length; i++) {
      for (let j = i + 1; j < px.length; j++) {
        let dx = px[j].px - px[i].px
        let dy = px[j].py - px[i].py
        let d = Math.hypot(dx, dy)
        if (d >= minPx) continue
        if (d < 0.001) {
          // 完全重合：按固定角度散开，避免除零
          dx = 1
          dy = 0
          d = 1
        }
        const push = (minPx - d) / 2
        const ux = dx / d
        const uy = dy / d
        px[i].px -= ux * push
        px[i].py -= uy * push
        px[j].px += ux * push
        px[j].py += uy * push
        moved = true
      }
    }
    px.forEach((p) => {
      p.px = Math.min(Math.max(p.px, padX), w - padX)
      p.py = Math.min(Math.max(p.py, padY), h - padY)
    })
    if (!moved) break
  }

  return px.map((p) => ({ ...p, x: (p.px / w) * 100, y: (p.py / h) * 100 }))
}

/** 逐点档完整布局：投影 → 境内外分开拟合 → 并簇 → 时间序 */
export function layoutPins<T extends MappablePlace>(
  places: T[],
  containerWidth: number,
  containerHeight: number
): { clusters: PinCluster<T>[]; inset: InsetRect | null } {
  // 并簇阈值按圆径的 0.8 折成百分比：两个圆明显叠上才并。
  // 用整圆径会链式合并——缩放后同一片区的点会被一个个串成一个大簇（实测过）。
  const gapX = ((PIN_DIAMETER_PX * 0.95) / containerWidth) * 100
  const gapY = ((PIN_DIAMETER_PX * 0.95) / containerHeight) * 100

  const { points, inset } = fitSplit(projectPlaces(places), containerWidth, containerHeight)
  const clusters = clusterPoints(points, gapX, gapY)

  // 时间序：按簇内最早的地点在原数组中的位置排序（保证连线仍是「去过的先后」）
  const order = new Map<T, number>()
  places.forEach((p, i) => order.set(p, i))
  clusters.sort((a, b) => {
    const ai = Math.min(...a.places.map((p) => order.get(p) ?? 0))
    const bi = Math.min(...b.places.map((p) => order.get(p) ?? 0))
    return ai - bi
  })
  return { clusters, inset }
}

/**
 * 连线路径：相邻簇按时间序连线，两端各缩进圈半径 + 空隙（线不得穿入圆内），
 * 段长不足两倍缩进的整段不画。viewBox 与容器等尺寸，故 1 单位 = 1px。
 */
export function trailPath(
  clusters: PinCluster<MappablePlace>[],
  widthPx: number,
  heightPx: number,
  trimPx = 27
): string {
  const segs: string[] = []
  for (let i = 1; i < clusters.length; i++) {
    const a = { x: (clusters[i - 1].x / 100) * widthPx, y: (clusters[i - 1].y / 100) * heightPx }
    const b = { x: (clusters[i].x / 100) * widthPx, y: (clusters[i].y / 100) * heightPx }
    const dx = b.x - a.x
    const dy = b.y - a.y
    const len = Math.sqrt(dx * dx + dy * dy)
    if (len <= trimPx * 2) continue
    const ux = dx / len
    const uy = dy / len
    segs.push(
      `M${(a.x + ux * trimPx).toFixed(1)} ${(a.y + uy * trimPx).toFixed(1)}` +
        ` L${(b.x - ux * trimPx).toFixed(1)} ${(b.y - uy * trimPx).toFixed(1)}`
    )
  }
  return segs.join(' ')
}

/** 省级行政区 → 大区（自治区/直辖市全称照 sgj_item_place 实际存的写法；缺省回退原省名） */
export const REGION: Record<string, string> = {
  北京: '华北', 天津: '华北', 河北: '华北', 山西: '华北', 内蒙古: '华北',
  辽宁: '东北', 吉林: '东北', 黑龙江: '东北',
  上海: '华东', 江苏: '华东', 浙江: '华东', 安徽: '华东', 福建: '华东',
  江西: '华东', 山东: '华东', 台湾: '华东',
  河南: '华中', 湖北: '华中', 湖南: '华中',
  广东: '华南', 广西: '华南', 海南: '华南', 香港: '华南', 澳门: '华南',
  重庆: '西南', 四川: '西南', 贵州: '西南', 云南: '西南', 西藏: '西南',
  陕西: '西北', 甘肃: '西北', 青海: '西北', 宁夏: '西北', 新疆: '西北'
}

/** 聚合泡数上限：超过就降一级粒度（大区仅 8 个必不超，两级必然够用） */
/**
 * 省级气泡上限：超过就再上一级（大区）。
 * 取 8 而不是 12——12 个省级气泡里多半各只有 1 个地点，那等于「没聚合，只是不连线」。
 */
export const AGG_MAX = 8

/** 境外判定：country 缺省视为中国（历史数据未填国家） */
export function isChina(p: MappablePlace): boolean {
  return !p.country || p.country === '中国'
}

/** 省（或国家）级分组键：境外按国家成泡；省国皆空归「未分类」 */
export function provinceKey(p: MappablePlace): string {
  if (!isChina(p)) return p.country as string
  return p.province || '未分类'
}

/** 省级行政区全称归一化：北京市→北京、西藏自治区→西藏、广西壮族自治区→广西 */
export function normalizeProvince(province: string): string {
  return province
    .replace(/特别行政区$/, '')
    .replace(/(维吾尔|回族|壮族)?自治区$/, '')
    .replace(/省$/, '')
    .replace(/市$/, '')
}

/** 大区级分组键：境外并入「境外」；省份按全称归一化后查表（库内存「北京市」这类全称） */
export function regionKey(p: MappablePlace): string {
  if (!isChina(p)) return '境外'
  if (!p.province) return '未分类'
  return REGION[normalizeProvince(p.province)] || p.province
}

/**
 * 聚合阶梯（D-48）：先按省试，泡数 ≤ AGG_MAX 即用，否则降到按大区。
 * 为什么需要自适应：分散型数据按省压不动（15 个地点跨 13 个省级行政区 + 2 个国家
 * 时按省是 15 个泡，超上限等于没聚合）。
 */
export function aggregatePlaces<T extends MappablePlace>(places: T[], box?: FitBox): AggResult<T> {
  const tryLevel = (keyOf: (p: T) => string): AggGroup<T>[] => {
    const map = new Map<string, T[]>()
    for (const p of places) {
      const list = map.get(keyOf(p)) || []
      list.push(p)
      map.set(keyOf(p), list)
    }
    return [...map.entries()].map(([key, ps]) => ({ key, places: ps, points: [] }))
  }
  let groups = tryLevel(provinceKey)
  let level: '省' | '大区' = '省'
  if (groups.length > AGG_MAX) {
    groups = tryLevel(regionKey)
    level = '大区'
  }
  groups.forEach((g) => (g.points = fitPoints(projectPlaces(g.places), box)))
  return { level, groups }
}

/** 聚合气泡：位置 = 组内真实坐标的中心，统一拟合进安全区；无坐标的组排到最下一行 */
export interface AggBubble<T extends MappablePlace> {
  key: string
  places: T[]
  x: number
  y: number
}

export function aggregateBubbles<T extends MappablePlace>(
  places: T[],
  box: FitBox
): { level: '省' | '大区'; bubbles: AggBubble<T>[] } {
  const agg = aggregatePlaces(places)
  const located: { g: AggGroup<T>; x: number; y: number }[] = []
  const blind: AggGroup<T>[] = []

  agg.groups.forEach((g) => {
    const pts = projectPlaces(g.places)
    if (!pts.length) {
      blind.push(g)
      return
    }
    located.push({
      g,
      x: pts.reduce((s, p) => s + p.x, 0) / pts.length,
      y: pts.reduce((s, p) => s + p.y, 0) / pts.length
    })
  })

  // 气泡中心同样按「境内外分开」拟合，否则远端点会把整幅比例压扁（实测气泡挤到 6px）
  const fitted = fitSplit(
    located.map((l, i) => ({ x: l.x, y: l.y, place: { ...l.g.places[0], __i: i } as T })),
    box.width,
    box.height
  ).points

  const spread = spreadPoints(fitted, PIN_DIAMETER_PX + 6, box)
  const bubbles: AggBubble<T>[] = spread.map((pt) => {
    const idx = (pt.place as T & { __i: number }).__i
    return { key: located[idx].g.key, places: located[idx].g.places, x: pt.x, y: pt.y }
  })

  // 无坐标组排在底部一行（等分），保证不会堆到中心互相压
  const rowY = 92
  blind.forEach((g, i) => {
    bubbles.push({
      key: g.key,
      places: g.places,
      x: blind.length === 1 ? 50 : 22 + (i * 56) / Math.max(1, blind.length - 1),
      y: rowY
    })
  })
  return { level: agg.level, bubbles }
}
