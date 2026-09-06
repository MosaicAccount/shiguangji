import request from '@/utils/request'
import type { AjaxResult } from '@/types/api/common'
import type { DashboardStatistics } from '@/types/api/business/dashboard'

// 查询后台首页统计数据
export function getDashboardStatistics(): Promise<AjaxResult<DashboardStatistics>> {
  return request({
    url: '/business/dashboard/statistics',
    method: 'get'
  })
}