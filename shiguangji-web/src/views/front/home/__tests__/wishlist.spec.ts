import { describe, it, expect, vi, beforeEach } from 'vitest'
import { defineComponent } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { ElButton, ElEmpty } from 'element-plus'
import FrontHome from '../index.vue'
import { getFrontHomeData } from '@/api/front/home'
import { listPlanItems } from '@/api/front/item'
import type { FrontHomeData } from '@/types/api/front/home'
import type { SgjItem } from '@/types/api/business/item'

/**
 * T-84（docs/design/plan-testcases.md）+ 0.0.3 修订：
 * - 心愿条数字 = wishTotal + doingTotal，文案「在想 X / 在做 Y」，链接指向 /plan；
 * - 心愿单状态文案三态（DOING 显示在看 / 在读，不再显示想看 / 想读）；
 * - 心愿单与计划页同一数据源（plan/list）：带计划日期，且有分页（已展示 / 共 N + 加载更多）。
 */

vi.mock('@/utils/auth', () => ({
  getToken: () => 'test-token'
}))

vi.mock('@/api/front/home', () => ({
  getFrontHomeData: vi.fn(),
  listFrontHomeTimeline: vi.fn().mockResolvedValue({ data: [], total: 0 })
}))

vi.mock('@/api/front/item', () => ({
  listPlanItems: vi.fn(),
  completeFrontItem: vi.fn(),
  updateFrontItem: vi.fn()
}))

const mockedHomeData = vi.mocked(getFrontHomeData)
const mockedPlanList = vi.mocked(listPlanItems)

const ElPopoverStub = defineComponent({
  template: '<div class="pop-stub"><slot name="reference" /><slot /></div>'
})

function homePayload(): FrontHomeData {
  return {
    summary: {
      total: { MOVIE: 10, TV: 2, BOOK: 3 },
      done: { MOVIE: 8 },
      wish: { MOVIE: 2, BOOK: 1 },
      doing: { TV: 1, BOOK: 1 },
      itemTotal: 15,
      doneTotal: 8,
      wishTotal: 3,
      doingTotal: 2,
      noteTotal: 5,
      allTotal: 20
    },
    timeline: [],
    timelineTotal: 0,
    recentNotes: []
  } as unknown as FrontHomeData
}

/** 动态「明天」：PlanDateBadge 的相对文案按真实今天算，写死日期的用例过一天就翻车 */
function tomorrow(): string {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function wishRows(count: number): SgjItem[] {
  const rows: SgjItem[] = [
    { itemId: 101, itemType: 'TV', title: '漫长的季节', status: 'DOING', startDate: tomorrow() },
    { itemId: 102, itemType: 'MOVIE', title: '肖申克的救赎', status: 'WANT' },
    { itemId: 103, itemType: 'BOOK', title: '置身事内', status: 'DOING' }
  ]
  for (let i = rows.length; i < count; i++) {
    rows.push({ itemId: 200 + i, itemType: 'BOOK', title: `填充${i}`, status: 'WANT' })
  }
  return rows.slice(0, count)
}

function buildRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/home', component: { template: '<div />' } },
      { path: '/note', component: { template: '<div />' } },
      { path: '/plan', component: { template: '<div />' } }
    ]
  })
}

async function mountHome() {
  const wrapper = mount(FrontHome, {
    global: {
      plugins: [buildRouter()],
      components: { ElButton, ElEmpty, ElPopover: ElPopoverStub },
      directives: { loading: {} }
    }
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  mockedHomeData.mockReset()
  mockedHomeData.mockResolvedValue({ code: 200, data: homePayload() } as any)
  mockedPlanList.mockReset()
  mockedPlanList.mockImplementation(async (query: any) => {
    const pageSize = query?.pageSize ?? 10
    const pageNum = query?.pageNum ?? 1
    return { code: 200, data: wishRows(25).slice((pageNum - 1) * pageSize, pageNum * pageSize), total: 25 } as any
  })
})

describe('T-84 首页心愿条与心愿单', () => {
  it('心愿条数字 = wishTotal + doingTotal，文案拆列「在想 3 / 在做 2」', async () => {
    const wrapper = await mountHome()
    const bar = wrapper.find('.wish-bar')
    expect(bar.find('.wish-bar-num').text()).toBe('5')
    expect(bar.find('.wish-bar-label').text()).toContain('在想 3')
    expect(bar.find('.wish-bar-label').text()).toContain('在做 2')
    wrapper.unmount()
  })

  it('心愿条链接指向 /plan（回归：原误指 /note）', async () => {
    const wrapper = await mountHome()
    expect(wrapper.find('.wish-bar').attributes('href')).toBe('/plan')
    wrapper.unmount()
  })

  it('心愿单状态文案：DOING 显示 在看/在读，WANT 显示 想看', async () => {
    const wrapper = await mountHome()
    const statuses = wrapper.findAll('.wish-status').map(s => s.text())
    expect(statuses.slice(0, 3)).toEqual(['在看', '想看', '在读'])
    wrapper.unmount()
  })

  it('心愿单展示计划日期（与计划页同一数据源）', async () => {
    const wrapper = await mountHome()
    const texts = wrapper.findAll('.wish-item .pdb-btn').map(b => b.text())
    expect(texts[0]).toContain('明天')          // startDate = 动态明天
    expect(texts.some(t => t.includes('定个日子'))).toBe(true)  // 未排期那条
    wrapper.unmount()
  })

  it('心愿单分页：分页脚与「最近笔记」同款，点一下以 pageNum=2 追加', async () => {
    const wrapper = await mountHome()
    const more = wrapper.find('.wish-more')
    expect(more.exists(), '应出现「加载更多」链接式按钮').toBe(true)
    expect(more.text()).toContain('还有 15 项')   // 25 - 10
    expect(more.text()).toContain('→')
    // 与最近笔记的 .note-more 同一视觉语言（复用同一 class）
    expect(more.classes()).toContain('note-more')

    await more.trigger('click')
    await flushPromises()

    expect(mockedPlanList).toHaveBeenLastCalledWith({ pageNum: 2, pageSize: 10 })
    expect(wrapper.findAll('.wish-item')).toHaveLength(20)
    expect(wrapper.find('.wish-more').text()).toContain('还有 5 项')
    wrapper.unmount()
  })

  it('心愿单加载完最后一页后显示「已全部显示 · 共 N 项」', async () => {
    mockedPlanList.mockImplementation(async (query: any) => {
      const pageSize = query?.pageSize ?? 10
      const pageNum = query?.pageNum ?? 1
      return { code: 200, data: wishRows(12).slice((pageNum - 1) * pageSize, pageNum * pageSize), total: 12 } as any
    })
    const wrapper = await mountHome()
    expect(wrapper.find('.wish-more').text()).toContain('还有 2 项')

    await wrapper.find('.wish-more').trigger('click')
    await flushPromises()

    expect(wrapper.find('.wish-more').exists()).toBe(false)
    expect(wrapper.find('.wish-all-loaded').text()).toBe('已全部显示 · 共 12 项')
    wrapper.unmount()
  })
})
