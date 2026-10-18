/** 前台笔记查询参数 */
export interface FrontNoteQuery {
  /** 检索关键词（标题+正文全文匹配；整串匹配，不按空白拆多词；单个汉字切不出 token 搜不到） */
  keyword?: string
  /** 标题过滤（LIKE；0.0.5 编辑器「插入笔记链接」选择器用，后端 listWhere 既有能力） */
  title?: string
  itemId?: number
  tags?: string
  /** 分页页码（默认1） */
  pageNum?: number
  /** 分页大小（默认10） */
  pageSize?: number
}
