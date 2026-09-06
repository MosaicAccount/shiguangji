import Cookies from 'js-cookie'

const TokenKey = 'Admin-Token'

// 显式固定 Cookie 的 path/domain，保证 set 与 remove 的属性完全一致，
// 避免因默认 path 与部署路径差异导致退出时 Cookie 删除失败、残留 token，
// 从而让前台误判已登录（显示"管理后台"）并触发 getInfo 401 弹窗。
const TOKEN_ATTRS: Cookies.CookieAttributes = { path: '/' }

export function getToken(): string | undefined {
  return Cookies.get(TokenKey)
}

export function setToken(token: string): string | undefined {
  return Cookies.set(TokenKey, token, TOKEN_ATTRS)
}

export function removeToken(): void {
  Cookies.remove(TokenKey, TOKEN_ATTRS)
}
