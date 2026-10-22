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
/* 0.0.6 反向链接：设计图口径——顶部一条虚线，下面是引用清单，不再套一张冷雾卡片 */
.backlinks {
  margin-top: 34px;
  padding-top: 20px;
  border-top: 2px dashed var(--hd-line-soft);
}

.bl-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
  padding-bottom: 6px;

  .bl-kicker {
    font-size: 12.5px;
    letter-spacing: 2px;
    color: var(--hd-ink-faint);
  }

  .bl-title {
    font-size: 17px;
    letter-spacing: 1px;
    color: var(--hd-ink);
  }

  .bl-sub {
    margin-left: auto;
    font-size: 13px;
    color: var(--hd-ink-soft);
  }
}

.bl-list {
  list-style: none;
  margin: 4px 0 0;
  padding: 0;
}

.bl-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 2px 12px;
  width: 100%;
  padding: 9px 2px;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1.5px dashed var(--hd-line-soft);
  cursor: pointer;
  font-family: var(--hd-hand);

  &:last-child { border-bottom: 0 }
  &:hover .bl-item-title,
  &:focus-visible .bl-item-title { color: var(--hd-red) }
}

.bl-item-title {
  font-size: 15px;
  color: var(--hd-ink);
}

.bl-item-time {
  font-size: 13px;
  color: var(--hd-ink-faint);
  font-variant-numeric: tabular-nums;
}

.bl-item-snippet {
  grid-column: 1 / -1;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--hd-ink-soft);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.bl-empty {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.9;
  color: var(--hd-ink-soft);
}
</style>
