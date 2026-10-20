<template>
  <router-view />
</template>

<script setup lang="ts">
import useSettingsStore from '@/store/modules/settings'
import { handleThemeStyle } from '@/utils/theme'

// 仅开发环境：?theme=dark|light 强制主题，便于逐页截图与设计图比对（生产不生效）。
// 须在 setup 阶段写进 VueUse 的存储键，否则子组件（如 TravelMap）里再次 useDark()
// 会按系统偏好把暗色类加回来。
if (import.meta.env.DEV) {
  const forced = new URLSearchParams(window.location.search).get('theme')
  if (forced === 'dark' || forced === 'light') {
    localStorage.setItem('vueuse-color-scheme', forced)
    document.documentElement.classList.toggle('dark', forced === 'dark')
  }
}

onMounted(() => {
  nextTick(() => {
    // 初始化主题样式
    handleThemeStyle(useSettingsStore().theme)
  })
})
</script>
