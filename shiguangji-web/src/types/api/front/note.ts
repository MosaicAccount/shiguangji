/** 前台笔记查询参数 */
export interface FrontNoteQuery {
  /** 关键词：标题或正文模糊匹配 */
  keyword?: string
  itemId?: number
  tags?: string
  /** 分页页码（默认1） */
  pageNum?: number
  /** 分页大小（默认10） */
  pageSize?: number
}
