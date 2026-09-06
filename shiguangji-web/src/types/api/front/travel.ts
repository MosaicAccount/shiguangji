/** 旅行轨迹点 */
export interface TravelPoint {
  itemId?: number
  title?: string
  city?: string
  country?: string
  latitude?: number
  longitude?: number
  finishDate?: string
}

/** 旅行轨迹数据 */
export interface TravelTrajectory {
  /** 去过地点，按时间排序 */
  visited: TravelPoint[]
  /** 想去地点 */
  wish: TravelPoint[]
}