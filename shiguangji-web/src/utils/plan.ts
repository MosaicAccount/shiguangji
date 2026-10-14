import type { SgjItem } from '@/types/api/business/item'

/**
 * 计划页的纯逻辑（0.0.3）：分组与日期文案。
 *
 * 口径（设计 结论 78）：服务端只保证「有日期的按日期升序、无日期置后」的顺序，
 * 「逾期 / 本周 / 以后 / 未排期」由前端按浏览器本地日期切——分组的正确性只对
 * 「用户眼里的今天」负责，两侧各算一次必然在跨时区与跨零点上对不齐。
 *
 * 日期一律用 'YYYY-MM-DD' 字符串（字典序即时间序），需要日期运算时取本地正午
 * 构造 Date（避开 DST 边界），不做任何时区换算。
 */

export type PlanGroupKey = 'overdue' | 'thisWeek' | 'later' | 'unscheduled'

export interface PlanGroup {
  key: PlanGroupKey
  title: string
  /** 组说明：这一组是什么意思（四组都有，不只未排期） */
  hint: string
  items: SgjItem[]
}

/** 'YYYY-MM-DD' → 本地正午的 Date（正午避开 DST 边界，让 addDays 稳定） */
function parseLocal(date: string): Date {
  const [y, m, d] = date.split('-').map(Number)
  return new Date(y, m - 1, d, 12, 0, 0, 0)
}

function formatDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

/** 本周一为起点的本周周日（今天所在周的周末） */
export function weekEndOf(today: string): string {
  const d = parseLocal(today)
  const daysSinceMonday = (d.getDay() + 6) % 7
  d.setDate(d.getDate() + (6 - daysSinceMonday))
  return formatDate(d)
}

/** 本周一（今天所在周的周一） */
export function weekStartOf(today: string): string {
  const d = parseLocal(today)
  const daysSinceMonday = (d.getDay() + 6) % 7
  d.setDate(d.getDate() - daysSinceMonday)
  return formatDate(d)
}

/** M月D日（用于组说明里的日期区间） */
function monthDay(date: string): string {
  const d = parseLocal(date)
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

/** 截掉时分秒，取本地 YYYY-MM-DD */
export function localDateKey(date: Date): string {
  return formatDate(new Date(date.getFullYear(), date.getMonth(), date.getDate(), 12, 0, 0, 0))
}

/**
 * 组标题与说明。说明是给人看的「这一组是什么意思」——0.0.3 实测反馈：
 * 只有「以后 / 未排期」两个词时看不出分组依据（是时间？是状态？），故每组都配一句人话，
 * 本周还把实际日期区间写出来。
 */
function groupMeta(today: string): Record<PlanGroupKey, { title: string; hint: string }> {
  const start = monthDay(weekStartOf(today))
  const end = monthDay(weekEndOf(today))
  return {
    overdue: { title: '逾期', hint: '计划日期已经过了' },
    thisWeek: { title: '本周', hint: `本周内（${start} – ${end}）` },
    later: { title: '以后', hint: '排在下周或更远' },
    unscheduled: { title: '未排期', hint: '还没定哪天 —— 点「定个日子」补上' }
  }
}

/**
 * 把计划页条目切成四组。组内保持入参顺序（服务端已按 start_date 升序、
 * 无日期置后），不在前端重排——两组排序口径只会打架。
 */
export function groupPlanItems(items: SgjItem[], today: string): PlanGroup[] {
  const weekEnd = weekEndOf(today)
  const buckets: Record<PlanGroupKey, SgjItem[]> = { overdue: [], thisWeek: [], later: [], unscheduled: [] }

  for (const item of items) {
    const date = item.startDate ? item.startDate.slice(0, 10) : ''
    if (!date) {
      buckets.unscheduled.push(item)
    } else if (date < today) {
      buckets.overdue.push(item)
    } else if (date <= weekEnd) {
      buckets.thisWeek.push(item)
    } else {
      buckets.later.push(item)
    }
  }

  const meta = groupMeta(today)
  return (Object.keys(buckets) as PlanGroupKey[])
    .map(key => ({ key, title: meta[key].title, hint: meta[key].hint, items: buckets[key] }))
    .filter(group => group.items.length > 0)
}

const WEEKDAY_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/**
 * 计划日期的相对文案：
 * 过去 → 「逾期 N 天」；今天 / 明天 → 原词；本周内 → 「周X」；
 * 更远 → 「M月D日」（跨年补年份）。
 */
export function planDateLabel(startDate: string, today: string): string {
  const start = startDate.slice(0, 10)
  const diffDays = Math.round((parseLocal(start).getTime() - parseLocal(today).getTime()) / 86400000)
  if (diffDays < 0) {
    return `逾期 ${-diffDays} 天`
  }
  if (diffDays === 0) {
    return '今天'
  }
  if (diffDays === 1) {
    return '明天'
  }
  if (start <= weekEndOf(today)) {
    return WEEKDAY_NAMES[parseLocal(start).getDay()]
  }
  const d = parseLocal(start)
  const base = `${d.getMonth() + 1}月${d.getDate()}日`
  return d.getFullYear() === parseLocal(today).getFullYear() ? base : `${d.getFullYear()}年${base}`
}
