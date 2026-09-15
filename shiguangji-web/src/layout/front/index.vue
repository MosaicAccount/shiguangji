<template>
  <div class="front-layout">
    <header class="front-header">
      <div class="front-header-inner">
        <router-link to="/home" class="logo">
          <span class="logo-text">拾光记</span>
          <span class="logo-en">SHIGUANGJI</span>
        </router-link>
        <nav class="front-nav">
          <router-link v-for="l in navLinks" :key="l.to" :to="l.to" class="nav-link">{{ l.label }}</router-link>
        </nav>
        <button
          class="theme-toggle"
          type="button"
          :title="isDark ? '切换到日间模式' : '切换到夜间模式'"
          :aria-label="isDark ? '切换到日间模式' : '切换到夜间模式'"
          @click="handleToggleTheme"
        >
          <svg-icon :icon-class="isDark ? 'sunny' : 'moon'" />
        </button>
      </div>
    </header>

    <main class="front-main">
      <router-view />
    </main>

    <footer class="front-footer">
      <div class="film-strip" aria-hidden="true">
        <span v-for="i in 17" :key="i" class="perf" />
      </div>
      <div class="footer-line">拾光记 · 把平凡的日子，写成值得回看的照片</div>
      <div class="footer-sub">© 2026 SHIGUANGJI —— 记录电影、书、旅程与每一个瞬间</div>
      <!-- 管理入口：低调放页脚，不打扰浏览 -->
      <div class="footer-links">
        <router-link v-if="isLogin" to="/admin" class="footer-link">后台管理</router-link>
        <router-link v-else to="/login" class="footer-link">登录后台</router-link>
      </div>
    </footer>

    <!-- 移动端底部导航（设计稿 04） -->
    <nav class="mobile-tab">
      <router-link v-for="t in mobileTabs" :key="t.to" :to="t.to" class="tab-item">
        <span>{{ t.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup lang="ts" name="FrontLayout">
import { getToken } from '@/utils/auth'
import useSettingsStore from '@/store/modules/settings'
import { animateThemeToggle } from '@/utils/theme'

/** 是否登录（访客页脚仅显示登录入口） */
const isLogin = computed(() => !!getToken())
const settingsStore = useSettingsStore()

const isDark = computed(() => settingsStore.isDark)

/** 日夜切换：与后台一致的圆形扩散过渡 */
function handleToggleTheme(event: MouseEvent): void {
  animateThemeToggle(() => settingsStore.toggleTheme(), event)
}

const navLinks = [
  { to: '/home', label: '首页' },
  { to: '/movie', label: '影单' },
  { to: '/book', label: '书单' },
  { to: '/travel', label: '足迹' },
  { to: '/note', label: '笔记' }
]

/** 移动端底部导航（设计稿 04：首页 / 影单 / 足迹 / 我的） */
const mobileTabs = [
  { to: '/home', label: '首页' },
  { to: '/movie', label: '影单' },
  { to: '/travel', label: '足迹' },
  { to: '/note', label: '我的' }
]
</script>

<style scoped lang="scss">
.front-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--sgj-bg);
  font-family: var(--sgj-font-sans);
}

