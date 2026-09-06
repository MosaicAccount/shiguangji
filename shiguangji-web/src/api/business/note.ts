import request from '@/utils/request'
import type { AjaxResult, TableDataInfo } from '@/types/api/common'
import type { SgjNote, SgjNoteQueryParams } from '@/types/api/business/note'

// 查询学习笔记列表
export function listNote(query: SgjNoteQueryParams): Promise<TableDataInfo<SgjNote>> {
  return request({
    url: '/business/note/list',
    method: 'get',
    params: query
  })
}

// 查询学习笔记详细
export function getNote(noteId: number): Promise<AjaxResult<SgjNote>> {
  return request({
    url: '/business/note/' + noteId,
    method: 'get'
  })
}

// 新增学习笔记
export function addNote(data: SgjNote): Promise<AjaxResult> {
  return request({
    url: '/business/note',
    method: 'post',
    data: data
  })
}

// 修改学习笔记
export function updateNote(data: SgjNote): Promise<AjaxResult> {
  return request({
    url: '/business/note',
    method: 'put',
    data: data
  })
}

// 删除学习笔记
export function delNote(noteId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/business/note/' + noteId,
    method: 'delete'
  })
}