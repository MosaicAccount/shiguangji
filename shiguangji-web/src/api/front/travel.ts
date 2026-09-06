import request from '@/utils/request'
import type { AjaxResult } from '@/types/api/common'
import type { TravelTrajectory } from '@/types/api/front/travel'

// 查询旅行轨迹数据
export function getTravelTrajectory(): Promise<AjaxResult<TravelTrajectory>> {
  return request({
    url: '/app/travel/trajectory',
    method: 'get'
  })
}