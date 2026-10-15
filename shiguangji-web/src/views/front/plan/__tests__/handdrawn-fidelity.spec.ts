import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * 设计图与实现的一致性守卫（issue #84 的教训）。
 *
 * 背景：手绘皮肤曾出现过「设计图与页面各有一份样式、逐项手抄导致走样」。
 * 现在的约束是结构性的：样式只有一份（views/front/plan/handdrawn.css），
 * 设计图 <link> 它、页面 import 它；类名两边逐一对齐。
 *
 * 本测试盯住三件事（任何一条破了都说明「实现又跑偏了」）：
 *   1. 设计图不再自带设计样式，而是引用那份共享 CSS；
 *   2. 页面 import 同一份 CSS；
 *   3. 设计图页面内容里用到的类名，页面模板里都有。
 */

// vitest 以 shiguangji-web 为运行目录，前端在仓库根之下
const webRoot = process.cwd()
const repoRoot = resolve(webRoot, '..')
const MOCKUP = resolve(repoRoot, 'docs/design/mockups/plan-0.0.3/handdrawn-checklist.html')
const PAGE = resolve(webRoot, 'src/views/front/plan/index.vue')

const SHARED_CSS = 'shiguangji-web/src/views/front/plan/handdrawn.css'

function classesIn(text: string): Set<string> {
  const found = new Set<string>()
  for (const m of text.matchAll(/class="([^"]+)"/g)) {
    m[1].split(/\s+/).filter(Boolean).forEach(c => found.add(c))
  }
  return found
}

describe('手绘皮肤：设计图与实现同源', () => {
  const mockup = readFileSync(MOCKUP, 'utf-8')
  const page = readFileSync(PAGE, 'utf-8')

  it('设计图引用共享样式，而不是自带一份设计 CSS', () => {
    expect(mockup).toContain(`href="../../../../${SHARED_CSS}"`)
    // 设计图里只允许剩下评审外壳的样式（工具条/字体切换/标注），不得再定义设计令牌
    expect(mockup).not.toContain('--hd-ink:')
    expect(mockup).not.toContain('--hd-paper:')
  })

  it('页面 import 同一份共享样式', () => {
    expect(page).toContain("import './handdrawn.css'")
  })

  it('设计图页面内容用到的类名，页面模板里都有（含动态拼接的 g-*）', async () => {
    const contentEnd = mockup.indexOf('<div class="notes">')
    const contentStart = mockup.indexOf('<header class="hd-head')
    expect(contentStart, '未能定位设计图内容区').toBeGreaterThan(-1)
    expect(contentStart).toBeLessThan(contentEnd)
    const content = mockup.slice(contentStart, contentEnd)

    const pageClasses = classesIn(page)
    // Vue 动态类（:class）里的字面量与对象键一并收集
    for (const m of page.matchAll(/'([a-z][a-z0-9-]*)'/g)) pageClasses.add(m[1])
    for (const m of page.matchAll(/:class="\{([^}]*)\}"/g)) {
      for (const k of m[1].matchAll(/([a-z][a-z0-9-]*)\s*:/g)) pageClasses.add(k[1])
    }

    // 页面里 g-<分组键> / g-<类型小写> 是拼出来的，按真实来源展开
    const { groupPlanItems, localDateKey } = await import('@/utils/plan')
    const today = localDateKey(new Date())
    const day = (offset: number) => {
      const d = new Date()
      d.setDate(d.getDate() + offset)
      return localDateKey(d)
    }
    // 四个分组各自造一条，确保 overdue/thisweek/later/unscheduled 都出现
    const fixtures = [
      { itemId: 1, itemType: 'TV', status: 'WANT', title: '过期样例', startDate: day(-3) },
      { itemId: 2, itemType: 'MOVIE', status: 'WANT', title: '本周样例', startDate: today },
      { itemId: 3, itemType: 'BOOK', status: 'WANT', title: '以后样例', startDate: day(20) },
      { itemId: 4, itemType: 'PLACE', status: 'WANT', title: '没定日子样例' }
    ] as any[]
    for (const g of groupPlanItems(fixtures, today)) pageClasses.add('g-' + g.key.toLowerCase())
    for (const t of ['MOVIE', 'TV', 'BOOK', 'PLACE']) pageClasses.add('g-' + t.toLowerCase())
    expect(page, '页面未用分组键拼 g-* 类').toContain("'g-' + group.key.toLowerCase()")
    expect(page, '页面未用类型拼 g-* 类').toContain("'g-' + String(item.itemType || '').toLowerCase()")

    const missing = [...classesIn(content)]
      .filter(c => c !== 'an' && c !== 'an-host') // 标注是评审外壳
      .filter(c => !pageClasses.has(c))

    expect(missing, `设计图有、页面缺的类名：${missing.join(', ')}`).toEqual([])
  })
})
