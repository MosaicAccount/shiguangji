import request from '@/utils/request'
import type { AjaxResult } from '@/types/api/common'
import type { SgjItem } from '@/types/api/business/item'
import type { FrontItemQuery } from '@/types/api/front/item'

// 前台查询内容条目列表
export function listFrontItem(query: FrontItemQuery): Promise<AjaxResult<SgjItem[]>> {
  return request({
    url: '/app/item/list',
    method: 'get',
    params: query
  })
}

// 前台查询内容条目详情
export function getFrontItem(itemId: number): Promise<AjaxResult<SgjItem>> {
  return request({
    url: '/app/item/' + itemId,
    method: 'get'
  })
}

// 前台新增内容条目
export function addFrontItem(data: SgjItem): Promise<AjaxResult> {
  return request({
    url: '/app/item',
    method: 'post',
    data: data
  })
}

// 前台标记条目已完成
export function completeFrontItem(itemId: number, data: Partial<SgjItem>): Promise<AjaxResult> {
  return request({
    url: '/app/item/' + itemId + '/complete',
    method: 'post',
    data: data
  })
}

// 前台编辑内容条目（仅本人/管理员）
export function updateFrontItem(itemId: number, data: Partial<SgjItem>): Promise<AjaxResult> {
  return request({
    url: '/app/item/' + itemId,
    method: 'put',
    data: data
  })
}

// 前台删除内容条目（仅本人/管理员）
export function delFrontItem(itemId: number): Promise<AjaxResult> {
  return request({
    url: '/app/item/' + itemId,
    method: 'delete'
  })
}

// 前台取消完成（DONE -> WANT，仅本人/管理员）
export function uncompleteFrontItem(itemId: number): Promise<AjaxResult> {
  return request({
    url: '/app/item/' + itemId + '/uncomplete',
    method: 'post'
  })
}