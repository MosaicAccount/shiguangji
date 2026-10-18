import request from '@/utils/request'
import type { AjaxResult } from '@/types/api/common'
import type { SearchResponse } from '@/types/api/front/search'

/** 全站聚合检索（0.0.5 FR-005-5）：影单/书单/足迹/笔记四组，空组不下发；关键词至少 2 字 */
export function searchFront(keyword: string): Promise<AjaxResult<SearchResponse>> {
  return request({
    url: '/app/search',
    method: 'get',
    params: { keyword }
  })
}
