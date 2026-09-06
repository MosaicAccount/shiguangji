<template>
  <div class="markdown-editor">
    <div class="md-toolbar">
      <el-radio-group v-model="mode" size="small">
        <el-radio-button value="edit">编辑</el-radio-button>
        <el-radio-button value="preview">预览</el-radio-button>
      </el-radio-group>
      <span class="md-hint">支持 Markdown 语法</span>
    </div>
    <el-input
      v-if="mode === 'edit'"
      v-model="innerValue"
      type="textarea"
      :rows="rows"
      placeholder="支持 Markdown 语法，如 **加粗**、# 标题、- 列表"
      @input="emitValue"
    />
    <div v-else class="md-preview">
      <markdown-viewer :content="innerValue" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import MarkdownViewer from '@/components/MarkdownViewer/index.vue'

const props = defineProps<{
  modelValue?: string | null
  rows?: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const mode = ref<'edit' | 'preview'>('edit')
const innerValue = ref(props.modelValue || '')

watch(
  () => props.modelValue,
  value => {
    if (value !== innerValue.value) {
      innerValue.value = value || ''
    }
  }
)

function emitValue(): void {
  emit('update:modelValue', innerValue.value)
}
</script>

<style scoped lang="scss">
.markdown-editor {
  .md-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;

    .md-hint {
      font-size: 12px;
      color: #909399;
    }
  }

  .md-preview {
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    padding: 12px 14px;
    min-height: 120px;
    max-height: 480px;
    overflow-y: auto;
    background: #fff;
  }
}
</style>
