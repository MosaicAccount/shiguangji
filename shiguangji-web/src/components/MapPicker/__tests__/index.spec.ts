import { describe, it, expect, vi, beforeEach } from 'vitest'
import { defineComponent } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { ElButton } from 'element-plus'
import MapPicker from '../index.vue'
import { loadChinaMap } from '@/utils/map'

/**
 * echarts mock：init 返回共享假 chart，捕获 zrender click handler，
 * convertFromPixel 可按用例改写返回坐标
 */
const echartsMock = vi.hoisted(() => {
  const state = {
    zrHandlers: {} as Record<string, (e?: unknown) => void>,
    convertResult: [116.4074, 39.9042]
  }
  const chart = {
    setOption: vi.fn(),
    dispose: vi.fn(),
    containPixel: vi.fn(() => true),
    convertFromPixel: vi.fn(() => state.convertResult),
    getZr: () => ({
      on: (event: string, handler: (e?: unknown) => void) => {
        state.zrHandlers[event] = handler
      }
    })
  }
  return { state, chart, init: vi.fn(() => chart), registerMap: vi.fn() }
})

vi.mock('echarts', () => ({ init: echartsMock.init, registerMap: echartsMock.registerMap }))

vi.mock('@/utils/map', async importOriginal => {
  const actual = await importOriginal<typeof import('@/utils/map')>()
  return { ...actual, loadChinaMap: vi.fn(async () => true) }
})

/** el-dialog 桩：modelValue 为 true 时内联渲染默认与 footer 插槽 */
const ElDialogStub = defineComponent({
  props: { modelValue: { type: Boolean, default: false } },
  template: '<div v-if="modelValue" class="dialog-stub"><slot /><slot name="footer" /></div>'
})

function mountPicker(props: Record<string, unknown> = {}) {
  return mount(MapPicker, {
    props: { modelValue: false, ...props },
    global: {
      components: { ElDialog: ElDialogStub, ElButton },
      directives: { loading: {} }
    }
  })
}

async function openPicker(props: Record<string, unknown> = {}) {
  const wrapper = mountPicker(props)
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
  return wrapper
}

function findConfirmButton(wrapper: ReturnType<typeof mountPicker>) {
  const button = wrapper.findAll('button').find(b => b.text().includes('确认选择'))
  expect(button, '确认按钮未渲染').toBeDefined()
  return button!
}

beforeEach(() => {
  vi.clearAllMocks()
  echartsMock.state.convertResult = [116.4074, 39.9042]
})

describe('MapPicker', () => {
  it('打开时加载地图，并回显已有经纬度', async () => {
    const wrapper = await openPicker({ latitude: 30.5, longitude: 100.25 })
    expect(loadChinaMap).toHaveBeenCalled()
    expect(echartsMock.init).toHaveBeenCalled()
    const option = echartsMock.chart.setOption.mock.calls.at(-1)![0]
    expect(option.series[0].data).toEqual([{ name: '已选', value: [100.25, 30.5] }])
    wrapper.unmount()
  })

  it('地图加载失败时提示错误且不初始化图表', async () => {
    vi.mocked(loadChinaMap).mockResolvedValueOnce(false)
    const wrapper = await openPicker()
    expect(echartsMock.init).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('地图加载失败')
    wrapper.unmount()
  })

  it('点击地图拾取经纬度并启用确认按钮', async () => {
    const wrapper = await openPicker()
    echartsMock.state.zrHandlers.click({ offsetX: 120, offsetY: 80 })
    await flushPromises()
    expect(echartsMock.chart.containPixel).toHaveBeenCalledWith('geo', [120, 80])
    expect(echartsMock.chart.convertFromPixel).toHaveBeenCalledWith('geo', [120, 80])
    expect(wrapper.text()).toContain('已选：纬度 39.9042，经度 116.4074')
    expect((findConfirmButton(wrapper).element as HTMLButtonElement).disabled).toBe(false)
    wrapper.unmount()
  })

  it('点击地图区域外（containPixel 为否）不选点', async () => {
    const wrapper = await openPicker()
    echartsMock.chart.containPixel.mockReturnValueOnce(false)
    echartsMock.state.zrHandlers.click({ offsetX: 1, offsetY: 1 })
    await flushPromises()
    expect(echartsMock.chart.convertFromPixel).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('已选')
    expect((findConfirmButton(wrapper).element as HTMLButtonElement).disabled).toBe(true)
    wrapper.unmount()
  })

  it('拾取坐标按 6 位小数精度截断', async () => {
    const wrapper = await openPicker()
    echartsMock.state.convertResult = [116.1234567, 39.98765432]
    echartsMock.state.zrHandlers.click({ offsetX: 10, offsetY: 10 })
    await flushPromises()
    expect(wrapper.text()).toContain('已选：纬度 39.987654，经度 116.123457')
    wrapper.unmount()
  })

  it('未选点时确认按钮禁用', async () => {
    const wrapper = await openPicker()
    expect((findConfirmButton(wrapper).element as HTMLButtonElement).disabled).toBe(true)
    wrapper.unmount()
  })

  it('确认选择后 emit confirm 坐标并关闭弹窗', async () => {
    const wrapper = await openPicker({ latitude: 1, longitude: 2 })
    echartsMock.state.zrHandlers.click({ offsetX: 120, offsetY: 80 })
    await flushPromises()
    await findConfirmButton(wrapper).trigger('click')
    expect(wrapper.emitted('confirm')).toEqual([[39.9042, 116.4074]])
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    wrapper.unmount()
  })

  it('关闭弹窗时销毁图表实例', async () => {
    const wrapper = await openPicker()
    expect(echartsMock.chart.dispose).not.toHaveBeenCalled()
    await wrapper.setProps({ modelValue: false })
    await flushPromises()
    expect(echartsMock.chart.dispose).toHaveBeenCalled()
    wrapper.unmount()
  })
})
