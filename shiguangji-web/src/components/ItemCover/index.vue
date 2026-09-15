<template>
  <el-image
    v-if="src"
    :src="displaySrc"
    :preview-src-list="[displaySrc]"
    preview-teleported
    fit="cover"
    :style="boxStyle"
  >
    <template #error>
      <span class="item-cover-fallback" :style="boxStyle">{{ glyph }}</span>
    </template>
  </el-image>
  <span v-else class="item-cover-fallback" :style="boxStyle">{{ glyph }}</span>
</template>

<script setup lang="ts">
import { photoUrl } from '@/utils/sgj'

/** 条目封面统一展示：有图显示图片，无图或加载失败时回退类型衬线字色块（影/剧/书/地，与前台占位一致） */
const props = defineProps<{
  src?: string
  itemType?: string
  width?: number
  height?: number
}>()

const glyphs: Record<string, string> = { MOVIE: '影', TV: '剧', BOOK: '书', PLACE: '地' }
const glyph = computed(() => (props.itemType && glyphs[props.itemType]) || '拾')

/** 站内相对路径（/profile/...）需补 API 前缀，外链原样 */
const displaySrc = computed(() => photoUrl(props.src))

const boxStyle = computed(() => ({
  width: (props.width ?? 48) + 'px',
  height: (props.height ?? 72) + 'px',
  borderRadius: '4px'
}))
</script>

<style scoped>
/* 封面回退：衬线字浅色块（设计稿 v3.3 封面占位语言） */
.item-cover-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--sgj-primary-soft);
  color: var(--sgj-primary-dark);
  font-family: var(--sgj-font-serif);
  font-size: 20px;
  font-weight: 700;
}
html.dark .item-cover-fallback {
  color: var(--sgj-primary-light);
}
</style>
