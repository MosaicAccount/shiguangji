<template>
  <div class="front-layout hd-app">
    <!-- 手绘抖线滤镜：全局皮肤里 .hd-rough / 装饰线用 filter:url(#rough) 指向它 -->
    <svg width="0" height="0" style="position: absolute" aria-hidden="true">
      <filter id="rough">
        <feTurbulence type="fractalNoise" baseFrequency="0.02 0.05" numOctaves="2" seed="7" result="n" />
        <feDisplacementMap in="SourceGraphic" in2="n" scale="3.2" xChannelSelector="R" yChannelSelector="G" />
      </filter>
    </svg>

    <header class="hd-top">
      <div class="hd-top-in">
        <router-link to="/home" class="hd-brand"><span class="seal">拾</span>拾光记</router-link>
        <nav class="hd-nav">
          <router-link
            v-for="l in navLinks"
            :key="l.to"
            :to="l.to"
            class="hd-nav-link"
            :class="{ on: isActive(l.to) }"
          >{{ l.label }}</router-link>
        </nav>
        <div class="hd-top-acts">
          <button class="hd-iconbtn" type="button" title="全站搜索" aria-label="全站搜索" @click="router.push('/search')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 Q18 18 21 21"/></svg>
          </button>
          <button
            class="hd-iconbtn"
            type="button"
            :title="isDark ? '切换到日间模式' : '切换到夜间模式'"
            :aria-label="isDark ? '切换到日间模式' : '切换到夜间模式'"
            @click="handleToggleTheme"
          >
            <svg v-if="isDark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="4.5"/><path d="M12 2.5 V5 M12 19 V21.5 M2.5 12 H5 M19 12 H21.5 M5 5 L6.8 6.8 M17.2 17.2 L19 19 M19 5 L17.2 6.8 M6.8 17.2 L5 19"/></svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M20 14 A8.5 8.5 0 1 1 10 4 A7 7 0 0 0 20 14 Z"/></svg>
          </button>
        </div>
      </div>
    </header>

    <main class="hd-main">
      <router-view />
    </main>

    <footer class="hd-foot">
      <div class="hd-foot-in">
        <span>拾光记 · 把平凡的日子，写成值得回看的照片</span>
        <span>© 2026 SHIGUANGJI —— 记录电影、书、旅程与每一个瞬间</span>
        <router-link v-if="isLogin" to="/admin">后台管理</router-link>
        <router-link v-else to="/login">登录后台</router-link>
      </div>
    </footer>

    <nav class="hd-tabbar" aria-label="移动端导航">
      <router-link
        v-for="t in mobileTabs"
        :key="t.to"
        :to="t.to"
        :class="{ on: isActive(t.to) }"
      >
        <svg v-if="t.icon === 'home'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 11 Q4 6 12 5 Q20 6 20 11 L20 20 L4 20 Z"/><path d="M9 20 L9 14 L15 14 L15 20"/></svg>
        <svg v-else-if="t.icon === 'movie'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="3" y="6" width="18" height="13" rx="2"/><path d="M3 10 L21 10 M8 6 L8 19 M16 6 L16 19"/></svg>
        <svg v-else-if="t.icon === 'travel'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 21 Q12 14 8 10 Q4 6 12 4 Q20 6 16 10 Q12 14 12 21 Z"/><circle cx="12" cy="9" r="2"/></svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 3 L18 3 L18 21 L6 21 Z"/><path d="M9 8 L15 8 M9 12 L15 12 M9 16 L13 16"/></svg>
        <span>{{ t.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup lang="ts" name="FrontLayout">
import { getToken } from '@/utils/auth'
// 0.0.6 手绘形制层（--hd-*）全站装载；字体同步全局加载（核心片复核见 #108）
import '@/assets/styles/handdrawn/handdrawn-site.css'
import useSettingsStore from '@/store/modules/settings'
import { animateThemeToggle } from '@/utils/theme'

/** 是否登录（访客页脚仅显示登录入口） */
const isLogin = computed(() => !!getToken())
const settingsStore = useSettingsStore()
const router = useRouter()
const route = useRoute()

/** 当前导航项（手绘下划线）：/note/detail 之类的子路由仍高亮所属一级项 */
function isActive(to: string): boolean {
  return route.path === to || route.path.startsWith(to + '/')
}

const isDark = computed(() => settingsStore.isDark)

/** 日夜切换：与后台一致的圆形扩散过渡 */
function handleToggleTheme(event: MouseEvent): void {
  animateThemeToggle(() => settingsStore.toggleTheme(), event)
}

/** 桌面导航（0.0.3：末尾追加「心愿」，不动既有顺序；768px 以下整体隐藏改显底部 tab）
 *  心愿是私人内容（设计 结论 108）：访客不显示该入口，直接访问 /plan 由路由守卫弹去登录 */
const navLinks = computed(() => {
  const links = [
    { to: '/home', label: '首页' },
    { to: '/movie', label: '影单' },
    { to: '/book', label: '书单' },
    { to: '/travel', label: '足迹' },
    { to: '/note', label: '笔记' },
    { to: '/plan', label: '心愿' }
  ]
  return isLogin.value ? links : links.filter(l => l.to !== '/plan')
})

/** 移动端底部导航（设计稿 04：首页 / 影单 / 足迹 / 笔记） */
const mobileTabs = [
  { to: '/home', label: '首页', icon: 'home' },
  { to: '/movie', label: '影单', icon: 'movie' },
  { to: '/travel', label: '足迹', icon: 'travel' },
  { to: '/note', label: '笔记', icon: 'note' }
]
</script>

<style scoped lang="scss">
/* 0.0.6：外壳（顶栏 / 页脚 / 移动 tab / 导航下划线 / 主题按钮）全部来自
   src/assets/styles/handdrawn/handdrawn-site.css 的共享类；
   这里只保留页面骨架，避免与共享皮肤重复定义。 */
.front-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.hd-main {
  flex: 1;
  width: 100%;
}
</style>
