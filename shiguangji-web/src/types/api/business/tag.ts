import type { BaseEntity, PageDomain } from '@/types/api/common'

/** 标签模块（与后端 sgj_tag.module / 字典 sgj_tag_module 对应） */
export type TagModule = 'MOVIE' | 'TV' | 'BOOK' | 'PLACE' | 'NOTE'

/** 标签 */
export interface SgjTag extends BaseEntity {
  tagId?: number
  /** 所属模块（MOVIE/TV/BOOK/PLACE/NOTE） */
  module?: TagModule
  /** 标签名称 */
  tagName?: string
  /** 显示排序 */
  sort?: number
  /** 状态（'0'启用 '1'停用；停用后不出现在前台下拉） */
  status?: string
}

/** 标签查询参数 */
export interface SgjTagQueryParams extends PageDomain {
  module?: TagModule
  tagName?: string
  status?: string
}
