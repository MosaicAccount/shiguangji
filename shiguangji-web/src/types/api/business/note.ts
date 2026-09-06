import type { BaseEntity, PageDomain } from '@/types/api/common'

/** 学习笔记 */
export interface SgjNote extends BaseEntity {
  noteId?: number
  /** 关联条目ID，空为独立笔记 */
  itemId?: number
  /** 笔记标题 */
  title?: string
  /** 笔记内容（Markdown） */
  content?: string
  /** 标签 */
  tags?: string
  /** 是否公开（'0'私密 '1'公开；新增默认私密，） */
  isPublic?: string
}

/** 学习笔记查询参数 */
export interface SgjNoteQueryParams extends PageDomain {
  itemId?: number
  title?: string
  tags?: string
  /** 公开筛选（'0'私密 '1'公开，空为全部；后端 SgjNoteMapper 已支持，） */
  isPublic?: string
}