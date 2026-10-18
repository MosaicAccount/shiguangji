import type { SgjItem } from '@/types/api/business/item'
import type { SgjNote } from '@/types/api/business/note'

/** 搜索结果分组键：影单（MOVIE+TV 混排）/ 书单 / 足迹 / 笔记；空组服务端不下发 */
export type SearchGroupKey = 'movie' | 'book' | 'place' | 'note'

/** 单条结果：条目或笔记（用 noteId 是否存在区分；服务端已剔除 comment/remark 与正文） */
export type SearchRow = SgjItem | SgjNote

export interface SearchGroup {
  key: SearchGroupKey
  /** 该组命中总数（每组只下发前 10 条，组头展示总数） */
  total: number
  rows: SearchRow[]
}

export interface SearchResponse {
  keyword: string
  groups: SearchGroup[]
}

/** 分组标签（展示口径，顺序即页面顺序） */
export const SEARCH_GROUP_LABELS: Record<SearchGroupKey, string> = {
  movie: '影单',
  book: '书单',
  place: '足迹',
  note: '笔记'
}
