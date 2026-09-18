import type { BaseEntity, PageDomain } from '@/types/api/common'

/** 学习笔记 */
export interface SgjNote extends BaseEntity {
  noteId?: number
  /** 关联条目ID，空为独立笔记 */
  itemId?: number
  /** 关联条目名称（后端联表带出，展示用） */
  itemName?: string
  /** 笔记标题 */
  title?: string
  /** 笔记内容（Markdown） */
  content?: string
  /** 标签 */
  tags?: string
  /** 是否公开（'0'私密 '1'公开；新增默认私密，） */
  isPublic?: string
  /** 前台列表摘要（纯文本；仅前台列表接口返回，正文不再随列表下发） */
  excerpt?: string
  /** 全文命中次数（仅关键词检索时有值；>1 时前台显示「共 N 处命中」） */
  hitTotal?: number
}

/** 学习笔记查询参数 */
export interface SgjNoteQueryParams extends PageDomain {
  itemId?: number
  /** 检索关键词（标题+正文全文匹配；单个汉字切不出 token 搜不到） */
  keyword?: string
  tags?: string
  /** 公开筛选（'0'私密 '1'公开，空为全部；后端 SgjNoteMapper 已支持，） */
  isPublic?: string
}