import { describe, it, expect } from 'vitest'
import { groupPlanItems, planDateLabel, weekEndOf, weekStartOf } from '@/utils/plan'
import type { SgjItem } from '@/types/api/business/item'

/**
 * T-80 分组边界 / T-81 日期文案（docs/design/plan-testcases.md）
 * 固定 today = '2026-10-14'（周三），周一为一周起点，本周日 = 2026-10-18。
 */

const TODAY = '2026-10-14'

function item(startDate?: string): SgjItem {
  return { itemId: Math.random(), title: 't', itemType: 'MOVIE', startDate }
}

describe('T-80 groupPlanItems 分组边界', () => {
  it('昨天过期、今天到周日算这周、下周一算下周及以后、无日期还没定日子', () => {
    const groups = groupPlanItems(
      [item('2026-10-13'), item('2026-10-14'), item('2026-10-18'), item('2026-10-19'), item()],
      TODAY
    )
    expect(groups.map(g => g.key)).toEqual(['overdue', 'thisWeek', 'later', 'unscheduled'])
    expect(groups[0].items).toHaveLength(1)
    expect(groups[1].items).toHaveLength(2)
    expect(groups[2].items).toHaveLength(1)
    expect(groups[3].items).toHaveLength(1)
    expect(groups[3].hint).toContain('定个日子')
  })

  it('组内顺序等于入参顺序（服务端已排好，前端不重排）', () => {
    const groups = groupPlanItems([item('2026-10-16'), item('2026-10-15')], TODAY)
    expect(groups[0].key).toBe('thisWeek')
    expect(groups[0].items.map(i => i.startDate)).toEqual(['2026-10-16', '2026-10-15'])
  })

  it('空入参返回空数组（四组皆空即整页空态）；空组不渲染', () => {
    expect(groupPlanItems([], TODAY)).toEqual([])
    const groups = groupPlanItems([item('2026-10-20')], TODAY)
    expect(groups.map(g => g.key)).toEqual(['later'])
  })

  it('四组名自解释、说明写成「该拿这一组做什么」，这周写明日期区间（结论 91/93）', () => {
    const groups = groupPlanItems(
      [item('2026-10-13'), item('2026-10-14'), item('2026-10-19'), item()],
      TODAY
    )
    const titles = Object.fromEntries(groups.map(g => [g.key, g.title]))
    const hints = Object.fromEntries(groups.map(g => [g.key, g.hint]))

    expect(titles).toEqual({
      overdue: '已过期',
      thisWeek: '这周',
      later: '下周及以后',
      unscheduled: '还没定日子'
    })
    expect(hints.overdue).toContain('改个日子')
    expect(hints.thisWeek).toContain('10月12日')
    expect(hints.thisWeek).toContain('10月18日')
    expect(hints.later).toBe('日子还远，先放着')
    expect(hints.unscheduled).toContain('定个日子')
  })

  it('weekEndOf：周一为一周起点（周三的本周日是 10-18，周日当天也归本周）', () => {
    expect(weekEndOf(TODAY)).toBe('2026-10-18')
    expect(weekEndOf('2026-10-18')).toBe('2026-10-18')
    expect(weekEndOf('2026-10-19')).toBe('2026-10-25')
    expect(weekStartOf(TODAY)).toBe('2026-10-12')
    expect(weekStartOf('2026-10-18')).toBe('2026-10-12')
  })
})

describe('T-81 planDateLabel 日期文案', () => {
  it('昨天 → 过期 1 天；今天 / 明天 / 本周内 → 原词或周X', () => {
    expect(planDateLabel('2026-10-13', TODAY)).toBe('过期 1 天')
    expect(planDateLabel('2026-10-14', TODAY)).toBe('今天')
    expect(planDateLabel('2026-10-15', TODAY)).toBe('明天')
    expect(planDateLabel('2026-10-17', TODAY)).toBe('周六')
    expect(planDateLabel('2026-10-18', TODAY)).toBe('周日')
  })

  it('本周日之后 → M月D日；跨年补年份；过期天数不出现 0', () => {
    expect(planDateLabel('2026-10-20', TODAY)).toBe('10月20日')
    expect(planDateLabel('2026-12-31', TODAY)).toBe('12月31日')
    expect(planDateLabel('2027-01-05', TODAY)).toBe('2027年1月5日')
    expect(planDateLabel('2026-10-14', TODAY)).not.toContain('过期 0')
  })
})
