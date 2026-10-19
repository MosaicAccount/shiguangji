<template>
  <!-- 0.0.5 FR-005-7：插入笔记链接选择器。按标题搜索（复用 /app/note/list 的 title 过滤），
       同名合并为一条（互链按标题解析，本就区分不了）；标题含 [ ] 的置灰不可选 -->
  <el-dialog
    :model-value="modelValue"
    title="插入笔记链接"
    width="520px"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
    @open="onOpen"
    @closed="reset"
  >
    <div class="picker-body">
      <el-input
        v-model="query"
        placeholder="按标题搜索笔记"
        clearable
        :prefix-icon="Search"
        @input="debouncedReload"
        @clear="reload"
      />
      <p class="picker-hint">插入到光标处 · 保存后在详情页生效；同名笔记合并为一条，标题含 [ ] 的暂不可选。</p>
      <ul v-loading="loading" class="picker-list">
        <li v-for="row in rows" :key="row.title">
          <button
            type="button"
            class="pick-row"
            :disabled="!row.linkable"
            :title="row.linkable ? `插入 ${row.token}` : row.reason"
            @click="choose(row)"
          >
            <span class="pick-main">
              <span class="pick-title">{{ row.title }}</span>
              <span class="pick-meta">{{ row.meta }}</span>
            </span>
            <span v-if="row.merged > 1" class="pick-badge">同名 {{ row.merged }} 篇 · 已合并</span>
            <span v-if="!row.linkable" class="pick-badge is-warn">不可用</span>
            <svg-icon v-else icon-class="enter" class="pick-arrow" />
          </button>
        </li>
        <li v-if="!loading && !rows.length" class="pick-empty">
          {{ query ? '没有匹配的笔记，换个标题试试' : '还没有其他笔记' }}
        </li>
      </ul>
      <div v-if="hasMore" class="picker-more">
        <el-button size="small" text :loading="loadingMore" @click="loadMore">加载更多</el-button>
      </div>
    </div>
    <template #footer>
      <el-button @click="close">取 消</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="NoteLinkPicker">
import { Search } from '@element-plus/icons-vue'
import { listFrontNote } from '@/api/front/note'
import type { SgjNote } from '@/types/api/business/note'
import { isLinkableTitle } from '@/utils/noteLinks'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'select', title: string): void
}>()

interface PickRow {
  title: string
  token: string
  meta: string
  linkable: boolean
  reason?: string
  merged: number
}

const PAGE_SIZE = 20

const query = ref('')
const rows = ref<PickRow[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const pageNum = ref(1)
const total = ref(0)
/** 标题 → 已合并条数（同名合并：互链按标题解析，区分不了是哪篇） */
const mergedTitles = ref(new Map<string, number>())

const hasMore = computed(() => rows.value.length < total.value)

let searchTimer: number | undefined

function onOpen(): void {
  reload()
}

function reset(): void {
  query.value = ''
  rows.value = []
  pageNum.value = 1
  total.value = 0
  mergedTitles.value = new Map()
}

/** 输入防抖 300ms：每次敲键都打接口会刷屏，也容易撞上分页竞态 */
function debouncedReload(): void {
  clearTimeout(searchTimer)
  searchTimer = window.setTimeout(reload, 300)
}

function reload(): void {
  clearTimeout(searchTimer)
  pageNum.value = 1
  rows.value = []
  mergedTitles.value = new Map()
  void loadPage(false)
}

function loadMore(): void {
  void loadPage(true)
}

function loadPage(append: boolean): void {
  loading.value = !append
  loadingMore.value = append
  listFrontNote({
    title: query.value.trim() || undefined,
    pageNum: pageNum.value,
    pageSize: PAGE_SIZE
  }).then(response => {
    const list = response.data || []
    total.value = (response as any).total || 0
    const seen = append ? mergedTitles.value : new Map<string, number>()
    for (const note of list) {
      seen.set(note.title || '', (seen.get(note.title || '') ?? 0) + 1)
    }
    mergedTitles.value = seen
    const merged = new Map<string, PickRow>()
    for (const note of list) {
      const title = note.title || ''
      if (merged.has(title)) continue
      merged.set(title, buildRow(note, title, seen.get(title) ?? 1))
    }
    rows.value = append ? [...rows.value, ...merged.values()] : [...merged.values()]
    pageNum.value += 1
  }).finally(() => {
    loading.value = false
    loadingMore.value = false
  })
}

function buildRow(note: SgjNote, title: string, mergedCount: number): PickRow {
  const linkable = isLinkableTitle(title)
  const updated = String(note.updateTime || note.createTime || '').slice(0, 10)
  return {
    title,
    token: `[[${title}]]`,
    meta: linkable ? `更新于 ${updated}` : '标题含 [ ]，无法用 [[ ]] 引用',
    linkable,
    reason: linkable ? undefined : '标题含 [ ]，无法用 [[ ]] 引用',
    merged: mergedCount
  }
}

function choose(row: PickRow): void {
  if (!row.linkable) return
  emit('select', row.title)
  close()
}

function close(): void {
  emit('update:modelValue', false)
}
</script>

<style scoped lang="scss">
.picker-body {
  display: grid;
  gap: 10px;
}

.picker-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--sgj-text-4);
}

.picker-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 320px;
  overflow: auto;
}

.pick-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 10px;
  cursor: pointer;

  &:hover:not(:disabled),
  &:focus-visible:not(:disabled) {
    background: var(--sgj-bg);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }

  .pick-main {
    flex: 1;
    min-width: 0;
    display: grid;
    gap: 2px;
  }

  .pick-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--sgj-text);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .pick-meta {
    font-size: 11.5px;
    color: var(--sgj-text-4);
  }

  .pick-badge {
    flex-shrink: 0;
    font-size: 10px;
    padding: 2px 7px;
    border-radius: var(--sgj-radius-pill);
    border: 1px solid var(--sgj-border);
    color: var(--sgj-text-3);

    &.is-warn {
      border-color: rgba(177, 132, 88, 0.45);
      background: var(--sgj-amber-soft);
      color: var(--sgj-amber);
    }
  }

  .pick-arrow {
    flex-shrink: 0;
    color: var(--sgj-text-4);
  }
}

.pick-empty {
  padding: 26px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sgj-text-4);
}

.picker-more {
  text-align: center;
}
</style>
