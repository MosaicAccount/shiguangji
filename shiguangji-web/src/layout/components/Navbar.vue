<template>
  <div class="navbar">
    <hamburger id="hamburger-container" :is-active="appStore.sidebar.opened" class="hamburger-container" @toggleClick="toggleSideBar" />
    <breadcrumb id="breadcrumb-container" class="breadcrumb-container" />

    <!-- 桌面端右侧已清空：全局工具与账号统一收进侧边栏底部坞；移动端保留返回前台入口 -->
    <div class="right-menu">
      <router-link v-if="appStore.device === 'mobile'" to="/home" class="front-entry" title="返回前台">
        <svg-icon icon-class="home" />
        <span class="front-entry-text">返回前台</span>
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import Breadcrumb from '@/components/Breadcrumb/index.vue'
import Hamburger from '@/components/Hamburger/index.vue'
import useAppStore from '@/store/modules/app'

const appStore = useAppStore()

function toggleSideBar(): void {
  appStore.toggleSideBar()
}
</script>

<style lang='scss' scoped>
.navbar {
  height: 64px;
  overflow: hidden;
  position: relative;
  background: var(--navbar-bg);
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  align-items: center;
  box-sizing: border-box;

  .hamburger-container {
    line-height: 60px;
    height: 100%;
    cursor: pointer;
    transition: background 0.3s;
    -webkit-tap-highlight-color: transparent;
    display: flex;
    align-items: center;
    flex-shrink: 0;
    margin-right: 8px;

    &:hover {
      background: rgba(0, 0, 0, 0.025);
    }
  }

  .breadcrumb-container {
    flex-shrink: 0;

    /* 面包屑：衬线标题 + 11px 次级（设计稿顶栏） */
    .el-breadcrumb {
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 18px;
      color: var(--navbar-text);

      .el-breadcrumb__separator {
        font-family: var(--sgj-font-sans);
        font-weight: 400;
        font-size: 12px;
        color: var(--el-text-color-placeholder);
      }

      .el-breadcrumb__inner {
        color: var(--navbar-text);
        font-weight: 700;
      }

      .el-breadcrumb__item:last-child .el-breadcrumb__inner {
        color: var(--navbar-text);
      }
    }
  }

  .right-menu {
    height: 100%;
    display: flex;
    align-items: center;
    flex-shrink: 0;
    margin-left: auto;

    /* 返回前台：描边胶囊按钮（仅移动端顶栏保留） */
    .front-entry {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      height: 32px;
      padding: 0 14px;
      margin-right: 12px;
      border: 1px solid var(--sgj-border, #d5dcd9);
      border-radius: 16px;
      font-size: 13px;
      color: var(--navbar-text);
      text-decoration: none;
      white-space: nowrap;
      transition: color 0.24s, border-color 0.24s, background 0.24s;

      &:hover {
        color: var(--el-color-primary);
        border-color: var(--el-color-primary);
        background: var(--current-color-light, rgba(0, 0, 0, 0.03));
      }
    }
  }
}
</style>
