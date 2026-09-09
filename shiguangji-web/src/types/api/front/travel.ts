/** 旅行轨迹点 */
export interface TravelPoint {
  itemId?: number
  title?: string
  city?: string
  country?: string
  latitude?: number
  longitude?: number
  finishDate?: string
  /** 关联照片数（地图聚合与时间线用） */
  photoCount?: number
  /** 首张照片URL（时间线缩略图/聚合牌用） */
  cover?: string
}

/** 旅行轨迹数据 */
export interface TravelTrajectory {
  /** 去过地点，按时间排序 */
  visited: TravelPoint[]
  /** 想去地点 */
  wish: TravelPoint[]
}