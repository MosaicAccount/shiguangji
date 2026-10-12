import request from '@/utils/request'
import type { AjaxResult } from '@/types/api/common'

/** 笔记正文插图上传的响应（0.0.2 FR-002-9）：path 是站内相对路径，改写正文用它，别用绝对 URL */
export interface NoteImageUploadResult extends AjaxResult {
  path?: string
}

/**
 * 上传一张笔记正文插图（`POST /common/upload/note-image`，需登录）
 *
 * - `repeatSubmit: false`：防重复提交守卫按 `JSON.stringify(FormData)` 比对请求体，
 *   并发上传时每次都是 `'{}'`，1 秒内会被误判成重复提交而拒绝
 * - 超时放宽到 60s：一张 10MB 的图在慢网络下跑不完默认的 10s
 */
export function uploadNoteImage(file: File): Promise<NoteImageUploadResult> {
  const form = new FormData()
  form.append('file', file)
  return request({
    url: '/common/upload/note-image',
    method: 'post',
    data: form,
    headers: { repeatSubmit: false },
    timeout: 60000
  })
}
