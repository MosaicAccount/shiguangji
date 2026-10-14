import type { SgjItem } from '@/types/api/business/item'
import type { SgjNote } from '@/types/api/business/note'

/** 前台首页汇总 */
export interface FrontHomeSummary {
  /** 各类型总数 */
  total: Record<string, number>
  /** 各类型已完成数量 */
  done: Record<string, number>
  /** 各类型心愿数量（仅 WANT；0.0.3 起三态显式拆列） */
  wish: Record<string, number>
  /** 各类型进行中数量（仅 DOING） */
  doing: Record<string, number>
  /** 条目总数 */
  itemTotal: number
  /** 已完成总数 */
  doneTotal: number
  /** 心愿总数（仅 WANT） */
  wishTotal: number
  /** 进行中总数（仅 DOING） */
  doingTotal: number
  /** 笔记总数 */
  noteTotal: number
  /** 全部记录数 */
  allTotal: number
}

/** 前台首页数据 */
export interface FrontHomeData {
  summary: FrontHomeSummary
  /** 已完成时间线（首屏一页） */
  timeline: SgjItem[]
  /** 已完成时间线总数（配合“加载更多”分页） */
  timelineTotal: number
  /** 心愿单 */
  wishlist: SgjItem[]
  /** 最近笔记 */
  recentNotes: SgjNote[]
}