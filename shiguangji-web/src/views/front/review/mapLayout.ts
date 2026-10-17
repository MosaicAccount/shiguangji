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
export interface PinPoint<T extends MappablePlace = MappablePlace> {
  x: number
  y: number
  place: T
}

/** 并簇结果：places 保持时间序，up/edge 由标签规划写入 */
export interface PinCluster<T extends MappablePlace = MappablePlace> {
  x: number
  y: number
  /** 标签放上方（由相邻簇的路线走向决定，不按奇偶交替） */
  up: boolean
  /** 贴边内收：l=贴左边标签向右长，r=贴右边标签向左长 */
  edge: '' | 'l' | 'r'
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

/** 逐点档阈值（D-48）：390px 宽的手机里 10 个图钉必然互相压 */
export const PIN_LIMIT_DESKTOP = 10
export const PIN_LIMIT_MOBILE = 5
export const MOBILE_WIDTH = 768

export function pinLimit(containerWidth: number): number {
  return containerWidth < MOBILE_WIDTH ? PIN_LIMIT_MOBILE : PIN_LIMIT_DESKTOP
}

/** 分档判定：地点数不超过阈值 → 逐点绘制 */
export function shouldUsePins(placeCount: number, containerWidth: number): boolean {
  return placeCount <= pinLimit(containerWidth)
}

/** 等比投影：x=经度、y=-纬度（不拉伸，南半球同样落框） */
export function projectPlaces<T extends MappablePlace>(places: T[]): PinPoint<T>[] {
  return places
    .filter((p) => p.latitude != null && p.longitude != null)
    .map((p) => ({ x: p.longitude as number, y: -(p.latitude as number), place: p }))
}

/**
 * 自动缩放（真地图的自动缩放同理）：bbox 等比缩放到 [14%, 86%]，单点居中。
 * ponytail: span 下限 0.5°——同一点重复出现时防除零；真实数据里两点相距小于
 * 0.5° 会退化成单点布局，可接受（那个缩放下它们必然并簇）。
 */
export function fitPoints<T extends MappablePlace>(points: PinPoint<T>[]): PinPoint<T>[] {
  if (points.length === 0) return []
  if (points.length === 1) return [{ ...points[0], x: 50, y: 50 }]
  const xs = points.map((p) => p.x)
  const ys = points.map((p) => p.y)
  const x0 = Math.min(...xs)
  const x1 = Math.max(...xs)
  const y0 = Math.min(...ys)
  const y1 = Math.max(...ys)
  const k = Math.min(72 / Math.max(x1 - x0, 0.5), 72 / Math.max(y1 - y0, 0.5))
  const cx = (x0 + x1) / 2
  const cy = (y0 + y1) / 2
  return points.map((p) => ({ ...p, x: 50 + (p.x - cx) * k, y: 50 + (p.y - cy) * k }))
}

/** 投影 + 缩放一步到位（逐点档用） */
export function layoutPoints<T extends MappablePlace>(places: T[]): PinPoint<T>[] {
  return fitPoints(projectPlaces(places))
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
      clusters.push({ x: p.x, y: p.y, up: false, edge: '', places: [p.place] })
    }
  }
  return clusters
}

/**
 * 标签侧规划：标签放哪一侧由「路线往哪边走」决定——相邻簇（前 / 后）的 y 均值在
 * 下方则标签放上方，反之放下方；按奇偶交替与路线无关，实测会把标签正好压在连线上
 * （标签有实心纸底，连线从底下穿过会被切断，看起来像断了一截）。
 * 贴左右边的簇把标签向内收，长标签不出框。
 */
export function planLabels<T extends MappablePlace>(clusters: PinCluster<T>[]): PinCluster<T>[] {
  clusters.forEach((c, i) => {
    const neighbors = [clusters[i - 1], clusters[i + 1]].filter(Boolean) as PinCluster<T>[]
    c.up = neighbors.length > 0 && neighbors.reduce((s, n) => s + n.y, 0) / neighbors.length > c.y
    c.edge = c.x < 26 ? 'l' : c.x > 74 ? 'r' : ''
  })
  return clusters
}

/** 逐点档完整布局：投影 → 缩放 → 并簇 → 标签规划 */
export function layoutPins<T extends MappablePlace>(places: T[], containerWidth: number): PinCluster<T>[] {
  return planLabels(clusterPoints(layoutPoints(places), 5, 5))
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
export const AGG_MAX = 12

/** 境外判定：country 缺省视为中国（历史数据未填国家） */
export function isChina(p: MappablePlace): boolean {
  return !p.country || p.country === '中国'
}

/** 省（或国家）级分组键：境外按国家成泡；省国皆空归「未分类」 */
export function provinceKey(p: MappablePlace): string {
  if (!isChina(p)) return p.country as string
  return p.province || '未分类'
}

/** 大区级分组键：境外并入「境外」 */
export function regionKey(p: MappablePlace): string {
  if (!isChina(p)) return '境外'
  return REGION[p.province || ''] || p.province || '未分类'
}

/**
 * 聚合阶梯（D-48）：先按省试，泡数 ≤ AGG_MAX 即用，否则降到按大区。
 * 为什么需要自适应：分散型数据按省压不动（15 个地点跨 13 个省级行政区 + 2 个国家
 * 时按省是 15 个泡，超上限等于没聚合）。
 */
export function aggregatePlaces<T extends MappablePlace>(places: T[]): AggResult<T> {
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
  groups.forEach((g) => (g.points = fitPoints(projectPlaces(g.places))))
  return { level, groups }
}

/** 无坐标分组的示意位置兜底（大区级；省级无坐标组按 50,50 居中） */
export const REGION_POS: Record<string, [number, number]> = {
  华北: [62, 20], 东北: [90, 15], 华东: [84, 46], 华中: [58, 56],
  华南: [72, 78], 西南: [26, 66], 西北: [40, 28], 境外: [93, 82]
}
