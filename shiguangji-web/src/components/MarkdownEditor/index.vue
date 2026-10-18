<template>
  <div class="markdown-editor" @paste="onPaste">
    <div class="md-toolbar">
      <div class="md-toolbar-left">
        <!-- 插图（#56）：上传到 /profile 后在光标处插入；粘贴截图走同一条链路 -->
        <el-button size="small" plain :loading="uploadingCount > 0" @click="imageInput?.click()">插图</el-button>
        <!-- 插入笔记链接（0.0.5 FR-005-7）：从已有笔记里选一条，光标处插入 [[标题]]；
             前后台编辑共用本组件，两处同时生效。强调样式按原型（accent 底 + 链接图标） -->
        <el-button size="small" class="md-note-link-btn" @click="notePickerOpen = true">
          <svg-icon icon-class="link" class="md-link-icon" />插入笔记链接
        </el-button>
        <span class="md-tip">上传后插入到光标处；也可以直接粘贴截图</span>
      </div>
      <span class="md-hint">{{ isMobile ? '支持 Markdown 语法' : '支持 Markdown 语法，右侧实时预览' }}</span>
      <div class="md-toolbar-right">
        <!-- 移动端无侧栏预览，收进弹窗 -->
        <el-button v-if="isMobile" size="small" plain @click="previewOpen = true">预览</el-button>
        <span class="md-count">{{ innerValue.length }} 字</span>
      </div>
    </div>
    <div class="md-panes" :style="{ height: paneHeight }">
      <el-input
        ref="inputRef"
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
    <!-- 插图的真实选择器（可多选），按钮只是它的代理 -->
    <input
      ref="imageInput"
      type="file"
      hidden
      multiple
      :accept="acceptTypes"
      @change="onImageInputChange"
    />
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
    <!-- 插入笔记链接（0.0.5）：预览不解析互链（保存后的详情页才成链），插入后给一句提示 -->
    <note-link-picker v-model="notePickerOpen" @select="insertNoteToken" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import MarkdownViewer from '@/components/MarkdownViewer/index.vue'
import NoteLinkPicker from '@/components/NoteLinkPicker/index.vue'
import { uploadNoteImage } from '@/api/common/upload'
import {
  NOTE_IMAGE_EXTENSIONS,
  NOTE_IMAGE_MAX_SIZE,
  imageMarkdown,
  insertAtCursorPosition
} from '@/utils/noteImages'

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
/** 在飞的插入任务数（>0 时按钮 loading，防重复点出一串重复上传） */
const uploadingCount = ref(0)

const imageInput = ref<HTMLInputElement>()
const inputRef = ref<{ textarea?: HTMLTextAreaElement }>()
/** 插入笔记链接选择器（0.0.5 FR-005-7） */
const notePickerOpen = ref(false)

const paneHeight = computed(() => (isMobile.value ? '55vh' : props.height || '320px'))
const acceptTypes = computed(() => NOTE_IMAGE_EXTENSIONS.map(ext => `.${ext}`).join(','))

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

// ------------------------------------------------------------------
// 插图（0.0.2 FR-002-9）：按钮选择与粘贴截图共用同一条「上传 → 光标处插入」链路
// ------------------------------------------------------------------

function onImageInputChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  // 先清空：连着选同一个文件不会再触发 change
  input.value = ''
  void uploadAndInsert(files)
}

/** 只在剪贴板里有图片时拦截；纯文本粘贴不受影响 */
function onPaste(event: ClipboardEvent): void {
  const images = Array.from(event.clipboardData?.items ?? [])
    .filter(item => item.kind === 'file' && item.type.startsWith('image/'))
    .map(item => item.getAsFile())
    .filter((file): file is File => file !== null)
  if (images.length === 0) return
  event.preventDefault()
  void uploadAndInsert(images)
}

