import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { ElButton, ElEmpty } from 'element-plus'
import FrontHome from '../index.vue'
import { getFrontHomeData, listFrontHomeTimeline } from '@/api/front/home'
import type { FrontHomeData } from '@/types/api/front/home'

/**
 * T-84（docs/design/plan-testcases.md）：首页心愿条与心愿单的三态对齐。
 * - 心愿条数字 = wishTotal + doingTotal，文案「在想 X / 在做 Y」；
 * - DOING 条目状态文案「在看 / 在读」，不再显示「想看 / 想读」；
 * - 心愿条链接指向 /plan（原误指 /note，属回归断言）。
 */

vi.mock('@/utils/auth', () => ({
  getToken: () => 'test-token'
}))

vi.mock('@/api/front/home', () => ({
  getFrontHomeData: vi.fn(),
  listFrontHomeTimeline: vi.fn().mockResolvedValue({ data: [], total: 0 })
}))

vi.mock('@/api/front/item', () => ({
  completeFrontItem: vi.fn()
}))

const mockedHomeData = vi.mocked(getFrontHomeData)

function fixture(): FrontHomeData {
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
    wishlist: [
      { itemId: 101, itemType: 'TV', title: '漫长的季节', status: 'DOING' },
      { itemId: 102, itemType: 'MOVIE', title: '肖申克的救赎', status: 'WANT' },
      { itemId: 103, itemType: 'BOOK', title: '置身事内', status: 'DOING' }
    ],
    recentNotes: []
  } as unknown as FrontHomeData
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
      components: { ElButton, ElEmpty },
      directives: { loading: {} }
    }
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  mockedHomeData.mockReset()
  mockedHomeData.mockResolvedValue({ code: 200, data: fixture() } as any)
})

describe('T-84 首页心愿条与状态文案', () => {
  it('心愿条数字 = wishTotal + doingTotal，文案拆列「在想 3 / 在做 2」', async () => {
    const wrapper = await mountHome()
    const bar = wrapper.find('.wish-bar')
    expect(bar.exists()).toBe(true)
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
    expect(statuses).toEqual(['在看', '想看', '在读'])
    wrapper.unmount()
  })
})
