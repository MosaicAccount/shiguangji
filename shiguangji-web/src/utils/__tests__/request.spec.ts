import { describe, it, expect, vi, beforeEach } from 'vitest'

/** element-plus 的三个全局提示入口全部换成 spy，便于断言「静默请求不弹」 */
const mocks = vi.hoisted(() => ({
  confirm: vi.fn((..._args: any[]) => Promise.reject(new Error('cancel'))),
  message: vi.fn((..._args: any[]) => undefined),
  notifyError: vi.fn((..._args: any[]) => undefined)
}))

vi.mock('element-plus', () => ({
  ElMessage: (...args: any[]) => mocks.message(...args),
  ElMessageBox: { confirm: (...args: any[]) => mocks.confirm(...args) },
  ElNotification: { error: (...args: any[]) => mocks.notifyError(...args) },
  ElLoading: { service: () => ({ close: vi.fn() }) }
}))
vi.mock('@/utils/auth', () => ({ getToken: () => 'test-token' }))
vi.mock('@/store/modules/user', () => ({ default: () => ({ logOut: () => Promise.resolve() }) }))

import service, { isRelogin } from '../request'

/** 取响应拦截器的成功分支（axios 的 InterceptorManager.handlers[0].fulfilled） */
function responseHandler(): (res: any) => any {
  return ((service.interceptors.response as any).handlers[0] as any).fulfilled
}

/** 取响应拦截器的失败分支（网络异常等） */
function errorHandler(): (error: any) => any {
  return ((service.interceptors.response as any).handlers[0] as any).rejected
}

function res(code: number, headers: Record<string, any> = {}) {
  return { data: { code, msg: `code-${code}` }, config: { headers, url: '/app/note/draft' }, request: {} }
}

/**
 * issue #32 结论 22：编辑页的自动同步必须静默。
 * 静默请求（config.headers.silent）在 401 / 500 / 601 / 网络异常时都只压掉全局提示，
 * promise 照旧 reject（调用方靠它把状态条转「仅本地待同步」）；不带该标记的请求行为不变（回归）
 */
describe('request 静默开关', () => {
  beforeEach(() => {
    mocks.confirm.mockClear()
    mocks.message.mockClear()
    mocks.notifyError.mockClear()
    isRelogin.show = false
  })

  it('401 默认弹「登录状态已过期」确认框', async () => {
    await expect(responseHandler()(res(401))).rejects.toBeTruthy()
    expect(mocks.confirm).toHaveBeenCalled()
  })

  it('静默请求 401 不弹确认框，但仍然 reject', async () => {
    await expect(responseHandler()(res(401, { silent: true }))).rejects.toBeTruthy()
    expect(mocks.confirm).not.toHaveBeenCalled()
  })

  it('500 / 601 默认弹提示，静默请求不弹但照旧 reject', async () => {
    await expect(responseHandler()(res(500))).rejects.toBeTruthy()
    expect(mocks.message).toHaveBeenCalledTimes(1)

    await expect(responseHandler()(res(601))).rejects.toBeTruthy()
    expect(mocks.message).toHaveBeenCalledTimes(2)

    mocks.message.mockClear()
    await expect(responseHandler()(res(500, { silent: true }))).rejects.toBeTruthy()
    await expect(responseHandler()(res(601, { silent: true }))).rejects.toBeTruthy()
    expect(mocks.message).not.toHaveBeenCalled()
  })

  it('网络异常默认弹提示，静默请求不弹', async () => {
    await expect(errorHandler()({ message: 'Network Error', config: { headers: {} } })).rejects.toBeTruthy()
    expect(mocks.message).toHaveBeenCalledTimes(1)

    mocks.message.mockClear()
    await expect(errorHandler()({ message: 'Network Error', config: { headers: { silent: true } } })).rejects.toBeTruthy()
    expect(mocks.message).not.toHaveBeenCalled()
  })

  it('成功响应照旧返回 data（静默与否都一样）', async () => {
    const data = await responseHandler()({ data: { code: 200, data: [1, 2] }, config: { headers: { silent: true } }, request: {} })
    expect(data).toEqual({ code: 200, data: [1, 2] })
  })
})
