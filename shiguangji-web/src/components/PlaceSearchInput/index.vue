<template>
  <el-autocomplete
    :model-value="modelValue"
    :fetch-suggestions="fetchSuggestions"
    :placeholder="placeholder || '输入名称搜索地点，或直接填写'"
    value-key="label"
    :debounce="500"
    clearable
    style="width: 100%"
    @update:model-value="onInput"
    @select="onSelect"
  >
    <template #default="{ item }">
      <div class="place-suggestion">
        <span class="place-suggestion-title">{{ item.title }}</span>
        <span class="place-suggestion-addr">{{ item.address || item.label }}</span>
      </div>
    </template>
  </el-autocomplete>
</template>

<script setup lang="ts" name="PlaceSearchInput">
import { searchPlaces } from '@/utils/map'
import type { PlaceResult } from '@/utils/map'

const props = defineProps<{
  modelValue?: string
  placeholder?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'select', place: PlaceResult): void
}>()

/** 远程搜索地点候选；空关键词直接返回空列表 */
async function fetchSuggestions(query: string): Promise<PlaceResult[]> {
  const q = query.trim()
  if (!q) return []
  try {
    return await searchPlaces(q)
  } catch (e) {
    return []
  }
}

/** 输入即更新名称（可填自定义名称） */
function onInput(value: string): void {
  emit('update:modelValue', value)
}

/** 选中候选：更新名称并通知表单回填地址/城市/国家/坐标 */
function onSelect(place: PlaceResult): void {
  emit('update:modelValue', place.title)
  emit('select', place)
}
</script>

<style scoped lang="scss">
.place-suggestion {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
  overflow: hidden;

  .place-suggestion-title {
    font-size: 13px;
    color: var(--sgj-text, #333);
  }

  .place-suggestion-addr {
    font-size: 12px;
    color: var(--sgj-text-4, #999);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
