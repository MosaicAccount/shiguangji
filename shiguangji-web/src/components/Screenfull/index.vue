<template>
  <div class="screenfull-btn" @click="toggle">
    <el-icon :size="16">
      <close v-if="isFullscreen" />
      <full-screen v-else />
    </el-icon>
  </div>
</template>

<script setup lang="ts">
import { useFullscreen } from '@vueuse/core'
import { Close, FullScreen } from '@element-plus/icons-vue'

const { isFullscreen, toggle } = useFullscreen()
</script>

<style lang='scss' scoped>
.screenfull-btn {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}
</style>