.front-header {
  background: var(--sgj-bg-card);
  border-bottom: 1px solid var(--sgj-border-card);
  position: sticky;
  top: 0;
  z-index: 100;
  backdrop-filter: blur(6px);

  .front-header-inner {
    max-width: 1320px;
    margin: 0 auto;
    padding: 0 36px;
    height: 72px;
    display: flex;
    align-items: center;
  }

  .logo {
    display: flex;
    flex-direction: column;
    justify-content: center;
    text-decoration: none;
    line-height: 1.15;
    margin-right: 40px;

    .logo-text {
      font-family: var(--sgj-font-serif);
      font-size: 26px;
      font-weight: 700;
      color: var(--sgj-text);
      letter-spacing: 0.04em;
    }

    .logo-en {
      font-size: 9px;
      letter-spacing: 3.4px;
      color: var(--sgj-text-3);
      margin-top: 2px;
    }
  }

  .front-nav {
    display: flex;
    align-items: center;
    gap: 18px;

    .nav-link {
      position: relative;
      padding: 8px 0;
      color: var(--sgj-text-2);
      text-decoration: none;
      font-size: 14px;
      transition: color 0.2s;

      &:hover {
        color: var(--sgj-primary);
      }

      &.router-link-active {
        font-weight: 700;
        color: var(--sgj-primary);

        &::after {
          content: '';
          position: absolute;
          left: 50%;
          bottom: 0;
          transform: translateX(-50%);
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background: var(--sgj-primary);
        }
      }
    }
  }

  /* 日夜模式切换：圆形幽灵按钮 */
  .theme-toggle {
    margin-left: auto;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: var(--sgj-bg-deep);
    border: 1px solid var(--sgj-border-card);
    color: var(--sgj-text-2);
    font-size: 16px;
    cursor: pointer;
    flex-shrink: 0;
    transition: color 0.16s, border-color 0.16s, background 0.16s, transform 0.16s;

    &:hover {
      color: var(--sgj-primary);
      border-color: var(--sgj-primary);
      background: var(--sgj-primary-soft);
      transform: rotate(15deg);
    }

    &:active {
      transform: scale(0.94);
    }
  }
}

.front-main {
  flex: 1;
  width: 100%;
  max-width: 1320px;
  margin: 0 auto;
  padding: 36px;
  box-sizing: border-box;
}

/* 页脚：深色块 + 胶片齿孔条 + 文案 + 低调管理入口（设计稿） */
.front-footer {
  padding: 28px 20px 24px;
  text-align: center;
  background: var(--sgj-bg-deep);
  margin-top: 32px;

  .film-strip {
    display: flex;
    justify-content: center;
    gap: 14px;
    margin-bottom: 22px;

    .perf {
      width: 26px;
      height: 10px;
      border-radius: 5px;
      background: var(--sgj-bg-deep);
      box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.2);
    }
  }

  .footer-line {
    color: var(--sgj-text-3);
    font-size: 13px;
    letter-spacing: 1.2px;
  }

  .footer-sub {
    color: var(--sgj-text-4);
    font-size: 11px;
    letter-spacing: 1.4px;
    margin-top: 10px;
  }

  .footer-links {
    margin-top: 16px;
    display: flex;
    justify-content: center;
    gap: 18px;

    .footer-link {
      color: var(--sgj-text-4);
      font-size: 12px;
      letter-spacing: 1px;
      text-decoration: none;
      transition: color 0.16s;

      &:hover {
        color: var(--sgj-primary);
      }
    }
  }
}

/* 移动端最小适配：顶部导航不换行溢出，收缩内边距与字号 */
@media (max-width: 768px) {
  .front-header {
    .front-header-inner {
      padding: 0 12px;
      gap: 12px;
      height: 60px;
    }

    .logo {
      .logo-text {
        font-size: 20px;
      }

      .logo-en {
        display: none;
      }
    }

    .front-nav {
      gap: 2px;

      .nav-link {
        padding: 6px 8px;
        font-size: 14px;
      }
    }
  }

  .front-main {
    padding: 16px 12px;
  }

  .front-footer {
    padding: 18px 14px;
  }
}

/* 移动端底部导航：仅窄屏显示（设计稿 04） */
.mobile-tab {
  display: none;
}

@media (max-width: 768px) {
  .front-header .front-nav {
    display: none;
  }

  .mobile-tab {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 200;
    display: flex;
    height: 60px;
    background: var(--sgj-bg-card);
    border-top: 1px solid var(--sgj-border-card);

    .tab-item {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      text-decoration: none;
      color: var(--sgj-text-3);
      font-size: 13px;

      &.router-link-active {
        font-weight: 700;
        color: var(--sgj-primary);

        &::after {
          content: '';
          position: absolute;
          bottom: 10px;
          left: 50%;
          transform: translateX(-50%);
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background: var(--sgj-primary);
        }
      }
    }
  }

  .front-main {
    padding-bottom: 76px;
  }
}

@media (max-width: 375px) {
  .front-header {
    .front-nav .nav-link {
      padding: 6px 6px;
      font-size: 13px;
    }
  }
}
</style>
