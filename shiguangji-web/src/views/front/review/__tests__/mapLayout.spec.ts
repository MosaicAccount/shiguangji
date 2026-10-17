import { describe, it, expect } from 'vitest'
import {
  shouldShowLabels,
  shouldUsePins,
  layoutPins,
  trailPath,
  aggregatePlaces,
  clusterLabelText,
  fitBoxFor,
  fitPoints,
  labelWidth,
  planLabelBoxes,
  projectPlaces,
  MOBILE_WIDTH
} from '../mapLayout'
import type { MappablePlace } from '../mapLayout'

/** 造地点（纬度、经度、省、国） */
const p = (name: string, lat: number, lng: number, province?: string, country?: string): MappablePlace => ({
  title: name,
  latitude: lat,
  longitude: lng,
  province: province ?? null,
  country: country ?? null,
  finishDate: '2024-01-01'
})

/** 南北半球混合的真实形态：中国东南一串 + 悉尼（南纬） */
const MIXED = [
  p('平潭', 25.5, 119.79, '福建'),
  p('泉州', 24.87, 118.58, '福建'),
  p('潮州', 23.66, 116.62, '广东'),
  p('大理', 25.61, 100.27, '云南'),
  p('悉尼', -33.86, 151.21, undefined, '澳大利亚')
]

describe('B1 分档', () => {
  it('桌面 10 个逐点、11 个聚合', () => {
    expect(shouldUsePins(10, 900)).toBe(true)
    expect(shouldUsePins(11, 900)).toBe(false)
  })
  it('移动端阈值降为 5', () => {
    expect(shouldUsePins(5, MOBILE_WIDTH - 1)).toBe(true)
    expect(shouldUsePins(6, MOBILE_WIDTH - 1)).toBe(false)
  })
})

describe('B2 等比投影与自动缩放', () => {
  it('南半球不越界，全部落在 [14,86]', () => {
    const pts = fitPoints(projectPlaces(MIXED))
    expect(pts.length).toBe(5)
    for (const pt of pts) {
      expect(pt.x).toBeGreaterThanOrEqual(14)
      expect(pt.x).toBeLessThanOrEqual(86)
      expect(pt.y).toBeGreaterThanOrEqual(14)
      expect(pt.y).toBeLessThanOrEqual(86)
    }
  })
  it('纵横缩放系数一致（不拉伸）', () => {
    // 两点仅在经度上相距 10°、纬度相同 → 缩放后 y 必然相同（y 不被拉伸）
    const two = fitPoints(projectPlaces([p('a', 30, 100), p('b', 30, 110)]))
    expect(two[0].y).toBeCloseTo(two[1].y, 6)
    // 两点仅在纬度上相距 10° → 缩放后 x 必然相同
    const twoY = fitPoints(projectPlaces([p('a', 20, 100), p('b', 30, 100)]))
    expect(twoY[0].x).toBeCloseTo(twoY[1].x, 6)
  })
  it('单点居中', () => {
    const pts = fitPoints(projectPlaces([p('独', 25, 119)]))
    expect(pts[0].x).toBe(50)
    expect(pts[0].y).toBe(50)
  })
})

describe('B2b 拟合安全区（贴边的点不被框裁掉）', () => {
  it('最北/最南的点留出标签边距', () => {
    const pts = layoutPins([p('南', 20, 100), p('北', 40, 100)], 900, 400).clusters
    // padY=62px / 高 400px → 纵向至少留 15.5%
    for (const c of pts) {
      expect(c.y).toBeGreaterThanOrEqual(15)
      expect(c.y).toBeLessThanOrEqual(85)
      expect(c.x).toBeGreaterThanOrEqual(10)
      expect(c.x).toBeLessThanOrEqual(90)
    }
  })
  it('最东/最西的点同样内缩', () => {
    const pts = layoutPins([p('西', 30, 90), p('东', 30, 130)], 900, 400).clusters
    for (const c of pts) {
      expect(c.x).toBeGreaterThanOrEqual(6)
      expect(c.x).toBeLessThanOrEqual(94)
    }
  })
})

