import * as echarts from 'echarts'

/** 地图选点确认后回填表单的地点信息（除坐标外由逆地理编码解析，可能为空） */
export interface PickedPlace {
  latitude: number
  longitude: number
  title?: string
  address?: string
  city?: string
  country?: string
}

/**
 * 逆地理编码（Nominatim，免费无 key）：坐标解析为名称/详细地址/城市/国家。
 * zoom=18 取到门牌级明细；解析失败抛错由调用方兜底只回填坐标。
 */
export async function reverseGeocode(lat: number, lng: number): Promise<Omit<PickedPlace, 'latitude' | 'longitude'>> {
  const res = await fetch(
    `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1&accept-language=zh-CN`
  )
  if (!res.ok) throw new Error(`reverse geocode failed: ${res.status}`)
  const data = await res.json()
  const a = data.address || {}
  // 直辖市等 state 与 city 同名，拼接时去掉连续重复段
  const address = [a.state || a.province, a.city || a.town || a.village, a.county || a.suburb, a.road, a.house_number]
    .filter(Boolean)
    .filter((part: string, i: number, arr: string[]) => part !== arr[i - 1])
    .join('')
  return {
    title: data.name || [a.road, a.house_number].filter(Boolean).join('') || a.city || a.town || '',
    address,
    city: a.city || a.town || a.village || a.county || '',
    country: a.country || ''
  }
}

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
