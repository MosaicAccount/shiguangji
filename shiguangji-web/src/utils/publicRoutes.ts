import { isPathMatch } from '@/utils/validate'

/**
 * 免登录公开路由白名单（前台博客式只读页面）。
 *
 * 独立成一个模块的原因：这份清单是「新增前台页要记得加」的清单，漏加的后果是
 * 匿名访客被路由守卫弹去登录页（0.0.3 的 /plan 就漏过一次，接口本身是匿名的、
 * 页面也能匿名渲染，只有守卫拦着）。抽出来才能被单测钉住。
 *
 * - /login /register：认证页
 * - /home /movie /book /travel /note /note/detail /plan：前台公开只读
 * - /note/edit 需登录写笔记，不在白名单；后台路由（/index /admin /system 等）同理
 */
export const PUBLIC_ROUTES = ['/login', '/register', '/home', '/movie', '/book', '/travel', '/note', '/note/detail', '/plan']

/** 是否免登录可访问（前缀匹配，与守卫口径一致） */
export function isPublicRoute(path: string): boolean {
  return PUBLIC_ROUTES.some((pattern: string) => isPathMatch(pattern, path))
}
