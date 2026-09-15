import request from '@/utils/request'
import type { AjaxResult, TableDataInfo } from '@/types/api/common'
import type { SgjTag, SgjTagQueryParams } from '@/types/api/business/tag'

// 查询标签列表
export function listTag(query: SgjTagQueryParams): Promise<TableDataInfo<SgjTag>> {
  return request({
    url: '/business/tag/list',
    method: 'get',
    params: query
  })
}

// 查询标签详细
export function getTag(tagId: number): Promise<AjaxResult<SgjTag>> {
  return request({
    url: '/business/tag/' + tagId,
    method: 'get'
  })
}

// 新增标签
export function addTag(data: SgjTag): Promise<AjaxResult> {
  return request({
    url: '/business/tag',
    method: 'post',
    data: data
  })
}

// 修改标签
export function updateTag(data: SgjTag): Promise<AjaxResult> {
  return request({
    url: '/business/tag',
    method: 'put',
    data: data
  })
}

// 删除标签
export function delTag(tagId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/business/tag/' + tagId,
    method: 'delete'
  })
}
