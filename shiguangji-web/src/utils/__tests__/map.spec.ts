import { describe, it, expect, vi, afterEach } from 'vitest'
import { reverseGeocode } from '../map'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('reverseGeocode', () => {
  it('解析名称/详细地址/城市/国家', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          name: '故宫博物院',
          address: { state: '北京市', city: '北京市', county: '东城区', road: '景山前街', house_number: '4号', country: '中国' }
        })
      })
    )
    const place = await reverseGeocode(39.91634, 116.39716)
    // 直辖市 state/city 同名，地址拼接去重
    expect(place.title).toBe('故宫博物院')
    expect(place.address).toBe('北京市东城区景山前街4号')
    expect(place.city).toBe('北京市')
    expect(place.country).toBe('中国')
  })

  it('无 POI 名称时回退为路名+门牌', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          address: { state: '浙江省', city: '杭州市', road: '南山路', house_number: '12号', country: '中国' }
        })
      })
    )
    const place = await reverseGeocode(30.23, 120.15)
    expect(place.title).toBe('南山路12号')
    expect(place.address).toBe('浙江省杭州市南山路12号')
    expect(place.city).toBe('杭州市')
  })

  it('字段缺失时返回空串而不是 undefined', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => ({ address: { country: '中国' } }) })
    )
    const place = await reverseGeocode(30, 110)
    expect(place.title).toBe('')
    expect(place.address).toBe('')
    expect(place.city).toBe('')
    expect(place.country).toBe('中国')
  })

  it('HTTP 错误或网络失败时抛错', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }))
    await expect(reverseGeocode(30, 110)).rejects.toThrow()
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('down')))
    await expect(reverseGeocode(30, 110)).rejects.toThrow()
  })
})

describe('isInChina 中国轮廓点内判定', () => {
  const squareGeo = {
    type: 'FeatureCollection',
    features: [
      { type: 'Feature', properties: {}, geometry: { type: 'Polygon', coordinates: [[[100, 30], [101, 30], [101, 31], [100, 31], [100, 30]]] } }
    ]
  }

  async function loadModule(geo: unknown) {
    vi.resetModules()
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, json: async () => geo })))
    const mod = await import('../map')
    await mod.loadChinaPolygons()
    return mod
  }

  afterEach(() => {
    vi.resetModules()
    vi.unstubAllGlobals()
  })

  it('Polygon 内外判定', async () => {
    const mod = await loadModule(squareGeo)
    expect(mod.isInChina(100.5, 30.5)).toBe(true)
    expect(mod.isInChina(100.99, 30.99)).toBe(true)
    expect(mod.isInChina(100.5, 31.5)).toBe(false)
    expect(mod.isInChina(102, 30.5)).toBe(false)
  })

  it('MultiPolygon 任一块命中即国内', async () => {
    const geo = {
      type: 'FeatureCollection',
      features: [
        { type: 'Feature', geometry: { type: 'MultiPolygon', coordinates: [
          [[[100, 30], [101, 30], [101, 31], [100, 31], [100, 30]]],
          [[[102, 30], [103, 30], [103, 31], [102, 31], [102, 30]]]
        ] } }
      ]
    }
    const mod = await loadModule(geo)
    expect(mod.isInChina(100.5, 30.5)).toBe(true)
    expect(mod.isInChina(102.5, 30.5)).toBe(true)
    expect(mod.isInChina(101.5, 30.5)).toBe(false)
  })

  it('内环（洞）内为国外', async () => {
    const geo = {
      type: 'FeatureCollection',
      features: [
        { type: 'Feature', geometry: { type: 'Polygon', coordinates: [
          [[100, 32], [104, 32], [104, 34], [100, 34], [100, 32]],
          [[101.5, 32.5], [102.5, 32.5], [102.5, 33.5], [101.5, 33.5], [101.5, 32.5]]
        ] } }
      ]
    }
    const mod = await loadModule(geo)
    expect(mod.isInChina(100.5, 32.5)).toBe(true)
    expect(mod.isInChina(102, 33)).toBe(false)
  })

  it('加载失败后放行不阻塞选点', async () => {
    vi.resetModules()
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 500 })))
    const mod = await import('../map')
    await mod.loadChinaPolygons().catch(() => {})
    expect(mod.isInChina(120, 40)).toBe(true)
  })

  it('空 GeoJSON 不缓存，保持放行', async () => {
    vi.resetModules()
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, json: async () => ({ type: 'FeatureCollection', features: [] }) })))
    const mod = await import('../map')
    await mod.loadChinaPolygons().catch(() => {})
    expect(mod.isInChina(120, 40)).toBe(true)
  })
})

describe('searchPlaces 名称搜索', () => {
  const chinaSquare = {
    type: 'FeatureCollection',
    features: [
      { type: 'Feature', properties: {}, geometry: { type: 'MultiPolygon', coordinates: [[[[105, 25], [125, 25], [125, 45], [105, 45], [105, 25]]]] } }
    ]
  }

  function stubFetchWith(rows: unknown[]) {
    vi.stubGlobal('fetch', vi.fn(async (url: string | URL) => {
      const u = String(url)
      if (u.includes('/map/china.json')) {
        return { ok: true, json: async () => chinaSquare }
      }
      if (u.includes('/search')) {
        return { ok: true, json: async () => rows }
      }
      throw new Error('unexpected fetch: ' + u)
    }))
  }

  afterEach(() => {
    vi.resetModules()
    vi.unstubAllGlobals()
  })

  it('解析候选地点并过滤中国轮廓外结果', async () => {
    vi.resetModules()
    stubFetchWith([
      { display_name: '故宫博物院, 北京市, 中国', name: '故宫博物院', lat: '39.91634', lon: '116.39716', address: { state: '北京市', city: '北京市', road: '景山前街', country: '中国' } },
      { display_name: 'Ulan-Ude, Russia', lat: '51.83', lon: '107.58', address: { country: '俄罗斯' } }
    ])
    const mod = await import('../map')
    await mod.loadChinaPolygons()
    const results = await mod.searchPlaces('故宫')
    expect(results).toHaveLength(1)
    expect(results[0].title).toBe('故宫博物院')
    expect(results[0].latitude).toBe(39.91634)
    expect(results[0].city).toBe('北京市')
    expect(results[0].label).toContain('故宫博物院')
  })

  it('请求失败时抛错', async () => {
    vi.resetModules()
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 500 })))
    const mod = await import('../map')
    await expect(mod.searchPlaces('x')).rejects.toThrow()
  })
})
