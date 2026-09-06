import request from '@/utils/request'
import type { AjaxResult } from '@/types/api/common'
import type { FrontHomeData } from '@/types/api/front/home'
import type { SgjItem } from '@/types/api/business/item'

// 查询前台首页数据
export function getFrontHomeData(): Promise<AjaxResult<FrontHomeData>> {
  return request({
    url: '/app/home/index',
    method: 'get'
  })
}

// 分页查询已完成时间线（我的时光“加载更多”），返回 data 条目集合 + total 总数
export function listFrontHomeTimeline(params: { pageNum: number; pageSize: number }): Promise<AjaxResult<SgjItem[]> & { total: number }> {
  return request({
    url: '/app/home/timeline',
    method: 'get',
    params
  })
}