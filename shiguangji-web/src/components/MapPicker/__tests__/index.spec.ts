import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { ElButton, ElInput } from 'element-plus'
import MapPicker from '../index.vue'
import { wgs84ToGcj02, gcj02ToWgs84 } from '@/utils/coord'
import { loadAMap, searchPlaces, reverseGeocode, getPoiEmoji } from '@/utils/map'

/**
 * 高德 JS API mock：FakeMap 捕获构造参数与事件 handler，FakeMarker 记录实例；
 * '@/utils/map' 保留真实的 isInChina/loadChinaPolygons（fetch 桩驱动），
 * loadAMap/searchPlaces/reverseGeocode 以桩代替。
 */
const amapMock = vi.hoisted(() => {
  const state = {
    mapHandlers: {} as Record<string, (e?: any) => void>,
    mapOpts: [] as any[],
    markers: [] as any[],
    /** 各地图实例 setZoomAndCenter 的 [zoom, center] 调用汇总 */
    zoomCalls: [] as [number, number[]][],
    limitBounds: null as any,
    removeCalls: 0,
    destroyCalls: 0
  }
  class FakeBounds {
    constructor(public sw: number[], public ne: number[]) {}
  }
  class FakeMarker {
    setPosition = vi.fn()
    setContent = vi.fn()
    constructor(public opts: any) {
      state.markers.push(this)
    }
  }
  class FakeMap {
    constructor(public el: unknown, public opts: any) {
      state.mapOpts.push(opts)
    }
    on(event: string, handler: (e?: any) => void) {
      state.mapHandlers[event] = handler
    }
    setZoomAndCenter(zoom: number, center: number[]) {
      state.zoomCalls.push([zoom, center])
    }
    setLimitBounds(bounds: any) {
      state.limitBounds = bounds
    }
    add(_marker: any) {}
    remove(_marker: any) {
      state.removeCalls++
    }
    destroy() {
      state.destroyCalls++
    }
  }
  return {
    state,
    FakeBounds,
    FakeMarker,
    FakeMap,
    AMap: { Map: FakeMap, Bounds: FakeBounds, Marker: FakeMarker }
  }
})

/** 蓝点定位标记数量（与主题色选点标记按内容色区分） */
function locateMarkerCount(): number {
  return amapMock.state.markers.filter(m => String(m.opts.content).includes('#1E6FFF')).length
}

vi.mock('@/utils/map', async importOriginal => {
  const actual = await importOriginal<Record<string, unknown>>()
  return {
    ...actual,
    loadAMap: vi.fn(async () => amapMock.AMap),
    searchPlaces: vi.fn(async () => []),
    reverseGeocode: vi.fn(async () => ({})),
    getPoiEmoji: vi.fn(async () => '🏨')
  }
})

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
  amapMock.state.mapHandlers = {}
  amapMock.state.mapOpts = []
  amapMock.state.markers = []
  amapMock.state.zoomCalls = []
  amapMock.state.limitBounds = null
  amapMock.state.removeCalls = 0
  amapMock.state.destroyCalls = 0
  // 默认拦截 fetch，避免加载中国轮廓真实联网；需要轮廓的用例自行覆盖
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

/** 地图显示坐标：WGS-84 表单值转高德 GCJ-02 的 [lng, lat] */
function gcjLngLat(lat: number, lng: number): number[] {
  const [gLat, gLng] = wgs84ToGcj02(lat, lng)
  return [gLng, gLat]
}

