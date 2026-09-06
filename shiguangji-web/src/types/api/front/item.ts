/** 前台内容条目查询参数 */
export interface FrontItemQuery {
  itemType?: string
  status?: string
  title?: string
  /** 分页页码（默认1） */
  pageNum?: number
  /** 分页大小（默认10） */
  pageSize?: number
}