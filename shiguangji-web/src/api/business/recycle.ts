import request from '@/utils/request'
import type { AjaxResult } from '@/types/api/common'
import type { SgjItem } from '@/types/api/business/item'
import type { SgjNote } from '@/types/api/business/note'

// 回收站条目列表（登录态；博主/管理员全量可见，其他用户仅本人，；后端不分页，前端分页展示）
export function listRecycleItem(): Promise<AjaxResult<SgjItem[]>> {
  return request({
    url: '/app/item/recycle/list',
    method: 'get'
  })
}

// 恢复回收站条目（del_flag 还原为正常，回到原列表对应状态）
export function restoreRecycleItem(itemId: number): Promise<AjaxResult> {
  return request({
    url: '/app/item/recycle/restore/' + itemId,
    method: 'post'
  })
}

// 彻底删除回收站条目（物理删除，不可恢复）
export function purgeRecycleItem(itemId: number): Promise<AjaxResult> {
  return request({
    url: '/app/item/recycle/purge/' + itemId,
    method: 'post'
  })
}

// 回收站笔记列表（登录态，；后端不分页，前端分页展示）
export function listRecycleNote(): Promise<AjaxResult<SgjNote[]>> {
  return request({
    url: '/app/note/recycle/list',
    method: 'get'
  })
}

// 恢复回收站笔记（回到笔记列表）
export function restoreRecycleNote(noteId: number): Promise<AjaxResult> {
  return request({
    url: '/app/note/recycle/restore/' + noteId,
    method: 'post'
  })
}

// 彻底删除回收站笔记（物理删除，不可恢复）
export function purgeRecycleNote(noteId: number): Promise<AjaxResult> {
  return request({
    url: '/app/note/recycle/purge/' + noteId,
    method: 'post'
  })
}
