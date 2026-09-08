import * as echarts from 'echarts'

/** 加载中国地图 GeoJSON 并注册为 echarts 'china' 地图（本地 public/map 优先，CDN 兜底） */
export async function loadChinaMap(): Promise<boolean> {
  // 本地 public/map/china.json 优先
  try {
    const localRes = await fetch('/map/china.json')
    if (localRes.ok) {
      const geoJson = await localRes.json()
      echarts.registerMap('china', geoJson)
      return true
    }
  } catch (e) {
    // ignore
  }

  // CDN 兜底
  try {
    const res = await fetch('https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json')
    if (!res.ok) return false
    const geoJson = await res.json()
    echarts.registerMap('china', geoJson)
    return true
  } catch (e) {
    return false
  }
}

/** echarts 点击值转经纬度（非数组、长度不足或非数字返回 null） */
export function toCoord(value: unknown): { lat: number; lng: number } | null {
  if (!Array.isArray(value) || value.length < 2) return null
  const lng = Number(value[0])
  const lat = Number(value[1])
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) return null
  return { lat, lng }
}
