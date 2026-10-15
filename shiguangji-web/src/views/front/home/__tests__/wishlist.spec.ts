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

// 登录态可按用例切换（访客用例需要 getToken 返回空）
const authState = vi.hoisted(() => ({ token: 'test-token' as string }))

vi.mock('@/utils/auth', () => ({
  getToken: () => authState.token
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

  it('访客（未登录）不渲染心愿条与心愿单，且不请求计划接口（结论 108：心愿是私人模块）', async () => {
    authState.token = ''
    mockedPlanList.mockClear()
    const wrapper = await mountHome()

    expect(wrapper.find('.wish-bar').exists(), '访客不应看到心愿条').toBe(false)
    expect(wrapper.find('.wishlist-card').exists(), '访客不应看到心愿单卡片').toBe(false)
    expect(mockedPlanList, '访客不该为空请求买单').not.toHaveBeenCalled()

    wrapper.unmount()
    authState.token = 'test-token'
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

  it('心愿单只放首屏一页，右下角是「查看全部心愿 →」链接（与最近笔记同款、统一「心愿」措辞）', async () => {
    const wrapper = await mountHome()
    // 只渲染首屏 10 条，其余去 /plan 看（结论 95/96）
    expect(wrapper.findAll('.wish-item')).toHaveLength(10)

    const more = wrapper.find('.wishlist-card .note-more')
    expect(more.exists()).toBe(true)
    expect(more.text()).toBe('查看全部心愿 →')
    expect(more.attributes('href')).toBe('/plan')
    wrapper.unmount()
  })
})
