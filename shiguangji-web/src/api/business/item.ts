import request from '@/utils/request'
import type { AjaxResult, TableDataInfo } from '@/types/api/common'
import type { SgjItem, SgjItemQueryParams } from '@/types/api/business/item'

// 查询内容条目列表
export function listItem(query: SgjItemQueryParams): Promise<TableDataInfo<SgjItem>> {
  return request({
    url: '/business/item/list',
    method: 'get',
    params: query
  })
}

// 查询内容条目详细
export function getItem(itemId: number): Promise<AjaxResult<SgjItem>> {
  return request({
    url: '/business/item/' + itemId,
    method: 'get'
  })
}

// 新增内容条目
export function addItem(data: SgjItem): Promise<AjaxResult> {
  return request({
    url: '/business/item',
    method: 'post',
    data: data
  })
}

// 修改内容条目
export function updateItem(data: SgjItem): Promise<AjaxResult> {
  return request({
    url: '/business/item',
    method: 'put',
    data: data
  })
}

// 删除内容条目
export function delItem(itemId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/business/item/' + itemId,
    method: 'delete'
  })
}