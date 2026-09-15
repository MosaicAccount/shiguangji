<template>
  <div class="markdown-editor">
    <div class="md-toolbar">
      <span class="md-hint">{{ isMobile ? '支持 Markdown 语法' : '支持 Markdown 语法，右侧实时预览' }}</span>
      <div class="md-toolbar-right">
        <!-- 移动端无侧栏预览，收进弹窗 -->
        <el-button v-if="isMobile" size="small" plain @click="previewOpen = true">预览</el-button>
        <span class="md-count">{{ innerValue.length }} 字</span>
      </div>
    </div>
    <div class="md-panes" :style="{ height: paneHeight }">
      <el-input
        v-model="innerValue"
        type="textarea"
        class="md-input"
        :placeholder="placeholder"
        @input="emitValue"
      />
      <div v-if="!isMobile" class="md-preview">
        <markdown-viewer :content="innerValue" />
      </div>
    </div>
    <el-dialog
      v-model="previewOpen"
      title="Markdown 预览"
      width="94%"
      top="6vh"
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="md-preview-body">
        <markdown-viewer :content="innerValue" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import MarkdownViewer from '@/components/MarkdownViewer/index.vue'

const props = defineProps<{
  modelValue?: string | null
  /** 编辑/预览面板高度（CSS 值），默认 320px；移动端固定 55vh */
  height?: string
  placeholder?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const innerValue = ref(props.modelValue || '')
/** 移动端单栏：只展示编辑区，预览按钮弹窗打开；桌面端保持左右分栏 */
const isMobile = useMediaQuery('(max-width: 768px)')
const previewOpen = ref(false)

const paneHeight = computed(() => (isMobile.value ? '55vh' : props.height || '320px'))

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
  /* 父级常为 flex 容器（如 el-form-item__content），需显式占满一行 */
  width: 100%;

  .md-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;

    .md-toolbar-right {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .md-hint,
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

  .md-preview-body {
    max-height: 70vh;
    overflow-y: auto;
  }
}
</style>
