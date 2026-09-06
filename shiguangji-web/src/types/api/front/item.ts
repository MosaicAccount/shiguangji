/** 前台内容条目查询参数 */
export interface FrontItemQuery {
  itemType?: string
  status?: string
  title?: string
  /** 标签筛选（FIND_IN_SET 精确匹配单个标签名） */
  tags?: string
  /** 分页页码（默认1） */
  pageNum?: number
  /** 分页大小（默认10） */
  pageSize?: number
}