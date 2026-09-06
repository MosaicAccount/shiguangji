<template>
  <div class="sidebar-logo-container" :class="{ 'collapse': collapse }">
    <transition name="sidebarLogoFade">
      <router-link v-if="collapse" key="collapse" class="sidebar-logo-link" to="/">
        <span class="brand-letter">拾</span>
      </router-link>
      <router-link v-else key="expand" class="sidebar-logo-link" to="/">
        <div class="sidebar-logo-text">
          <h1 class="sidebar-title">拾光记</h1>
          <span class="sidebar-logo-en">C O N S O L E</span>
        </div>
      </router-link>
    </transition>
  </div>
</template>

<script setup lang="ts">
import useSettingsStore from '@/store/modules/settings'
import variables from '@/assets/styles/variables.module.scss'

defineProps({
  collapse: {
    type: Boolean,
    required: true
  }
})

const settingsStore = useSettingsStore()

// 获取Logo背景色
const getLogoBackground = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-bg)'
  }
  return variables.menuBg
})

// 获取Logo文字颜色
const getLogoTextColor = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-logo-text)'
  }
  return '#fff'
})
</script>

<style lang="scss" scoped>
.sidebarLogoFade-enter-active {
  transition: opacity 1.5s;
}

.sidebarLogoFade-enter,
.sidebarLogoFade-leave-to {
  opacity: 0;
}

.sidebar-logo-container {
  position: relative;
  height: 72px;
  /* flex 垂直居中：sidebar.scss 的 #app .sidebar-container a 会把内层链接覆盖为
     inline-block，line-height 基线对齐会让标题块偏上，这里直接由容器居中 */
  display: flex;
  align-items: center;
  justify-content: center;
  background: v-bind(getLogoBackground);
  text-align: center;
  overflow: hidden;

  & .sidebar-logo-link {
    height: 100%;
    width: 100%;
    /* sidebar.scss 的 #app .sidebar-container a { display: inline-block } 会盖掉 flex，
       标题块因此贴顶；用 !important 恢复 flex 让内容垂直居中 */
    display: flex !important;
    align-items: center;
    justify-content: center;
    gap: 6px;
    text-decoration: none;

    /*  品牌字标：衬线「拾」圆形 */
    & .brand-letter {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 30px;
      height: 30px;
      border-radius: 50%;
      background: var(--el-color-primary);
      color: #fff;
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 14px;
      line-height: 1;
    }

    & .sidebar-logo-text {
      display: flex;
      flex-direction: column;
      align-items: center;
      line-height: 1.2;
    }

    & .sidebar-title {
      display: block;
      margin: 0;
      color: v-bind(getLogoTextColor);
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 24px;
      letter-spacing: 0.06em;
      white-space: nowrap;
    }

      & .sidebar-logo-en {
      font-size: 10px;
      letter-spacing: 3px;
      color: rgba(174, 184, 179, 0.9);
      margin-top: 2px;
      white-space: nowrap;
    }
  }
}
</style>