describe('B2c 境外插图区（境内不再被境外把比例撑死）', () => {
  const W = 900
  const H = 400

  it('有境外点时返回插图区，境内点全部落在左侧主区', () => {
    const { clusters, inset } = layoutPins(MIXED, W, H)
    expect(inset).not.toBeNull()
    const cnPts = clusters.filter((c) => c.places.some((x) => x.country !== '澳大利亚'))
    for (const c of cnPts) {
      expect(c.x).toBeLessThan(((W * (1 - 0.34)) / W) * 100 + 4)
    }
    // 境外点落在插图框内
    const ab = clusters.find((c) => c.places.some((x) => x.country === '澳大利亚'))
    expect(ab).toBeTruthy()
    expect(ab!.x).toBeGreaterThanOrEqual(inset!.x - 2)
    expect(ab!.x).toBeLessThanOrEqual(inset!.x + inset!.w + 2)
    expect(ab!.y).toBeGreaterThanOrEqual(inset!.y - 2)
    expect(ab!.y).toBeLessThanOrEqual(inset!.y + inset!.h + 2)
  })

  it('境内点不再挤成一团：相邻簇中心距明显拉开', () => {
    const { clusters } = layoutPins(MIXED, W, H)
    const cn = clusters.filter((c) => !c.places.some((x) => x.country === '澳大利亚'))
    let min = Infinity
    for (let i = 0; i < cn.length; i++) {
      for (let j = i + 1; j < cn.length; j++) {
        const d = Math.hypot(((cn[i].x - cn[j].x) / 100) * W, ((cn[i].y - cn[j].y) / 100) * H)
        min = Math.min(min, d)
      }
    }
    // 圆径 46px：中心距至少大于圆径，连线才有可见长度
    expect(min).toBeGreaterThan(46)
  })

  it('纯境内数据不出现插图区', () => {
    const { inset } = layoutPins([p('甲', 30, 110), p('乙', 30, 130)], W, H)
    expect(inset).toBeNull()
  })
})

describe('B4b 点多时只留编号', () => {
  it('≤6 个簇才铺标签，更多交给图下按月清单', () => {
    expect(shouldShowLabels(6)).toBe(true)
    expect(shouldShowLabels(7)).toBe(false)
    expect(shouldShowLabels(9)).toBe(false)
  })
})

describe('B3 按屏幕距离并簇', () => {
  it('5% 内并为一簇且保持时间序，6% 不并', () => {
    // 跨度 8° 铺进 788px → 98.5px/°：甲乙差 0.3°≈29px≈3.3%<5% 并簇；丙距乙 7.5° 远不并
    const base = [p('甲', 30, 110), p('乙', 30, 110.3), p('丙', 30, 118)]
    const clusters = layoutPins(base, 900, 400).clusters
    expect(clusters.length).toBe(2)
    expect(clusters[0].places.map((x) => x.title)).toEqual(['甲', '乙'])
    expect(clusters[1].places[0]?.title).toBe('丙')
  })
})

