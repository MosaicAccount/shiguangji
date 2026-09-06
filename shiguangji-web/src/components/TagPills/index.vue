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

<style scoped>
.tag-pills {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.tag-label {
  font-size: 13px;
  color: var(--sgj-text-3);
  margin-right: 2px;
}

.tag-pill {
  padding: 0 14px;
  height: 28px;
  border: 1px solid var(--sgj-border);
  border-radius: 14px;
  background: var(--sgj-bg-card);
  color: var(--sgj-text-2);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.16s;
}

.tag-pill:hover {
  border-color: var(--sgj-primary);
  color: var(--sgj-primary);
}

.tag-pill.active {
  background: var(--sgj-primary);
  border-color: var(--sgj-primary);
  color: #fff;
}

.tag-more {
  border: none;
  background: none;
  padding: 0 4px;
  color: var(--sgj-text-4);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.16s;
}

.tag-more:hover {
  color: var(--sgj-primary);
}
</style>
