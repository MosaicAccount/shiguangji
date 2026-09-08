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
