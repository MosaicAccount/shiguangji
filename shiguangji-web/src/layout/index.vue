<template>
  <div :class="classObj" class="app-wrapper" :style="{ '--current-color': currentColor, '--current-color-light': currentColor + '1a', '--current-color-dark-bg': currentColor + '33' }">
    <!-- D-03/：移动端提示条，建议使用 PC 管理 -->
    <div v-if="device === 'mobile'" class="mobile-tip-bar">📱 建议使用 PC 端管理后台，部分功能在手机上可能不便操作</div>
    <div v-if="device === 'mobile' && sidebar.opened" class="drawer-bg" @click="handleClickOutside"/>
    <sidebar v-if="!sidebar.hide" class="sidebar-container" @setLayout="setLayout" />
    <div :class="{ hasTagsView: needTagsView, sidebarHide: sidebar.hide }" class="main-container">
      <div :class="{ 'fixed-header': fixedHeader }">
        <!-- 顶栏仅移动端渲染（抽屉开关）；桌面端全局工具与账号收进侧边栏底部坞 -->
        <navbar v-if="showNavbar" />
        <tags-view v-if="needTagsView" />
      </div>
      <app-main />
      <settings ref="settingRef" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useWindowSize } from '@vueuse/core'
import Sidebar from './components/Sidebar/index.vue'
import { AppMain, Navbar, Settings, TagsView } from './components'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'

const settingsStore = useSettingsStore()
/** 强调色（页签激活、弹出菜单高亮等）：暗色下提亮为 #C27A6D，与暗色 --el-color-primary 一致，避免深底上暗陶土字看不清 */
const currentColor = computed(() => (settingsStore.isDark ? '#C27A6D' : settingsStore.theme))
const sidebar = computed(() => useAppStore().sidebar)
const device = computed(() => useAppStore().device)
const needTagsView = computed(() => settingsStore.tagsView)
const fixedHeader = computed(() => settingsStore.fixedHeader)

/** 顶部导航栏仅保留给移动端（抽屉开关） */
const showNavbar = computed(() => device.value === 'mobile')

/** 纯净侧栏布局（桌面端）：无顶部栏，页签条即顶栏 */
const isSlimLayout = computed(() => device.value !== 'mobile')

const classObj = computed(() => ({
  hideSidebar: !sidebar.value.opened,
  openSidebar: sidebar.value.opened,
  withoutAnimation: sidebar.value.withoutAnimation,
  mobile: device.value === 'mobile',
  layoutSlim: isSlimLayout.value
}))

const { width, height } = useWindowSize()
const WIDTH = 992 // refer to Bootstrap's responsive design

watch(() => device.value, () => {
  if (device.value === 'mobile' && sidebar.value.opened) {
    useAppStore().closeSideBar({ withoutAnimation: false })
  }
})

watchEffect(() => {
  if (width.value - 1 < WIDTH) {
    useAppStore().toggleDevice('mobile')
    useAppStore().closeSideBar({ withoutAnimation: true })
  } else {
    useAppStore().toggleDevice('desktop')
  }
})

function handleClickOutside(): void {
  useAppStore().closeSideBar({ withoutAnimation: false })
}

const settingRef = ref<any>(null)
function setLayout() {
  settingRef.value.openSetting()
}
</script>

<style lang="scss" scoped>
@use "@/assets/styles/mixin.scss" as mix;
@use "@/assets/styles/variables.module.scss" as vars;

.app-wrapper {
  @include mix.clearfix;
  position: relative;
  height: 100%;
  width: 100%;

  &.mobile.openSidebar {
    position: fixed;
    top: 0;
  }
}

.main-container:has(.fixed-header) {
  height: 100vh;
  overflow: hidden;
}

.drawer-bg {
  background: #000;
  opacity: 0.3;
  width: 100%;
  top: 0;
  height: 100%;
  position: absolute;
  z-index: 999;
}

/* D-03/：移动端提示条（固定顶部，提示使用 PC 管理） */
.mobile-tip-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 2000;
  background: var(--sgj-primary, #a65336);
  color: #fff;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
  padding: 5px 12px;
}

.fixed-header {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 9;
  width: calc(100% - #{vars.$base-sidebar-width});
  transition: width 0.28s;
}

.hideSidebar .fixed-header {
  width: calc(100% - 54px);
}

.sidebarHide .fixed-header {
  width: 100%;
}

.mobile .fixed-header {
  width: 100%;
  /* D-03/：为顶部提示条让位 */
  top: 32px;
}

/* 纯净侧栏布局（桌面端）：无顶部栏，页签条即顶栏（34px） */
.app-wrapper.layoutSlim .main-container.hasTagsView .fixed-header + .app-main {
  margin-top: 34px;
  height: calc(100vh - 34px);
  min-height: calc(100vh - 34px);
}

.app-wrapper.layoutSlim .main-container:not(.hasTagsView) .fixed-header + .app-main {
  margin-top: 0;
  height: 100vh;
  min-height: 100vh;
}
</style>
