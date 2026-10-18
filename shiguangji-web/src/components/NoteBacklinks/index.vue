<template>
  <!-- 0.0.5 FR-005-2：正文末尾的反向链接区块。重名时改显说明（D-14），无来源时整块不渲染 -->
  <section v-if="showPanel" class="backlinks" aria-label="反向链接">
    <div class="bl-head">
      <span class="bl-kicker">BACKLINKS</span>
      <span class="bl-title">被引用 {{ backlinks!.length }} 篇</span>
      <span class="bl-sub">谁在正文里链接到这篇</span>
    </div>
    <ol class="bl-list">
      <li v-for="item in backlinks" :key="item.noteId">
        <button type="button" class="bl-item" :title="`打开笔记：${item.title}`" @click="openNote(item.noteId)">
          <span class="bl-item-title">{{ item.title }}</span>
          <span class="bl-item-time">{{ formatTime(item.updateTime) }}</span>
          <span v-if="item.snippet" class="bl-item-snippet">{{ item.snippet }}</span>
        </button>
      </li>
    </ol>
  </section>
  <section v-else-if="duplicated" class="backlinks" aria-label="反向链接">
    <div class="bl-head">
      <span class="bl-kicker">BACKLINKS</span>
      <span class="bl-title">标题重名，引用无法定位</span>
    </div>
    <p class="bl-empty">
      本篇标题与另外 {{ duplicatedOthers }} 篇笔记重复。指向它的链接会降级，因此这里不列出引用来源；
      改一个不重名的标题即可恢复。
    </p>
  </section>
</template>

<script setup lang="ts" name="NoteBacklinks">
import type { NoteBacklink } from '@/types/api/business/note'

const props = defineProps<{
  backlinks?: NoteBacklink[] | null
  /** 可见范围内同名笔记数（含本篇）；>1 时按重名处理 */
  sameTitleCount?: number | null
}>()

const router = useRouter()

const duplicated = computed(() => (props.sameTitleCount ?? 0) > 1)
const duplicatedOthers = computed(() => Math.max((props.sameTitleCount ?? 1) - 1, 1))
const showPanel = computed(() => !duplicated.value && !!props.backlinks?.length)

function formatTime(time?: string): string {
  return (time || '').slice(0, 10)
}

function openNote(noteId: number): void {
  router.push({ path: '/note/detail', query: { noteId: String(noteId) } })
}
</script>

<style scoped lang="scss">
/* 与原型（mockups/link-0.0.5）同语言：鼠尾草底索引卡，桌面/移动同一位置 */
.backlinks {
  margin-top: 28px;
  padding: 18px 20px 6px;
  background: var(--sgj-moss-soft);
  border: 1px solid var(--sgj-border-card);
  border-radius: var(--sgj-radius-lg);
}

.bl-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding-bottom: 8px;

  .bl-kicker {
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.16em;
    color: var(--sgj-moss);
  }

  .bl-title {
    font-family: var(--sgj-font-serif);
    font-size: 16px;
    font-weight: 700;
    color: var(--sgj-text);
  }

  .bl-sub {
    margin-left: auto;
    font-size: 12px;
    color: var(--sgj-text-3);
  }
}

.bl-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.bl-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 3px 12px;
  width: 100%;
  padding: 11px 2px;
  text-align: left;
  background: transparent;
  border: 0;
  border-top: 1px solid rgba(93, 111, 102, 0.22);
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover,
  &:focus-visible {
    background: rgba(251, 252, 251, 0.6);
  }

  .bl-item-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--sgj-text);
  }

  .bl-item-time {
    font-size: 11.5px;
    color: var(--sgj-text-4);
    white-space: nowrap;
  }

  .bl-item-snippet {
    grid-column: 1 / -1;
    font-size: 13px;
    line-height: 1.65;
    color: var(--sgj-text-2);
    overflow: hidden;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
}

html.dark .bl-item:hover,
html.dark .bl-item:focus-visible {
  background: rgba(34, 40, 38, 0.55);
}

.bl-empty {
  margin: 0;
  padding: 4px 2px 14px;
  border-top: 1px solid rgba(93, 111, 102, 0.22);
  font-size: 13px;
  line-height: 1.7;
  color: var(--sgj-text-2);
}

@media (max-width: 768px) {
  .backlinks {
    margin-top: 20px;
    padding: 14px 14px 2px;
    border-radius: 12px;
  }

  .bl-head {
    flex-wrap: wrap;

    .bl-sub {
      width: 100%;
      margin-left: 0;
    }
  }
}
</style>
