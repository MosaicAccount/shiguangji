import type { SgjItem } from '@/types/api/business/item'
import type { SgjNote } from '@/types/api/business/note'

/** 月度趋势 */
export interface DashboardTrend {
  month: string
  movie: number
  tv: number
  book: number
  place: number
  total: number
  note: number
}

/** 后台首页统计数据 */
export interface DashboardStatistics {
  /** 各类型总数 */
  total: Record<string, number>
  /** 各类型状态数量 */
  status: Record<string, Record<string, number>>
  /** 条目总数 */
  itemTotal: number
  /** 笔记总数 */
  noteTotal: number
  /** 本月笔记新增 */
  noteMonth: number
  /** 全部记录数 */
  allTotal: number
  /** 近6个月趋势 */
  trend: DashboardTrend[]
  /** 最近条目 */
  recentItems: SgjItem[]
  /** 待办条目 */
  todoItems: SgjItem[]
  /** 最近笔记 */
  recentNotes: SgjNote[]
}