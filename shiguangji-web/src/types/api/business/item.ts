import type { BaseEntity, PageDomain } from '@/types/api/common'

/** 内容条目 */
export interface SgjItem extends BaseEntity {
  itemId?: number
  /** 条目类型（MOVIE/TV/BOOK/PLACE） */
  itemType?: string
  /** 标题/名称 */
  title?: string
  /** 状态（WANT/DONE） */
  status?: string
  /** 评分 */
  rating?: number
  /** 个人短评 */
  comment?: string
  /** 标签 */
  tags?: string
  /** 封面图/图片地址 */
  coverUrl?: string
  /** 开始日期 */
  startDate?: string
  /** 完成日期 */
  finishDate?: string

  // 电影/电视剧
  director?: string
  actors?: string
  genre?: string
  region?: string
  language?: string
  imdbId?: string
  doubanId?: string

  // 电影
  releaseYear?: number
  durationMinutes?: number

  // 电视剧
  startYear?: number
  endYear?: number
  seasonCount?: number
  episodeCount?: number

  // 书籍
  author?: string
  publisher?: string
  publishDate?: string
  isbn?: string
  pages?: number

  // 地点
  address?: string
  city?: string
  province?: string
  country?: string
  latitude?: number
  longitude?: number
  bestSeason?: string
  placeCategory?: string

  /** 条目照片URL，多个英文逗号分隔（逗号顺序即展示顺序） */
  photos?: string
  /** 关联照片数量（列表查询返回，徽标用） */
  photoCount?: number
  /** 首张照片URL（列表查询返回，无封面时兜底） */
  photoCover?: string
}

/** 内容条目查询参数 */
export interface SgjItemQueryParams extends PageDomain {
  itemType?: string
  title?: string
  status?: string
  tags?: string
}

/** 豆瓣封面候选（issue #5） */
export interface CoverCandidate {
  /** 豆瓣条目编号 */
  sourceId: string
  title: string
  year: string
  /** 封面图地址（后端代理相对路径，展示用，需拼 baseURL） */
  imageUrl: string
  /** 豆瓣原始图地址（转存用） */
  sourceUrl: string
}