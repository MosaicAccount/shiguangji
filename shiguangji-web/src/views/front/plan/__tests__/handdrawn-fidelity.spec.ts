import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * 手绘皮肤唯一来源守卫（2026-10 修订：0.0.6 令牌升格后接替 0.0.3 的「页面 import 页级 CSS」版本）。
 *
 * 0.0.3 的约束是「设计图与页面共用 views/front/plan/handdrawn.css」；0.0.6 把形制层
 * （--hd-* 令牌与 hd-* 类）从 .plan-page 作用域升格为全局皮肤，改由前台布局
 * layout/front/index.vue 统一 import assets/styles/handdrawn/handdrawn-site.css，
 * 心愿页旧 CSS 随之退役（见 docs/design/handdrawn-architecture.md）。
 * 若某个页面又 import 一份页级皮肤，形制就会重新分叉成两份实现——本守卫盯住这一点。
 *
 * 本测试盯住三件事：
 *   1. 全局皮肤只在前台布局加载一次，且确实带着形制令牌；
 *   2. 心愿页不再 import 已退役的页级 handdrawn.css，并真的在用全局形制类；
 *   3. 心愿页设计图是可对账的 0.0.6 稿，且与产品引用同一份皮肤文件名。
 */

// vitest 以 shiguangji-web 为运行目录，前端在仓库根之下
const webRoot = process.cwd()
const repoRoot = resolve(webRoot, '..')
const LAYOUT = resolve(webRoot, 'src/layout/front/index.vue')
const SKIN = resolve(webRoot, 'src/assets/styles/handdrawn/handdrawn-site.css')
const PAGE = resolve(webRoot, 'src/views/front/plan/index.vue')
const MOCKUP = resolve(repoRoot, 'docs/design/mockups/handdrawn-0.0.6/plan.html')

describe('手绘皮肤：全局唯一来源', () => {
  it('前台布局是全局皮肤的唯一加载点，皮肤带着形制令牌', () => {
    expect(readFileSync(LAYOUT, 'utf-8'))
      .toContain("import '@/assets/styles/handdrawn/handdrawn-site.css'")
    expect(readFileSync(SKIN, 'utf-8'), '全局皮肤应定义 --hd-* 形制令牌').toContain('--hd-ink:')
  })

  it('心愿页不再 import 已退役的页级 handdrawn.css', () => {
    expect(readFileSync(PAGE, 'utf-8')).not.toMatch(/import\s+['"]\.\/handdrawn\.css['"]/)
  })

  it('心愿页真的在用全局形制类，不是只把皮肤挂在外壳上', () => {
    const page = readFileSync(PAGE, 'utf-8')
    for (const cls of ['hd-paper', 'hd-btn', 'hd-empty']) {
      expect(page, `心愿页缺少全局形制类 ${cls}`).toContain(cls)
    }
  })

  it('心愿页设计图是可对账的 0.0.6 稿，与产品引用同一份皮肤文件名', () => {
    const mockup = readFileSync(MOCKUP, 'utf-8')
    expect(mockup).toContain('href="handdrawn-site.css"')
    expect(mockup, '设计图应声明它属于 0.0.6').toContain('拾光记 0.0.6')
  })
})
