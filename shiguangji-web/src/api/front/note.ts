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
export function addFrontNote(data: SgjNote): Promise<AjaxResult> {  return request({
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

/**
 * 导出为 Markdown 的下载地址。
 *
 * 走 `$download.file()` 取文件（不是 request 包：要读响应头里的文件名，且要避开全局错误弹窗）。
 * **前台笔记详情页与后台笔记管理页共用这一个接口**：归属校验在服务端（本人或管理员），
 * 一个接口才能保证两边拿到的文件逐字节相同（设计 结论 39）
 */
export function noteExportUrl(noteId: number): string {
  return '/app/note/' + noteId + '/export'
}