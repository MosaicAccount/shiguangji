<template>
  <div class="item-notes">
    <div class="notes-title">
      <span>📄 关联笔记</span>
      <span v-if="notes.length" class="notes-count">{{ notes.length }}</span>
    </div>

    <div v-if="loading" class="notes-loading">加载中...</div>
    <div v-else-if="!notes.length" class="notes-empty">暂无关联笔记</div>
    <ul v-else class="notes-list">
      <li v-for="note in notes" :key="note.noteId" class="note-row" @click="openNote(note)">
        <div class="note-row-title">{{ note.title }}</div>
        <div v-if="noteSummary(note.content)" class="note-row-summary">{{ noteSummary(note.content) }}</div>
        <div class="note-row-meta">
          <span v-if="note.tags" class="note-row-tags">🏷 {{ note.tags }}</span>
          <span>{{ formatTime(note.updateTime || note.createTime) }}</span>
        </div>
      </li>
    </ul>

    <!-- 笔记详情 -->
    <el-dialog v-model="detailOpen" :title="noteDetail?.title || '笔记详情'" width="640px" append-to-body>
      <div v-if="noteDetail" class="note-detail">
        <div v-if="noteDetail.tags" class="note-detail-tags">🏷 {{ noteDetail.tags }}</div>
        <div class="note-detail-body">
          <markdown-viewer :content="noteDetail.content" />
        </div>
        <div class="note-detail-time">{{ formatTime(noteDetail.updateTime || noteDetail.createTime) }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="ItemNotes">
import MarkdownViewer from '@/components/MarkdownViewer/index.vue'
import { listFrontNote, getFrontNote } from '@/api/front/note'
import type { SgjNote } from '@/types/api/business/note'

const props = defineProps<{
  itemId?: number
}>()

const notes = ref<SgjNote[]>([])
const loading = ref(false)
const detailOpen = ref(false)
const noteDetail = ref<SgjNote | null>(null)

function loadNotes(): void {
  if (!props.itemId) {
    notes.value = []
    return
  }
  loading.value = true
  listFrontNote({ itemId: props.itemId, pageNum: 1, pageSize: 50 }).then(response => {
    notes.value = response.data || []
  }).catch(() => {
    notes.value = []
  }).finally(() => {
    loading.value = false
  })
}

function openNote(note: SgjNote): void {
  if (!note.noteId) return
  getFrontNote(note.noteId).then(response => {
    noteDetail.value = response.data || note
    detailOpen.value = true
  }).catch(() => {})
}

function formatTime(time?: string): string {
  if (!time) return ''
  return time.replace('T', ' ').slice(0, 16)
}

/** Markdown 转纯文本摘要（B-04 多笔记列表预览） */
function noteSummary(content?: string): string {
  if (!content) return ''
  return content
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[#>*`~\-_|]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

watch(
  () => props.itemId,
  () => {
    detailOpen.value = false
    loadNotes()
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
.item-notes {
  margin-top: 20px;
  text-align: left;

  .notes-title {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--sgj-text-2);
    font-weight: 600;
    margin-bottom: 8px;

    .notes-count {
      background: #f5e6d3;
      color: var(--sgj-primary);
      border-radius: 10px;
      padding: 0 8px;
      font-size: 12px;
      line-height: 20px;
    }
  }

  .notes-loading,
  .notes-empty {
    color: var(--sgj-text-4);
    font-size: 13px;
    padding: 8px 0;
  }

  .notes-list {
    list-style: none;
    margin: 0;
    padding: 0;

    .note-row {
      background: var(--sgj-bg);
      border-radius: 10px;
      padding: 10px 14px;
      margin-bottom: 8px;
      cursor: pointer;
      border: 1px solid #f0e4d6;
      transition: all 0.2s;

      &:hover {
        border-color: #d8a884;
        background: #fffdf9;
      }

      .note-row-title {
        font-size: 14px;
        font-weight: 600;
        color: var(--sgj-text);
      }

      .note-row-summary {
        margin-top: 4px;
        font-size: 12px;
        color: var(--sgj-text-3);
        line-height: 1.5;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }

      .note-row-meta {
        margin-top: 4px;
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
        font-size: 12px;
        color: var(--sgj-text-4);

        .note-row-tags {
          color: var(--sgj-amber);
        }
      }
    }
  }
}

.note-detail {
  .note-detail-tags {
    font-size: 13px;
    color: var(--sgj-amber);
    margin-bottom: 8px;
  }

  .note-detail-body {
    max-height: 480px;
    overflow-y: auto;
    background: var(--sgj-bg);
    border-radius: 12px;
    padding: 14px 16px;
  }

  .note-detail-time {
    margin-top: 12px;
    font-size: 12px;
    color: var(--sgj-text-4);
    text-align: right;
  }
}
</style>
