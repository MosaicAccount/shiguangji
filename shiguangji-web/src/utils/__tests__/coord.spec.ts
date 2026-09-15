import { describe, it, expect } from 'vitest'
import { wgs84ToGcj02, gcj02ToWgs84 } from '../coord'

describe('coord GCJ-02 <-> WGS-84', () => {
  it('国内坐标存在 GCJ-02 偏移（百米量级）', () => {
    const [gLat, gLng] = wgs84ToGcj02(39.9042, 116.4074) // 北京
    const dLat = Math.abs(gLat - 39.9042)
    const dLng = Math.abs(gLng - 116.4074)
    expect(dLat).toBeGreaterThan(0.0005)
    expect(dLat).toBeLessThan(0.01)
    expect(dLng).toBeGreaterThan(0.0005)
    expect(dLng).toBeLessThan(0.01)
  })

  it('round-trip 误差在 1e-4 度（约 10 米）内', () => {
    for (const [lat, lng] of [
      [39.9042, 116.4074], // 北京
      [31.2304, 121.4737], // 上海
      [22.5431, 114.0579], // 深圳
      [45.75, 126.65] // 哈尔滨
    ]) {
      const [gLat, gLng] = wgs84ToGcj02(lat, lng)
      const [wLat, wLng] = gcj02ToWgs84(gLat, gLng)
      expect(Math.abs(wLat - lat)).toBeLessThan(1e-4)
      expect(Math.abs(wLng - lng)).toBeLessThan(1e-4)
    }
  })

  it('国外坐标原样返回不偏移', () => {
    expect(wgs84ToGcj02(51.5074, -0.1278)).toEqual([51.5074, -0.1278]) // 伦敦
    expect(gcj02ToWgs84(35.6762, 139.6503)).toEqual([35.6762, 139.6503]) // 东京
  })
})
