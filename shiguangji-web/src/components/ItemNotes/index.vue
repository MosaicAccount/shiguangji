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
        <div v-if="note.excerpt" class="note-row-summary">{{ note.excerpt }}</div>
        <div class="note-row-meta">
          <span v-if="note.tags" class="note-row-tags">🏷 {{ note.tags }}</span>
          <span>{{ formatTime(note.updateTime || note.createTime) }}</span>
        </div>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts" name="ItemNotes">
import { listFrontNote } from '@/api/front/note'
import type { SgjNote } from '@/types/api/business/note'

const props = defineProps<{
  itemId?: number
}>()

const router = useRouter()
const notes = ref<SgjNote[]>([])
const loading = ref(false)

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

/** 跳转独立笔记详情页（与笔记列表一致，不再弹窗展示） */
function openNote(note: SgjNote): void {
  if (!note.noteId) return
  router.push({ path: '/note/detail', query: { noteId: String(note.noteId) } })
}

function formatTime(time?: string): string {
  if (!time) return ''
  return time.replace('T', ' ').slice(0, 16)
}

watch(
  () => props.itemId,
  () => {
    loadNotes()
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
/* 0.0.6 关联笔记：手绘纸片行（放在详情抽屉的纸卡里也不违和） */
.item-notes { text-align: left }

.notes-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 17px;
  color: var(--hd-ink-soft);

  .notes-count {
    font-size: 13px;
    color: var(--hd-paper);
    background: var(--hd-amber-ink);
    border-radius: 9px 3px 8px 3px / 3px 8px 3px 9px;
    padding: 0 7px;
    line-height: 1.6;
  }
}

.notes-loading,
.notes-empty {
  margin-top: 10px;
  padding: 14px 12px;
  font-size: 13.5px;
  color: var(--hd-ink-faint);
  border: 1.5px dashed var(--hd-line-soft);
  border-radius: 4px 2px 5px 3px / 3px 5px 2px 4px;
  text-align: center;
}

.notes-list {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
}

.note-row {
  padding: 10px 2px;
  border-bottom: 1.5px dashed var(--hd-line-soft);
  cursor: pointer;

  &:last-child { border-bottom: 0 }
  &:hover .note-row-title { color: var(--hd-red) }
}

.note-row-title { font-size: 15.5px; color: var(--hd-ink) }

.note-row-summary {
  margin-top: 3px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--hd-ink-soft);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.note-row-meta {
  margin-top: 4px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--hd-ink-faint);
}

.note-row-tags { color: var(--hd-amber-ink) }
</style>
