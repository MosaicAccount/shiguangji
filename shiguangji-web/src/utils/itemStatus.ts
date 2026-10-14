import type { SgjItem } from '@/types/api/business/item'

/**
 * 条目状态文案（0.0.3 三态）。
 *
 * 口径：**字典是唯一事实来源**，这里只在字典没给答案时兜底，且兜底必须是中文——
 * 曾经发生过一次「地点被置成 DOING → 地点字典没有这个值 → 界面直接显示 DOING」，
 * 裸枚举值对用户没有意义，所以最后一档也绝不回落成 status 原值。
 *
 * 三层取值：
 *   1. 对应类型的字典（`sgj_movie_status` / `sgj_book_status` / `sgj_place_status`）；
 *   2. 该类型的中文兜底（字典缓存过期、或 SQL 增量脚本还没执行时用得上）；
 *   3. 通用兜底（未完成 / 进行中 / 已完成），覆盖「值域里有、但这类条目语义上不该有」的情况。
 */

/** 条目类型 → 状态字典类型 */
const DICT_TYPE: Record<string, string> = {
  MOVIE: 'sgj_movie_status',
  TV: 'sgj_movie_status',
  BOOK: 'sgj_book_status',
  PLACE: 'sgj_place_status'
}

/** 各类型的中文兜底（与字典 label 一致；字典取到时以字典为准） */
const TYPE_FALLBACK: Record<string, Record<string, string>> = {
  MOVIE: { WANT: '想看', DOING: '在看', DONE: '看过' },
  TV: { WANT: '想看', DOING: '在看', DONE: '看过' },
  BOOK: { WANT: '想读', DOING: '在读', DONE: '已读' },
  PLACE: { WANT: '想去', DONE: '去过' }
}

/** 通用兜底：值域认识、但该类型语义上没有这一态时（如地点出现 DOING） */
const GENERIC_FALLBACK: Record<string, string> = {
  WANT: '未完成',
  DOING: '进行中',
  DONE: '已完成'
}

/**
 * 取状态文案。
 *
 * @param item 至少含 itemType / status
 * @param dicts 字典表，key 为字典类型（如 `sgj_movie_status`），value 为 `{ value, label }[]`
 */
export function itemStatusLabel(
  item: Pick<SgjItem, 'itemType' | 'status'>,
  dicts: Record<string, any[]> = {}
): string {
  const type = item.itemType || ''
  const status = item.status || ''
  if (!status) return ''

  const dict = dicts[DICT_TYPE[type]] || []
  const hit = dict.find((d: any) => d.value === status)
  if (hit?.label) return hit.label

  return TYPE_FALLBACK[type]?.[status] || GENERIC_FALLBACK[status] || status
}
