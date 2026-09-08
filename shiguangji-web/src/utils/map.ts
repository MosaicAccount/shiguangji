import * as echarts from 'echarts'
import { wgs84ToGcj02 } from './coord'

/** 地图选点确认后回填表单的地点信息（除坐标外由逆地理编码解析，可能为空） */
export interface PickedPlace {
  latitude: number
  longitude: number
  title?: string
  address?: string
  city?: string
  country?: string
}

/** 搜索候选地点（表单名称搜索与选点弹窗搜索共用） */
export interface PlaceResult {
  /** 下拉展示用完整描述 */
  label: string
  latitude: number
  longitude: number
  title: string
  address: string
  city: string
  country: string
}

/** 解析 Nominatim 行（jsonv2）为地点名称/地址/城市/国家 */
function parsePlace(row: any): { title: string; address: string; city: string; country: string } {
  const a = row.address || {}
  // 直辖市等 state 与 city 同名，拼接时去掉连续重复段
  const address = [a.state || a.province, a.city || a.town || a.village, a.county || a.suburb, a.road, a.house_number]
    .filter(Boolean)
    .filter((part: string, i: number, arr: string[]) => part !== arr[i - 1])
    .join('')
  return {
    title: row.name || [a.road, a.house_number].filter(Boolean).join('') || a.city || a.town || '',
    address,
    city: a.city || a.town || a.village || a.county || '',
    country: a.country || ''
  }
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
  return parsePlace(await res.json())
}

/** 关键词搜索地点（中国范围：viewbox 限定 + 中国轮廓过滤），地址明细随结果返回 */
export async function searchPlaces(keyword: string): Promise<PlaceResult[]> {
  const res = await fetch(
    `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&addressdetails=1&accept-language=zh-CN&viewbox=73,54,136,15&bounded=1&q=${encodeURIComponent(keyword)}`
  )
  if (!res.ok) throw new Error(`search failed: ${res.status}`)
  const rows = (await res.json()) as any[]
  return rows
    .map(row => ({ label: row.display_name, latitude: Number(row.lat), longitude: Number(row.lon), ...parsePlace(row) }))
    .filter(r => {
      const [gLat, gLng] = wgs84ToGcj02(r.latitude, r.longitude)
      return isInChina(gLng, gLat)
    })
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

/** 中国轮廓多边形缓存（GCJ-02，来自 public/map/china.json，与高德瓦片同坐标系） */
let chinaPolygons: number[][][][] | null = null

/** 加载中国轮廓多边形用于选点范围判定，失败由调用方降级放行 */
export async function loadChinaPolygons(): Promise<void> {
  if (chinaPolygons) return
  const res = await fetch('/map/china.json')
  if (!res.ok) throw new Error(`load china.json failed: ${res.status}`)
  const geo = await res.json()
  const polygons: number[][][][] = []
  for (const feature of geo.features || []) {
    const g = feature.geometry
    if (!g) continue
    if (g.type === 'Polygon') polygons.push(g.coordinates)
    else if (g.type === 'MultiPolygon') polygons.push(...g.coordinates)
  }
  if (!polygons.length) throw new Error('empty china geojson')
  chinaPolygons = polygons
}

/** 射线法：点是否在环内 */
function pointInRing(lng: number, lat: number, ring: number[][]): boolean {
  let inside = false
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i]
    const [xj, yj] = ring[j]
    if (yi > lat !== yj > lat && lng < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) {
      inside = !inside
    }
  }
  return inside
}

/** 判定坐标（GCJ-02 显示空间）是否在中国轮廓内；轮廓未加载成功时放行（降级不阻塞选点） */
export function isInChina(lng: number, lat: number): boolean {
  if (!chinaPolygons) return true
  return chinaPolygons.some(polygon => {
    if (!pointInRing(lng, lat, polygon[0])) return false
    return !polygon.slice(1).some(hole => pointInRing(lng, lat, hole))
  })
}
