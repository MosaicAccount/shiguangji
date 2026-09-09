import * as echarts from 'echarts'
import AMapLoader from '@amap/amap-jsapi-loader'
import { gcj02ToWgs84 } from './coord'

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

/** JS API 2.0 命名空间（官方 loader 未带类型，按 any 使用） */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type AMapNS = any

let amapPromise: Promise<AMapNS> | null = null

/** 加载高德 JS API 2.0（含选点所需插件），进程内只加载一次 */
export function loadAMap(): Promise<AMapNS> {
  if (!amapPromise) {
    window._AMapSecurityConfig = { securityJsCode: import.meta.env.VITE_AMAP_SECURITY_CODE }
    amapPromise = AMapLoader.load({
      key: import.meta.env.VITE_AMAP_KEY,
      version: '2.0',
      plugins: ['AMap.PlaceSearch', 'AMap.Geocoder']
    })
  }
  return amapPromise
}

/** 区划串去重拼接（直辖市等 province 与 city 同名时去掉连续重复段） */
function joinDistrict(parts: (string | undefined)[]): string {
  return parts
    .filter(Boolean)
    .filter((part, i, arr) => part !== arr[i - 1])
    .join('')
}

/** 从 PlaceSearch POI（GCJ-02）解析出展示与回填字段，坐标转 WGS-84 存表单 */
function parsePoi(poi: any): PlaceResult {
  const district = joinDistrict([poi.pname, poi.cityname, poi.adname])
  const address = typeof poi.address === 'string' ? poi.address : ''
  const [wLat, wLng] = gcj02ToWgs84(poi.location.lat, poi.location.lng)
  return {
    label: address ? `${poi.name}（${district}${address}）` : `${poi.name}（${district}）`,
    latitude: wLat,
    longitude: wLng,
    title: poi.name,
    address: address || district,
    city: poi.cityname || poi.pname || '',
    country: '中国'
  }
}

/**
 * 关键词搜索地点（高德 PlaceSearch，仅中国范围数据）。
 * 搜索失败（status=error）抛错由调用方兜底提示。
 */
export async function searchPlaces(keyword: string): Promise<PlaceResult[]> {
  const AMap = await loadAMap()
  return new Promise((resolve, reject) => {
    new AMap.PlaceSearch({ pageSize: 6, extensions: 'all' }).search(keyword, (status: string, result: any) => {
      if (status === 'error') {
        reject(new Error('place search failed'))
        return
      }
      const pois = status === 'complete' ? (result?.poiList?.pois ?? []) : []
      resolve(pois.map(parsePoi))
    })
  })
}

/**
 * 逆地理编码（高德 Geocoder）：坐标（GCJ-02）解析为名称/详细地址/城市/国家。
 * 解析失败抛错由调用方兜底只回填坐标。
 */
export async function reverseGeocode(lat: number, lng: number): Promise<Omit<PickedPlace, 'latitude' | 'longitude'>> {
  const AMap = await loadAMap()
  return new Promise((resolve, reject) => {
    new AMap.Geocoder().getAddress([lng, lat], (status: string, result: any) => {
      if (status !== 'complete' || !result?.regeocode) {
        reject(new Error('reverse geocode failed: ' + status))
        return
      }
      const { regeocode } = result
      const comp = regeocode.addressComponent || {}
      // 直辖市 city 为空数组，回退用 province
      const city = typeof comp.city === 'string' && comp.city ? comp.city : comp.province || ''
      const poiName = regeocode.pois?.[0]?.name || ''
      resolve({
        title: poiName || regeocode.formattedAddress || '',
        address: regeocode.formattedAddress || '',
        city,
        country: comp.country || '中国'
      })
    })
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
