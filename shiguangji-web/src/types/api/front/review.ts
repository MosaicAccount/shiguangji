/** 年度回顾地点（FR-004-2）：按 finishDate 升序下发，连线次序即此序 */
export interface ReviewPlace {
  itemId?: number
  title?: string
  /** 省/州；境外地点为 null（聚合时按 country 成泡） */
  province?: string | null
  country?: string | null
  /** 任一为 null 即「无坐标」：不上图但下发，页面在图内交代 */
  latitude?: number | null
  longitude?: number | null
  finishDate?: string
  rating?: number | null
  comment?: string | null
  tags?: string | null
  coverUrl?: string | null
  photoCount?: number
}

/** 评分榜条目（FR-004-3） */
export interface ReviewTopItem {
  itemId?: number
  title?: string
  /** MOVIE / TV / BOOK：榜单行内字形（影 / 剧 / 书） */
  itemType?: string
  rating?: number | null
  finishDate?: string
  coverUrl?: string | null
}

/** 标签分布条目（FR-004-4）：已拆分清洗，按 count 降序 */
export interface ReviewTagStat {
  name: string
  count: number
}

/** 年度汇总：原始类型数（movie/tv 分开，统计卡显示「电影 n · 剧集 m」分项；合并在前端做） */
export interface ReviewSummary {
  total: number
  movie: number
  tv: number
  book: number
  place: number
  note: number
}

/** 某一年的回顾数据（GET /app/review/year 契约，见 docs/design/review-design.md §1） */
export interface ReviewYearData {
  year: number
  /** 有数据的年份降序（条目 finish_date ∪ 笔记 create_time） */
  years: number[]
  summary: ReviewSummary
  places: ReviewPlace[]
  topRated: {
    movie: ReviewTopItem[]
    book: ReviewTopItem[]
    /** 该年各类型已评分条目总数（栏头「N 条里 M 条有评分」） */
    ratedCount: { movie: number; book: number }
  }
  tags: ReviewTagStat[]
}