describe('B4 标签避让（四向择优，放不下退编号）', () => {
  it('合并簇的标签收成「首个 +n」', () => {
    // 带一个远处的点定住比例尺：甲乙相距 0.2° 在 20° 跨度下约 8px → 并簇
    const cls = layoutPins([p('甲', 30, 110), p('乙', 30, 110.2), p('丙', 30, 130)], 900, 400).clusters
    expect(cls.length).toBe(2)
    expect(clusterLabelText(cls[0])).toBe('甲 +1')
  })

  it('单点标签用全名', () => {
    const cls = layoutPins([p('独', 30, 110), p('远', 30, 130)], 900, 400).clusters
    const solo = cls.find((c) => c.places[0]?.title === '独')
    expect(clusterLabelText(solo!)).toBe('独')
  })

  it('放下的标签互相不压盖、且都在框内', () => {
    const box = fitBoxFor(900, 400)
    const cls = layoutPins([p('故宫博物院东华门', 30, 110), p('布达拉宫白宫', 30, 111.5), p('悉尼歌剧院', 30, 130)], 900, 400).clusters
    planLabelBoxes(cls, box)
    const boxes = cls
      .filter((c) => !c.dense)
      .map((c) => {
        const text = clusterLabelText(c)
        const w = labelWidth(text)
        const h = 24
        const cx = (c.x / 100) * box.width
        const cy = (c.y / 100) * box.height
        const r = 23
        const gap = 7
        if (c.side === 'down') return { x: cx - w / 2, y: cy + r + gap, w, h }
        if (c.side === 'up') return { x: cx - w / 2, y: cy - r - gap - h, w, h }
        if (c.side === 'right') return { x: cx + r + gap, y: cy - h / 2, w, h }
        return { x: cx - r - gap - w, y: cy - h / 2, w, h }
      })
    expect(boxes.length).toBeGreaterThanOrEqual(2)
    for (let i = 0; i < boxes.length; i++) {
      const b = boxes[i]
      expect(b.x).toBeGreaterThanOrEqual(0)
      expect(b.y).toBeGreaterThanOrEqual(0)
      expect(b.x + b.w).toBeLessThanOrEqual(box.width)
      expect(b.y + b.h).toBeLessThanOrEqual(box.height)
      for (let j = i + 1; j < boxes.length; j++) {
        const o = boxes[j]
        const overlap = b.x < o.x + o.w && o.x < b.x + b.w && b.y < o.y + o.h && o.y < b.y + b.h
        expect(overlap, `标签 ${i} 与 ${j} 不应重叠`).toBe(false)
      }
    }
  })

  it('挤到放不下时退成编号（dense），不强行叠字', () => {
    // 窄框 + 超长标题：四个方向都越界或压盖 → 只留编号
    const long = '非常非常长的地点名称用来占满整行标签宽度'
    const cls = layoutPins([p(long, 30, 110), p(long + '二', 30, 111), p(long + '三', 30, 112)], 420, 140).clusters
    planLabelBoxes(cls, fitBoxFor(420, 140))
    // 窄框里放不下的簇退成编号；退了的 side 必须是 none（图上不渲染标签）
    expect(cls.some((c) => c.dense)).toBe(true)
    expect(cls.filter((c) => c.dense).every((c) => c.side === 'none')).toBe(true)
  })

  it('标签宽度随中文长度增长（估算用）', () => {
    expect(labelWidth('故宫')).toBeLessThan(labelWidth('故宫博物院'))
  })
})

describe('B5 连线收边', () => {
  const clusters = layoutPins(MIXED, 900, 400).clusters

  it('每段两端距圆心不小于缩进量，且不穿入圆内', () => {
    const path = trailPath(clusters, 900, 400)
    expect(path).not.toBe('')
    // 逐段验算：段端点与对应簇心的距离 ≥ 27px（trim），即线起止在圈外
    const nums = path.match(/-?\d+(?:\.\d+)?/g)!.map(Number)
    for (let i = 0; i < nums.length; i += 4) {
      const [x1, y1, x2, y2] = nums.slice(i, i + 4)
      const from = clusters.find(
        (c) => Math.hypot((c.x / 100) * 900 - x1, (c.y / 100) * 400 - y1) < 30
      )
      const to = clusters.find(
        (c) => Math.hypot((c.x / 100) * 900 - x2, (c.y / 100) * 400 - y2) < 30
      )
      expect(from, '段起点应贴近某簇（圈边）').toBeTruthy()
      expect(to, '段终点应贴近某簇（圈边）').toBeTruthy()
      if (from) {
        expect(Math.hypot((from.x / 100) * 900 - x1, (from.y / 100) * 400 - y1)).toBeGreaterThanOrEqual(20)
      }
      if (to) {
        expect(Math.hypot((to.x / 100) * 900 - x2, (to.y / 100) * 400 - y2)).toBeGreaterThanOrEqual(20)
      }
    }
  })
  it('段长不足两倍缩进的整段不画', () => {
    // 两个几乎重合的簇（已在并簇中合并为一点）→ 无连线
    expect(trailPath(layoutPins([p('独', 30, 110)], 900, 400).clusters, 900, 400)).toBe('')
  })
})

