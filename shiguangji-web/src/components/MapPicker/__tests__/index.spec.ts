import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { ElButton, ElInput } from 'element-plus'
import MapPicker from '../index.vue'
import { wgs84ToGcj02, gcj02ToWgs84 } from '@/utils/coord'

/**
 * leaflet mock：init 返回共享假 map，捕获 click/dragstart handler 与 marker 交互。
 * 地图显示空间是高德瓦片的 GCJ-02，断言里的显示坐标用真实转换函数计算。
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
    removeLayer: vi.fn(),
    flyTo: vi.fn()
  }
  const marker = {
    addTo: vi.fn(() => marker),
    setLatLng: vi.fn()
  }
  return {
    state,
    map,
    marker,
    mapFactory: vi.fn(() => map),
    latLngBounds: vi.fn((sw, ne) => ({
      sw,
      ne,
      contains: vi.fn(([lat, lng]: [number, number]) =>
        lat >= sw[0] && lat <= ne[0] && lng >= sw[1] && lng <= ne[1]
      )
    })),
    tileLayer: vi.fn(() => ({ addTo: vi.fn() })),
    markerFactory: vi.fn(() => marker),
    divIcon: vi.fn(opts => opts)
  }
})

vi.mock('leaflet', () => ({
  default: {
    map: leafletMock.mapFactory,
    latLngBounds: leafletMock.latLngBounds,
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

/** v-loading 桩：把绑定值写到 dataset，供断言定位等待遮罩的开关时机 */
const loadingStub = {
  mounted(el: HTMLElement, binding: { value: boolean }) {
    el.dataset.loading = String(binding.value)
  },
  updated(el: HTMLElement, binding: { value: boolean }) {
    el.dataset.loading = String(binding.value)
  }
}

function mountPicker(props: Record<string, unknown> = {}) {
  return mount(MapPicker, {
    props: { modelValue: false, ...props },
    global: {
      components: { ElDialog: ElDialogStub, ElButton, ElInput },
      directives: { loading: loadingStub }
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
  // 默认拦截 fetch，避免确认选点的逆地理编码真实联网；搜索用例自行覆盖
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('no fetch in tests')))
})

afterEach(() => {
  vi.unstubAllGlobals()
  delete (window.navigator as any).geolocation
  delete (window.navigator as any).permissions
})

/** 注入 mock 的浏览器定位 API */
function stubGeolocation(impl: (success: (p: { coords: { latitude: number; longitude: number } }) => void, error: (e: unknown) => void) => void) {
  Object.defineProperty(window.navigator, 'geolocation', {
    value: { getCurrentPosition: impl },
    configurable: true
  })
}

/** 注入权限查询状态：granted=已授权（拖动=主动避开定位），prompt=待授权（同意后必跳转） */
function stubPermissionState(state: 'granted' | 'prompt') {
  Object.defineProperty(window.navigator, 'permissions', {
    value: { query: async () => ({ state }) },
    configurable: true
  })
}

/** 假的中国轮廓（105~125E, 25~45N，覆盖北京），可附带搜索结果按 URL 路由 fetch */
function stubChinaPolygonFetch(searchResults?: unknown[]) {
  vi.stubGlobal('fetch', vi.fn(async (url: string | URL) => {
    const u = String(url)
    if (u.includes('/map/china.json')) {
      return {
        ok: true,
        json: async () => ({
          type: 'FeatureCollection',
          features: [
            { type: 'Feature', properties: {}, geometry: { type: 'MultiPolygon', coordinates: [[[[105, 25], [125, 25], [125, 45], [105, 45], [105, 25]]]] } }
          ]
        })
      }
    }
    if (searchResults && u.includes('/search')) {
      return { ok: true, json: async () => searchResults }
    }
    throw new Error('unexpected fetch: ' + u)
  }))
}

