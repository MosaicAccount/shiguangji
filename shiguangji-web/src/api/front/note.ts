import request from '@/utils/request'
import type { AjaxResult } from '@/types/api/common'
import type { SgjNote } from '@/types/api/business/note'
import type { FrontNoteQuery } from '@/types/api/front/note'

// 前台查询笔记列表
export function listFrontNote(query: FrontNoteQuery): Promise<AjaxResult<SgjNote[]>> {
  return request({
    url: '/app/note/list',
    method: 'get',
    params: query
  })
}

// 前台查询笔记详情
export function getFrontNote(noteId: number): Promise<AjaxResult<SgjNote>> {
  return request({
    url: '/app/note/' + noteId,
    method: 'get'
  })
}

// 前台新增笔记
export function addFrontNote(data: SgjNote): Promise<AjaxResult> {
  return request({
    url: '/app/note',
    method: 'post',
    data: data
  })
}

// 前台修改笔记
export function updateFrontNote(data: SgjNote): Promise<AjaxResult> {
  return request({
    url: '/app/note',
    method: 'put',
    data: data
  })
}

// 前台删除笔记
export function delFrontNote(noteId: number): Promise<AjaxResult> {
  return request({
    url: '/app/note/' + noteId,
    method: 'delete'
  })
}