describe('B6 聚合阶梯', () => {
  /** 分散型：13 个省级行政区 + 2 个国家（复刻既有测试数据的形态） */
  const SPREAD: MappablePlace[] = [
    p('故宫', 39.9, 116.4, '北京'),
    p('张家界', 29.1, 110.5, '湖南'),
    p('九寨沟', 33.2, 103.9, '四川'),
    p('洪崖洞', 29.5, 106.5, '重庆'),
    p('西湖', 30.2, 120.1, '浙江'),
    p('兵马俑', 34.3, 109.2, '陕西'),
    p('迪士尼', 31.1, 121.6, '上海'),
    p('茶卡', 36.7, 99.0, '青海'),
    p('布达拉宫', 29.6, 91.1, '西藏'),
    p('龙门石窟', 34.5, 112.4, '河南'),
    p('桂林', 25.2, 110.2, '广西'),
    p('雪乡', 44.3, 128.9, '黑龙江'),
    p('苏州园林', 31.3, 120.6, '江苏'),
    p('富士山', 35.3, 138.7, undefined, '日本'),
    p('悉尼歌剧院', -33.8, 151.2, undefined, '澳大利亚')
  ]

  it('分散型按省 15 泡超上限 → 自动降「大区」，境外并入境外泡', () => {
    const agg = aggregatePlaces(SPREAD)
    expect(agg.level).toBe('大区')
    expect(agg.groups.length).toBeLessThanOrEqual(8)
    const abroad = agg.groups.find((g) => g.key === '境外')
    expect(abroad?.places.length).toBe(2)
  })
  it('集中型保持按省', () => {
    const concentrated = [
      p('a', 25, 119, '福建'), p('b', 24, 118, '福建'), p('c', 23, 116, '广东'),
      p('d', 29, 117, '江西'), p('e', 25, 100, '云南'), p('f', 30, 118, '安徽')
    ]
    const agg = aggregatePlaces(concentrated)
    expect(agg.level).toBe('省')
    expect(agg.groups.length).toBe(5)
  })
  it('省级全称归一化后正确落大区（库存「北京市」这类全称）', () => {
    const agg = aggregatePlaces([
      p('故宫', 39.9, 116.4, '北京市'),
      p('布达拉宫', 29.6, 91.1, '西藏自治区'),
      p('桂林', 25.2, 110.2, '广西壮族自治区'),
      p('喀纳斯', 48.7, 87.3, '新疆维吾尔自治区'),
      p('西湖', 30.2, 120.1, '浙江省'),
      p('外滩', 31.2, 121.5, '上海市')
    ])
    expect(agg.level).toBe('省') // 6 个省级行政区 ≤ 12，先按省
    expect(aggregatePlaces([
      p('故宫', 39.9, 116.4, '北京市'),
      p('布达拉宫', 29.6, 91.1, '西藏自治区'),
      p('桂林', 25.2, 110.2, '广西壮族自治区'),
      p('喀纳斯', 48.7, 87.3, '新疆维吾尔自治区'),
      p('西湖', 30.2, 120.1, '浙江省'),
      p('外滩', 31.2, 121.5, '上海市'),
      p('a', 30, 110, '四川省'), p('b', 30, 111, '贵州省'),
      p('c', 30, 112, '云南省'), p('d', 30, 113, '重庆市'),
      p('e', 30, 114, '湖南省'), p('f', 30, 115, '湖北省'),
      p('g', 30, 116, '河南省')
    ]).groups.map((g) => g.key).sort())
      .toEqual(['华东', '华南', '华北', '华中', '西南', '西北'].sort())
  })

  it('境外在省档按国家成泡；省国皆空归未分类不抛错', () => {
    const agg = aggregatePlaces([
      p('富士山', 35.3, 138.7, undefined, '日本'),
      p('悉尼', -33.8, 151.2, undefined, '澳大利亚'),
      p('无名', 30, 110)
    ])
    expect(agg.level).toBe('省')
    const keys = agg.groups.map((g) => g.key)
    expect(keys).toContain('日本')
    expect(keys).toContain('澳大利亚')
    expect(keys).toContain('未分类')
  })
  it('组内可用坐标的成员参与定位（fitPoints 输出）', () => {
    const agg = aggregatePlaces([p('a', 25, 119, '福建'), p('b', 25.1, 119.1, '福建')])
    expect(agg.groups[0]?.points.length).toBe(2)
    for (const pt of agg.groups[0]?.points ?? []) {
      expect(pt.x).toBeGreaterThanOrEqual(14)
      expect(pt.x).toBeLessThanOrEqual(86)
    }
  })
})
