import { describe, it, expect, vi, beforeEach } from 'vitest'
import { defineComponent } from 'vue'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import FrontPlan from '../index.vue'
import { listPlanItems } from '@/api/front/item'
import type { SgjItem } from '@/types/api/business/item'

/**
 * T-98（docs/design/plan-testcases.md，issue #92）：心愿页状态胶囊即筛选。
 * - 胶囊是按钮：点「在做/在想」单选该档、点「共 N 项」回全部，选中档高亮（.on）；
 * - 筛选在服务端（请求带 status），翻页沿用同一档位；
 * - 胶囊数字用响应的 wantTotal/doingTotal 全量分档，不随筛选变小；
 * - 筛出来的空态给「看全部心愿」，不是「还没有心愿」的引导文案。
 */

vi.mock('@/utils/auth', () => ({
  getToken: () => 'test-token'
}))

vi.mock('@/utils/dict', async () => {
  const { ref } = await import('vue')
  return {
    // 空字典即可：状态文案有中文兜底（utils/itemStatus.ts），本组用例不断言文案来源
    useDict: (...types: string[]) => Object.fromEntries(types.map(t => [t, ref([])]))
  }
})

vi.mock('@/api/front/item', () => ({
  listPlanItems: vi.fn(),
  updateFrontItem: vi.fn()
}))

const mockedPlanList = vi.mocked(listPlanItems)

/** 动态日期：分组按真实今天算，写死日期过一夜就翻组 */
function day(offset: number): string {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const rows: SgjItem[] = [
  { itemId: 1, itemType: 'MOVIE', title: '在做的一部电影', status: 'DOING', startDate: day(1) },
  { itemId: 2, itemType: 'BOOK', title: '在想的一本书', status: 'WANT', startDate: day(5) },
  { itemId: 3, itemType: 'PLACE', title: '想去的地点', status: 'WANT' }
]

/** 模拟服务端口径：status 单档过滤 + 分页切片、total 跟随筛选、wantTotal/doingTotal 恒为全量分档 */
function mockPlanApi(overrides: SgjItem[] = rows) {
  mockedPlanList.mockImplementation(async (query: any) => {
    const filtered = query?.status ? overrides.filter(r => r.status === query.status) : overrides
    const pageSize = query?.pageSize ?? 10
    const pageNum = query?.pageNum ?? 1
    return {
      code: 200,
      data: filtered.slice((pageNum - 1) * pageSize, pageNum * pageSize),
      total: filtered.length,
      wantTotal: overrides.filter(r => r.status === 'WANT').length,
      doingTotal: overrides.filter(r => r.status === 'DOING').length
    } as any
  })
}

const ElPopoverStub = defineComponent({
  // 只渲染触发按钮：弹层里的 el-date-picker 与本组用例无关，避免再引一组组件
  template: '<div class="pop-stub"><slot name="reference" /></div>'
})

const ItemCompleteDialogStub = defineComponent({
  template: '<div class="complete-stub" />'
})

async function mountPlan() {
  const wrapper = mount(FrontPlan, {
    global: {
      plugins: [createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })],
      components: { ElPopover: ElPopoverStub, ItemCompleteDialog: ItemCompleteDialogStub },
      directives: { loading: {} }
    }
  })
  await flushPromises()
  return wrapper
}

function chips(wrapper: VueWrapper) {
  return wrapper.findAll('.hd-chip')
}

async function clickChip(wrapper: VueWrapper, index: number) {
  await wrapper.findAll('.hd-chip')[index].trigger('click')
  await flushPromises()
}

beforeEach(() => {
  mockedPlanList.mockReset()
  mockPlanApi()
})

describe('T-98 心愿页状态胶囊即筛选', () => {
  it('初始不筛：请求不带 status，胶囊数字取全量分档（共 3 / 在做 1 / 在想 2），选中态在「共」', async () => {
    const wrapper = await mountPlan()

    expect(mockedPlanList.mock.calls[0][0].status).toBeUndefined()
    const texts = chips(wrapper).map(c => c.text())
    expect(texts).toEqual(['共 3 项', '在做 1', '在想 2'])
    const onIndexes = chips(wrapper).filter(c => c.classes().includes('on')).map(c => c.text())
    expect(onIndexes, '默认只有「共」高亮').toEqual(['共 3 项'])
    expect(wrapper.findAll('.row')).toHaveLength(3)

    wrapper.unmount()
  })

  it('点「在做」：请求带 status=DOING，列表只剩在做条目，高亮挪到「在做」，数字不变', async () => {
    const wrapper = await mountPlan()
    await clickChip(wrapper, 1)

    expect(mockedPlanList).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'DOING' }))
    const titles = wrapper.findAll('.row .t').map(t => t.text())
    expect(titles).toEqual(['在做的一部电影'])
    expect(chips(wrapper).filter(c => c.classes().includes('on')).map(c => c.text()))
      .toEqual(['在做 1'])
    // 全量分档数字不随筛选缩水
    expect(chips(wrapper).map(c => c.text())).toEqual(['共 3 项', '在做 1', '在想 2'])
    // 分页脚的 total 跟随筛选
    expect(wrapper.find('.list-foot .cnt').text()).toContain('已展示 1 / 共 1 项')

    wrapper.unmount()
  })

  it('点「在想」再点回「共」：档位切换与复原，同档重复点击不重复取数', async () => {
    const wrapper = await mountPlan()
    await clickChip(wrapper, 2)
    expect(mockedPlanList).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'WANT' }))
    expect(wrapper.findAll('.row .t').map(t => t.text())).toEqual(['在想的一本书', '想去的地点'])

    await clickChip(wrapper, 2)
    expect(mockedPlanList, '同档再点不应重新请求').toHaveBeenCalledTimes(2)

    await clickChip(wrapper, 0)
    expect(mockedPlanList).toHaveBeenLastCalledWith(expect.objectContaining({ status: undefined }))
    expect(wrapper.findAll('.row')).toHaveLength(3)

    wrapper.unmount()
  })

  it('筛出来的空态给「看全部心愿」出口，且能一键回全部', async () => {
    mockPlanApi([rows[0]])  // 库里没有 WANT：筛「在想」是空的
    const wrapper = await mountPlan()
    await clickChip(wrapper, 2)

    expect(wrapper.find('.hd-empty p').text()).toContain('这一档还没有心愿')
    expect(wrapper.find('.hd-empty').text()).not.toContain('去影单 / 书单挑一个')

    await wrapper.find('.hd-empty .hd-btn').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.row')).toHaveLength(1)
    expect(mockedPlanList).toHaveBeenLastCalledWith(expect.objectContaining({ status: undefined }))

    wrapper.unmount()
  })

  it('翻页沿用当前档位：加载更多时 status 不丢（丢了第二页会混进其他档）', async () => {
    mockedPlanList.mockClear()
    const page: SgjItem[] = Array.from({ length: 12 }, (_, i) => ({
      itemId: 100 + i, itemType: 'BOOK', title: `在做${i}`, status: 'DOING', startDate: day(3)
    })) as SgjItem[]
    mockPlanApi(page)
    const wrapper = await mountPlan()
    await clickChip(wrapper, 1)

    await wrapper.find('.list-foot .hd-btn').trigger('click')
    await flushPromises()

    // 首次 undefined 是挂载时的初始加载；之后两次（切档、加载更多）都必须带 DOING
    const statuses = mockedPlanList.mock.calls.map(c => c[0]?.status)
    expect(statuses).toEqual([undefined, 'DOING', 'DOING'])

    wrapper.unmount()
  })
})
