import request from '@/utils/request'
import type { AjaxResult } from '@/types/api/common'
import type { CoverCandidate } from '@/types/api/business/item'

// 搜索豆瓣封面候选（issue #5，仅 MOVIE/TV/BOOK）
export function searchItemCovers(params: { itemType: string; title: string }): Promise<AjaxResult<CoverCandidate[]>> {
  return request({
    url: '/app/item/cover/search',
    method: 'get',
    params: params
  })
}

// 转存豆瓣封面到自有存储，返回 { url: /profile 相对路径 }
export function importItemCover(data: { itemType: string; sourceUrl: string; sourceId?: string }): Promise<AjaxResult<{ url: string }>> {
  return request({
    url: '/app/item/cover/import',
    method: 'post',
    data: data
  })
}

// 批量补全缺失封面（后台管理，按标题自动匹配豆瓣首个候选）
export function backfillItemCovers(params: { itemType?: string }): Promise<AjaxResult<{ updated: number; skipped: number; failed: number }>> {
  return request({
    url: '/business/item/cover/backfill',
    method: 'post',
    params: params
  })
}
