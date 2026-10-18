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
  /** 全文命中次数（仅关键词检索时有值；有值即在前台显示「文中出现 N 次」，1 次也显示） */
  hitTotal?: number
  /** 详情渲染用正文（后端已剥离前言与重复标题行；仅详情接口返回，content 仍为原文） */
  body?: string
  /** 保存时携带的草稿ID：服务端在保存成功的同一次操作里删掉它（非表字段） */
  draftId?: number
  /** 正文互链解析结果（0.0.5；仅前台详情接口返回，按 distinct raw 去重） */
  links?: NoteLink[]
  /** 反向链接来源（0.0.5；仅前台详情接口返回，本篇标题重名时为空数组） */
  backlinks?: NoteBacklink[]
  /** 可见范围内同名笔记数（含本篇；>1 表示标题重名，此时反链不列来源） */
  sameTitleCount?: number
}

/** 互链降级原因：目标不存在（对匿名，私密同为此值）/ 标题重名无法确定目标 */
export type NoteLinkReason = 'NOT_FOUND' | 'AMBIGUOUS'

/** 正文互链解析结果（raw 为标记原文片段，前端据此做字面量替换；成链时 reason 为空） */
export interface NoteLink {
  /** 标记在正文里的原文片段（含括号内空白原样） */
  raw: string
  /** 去空白后的目标标题 */
  title: string
  /** 目标笔记ID（成链时有值） */
  noteId?: number
  /** 降级原因（noteId 为空时有值） */
  reason?: NoteLinkReason
}

/** 反向链接来源（由来源正文实时派生，不落库） */
export interface NoteBacklink {
  noteId: number
  title: string
  updateTime?: string
  /** 含 [[本篇标题]] 的正文片段（纯文本、折叠空白） */
  snippet?: string
  /** 该来源中有效引用的处数（代码块里的不算） */
  hitCount?: number
}

/** 学习笔记查询参数 */
export interface SgjNoteQueryParams extends PageDomain {
  itemId?: number
  /** 检索关键词（标题+正文全文匹配；整串匹配，不按空白拆多词；单个汉字切不出 token 搜不到） */
  keyword?: string
  /** 标题过滤（LIKE，编辑器「插入笔记链接」选择器用；后台 listWhere 既有能力） */
  title?: string
  tags?: string
  /** 公开筛选（'0'私密 '1'公开，空为全部；后端 SgjNoteMapper 已支持，） */
  isPublic?: string
}