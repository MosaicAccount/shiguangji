import { describe, it, expect } from 'vitest'
import { isPublicRoute, PUBLIC_ROUTES } from '@/utils/publicRoutes'

/**
 * 路由守卫的公开页白名单（0.0.3 回归：/plan 曾漏加，匿名访客被弹去登录页）。
 * 这类漏加的后果是「接口匿名可读、页面也能匿名渲染，只有守卫拦着」，肉眼不易发现，
 * 故在此钉住：公开的前台只读页必须在白名单里；私人页（/plan 心愿）、写笔记与后台不得进入。
 */

describe('isPublicRoute 公开路由白名单', () => {
  it('前台公开只读页免登录', () => {
    for (const path of ['/login', '/register', '/home', '/movie', '/book', '/travel', '/note', '/note/detail', '/search']) {
      expect(isPublicRoute(path), `${path} 应免登录`).toBe(true)
    }
  })

  it('心愿页是私人内容，不得免登录（结论 108）', () => {
    expect(isPublicRoute('/plan'), '/plan 是私人页，应要求登录').toBe(false)
  })

  it('回顾页是私人内容，不得免登录（D-11）', () => {
    expect(isPublicRoute('/review'), '/review 是私人页，应要求登录').toBe(false)
  })

  it('写笔记与后台不在白名单', () => {
    for (const path of ['/note/edit', '/note/draft', '/index', '/admin', '/system/user', '/business/item']) {
      expect(isPublicRoute(path), `${path} 不应免登录`).toBe(false)
    }
  })

  it('白名单内没有重复项', () => {
    expect(PUBLIC_ROUTES.length).toBe(new Set(PUBLIC_ROUTES).size)
  })
})
