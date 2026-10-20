<template>
  <div v-if="options.length" class="tag-pills">
    <span class="tag-label">标签</span>
    <button
      v-for="t in visibleTags"
      :key="t"
      type="button"
      class="tag-pill"
      :class="{ active: t === modelValue }"
      @click="toggle(t)"
    >{{ t }}</button>
    <button v-if="options.length > limit" type="button" class="tag-more" @click="expanded = !expanded">
      {{ expanded ? '收起 ▴' : '更多 ▾' }}
    </button>
  </div>
</template>

<script setup lang="ts" name="TagPills">
import { listAppTag } from '@/api/front/tag'
import type { TagModule } from '@/types/api/business/tag'

/**
 * 豆瓣式标签筛选行：一行可点击的标签胶囊，点选高亮、再点取消（单选）。
 * 选项来自后台标签管理（仅启用标签，匿名可用）；默认展示 limit 个，其余收进「更多」。
 */
const props = withDefaults(defineProps<{
  /** 当前选中的标签名（'' 为未选中） */
  modelValue?: string
  module: TagModule
  /** 收起状态最多展示的标签数 */
  limit?: number
}>(), {
  modelValue: '',
  limit: 10
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const options = ref<string[]>([])
const expanded = ref(false)

const visibleTags = computed(() => {
  if (expanded.value) return options.value
  const base = options.value.slice(0, props.limit)
  // 选中的标签不在首屏时追加展示，避免"选了但看不到"的错觉
  if (props.modelValue && options.value.includes(props.modelValue) && !base.includes(props.modelValue)) {
    base.push(props.modelValue)
  }
  return base
})

watch(() => props.module, loadTags, { immediate: true })

function loadTags(): void {
  listAppTag(props.module).then(response => {
    options.value = (response.data || [])
      .map(t => t.tagName || '')
      .filter(Boolean)
  }).catch(() => {})
}

function toggle(tag: string): void {
  emit('update:modelValue', props.modelValue === tag ? '' : tag)
}
</script>

<style scoped lang="scss">
/* 0.0.6 标签筛选行：手绘胶囊（纸色底 + 抖线边，选中陶红） */
.tag-pills {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-family: var(--hd-hand);
}

.tag-label {
  font-size: 13px;
  color: var(--hd-ink-faint);
  letter-spacing: 1px;
}

.tag-pill {
  padding: 2px 12px;
  font-size: 13.5px;
  color: var(--hd-ink-soft);
  background: var(--hd-paper);
  border: 1.5px solid var(--hd-line-soft);
  border-radius: 999px;
  cursor: pointer;
  transition: color .14s, border-color .14s, background-color .14s;

  &:hover { color: var(--hd-red); border-color: var(--hd-red) }

  &.active {
    color: var(--hd-red);
    border-color: var(--hd-red);
    background: var(--hd-red-soft);
  }
}

.tag-more {
  padding: 2px 8px;
  font-size: 13px;
  color: var(--hd-ink-faint);
  background: none;
  border: 0;
  cursor: pointer;

  &:hover { color: var(--hd-red) }
}
</style>
