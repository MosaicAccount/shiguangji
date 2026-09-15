<template>
  <div class="markdown-editor">
    <div class="md-toolbar">
      <span class="md-hint">支持 Markdown 语法，右侧实时预览</span>
      <span class="md-count">{{ innerValue.length }} 字</span>
    </div>
    <div class="md-panes" :style="{ height: paneHeight }">
      <el-input
        v-model="innerValue"
        type="textarea"
        class="md-input"
        :placeholder="placeholder"
        @input="emitValue"
      />
      <div class="md-preview">
        <markdown-viewer :content="innerValue" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import MarkdownViewer from '@/components/MarkdownViewer/index.vue'

const props = defineProps<{
  modelValue?: string | null
  /** 编辑/预览面板高度（CSS 值），默认 320px */
  height?: string
  placeholder?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const innerValue = ref(props.modelValue || '')
const paneHeight = ref(props.height || '320px')

watch(
  () => props.modelValue,
  value => {
    if (value !== innerValue.value) {
      innerValue.value = value || ''
    }
  }
)

watch(
  () => props.height,
  value => {
    paneHeight.value = value || '320px'
  }
)

function emitValue(): void {
  emit('update:modelValue', innerValue.value)
}
</script>

<style scoped lang="scss">
.markdown-editor {
  /* 父级常为 flex 容器（如 el-form-item__content），需显式占满一行 */
  width: 100%;

  .md-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;

    .md-hint {
      font-size: 12px;
      color: #909399;
    }

    .md-count {
      font-size: 12px;
      color: #909399;
    }
  }

  .md-panes {
    display: flex;
    gap: 12px;

    .md-input,
    .md-preview {
      flex: 1;
      min-width: 0;
      height: 100%;
    }

    .md-input {
      :deep(.el-textarea) {
        height: 100%;
      }

      :deep(.el-textarea__inner) {
        height: 100%;
        resize: none;
      }
    }

    .md-preview {
      border: 1px solid var(--sgj-border-card, #dcdfe6);
      border-radius: 4px;
      padding: 5px 14px;
      overflow-y: auto;
      background: var(--sgj-bg-card, #fff);
      box-sizing: border-box;
    }
  }

  /* 窄屏上下堆叠，保证编辑区与预览区各自可用宽度 */
  @media (max-width: 768px) {
    .md-panes {
      flex-direction: column;
      height: auto !important;

      .md-input,
      .md-preview {
        height: 260px;
      }
    }
  }
}
</style>
