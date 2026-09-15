import { describe, it, expect, vi, afterEach } from 'vitest'
import { gcj02ToWgs84 } from '../coord'

/**
 * 高德 JS API loader mock：PlaceSearch/Geocoder 以可注入回调的假类代替，
 * 每个用例通过 state.searchImpl/regeoImpl 提供桩数据。
 */
const amapMock = vi.hoisted(() => {
  const state = {
    searchImpl: null as null | ((keyword: string, cb: (status: string, result: any) => void) => void),
    regeoImpl: null as null | ((lnglat: number[], cb: (status: string, result: any) => void) => void),
    detailsImpl: null as null | ((id: string, cb: (status: string, result: any) => void) => void),
    loadCalls: [] as any[]
  }
  class FakePlaceSearch {
    constructor(public opts: any) {}
    search(keyword: string, cb: (status: string, result: any) => void) {
      state.searchImpl!(keyword, cb)
    }
    getDetails(id: string, cb: (status: string, result: any) => void) {
      state.detailsImpl!(id, cb)
    }
  }
  class FakeGeocoder {
    constructor(public opts: any = {}) {}
    getAddress(lnglat: number[], cb: (status: string, result: any) => void) {
      state.regeoImpl!(lnglat, cb)
    }
  }
  return { state, FakePlaceSearch, FakeGeocoder }
})

vi.mock('@amap/amap-jsapi-loader', () => ({
  default: {
    load: vi.fn(async (opts: any) => {
      amapMock.state.loadCalls.push(opts)
      return { PlaceSearch: amapMock.FakePlaceSearch, Geocoder: amapMock.FakeGeocoder }
    })
  }
}))

/** 每个用例独立模块实例（隔离 loadAMap 的进程内缓存） */
async function loadMod() {
  vi.resetModules()
  return await import('../map')
}

afterEach(() => {
  vi.resetModules()
  amapMock.state.searchImpl = null
  amapMock.state.regeoImpl = null
  amapMock.state.detailsImpl = null
  amapMock.state.loadCalls = []
  vi.unstubAllGlobals()
  delete (window as any)._AMapSecurityConfig
})

describe('loadAMap JS API 加载', () => {
  it('挂载安全密钥、带插件加载，且进程内只加载一次', async () => {
    const mod = await loadMod()
    await mod.loadAMap()
    await mod.loadAMap()
    expect(amapMock.state.loadCalls).toHaveLength(1)
    expect(amapMock.state.loadCalls[0]).toMatchObject({
      key: 'test-amap-key',
      version: '2.0',
      plugins: expect.arrayContaining(['AMap.PlaceSearch', 'AMap.Geocoder'])
    })
    expect(window._AMapSecurityConfig).toEqual({ securityJsCode: 'test-amap-security-code' })
  })
})

describe('searchPlaces 名称搜索（高德 PlaceSearch）', () => {
  it('解析 POI 为候选：坐标 GCJ 转 WGS、区划拼接、city 兜底', async () => {
    const mod = await loadMod()
    amapMock.state.searchImpl = (keyword, cb) => {
      expect(keyword).toBe('故宫')
      cb('complete', {
        poiList: {
          pois: [
            { name: '故宫博物院', type: '风景名胜;风景名胜;国家级景点', address: '景山前街4号', pname: '北京市', cityname: '北京市', adname: '东城区', location: { lat: 39.916311, lng: 116.397161 } },
            { name: '西湖景区', type: '风景名胜;公园;城市公园', address: [], pname: '浙江省', cityname: '杭州市', adname: '西湖区', location: { lat: 30.24, lng: 120.15 } }
          ]
        }
      })
    }
    const results = await mod.searchPlaces('故宫')
    expect(results).toHaveLength(2)
    const [r0, r1] = results
    // 坐标转 WGS-84 存表单（与真实转换函数对齐）
    expect(r0.latitude).toBeCloseTo(gcj02ToWgs84(39.916311, 116.397161)[0], 5)
    expect(r0.longitude).toBeCloseTo(gcj02ToWgs84(39.916311, 116.397161)[1], 5)
    expect(r0.title).toBe('故宫博物院')
    expect(r0.address).toBe('景山前街4号')
    expect(r0.city).toBe('北京市')
    expect(r0.country).toBe('中国')
    expect(r0.label).toContain('故宫博物院')
    // 分类 emoji 随结果返回，选中也显示放大徽标
    expect(r0.emoji).toBe('🏞️')
    expect(r1.emoji).toBe('🏞️')
    // 直辖市 province 与 city 同名，区划拼接去重
    expect(r0.label).toContain('北京市东城区')
    expect(r0.label).not.toContain('北京市北京市')
    // address 缺失（高德返回空数组）时回退区划串
    expect(r1.address).toBe('浙江省杭州市西湖区')
    expect(r1.city).toBe('杭州市')
  })

  it('无结果（no_data）返回空数组', async () => {
    const mod = await loadMod()
    amapMock.state.searchImpl = (_kw, cb) => cb('no_data', {})
    expect(await mod.searchPlaces('不存在的地方xyz')).toEqual([])
  })

  it('status=error 时抛错由调用方兜底', async () => {
    const mod = await loadMod()
    amapMock.state.searchImpl = (_kw, cb) => cb('error', 'INVALID_API_KEY')
    await expect(mod.searchPlaces('x')).rejects.toThrow()
  })
})

