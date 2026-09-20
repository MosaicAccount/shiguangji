import request from '@/utils/request'
import type { AjaxResult } from '@/types/api/common'
import type { SgjNoteDraft, NoteDraftPayload, NoteDraftSaved } from '@/types/api/front/noteDraft'

// 草稿箱列表（当前用户的新笔记草稿；后端只回 excerpt，不下发正文）
// silent=true 用于笔记列表页的入口条：该页免登录，失效 token 也不得弹「登录状态已过期」（设计结论 21）
export function listNoteDrafts(silent = false): Promise<AjaxResult<SgjNoteDraft[]>> {
  return request({
    url: '/app/note/draft/list',
    method: 'get',
    headers: silent ? { silent: true } : {}
  })
}

// 某篇笔记未保存完的改动（编辑页静默恢复用，含正文）
export function getNoteDraftByNoteId(noteId: number): Promise<AjaxResult<SgjNoteDraft>> {
  return request({
    url: '/app/note/draft/list',
    method: 'get',
    params: { noteId }
  })
}

// 草稿箱「继续写」/ 冷启动 /note/edit?draftId= 按 id 取单条（含正文）
export function getNoteDraft(draftId: number): Promise<AjaxResult<SgjNoteDraft>> {
  return request({
    url: '/app/note/draft/' + draftId,
    method: 'get'
  })
}

// 本人的空白草稿（每人一份，含正文）：进「写笔记」时按身份取回上一份没写完的内容
// 本机 localStorage 里没有 draftId 时（换设备 / 清过缓存）就靠它，否则写第二篇会把第一篇静默覆盖
export function getBlankNoteDraft(): Promise<AjaxResult<SgjNoteDraft>> {
  return request({
    url: '/app/note/draft/blank',
    method: 'get'
  })
}

// 保存草稿：带 draftId 则更新、不带则新建
// silent=true 用于编辑页的后台自动同步：失败不弹全局提示（401 也不弹「登录状态已过期」），
// 调用方靠 promise reject 把状态条转「仅本地待同步」
export function saveNoteDraft(data: NoteDraftPayload, silent = false): Promise<AjaxResult<NoteDraftSaved>> {
  return request({
    url: '/app/note/draft',
    method: 'put',
    data,
    headers: silent ? { silent: true, repeatSubmit: false } : {}
  })
}

// 删除草稿（硬删除，不进回收站）
export function delNoteDraft(draftId: number): Promise<AjaxResult> {
  return request({
    url: '/app/note/draft/' + draftId,
    method: 'delete'
  })
}