describe('MapPicker', () => {
  it('地图限定中国：zooms 6-19、视角硬边界为中国范围', async () => {
    const wrapper = await openPicker()
    expect(amapMock.state.mapOpts[0]).toMatchObject({ zoom: 13, zooms: [6, 19] })
    expect(amapMock.state.limitBounds).toBeInstanceOf(amapMock.FakeBounds)
    expect(amapMock.state.limitBounds.sw).toEqual([73, 15])
    expect(amapMock.state.limitBounds.ne).toEqual([136, 54])
    wrapper.unmount()
  })

  it('打开时创建高德地图，已有坐标转 GCJ-02 定位街道级并打点', async () => {
    const wrapper = await openPicker({ latitude: 30.5, longitude: 100.25 })
    expect(amapMock.state.mapOpts[0].center).toEqual(gcjLngLat(30.5, 100.25))
    expect(amapMock.state.markers).toHaveLength(1)
    expect(amapMock.state.markers[0].opts.position).toEqual(gcjLngLat(30.5, 100.25))
    wrapper.unmount()
  })

  it('无定位授权时默认定位故宫视角且不打点', async () => {
    const wrapper = await openPicker()
    expect(amapMock.state.mapOpts[0].center).toEqual(gcjLngLat(39.91634, 116.3972))
    expect(amapMock.state.markers).toHaveLength(0)
    expect(wrapper.text()).not.toContain('已选')
    wrapper.unmount()
  })

  it('用户允许定位时视角移动到当前位置并显示蓝点标记', async () => {
    stubGeolocation((success) => success({ coords: { latitude: 31.2304, longitude: 121.4737 } }))
    const wrapper = await openPicker()
    await flushPromises()
    expect(amapMock.state.zoomCalls).toContainEqual([13, gcjLngLat(31.2304, 121.4737)])
    expect(locateMarkerCount()).toBe(1)
    wrapper.unmount()
  })

  it('首次定位等待期间地图显示 loading 遮罩，定位返回后解除并跳转', async () => {
    stubGeolocation((success) => {
      setTimeout(() => success({ coords: { latitude: 31.2304, longitude: 121.4737 } }), 10)
    })
    const wrapper = mountPicker()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    const mapEl = wrapper.find('.picker-map').element as HTMLElement
    expect(mapEl.dataset.loading).toBe('true')
    await new Promise(resolve => setTimeout(resolve, 40))
    expect(mapEl.dataset.loading).toBe('false')
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
    expect(amapMock.state.markers).toHaveLength(0)
    wrapper.unmount()
  })

  it('会话内缓存定位：再次打开立即就位且不挂遮罩，无需等待定位返回', async () => {
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
    // 视角与蓝点立即出现在缓存定位处，不依赖定位返回；后台刷新不遮罩，地图可操作
    expect(wrapper.find('.picker-map').element as HTMLElement).toMatchObject({ dataset: { loading: 'false' } })
    expect(amapMock.state.mapOpts[1].center).toEqual(gcjLngLat(31.2304, 121.4737))
    expect(amapMock.state.markers.length).toBeGreaterThanOrEqual(1)
    wrapper.unmount()
  })

  it('已授权时用户拖动地图后定位仅显示蓝点标记，不抢跳视角', async () => {
    stubPermissionState('granted')
    stubGeolocation((success) => {
      setTimeout(() => success({ coords: { latitude: 31.2304, longitude: 121.4737 } }), 10)
    })
    const wrapper = mountPicker()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    amapMock.state.mapHandlers.dragstart?.({})
    await new Promise(resolve => setTimeout(resolve, 40))
    // 仅初始视角（故宫），未跳到当前位置；蓝点标记已显示
    expect(amapMock.state.zoomCalls).toEqual([])
    expect(locateMarkerCount()).toBe(1)
    wrapper.unmount()
  })

  it('首次授权流程：等待授权期间拖动过地图，同意授权后仍定位到当前位置', async () => {
    stubPermissionState('prompt')
    stubGeolocation((success) => {
      setTimeout(() => success({ coords: { latitude: 31.2304, longitude: 121.4737 } }), 10)
    })
    const wrapper = mountPicker()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    amapMock.state.mapHandlers.dragstart?.({})
    amapMock.state.mapHandlers.zoomstart?.({})
    await new Promise(resolve => setTimeout(resolve, 40))
    // 同意授权 = 明确要求定位：即使拖动过也要跳到当前位置并显示蓝点
    expect(amapMock.state.zoomCalls).toContainEqual([13, gcjLngLat(31.2304, 121.4737)])
    expect(locateMarkerCount()).toBe(1)
    wrapper.unmount()
  })

  it('定位成功后显示回到当前位置按钮，点击飞回定位点', async () => {
    stubGeolocation((success) => success({ coords: { latitude: 31.2304, longitude: 121.4737 } }))
    const wrapper = await openPicker()
    await flushPromises()
    const backBtn = wrapper.findAll('button').find(b => b.attributes('title') === '回到当前位置')
    expect(backBtn).toBeDefined()
    amapMock.state.zoomCalls.length = 0
    await backBtn!.trigger('click')
    expect(amapMock.state.zoomCalls).toEqual([[13, gcjLngLat(31.2304, 121.4737)]])
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
    expect(amapMock.state.zoomCalls).toEqual([])
    wrapper.unmount()
  })

  it('点击地图拾取 GCJ-02 坐标，转 WGS-84 存表单并打点', async () => {
    const wrapper = await openPicker()
    amapMock.state.mapHandlers.click({ lnglat: { lat: 39.9042, lng: 116.4074 } })
    await flushPromises()
    const [wLat, wLng] = gcj02ToWgs84(39.9042, 116.4074)
    const expLat = Number(wLat.toFixed(6))
    const expLng = Number(wLng.toFixed(6))
    expect(wrapper.text()).toContain(`已选：纬度 ${expLat}，经度 ${expLng}`)
    expect(amapMock.state.markers).toHaveLength(1)
    // 落点回 GCJ 显示（GCJ→WGS→GCJ 算法往返有 1e-6 级微差，近似断言）
    const [pickLng, pickLat] = amapMock.state.markers[0].opts.position
    expect(pickLng).toBeCloseTo(116.4074, 4)
    expect(pickLat).toBeCloseTo(39.9042, 4)
    expect((findConfirmButton(wrapper).element as HTMLButtonElement).disabled).toBe(false)
    // 选点标记带弹跳动画类；重复选点重建标记（而非挪位），重放落点弹跳动画
    expect(amapMock.state.markers[0].opts.content).toContain('pick-pin')
    amapMock.state.mapHandlers.click({ lnglat: { lat: 39.905, lng: 116.408 } })
    await flushPromises()
    expect(amapMock.state.markers).toHaveLength(2)
    expect(amapMock.state.removeCalls).toBe(1)
    wrapper.unmount()
  })

  it('点击中国轮廓外（边界矩形内）不选点并提示', async () => {
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
      throw new Error('unexpected fetch: ' + u)
    }))
    const wrapper = await openPicker()
    await flushPromises()
    // 视角矩形范围内、但中国轮廓外（境外）
    amapMock.state.mapHandlers.click({ lnglat: { lat: 20, lng: 110 } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('已选')
    expect((findConfirmButton(wrapper).element as HTMLButtonElement).disabled).toBe(true)
    wrapper.unmount()
  })

  it('点击底图 POI 图标（hotspot）：白底徽标盖住原图标并放大显示分类图标', async () => {
    vi.mocked(getPoiEmoji).mockResolvedValueOnce('🏨')
    const wrapper = await openPicker()
    amapMock.state.mapHandlers.hotspotclick?.({ name: '故宫博物院', id: 'B001', lnglat: { lat: 39.916, lng: 116.397 } })
    await flushPromises()
    const [wLat, wLng] = gcj02ToWgs84(39.916, 116.397)
    expect(wrapper.text()).toContain(`已选：故宫博物院，纬度 ${Number(wLat.toFixed(6))}`)
    expect(amapMock.state.markers).toHaveLength(1)
    // 徽标标记：白底大圆（先显示回退 📍，POI 分类查询返回后替换为 🏨），名称标签放大显示
    const badge = amapMock.state.markers[0]
    expect(badge.opts.content).toContain('32px')
    expect(badge.opts.content).toContain('📍')
    expect(badge.opts.content).toContain('font-size:14px')
    expect(badge.opts.content).toContain('故宫博物院')
    await flushPromises()
    expect(badge.setContent).toHaveBeenCalledTimes(1)
    // 静默替换：emoji 更新为分类图标，但不带动画类，避免二次弹跳
    const swapped = String(badge.setContent.mock.calls[0][0])
    expect(swapped).toContain('🏨')
    expect(swapped).toContain('故宫博物院')
    expect(swapped).not.toContain('pick-pin')
    wrapper.unmount()
  })

  it('POI 热点点击后紧随的普通点击不覆盖名称；之后普通点击恢复无名称选点', async () => {
    const wrapper = await openPicker()
    amapMock.state.mapHandlers.hotspotclick?.({ name: '故宫博物院', lnglat: { lat: 39.916, lng: 116.397 } })
    await flushPromises()
    // 热点后紧随的 click（事件顺序兜底）：不覆盖 POI 名称
    amapMock.state.mapHandlers.click({ lnglat: { lat: 39.9042, lng: 116.4074 } })
    await flushPromises()
    expect(wrapper.text()).toContain('已选：故宫博物院，')
    // 超出热点时间窗口后的普通点击：清掉名称，正常选点
    await new Promise(resolve => setTimeout(resolve, 220))
    amapMock.state.mapHandlers.click({ lnglat: { lat: 39.9042, lng: 116.4074 } })
    await flushPromises()
    const [wLat, wLng] = gcj02ToWgs84(39.9042, 116.4074)
    expect(wrapper.text()).toContain(`已选：纬度 ${Number(wLat.toFixed(6))}`)
    expect(wrapper.text()).not.toContain('故宫博物院')
    wrapper.unmount()
  })

  it('未选点时确认按钮禁用', async () => {
    const wrapper = await openPicker()
    expect((findConfirmButton(wrapper).element as HTMLButtonElement).disabled).toBe(true)
    wrapper.unmount()
  })

  it('确认选择后 emit confirm 坐标并关闭弹窗（逆地理失败时仅坐标）', async () => {
    const wrapper = await openPicker({ latitude: 1, longitude: 2 })
    amapMock.state.mapHandlers.click({ lnglat: { lat: 39.9042, lng: 116.4074 } })
    await flushPromises()
    const [wLat, wLng] = gcj02ToWgs84(39.9042, 116.4074)
    await findConfirmButton(wrapper).trigger('click')
    await flushPromises()
    expect(wrapper.emitted('confirm')).toEqual([[{ latitude: Number(wLat.toFixed(6)), longitude: Number(wLng.toFixed(6)) }]])
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    wrapper.unmount()
  })

  it('确认选择时逆地理编码解析名称/地址/城市/国家一起 emit', async () => {
    vi.mocked(reverseGeocode).mockResolvedValueOnce({
      title: '景山公园',
      address: '北京市东城区景山前街4号',
      city: '北京市',
      country: '中国'
    })
    const wrapper = await openPicker({ latitude: 1, longitude: 2 })
    amapMock.state.mapHandlers.click({ lnglat: { lat: 39.9042, lng: 116.4074 } })
    await flushPromises()
    const [wLat, wLng] = gcj02ToWgs84(39.9042, 116.4074)
    await findConfirmButton(wrapper).trigger('click')
    await flushPromises()
    expect(wrapper.emitted('confirm')).toEqual([
      [
        {
          latitude: Number(wLat.toFixed(6)),
          longitude: Number(wLng.toFixed(6)),
          title: '景山公园',
          address: '北京市东城区景山前街4号',
          city: '北京市',
          country: '中国'
        }
      ]
    ])
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    wrapper.unmount()
  })

  it('POI 命中的名称优先于逆地理结果', async () => {
    vi.mocked(reverseGeocode).mockResolvedValueOnce({ title: '逆地理名', address: '某地址', city: '某市', country: '中国' })
    const wrapper = await openPicker({ latitude: 1, longitude: 2 })
    amapMock.state.mapHandlers.hotspotclick?.({ name: '故宫博物院', lnglat: { lat: 39.916, lng: 116.397 } })
    await flushPromises()
    await findConfirmButton(wrapper).trigger('click')
    await flushPromises()
    const [place] = (wrapper.emitted('confirm') as any[][])[0] as any[]
    expect(place.title).toBe('故宫博物院')
    expect(place.address).toBe('某地址')
    wrapper.unmount()
  })

  it('关闭弹窗时销毁地图实例', async () => {
    const wrapper = await openPicker()
    expect(amapMock.state.mapOpts).toHaveLength(1)
    await wrapper.setProps({ modelValue: false })
    await flushPromises()
    expect(amapMock.state.destroyCalls).toBe(1)
    wrapper.unmount()
  })

  it('JS API 加载失败时提示且不创建地图，重开可恢复', async () => {
    vi.mocked(loadAMap).mockRejectedValueOnce(new Error('load fail'))
    const wrapper = mountPicker()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    expect(amapMock.state.mapOpts).toHaveLength(0)
    await wrapper.setProps({ modelValue: false })
    await flushPromises()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    expect(amapMock.state.mapOpts).toHaveLength(1)
    wrapper.unmount()
  })

  it('搜索景点命中结果列表，选择后打点、定位并显示名称', async () => {
    vi.mocked(searchPlaces).mockResolvedValueOnce([
      { label: '故宫博物院（北京市东城区）', latitude: 39.91634, longitude: 116.39716, title: '故宫博物院', address: '景山前街4号', city: '北京市', country: '中国' }
    ])
    const wrapper = await openPicker()
    await wrapper.find('input').setValue('故宫')
    const searchButton = wrapper.findAll('button').find(b => b.text().includes('搜索'))!
    await searchButton.trigger('click')
    await flushPromises()
    expect(searchPlaces).toHaveBeenCalledWith('故宫')
    expect(wrapper.text()).toContain('故宫博物院（北京市东城区）')
    await wrapper.find('.picker-results li').trigger('click')
    await flushPromises()
    // 打点（显示坐标转 GCJ-02）+ 定位到 15 级；表单显示 WGS-84 原值与地点名称
    expect(amapMock.state.markers).toHaveLength(1)
    expect(amapMock.state.markers[0].opts.position).toEqual(gcjLngLat(39.91634, 116.39716))
    expect(amapMock.state.zoomCalls).toContainEqual([15, gcjLngLat(39.91634, 116.39716)])
    expect(wrapper.text()).toContain('已选：故宫博物院，纬度 39.91634，经度 116.39716')
    wrapper.unmount()
  })

  it('搜索无结果时提示换关键词', async () => {
    const wrapper = await openPicker()
    await wrapper.find('input').setValue('不存在的地方xyz')
    await wrapper.findAll('button').find(b => b.text().includes('搜索'))!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('未找到相关地点')
    wrapper.unmount()
  })

  it('搜索接口失败时提示可直接点图选点', async () => {
    vi.mocked(searchPlaces).mockRejectedValueOnce(new Error('network down'))
    const wrapper = await openPicker()
    await wrapper.find('input').setValue('杭州')
    await wrapper.findAll('button').find(b => b.text().includes('搜索'))!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('搜索失败')
    wrapper.unmount()
  })
})