async function uploadAndInsert(files: File[]): Promise<void> {
  const acceptable = files.filter(file => NOTE_IMAGE_EXTENSIONS.includes(file.name.split('.').pop()?.toLowerCase() ?? ''))
  if (acceptable.length === 0) {
    ElMessage.error('只支持 png / jpg / jpeg / gif / webp / bmp 图片')
    return
  }
  const oversize = acceptable.filter(file => file.size > NOTE_IMAGE_MAX_SIZE)
  const list = acceptable.filter(file => file.size <= NOTE_IMAGE_MAX_SIZE)
  if (oversize.length > 0) ElMessage.warning(`已忽略 ${oversize.length} 个超过 10MB 的文件`)
  if (list.length === 0) return

  uploadingCount.value++
  try {
    const paths: string[] = []
    for (const file of list) {
      const res = await uploadNoteImage(file)
      if (res.code === 200 && res.path) {
        paths.push(res.path)
      } else {
        // 失败的只提示，不插入任何内容，正文保持原样
        ElMessage.error(`图片上传失败：${res.msg || file.name}`)
      }
    }
    if (paths.length > 0) insertAtCursor(paths.map(path => imageMarkdown(path)))
  } finally {
    uploadingCount.value--
  }
}

/** 光标处插入：图片语法独占一行；v-model 回流会重置选区，nextTick 后恢复光标 */
function insertAtCursor(markdowns: string[]): void {
  const textarea = inputRef.value?.textarea
  const insert = markdowns.join('\n\n')
  const position = textarea?.selectionStart ?? innerValue.value.length
  const { text, cursor } = insertAtCursorPosition(innerValue.value, position, insert)
  innerValue.value = text
  emitValue()
  nextTick(() => {
    textarea?.focus()
    textarea?.setSelectionRange(cursor, cursor)
  })
}

/**
 * 插入笔记链接（0.0.5 FR-005-7）。互链是行内语法，不能复用插图的独占一行插入
 * （那个会强加前后空行），这里原样落在光标处并覆盖选中区；预览不解析 token，保存后才成链。
 */
function insertNoteToken(title: string): void {
  const textarea = inputRef.value?.textarea
  const token = `[[${title}]]`
  const position = textarea?.selectionStart ?? innerValue.value.length
  const end = textarea?.selectionEnd ?? position
  innerValue.value = innerValue.value.slice(0, position) + token + innerValue.value.slice(end)
  emitValue()
  nextTick(() => {
    textarea?.focus()
    textarea?.setSelectionRange(position + token.length, position + token.length)
  })
  ElMessage.success(`已插入 ${token}，保存后在详情页生效`)
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
    gap: 10px;
    margin-bottom: 8px;

    .md-toolbar-left {
      display: flex;
      align-items: center;
      gap: 8px;

      .md-tip {
        font-size: 12px;
        color: var(--sgj-text-4);
      }

      .md-link-icon {
        margin-right: 4px;
        font-size: 13px;
      }

      /* 互链入口用强调色（原型 .btn.accent）：浅陶红底 + 深陶红字，与「插图」的中性 plain 拉开层次 */
      .md-note-link-btn.el-button {
        color: var(--sgj-primary-dark);
        background: var(--sgj-primary-soft);
        border-color: rgba(168, 95, 82, 0.36);

        &:hover,
        &:focus-visible {
          color: var(--sgj-primary-dark);
          background: var(--sgj-primary-soft);
          border-color: var(--sgj-primary);
        }
      }

      html.dark .md-note-link-btn.el-button {
        color: var(--sgj-primary-light);
        border-color: rgba(194, 122, 109, 0.4);
      }
    }

    .md-toolbar-right {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .md-hint,
    .md-count {
      font-size: 12px;
      color: var(--sgj-text-4);
    }

    /* 窄屏让位：插图按钮留下，说明文字与语法提示收起来（移动端预览按钮仍有位置） */
    @media (max-width: 900px) {
      .md-tip,
      .md-hint {
        display: none;
      }
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
      border: 1px solid var(--sgj-border-card);
      border-radius: 4px;
      padding: 5px 14px;
      overflow-y: auto;
      background: var(--sgj-bg-card);
      box-sizing: border-box;
    }
  }

  .md-preview-body {
    max-height: 70vh;
    overflow-y: auto;
  }
}
</style>
