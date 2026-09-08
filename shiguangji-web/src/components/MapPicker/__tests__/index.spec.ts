import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { ElButton, ElInput } from 'element-plus'
import MapPicker from '../index.vue'

/**
 * leaflet mock：init 返回共享假 map，捕获 click handler 与 marker 交互
 */
const leafletMock = vi.hoisted(() => {
  const state = {
    mapHandlers: {} as Record<string, (e?: unknown) => void>,
    clickLatlng: { lat: 39.9042, lng: 116.4074 }
  }
  const map = {
    setView: vi.fn(() => map),
    on: vi.fn((event: string, handler: (e?: unknown) => void) => {
      state.mapHandlers[event] = handler
    }),
    remove: vi.fn(),
    flyTo: vi.fn()
  }
  const marker = {
    addTo: vi.fn(),
    setLatLng: vi.fn()
  }
  return {
    state,
    map,
    marker,
    mapFactory: vi.fn(() => map),
    tileLayer: vi.fn(() => ({ addTo: vi.fn() })),
    markerFactory: vi.fn(() => marker),
    divIcon: vi.fn(opts => opts)
  }
})

vi.mock('leaflet', () => ({
  default: {
    map: leafletMock.mapFactory,
    tileLayer: leafletMock.tileLayer,
    marker: leafletMock.markerFactory,
    divIcon: leafletMock.divIcon
  }
}))

/** el-dialog 桩：modelValue 为 true 时内联渲染默认与 footer 插槽 */
const ElDialogStub = defineComponent({
  props: { modelValue: { type: Boolean, default: false } },
  template: '<div v-if="modelValue" class="dialog-stub"><slot /><slot name="footer" /></div>'
})

function mountPicker(props: Record<string, unknown> = {}) {
  return mount(MapPicker, {
    props: { modelValue: false, ...props },
    global: {
      components: { ElDialog: ElDialogStub, ElButton, ElInput }
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
  leafletMock.state.mapHandlers = {}
  leafletMock.state.clickLatlng = { lat: 39.9042, lng: 116.4074 }
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('MapPicker', () => {
  it('打开时创建 OSM 地图，已有坐标定位到街道级并打点', async () => {
    const wrapper = await openPicker({ latitude: 30.5, longitude: 100.25 })
    expect(leafletMock.mapFactory).toHaveBeenCalled()
    expect(leafletMock.map.setView).toHaveBeenCalledWith([30.5, 100.25], 13)
    expect(leafletMock.tileLayer).toHaveBeenCalledWith(
      expect.stringContaining('openstreetmap'),
      expect.objectContaining({ maxZoom: 19 })
    )
    expect(leafletMock.markerFactory).toHaveBeenCalledWith([30.5, 100.25], expect.anything())
    wrapper.unmount()
  })

  it('无坐标时默认中国视角且不打点', async () => {
    const wrapper = await openPicker()
    expect(leafletMock.map.setView).toHaveBeenCalledWith([35, 105], 4)
    expect(leafletMock.markerFactory).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('已选')
    wrapper.unmount()
  })

  it('点击地图拾取经纬度并打点', async () => {
    const wrapper = await openPicker()
    leafletMock.state.mapHandlers.click({ latlng: leafletMock.state.clickLatlng })
    await flushPromises()
    expect(leafletMock.markerFactory).toHaveBeenCalledWith([39.9042, 116.4074], expect.anything())
    expect(wrapper.text()).toContain('已选：纬度 39.9042，经度 116.4074')
    expect((findConfirmButton(wrapper).element as HTMLButtonElement).disabled).toBe(false)
    wrapper.unmount()
  })

  it('点击坐标按 6 位小数精度截断', async () => {
    const wrapper = await openPicker()
    leafletMock.state.clickLatlng = { lat: 39.98765432, lng: 116.1234567 }
    leafletMock.state.mapHandlers.click({ latlng: leafletMock.state.clickLatlng })
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
    leafletMock.state.mapHandlers.click({ latlng: leafletMock.state.clickLatlng })
    await flushPromises()
    await findConfirmButton(wrapper).trigger('click')
    expect(wrapper.emitted('confirm')).toEqual([[39.9042, 116.4074]])
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    wrapper.unmount()
  })

  it('关闭弹窗时销毁地图实例', async () => {
    const wrapper = await openPicker()
    expect(leafletMock.map.remove).not.toHaveBeenCalled()
    await wrapper.setProps({ modelValue: false })
    await flushPromises()
    expect(leafletMock.map.remove).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('搜索景点命中结果列表，选择后打点并定位', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [{ display_name: '故宫博物院, 北京', lat: '39.91634', lon: '116.39716' }]
      })
    )
    const wrapper = await openPicker()
    await wrapper.find('input').setValue('故宫')
    const searchButton = wrapper.findAll('button').find(b => b.text().includes('搜索'))!
    await searchButton.trigger('click')
    await flushPromises()
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining('q=%E6%95%85%E5%AE%AB'))
    expect(wrapper.text()).toContain('故宫博物院, 北京')
    await wrapper.find('.picker-results li').trigger('click')
    await flushPromises()
    // 打点 + 定位到 15 级
    expect(leafletMock.markerFactory).toHaveBeenCalledWith([39.91634, 116.39716], expect.anything())
    expect(leafletMock.map.flyTo).toHaveBeenCalledWith([39.91634, 116.39716], 15)
    expect(wrapper.text()).toContain('已选：纬度 39.91634，经度 116.39716')
    wrapper.unmount()
  })

  it('搜索无结果时提示换关键词', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => [] })
    )
    const wrapper = await openPicker()
    await wrapper.find('input').setValue('不存在的地方xyz')
    await wrapper.findAll('button').find(b => b.text().includes('搜索'))!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('未找到相关地点')
    wrapper.unmount()
  })

  it('搜索接口失败时提示可直接点图选点', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network down')))
    const wrapper = await openPicker()
    await wrapper.find('input').setValue('杭州')
    await wrapper.findAll('button').find(b => b.text().includes('搜索'))!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('搜索失败')
    wrapper.unmount()
  })
})
