<template>
  <el-select
    v-model="selected"
    :multiple="multiple"
    filterable
    clearable
    :collapse-tags="multiple"
    :collapse-tags-tooltip="multiple"
    :placeholder="placeholder"
    :loading="loading"
    style="width: 100%"
  >
    <el-option v-for="opt in options" :key="opt.value" :label="opt.label" :value="opt.value" />
  </el-select>
</template>

<script setup lang="ts" name="TagSelect">
import { listAppTag } from '@/api/front/tag'
import type { TagModule } from '@/types/api/business/tag'

/**
 * 标签选择器：选项来自后台标签管理（仅启用标签），不可自由输入（无 allow-create）。
 * v-model 为英文逗号分隔字符串，与记录的 tags 字段存储格式一致；
 * 存量记录中不在标签表内的旧值仍会在回显时以原样显示（el-select 直显 value）。
 */
const props = withDefaults(defineProps<{
  modelValue?: string
  module: TagModule
  /** 表单多选（默认）；筛选用单选 */
  multiple?: boolean
  placeholder?: string
}>(), {
  modelValue: '',
  multiple: true,
  placeholder: '选择标签'
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const options = ref<{ label: string; value: string }[]>([])
const loading = ref(false)

const selected = computed<string | string[]>({
  get() {
    const parts = (props.modelValue || '').split(',').map(s => s.trim()).filter(Boolean)
    return props.multiple ? parts : (parts[0] || '')
  },
  set(value) {
    const arr = Array.isArray(value) ? value : (value ? [value] : [])
    emit('update:modelValue', arr.join(','))
  }
})

watch(() => props.module, loadTags, { immediate: true })

function loadTags(): void {
  if (!props.module) return
  loading.value = true
  listAppTag(props.module).then(response => {
    options.value = (response.data || [])
      .map(t => t.tagName || '')
      .filter(Boolean)
      .map(name => ({ label: name, value: name }))
  }).catch(() => {}).finally(() => {
    loading.value = false
  })
}
</script>