describe('MapPicker', () => {
  it('地图限定中国：minZoom=6（省级）、maxBounds 为中国范围且不可拖出', async () => {
    const wrapper = await openPicker()
    expect(leafletMock.mapFactory).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        minZoom: 6,
        maxBounds: expect.objectContaining({ sw: [15, 73], ne: [54, 136] }),
        maxBoundsViscosity: 1.0
      })
    )
    wrapper.unmount()
  })

  it('打开时创建高德瓦片地图，已有坐标转 GCJ-02 定位街道级并打点', async () => {
    const wrapper = await openPicker({ latitude: 30.5, longitude: 100.25 })
    expect(leafletMock.mapFactory).toHaveBeenCalled()
    expect(leafletMock.map.setView).toHaveBeenCalledWith(wgs84ToGcj02(30.5, 100.25), 13)
    expect(leafletMock.tileLayer).toHaveBeenCalledWith(
      expect.stringContaining('autonavi.com'),
      expect.objectContaining({ maxZoom: 19 })
    )
    expect(leafletMock.markerFactory).toHaveBeenCalledWith(wgs84ToGcj02(30.5, 100.25), expect.anything())
    wrapper.unmount()
  })

  it('无定位授权时默认定位故宫视角且不打点', async () => {
    const wrapper = await openPicker()
    expect(leafletMock.map.setView).toHaveBeenCalledWith(wgs84ToGcj02(39.91634, 116.3972), 13)
    expect(leafletMock.markerFactory).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('已选')
    wrapper.unmount()
  })

  it('用户允许定位时视角移动到当前位置并显示蓝点标记', async () => {
    stubGeolocation((success) => success({ coords: { latitude: 31.2304, longitude: 121.4737 } }))
    const wrapper = await openPicker()
    await flushPromises()
    expect(leafletMock.map.setView).toHaveBeenCalledWith(wgs84ToGcj02(31.2304, 121.4737), 13)
    expect(leafletMock.markerFactory).toHaveBeenCalledWith(wgs84ToGcj02(31.2304, 121.4737), expect.anything())
    wrapper.unmount()
  })

  it('首次定位等待期间地图显示 loading 遮罩，定位返回后解除并跳转', async () => {
    // 定位慢：10ms 后才返回，模拟授权/定位耗时
    stubGeolocation((success) => {
      setTimeout(() => success({ coords: { latitude: 31.2304, longitude: 121.4737 } }), 10)
    })
    const wrapper = mountPicker()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    const mapEl = wrapper.find('.picker-map').element as HTMLElement
    // 等待期间遮罩挂起，挡住拖动/缩放/点选
    expect(mapEl.dataset.loading).toBe('true')
    await new Promise(resolve => setTimeout(resolve, 40))
    expect(mapEl.dataset.loading).toBe('false')
    expect(leafletMock.map.setView).toHaveBeenCalledWith(wgs84ToGcj02(31.2304, 121.4737), 13)
    wrapper.unmount()
  })

  it('定位失败（如拒绝授权）时解除等待遮罩，可正常操作地图', async () => {
    stubGeolocation((_success, error) => setTimeout(() => error(new Error('denied')), 10))
    const wrapper = mountPicker()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    const mapEl = wrapper.find('.picker-map').element as HTMLElement
    expect(mapEl.dataset.loading).toBe('true')
    await new Promise(resolve => setTimeout(resolve, 40))
    expect(mapEl.dataset.loading).toBe('false')
    expect(leafletMock.map.setView).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('有会话缓存定位时再次打开不挂遮罩，立即就位可操作', async () => {
    // 第一次打开：定位成功，写入会话缓存
    stubGeolocation((success) => success({ coords: { latitude: 31.2304, longitude: 121.4737 } }))
    const wrapper = await openPicker()
    await flushPromises()
    await wrapper.setProps({ modelValue: false })
    await flushPromises()
    // 第二次打开：定位接口迟迟不返回（真实场景的数秒等待）
    stubGeolocation(() => {})
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    // 视角与蓝点立即出现在缓存定位处，且地图未被遮罩挡住
    expect(wrapper.find('.picker-map').element as HTMLElement).toMatchObject({ dataset: { loading: 'false' } })
    expect(leafletMock.map.setView).toHaveBeenCalledWith(wgs84ToGcj02(31.2304, 121.4737), 13)
    expect(leafletMock.markerFactory).toHaveBeenCalledWith(wgs84ToGcj02(31.2304, 121.4737), expect.anything())
    wrapper.unmount()
  })

  it('已授权时用户拖动地图后定位仅显示蓝点标记，不抢跳视角', async () => {
    stubPermissionState('granted')
    // 定位慢：10ms 后才返回，期间用户先拖动地图
    stubGeolocation((success) => {
      setTimeout(() => success({ coords: { latitude: 31.2304, longitude: 121.4737 } }), 10)
    })
    const wrapper = mountPicker()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    leafletMock.state.mapHandlers.dragstart?.({})
    await new Promise(resolve => setTimeout(resolve, 40))
    expect(leafletMock.map.setView).toHaveBeenCalledTimes(1)
    expect(leafletMock.map.setView).toHaveBeenCalledWith(wgs84ToGcj02(39.91634, 116.3972), 13)
    expect(leafletMock.markerFactory).toHaveBeenCalledWith(wgs84ToGcj02(31.2304, 121.4737), expect.anything())
    wrapper.unmount()
  })

  it('首次授权流程：等待授权期间拖动过地图，同意授权后仍定位到当前位置', async () => {
    stubPermissionState('prompt')
    // 授权慢：10ms 后用户才点「允许」返回，期间用户先拖动/缩放地图
    stubGeolocation((success) => {
      setTimeout(() => success({ coords: { latitude: 31.2304, longitude: 121.4737 } }), 10)
    })
    const wrapper = mountPicker()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    leafletMock.state.mapHandlers.dragstart?.({})
    leafletMock.state.mapHandlers.zoomstart?.({})
    await new Promise(resolve => setTimeout(resolve, 40))
    // 同意授权 = 明确要求定位：即使拖动过也要跳到当前位置并显示蓝点
    expect(leafletMock.map.setView).toHaveBeenCalledWith(wgs84ToGcj02(31.2304, 121.4737), 13)
    expect(leafletMock.markerFactory).toHaveBeenCalledWith(wgs84ToGcj02(31.2304, 121.4737), expect.anything())
    wrapper.unmount()
  })

  it('定位成功后显示回到当前位置按钮，点击飞回定位点', async () => {
    stubGeolocation((success) => success({ coords: { latitude: 31.2304, longitude: 121.4737 } }))
    const wrapper = await openPicker()
    await flushPromises()
    const backBtn = wrapper.findAll('button').find(b => b.attributes('title') === '回到当前位置')
    expect(backBtn).toBeDefined()
    expect(leafletMock.map.flyTo).not.toHaveBeenCalled()
    await backBtn!.trigger('click')
    expect(leafletMock.map.flyTo).toHaveBeenCalledWith(wgs84ToGcj02(31.2304, 121.4737), 13)
    wrapper.unmount()
  })

  it('定位未成功时不显示回到当前位置按钮', async () => {
    const wrapper = await openPicker()
    await flushPromises()
    expect(wrapper.findAll('button').find(b => b.attributes('title') === '回到当前位置')).toBeUndefined()
    wrapper.unmount()
  })

  it('定位结果在国外时保持故宫默认视角', async () => {
    stubGeolocation((success) => success({ coords: { latitude: 35.6762, longitude: 139.6503 } }))
    const wrapper = await openPicker()
    await flushPromises()
    expect(leafletMock.map.setView).toHaveBeenCalledTimes(1)
    expect(leafletMock.map.setView).toHaveBeenCalledWith(wgs84ToGcj02(39.91634, 116.3972), 13)
    wrapper.unmount()
  })

  it('拒绝授权时保持故宫默认视角', async () => {
    stubGeolocation((_success, error) => error(new Error('denied')))
    const wrapper = await openPicker()
    await flushPromises()
    expect(leafletMock.map.setView).toHaveBeenCalledTimes(1)
    expect(leafletMock.map.setView).toHaveBeenCalledWith(wgs84ToGcj02(39.91634, 116.3972), 13)
    wrapper.unmount()
  })

  it('点击中国轮廓外（边界矩形内）不选点并提示', async () => {
    stubChinaPolygonFetch()
    const wrapper = await openPicker()
    await flushPromises()
    // 视角矩形范围内、但中国轮廓外（境外）
    leafletMock.state.mapHandlers.click({ latlng: { lat: 20, lng: 110 } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('已选')
    expect((findConfirmButton(wrapper).element as HTMLButtonElement).disabled).toBe(true)
    wrapper.unmount()
  })

  it('点击地图拾取 GCJ-02 坐标，转 WGS-84 存表单并打点', async () => {
    const wrapper = await openPicker()
    leafletMock.state.mapHandlers.click({ latlng: leafletMock.state.clickLatlng })
    await flushPromises()
    const [wLat, wLng] = gcj02ToWgs84(39.9042, 116.4074)
    const expLat = Number(wLat.toFixed(6))
    const expLng = Number(wLng.toFixed(6))
    expect(wrapper.text()).toContain(`已选：纬度 ${expLat}，经度 ${expLng}`)
    expect(leafletMock.markerFactory).toHaveBeenCalledWith(wgs84ToGcj02(expLat, expLng), expect.anything())
    expect((findConfirmButton(wrapper).element as HTMLButtonElement).disabled).toBe(false)
    // 选点标记带弹跳动画类
    expect(leafletMock.divIcon).toHaveBeenCalledWith(expect.objectContaining({ html: expect.stringContaining('pick-pin') }))
    // 重复选点：重建标记（而非挪位），重放落点弹跳动画
    leafletMock.state.mapHandlers.click({ latlng: { lat: 39.905, lng: 116.408 } })
    await flushPromises()
    expect(leafletMock.markerFactory).toHaveBeenCalledTimes(2)
    expect(leafletMock.map.removeLayer).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('存储坐标按 6 位小数精度截断', async () => {
    const wrapper = await openPicker()
    leafletMock.state.clickLatlng = { lat: 39.98765432, lng: 116.1234567 }
    leafletMock.state.mapHandlers.click({ latlng: leafletMock.state.clickLatlng })
    await flushPromises()
    const [wLat, wLng] = gcj02ToWgs84(39.98765432, 116.1234567)
    expect(wrapper.text()).toContain(`已选：纬度 ${Number(wLat.toFixed(6))}，经度 ${Number(wLng.toFixed(6))}`)
    wrapper.unmount()
  })

  it('未选点时确认按钮禁用', async () => {
    const wrapper = await openPicker()
    expect((findConfirmButton(wrapper).element as HTMLButtonElement).disabled).toBe(true)
    wrapper.unmount()
  })

  it('确认选择后 emit confirm 坐标并关闭弹窗（逆地理失败时仅坐标）', async () => {
    const wrapper = await openPicker({ latitude: 1, longitude: 2 })
    leafletMock.state.mapHandlers.click({ latlng: leafletMock.state.clickLatlng })
    await flushPromises()
    const [wLat, wLng] = gcj02ToWgs84(39.9042, 116.4074)
    await findConfirmButton(wrapper).trigger('click')
    await flushPromises()
    expect(wrapper.emitted('confirm')).toEqual([[{ latitude: Number(wLat.toFixed(6)), longitude: Number(wLng.toFixed(6)) }]])
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    wrapper.unmount()
  })

  it('确认选择时逆地理编码解析名称/地址/城市/国家一起 emit', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          name: '故宫博物院',
          address: { state: '北京市', city: '北京市', county: '东城区', road: '景山前街', house_number: '4号', country: '中国' }
        })
      })
    )
    const wrapper = await openPicker({ latitude: 1, longitude: 2 })
    leafletMock.state.mapHandlers.click({ latlng: leafletMock.state.clickLatlng })
    await flushPromises()
    const [wLat, wLng] = gcj02ToWgs84(39.9042, 116.4074)
    await findConfirmButton(wrapper).trigger('click')
    await flushPromises()
    expect(wrapper.emitted('confirm')).toEqual([
      [
        {
          latitude: Number(wLat.toFixed(6)),
          longitude: Number(wLng.toFixed(6)),
          title: '故宫博物院',
          address: '北京市东城区景山前街4号',
          city: '北京市',
          country: '中国'
        }
      ]
    ])
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
    stubChinaPolygonFetch([
      { display_name: '故宫博物院, 北京', lat: '39.91634', lon: '116.39716' }
    ])
    const wrapper = await openPicker()
    await wrapper.find('input').setValue('故宫')
    const searchButton = wrapper.findAll('button').find(b => b.text().includes('搜索'))!
    await searchButton.trigger('click')
    await flushPromises()
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining('q=%E6%95%85%E5%AE%AB'))
    // 搜索结果限定在中国范围
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining('viewbox=73,54,136,15'))
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining('bounded=1'))
    expect(wrapper.text()).toContain('故宫博物院, 北京')
    await wrapper.find('.picker-results li').trigger('click')
    await flushPromises()
    // 打点（显示坐标转 GCJ-02）+ 定位到 15 级；表单显示 WGS-84 原值
    expect(leafletMock.markerFactory).toHaveBeenCalledWith(wgs84ToGcj02(39.91634, 116.39716), expect.anything())
    expect(leafletMock.map.flyTo).toHaveBeenCalledWith(wgs84ToGcj02(39.91634, 116.39716), 15)
    expect(wrapper.text()).toContain('已选：纬度 39.91634，经度 116.39716')
    wrapper.unmount()
  })

  it('搜索无结果时提示换关键词', async () => {
    stubChinaPolygonFetch([])
    const wrapper = await openPicker()
    await wrapper.find('input').setValue('不存在的地方xyz')
    await wrapper.findAll('button').find(b => b.text().includes('搜索'))!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('未找到中国范围内的地点')
    wrapper.unmount()
  })

  it('搜索结果全部在中国轮廓外时提示无中国范围结果', async () => {
    // 乌兰乌德（俄罗斯）：viewbox 矩形内、中国轮廓外
    stubChinaPolygonFetch([
      { display_name: 'Ulan-Ude, Russia', lat: '51.83', lon: '107.58' }
    ])
    const wrapper = await openPicker()
    await wrapper.find('input').setValue('乌兰乌德')
    await wrapper.findAll('button').find(b => b.text().includes('搜索'))!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('未找到中国范围内的地点')
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