describe('reverseGeocode 逆地理编码（高德 Geocoder）', () => {
  it('解析 formattedAddress/最近POI/城市/国家，直辖市 city 数组回退 province', async () => {
    const mod = await loadMod()
    amapMock.state.regeoImpl = (lnglat, cb) => {
      expect(lnglat).toEqual([116.39716, 39.91634])
      cb('complete', {
        regeocode: {
          formattedAddress: '北京市东城区景山前街4号',
          addressComponent: { country: '中国', province: '北京市', city: [] },
          pois: [{ name: '故宫博物院' }]
        }
      })
    }
    const place = await mod.reverseGeocode(39.91634, 116.39716)
    expect(place).toEqual({
      title: '故宫博物院',
      address: '北京市东城区景山前街4号',
      city: '北京市',
      country: '中国'
    })
  })

  it('无 POI 时 title 回退 formattedAddress，country 缺省中国', async () => {
    const mod = await loadMod()
    amapMock.state.regeoImpl = (_lnglat, cb) =>
      cb('complete', {
        regeocode: {
          formattedAddress: '浙江省杭州市西湖区南山路12号',
          addressComponent: { province: '浙江省', city: '杭州市' },
          pois: []
        }
      })
    const place = await mod.reverseGeocode(30.23, 120.15)
    expect(place.title).toBe('浙江省杭州市西湖区南山路12号')
    expect(place.city).toBe('杭州市')
    expect(place.country).toBe('中国')
  })

  it('status=error 时抛错由调用方兜底只回填坐标', async () => {
    const mod = await loadMod()
    amapMock.state.regeoImpl = (_lnglat, cb) => cb('error', {})
    await expect(mod.reverseGeocode(30, 110)).rejects.toThrow()
  })
})

describe('getPoiEmoji POI 分类徽标', () => {
  it('按 POI 一级分类映射 emoji（酒店/医院）', async () => {
    const mod = await loadMod()
    amapMock.state.detailsImpl = (id, cb) => {
      expect(id).toBe('B001')
      cb('complete', { info: 'OK', poiList: { pois: [{ type: '住宿服务;星级酒店;酒店' }] } })
    }
    expect(await mod.getPoiEmoji('B001')).toBe('🏨')
    amapMock.state.detailsImpl = (_id, cb) =>
      cb('complete', { info: 'OK', poiList: { pois: [{ type: '医疗保健服务;综合医院;三级医院' }] } })
    expect(await mod.getPoiEmoji('B002')).toBe('🏥')
  })

  it('未收录分类回退 📍，查询失败抛错', async () => {
    const mod = await loadMod()
    amapMock.state.detailsImpl = (_id, cb) =>
      cb('complete', { info: 'OK', poiList: { pois: [{ type: '地名地址信息;门牌' }] } })
    expect(await mod.getPoiEmoji('B003')).toBe('📍')
    amapMock.state.detailsImpl = (_id, cb) => cb('error', {})
    await expect(mod.getPoiEmoji('B004')).rejects.toThrow()
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
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, json: async () => geo })))
    return await loadMod()
  }

  it('Polygon 内外判定', async () => {
    const mod = await loadModule(squareGeo)
    await mod.loadChinaPolygons()
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
    await mod.loadChinaPolygons()
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
    await mod.loadChinaPolygons()
    expect(mod.isInChina(100.5, 32.5)).toBe(true)
    expect(mod.isInChina(102, 33)).toBe(false)
  })

  it('加载失败后放行不阻塞选点', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 500 })))
    const mod = await loadMod()
    await mod.loadChinaPolygons().catch(() => {})
    expect(mod.isInChina(120, 40)).toBe(true)
  })

  it('空 GeoJSON 不缓存，保持放行', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, json: async () => ({ type: 'FeatureCollection', features: [] }) })))
    const mod = await loadMod()
    await mod.loadChinaPolygons().catch(() => {})
    expect(mod.isInChina(120, 40)).toBe(true)
  })
})
