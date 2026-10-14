<template>
  <div class="item-select">
    <!-- 触发框：与 el-input 同规格（32px），未选显示占位、已选显示条目名 -->
    <div
      class="select-trigger"
      :class="{ 'is-selected': modelValue }"
      role="button"
      tabindex="0"
      @click="openDialog"
      @keydown.enter="openDialog"
    >
      <template v-if="modelValue">
        <span class="trigger-icon">{{ typeIcon(selectedType) }}</span>
        <span class="trigger-text">{{ selectedTitle ? `${selectedTitle}（${typeLabel(selectedType)}）` : `已关联条目 #${modelValue}` }}</span>
        <el-icon class="trigger-clear" title="取消关联" @click.stop="clear"><Close /></el-icon>
      </template>
      <template v-else>
        <span class="trigger-placeholder">选择关联条目（可选）</span>
        <el-icon class="trigger-arrow"><ArrowDown /></el-icon>
      </template>
    </div>

    <!-- 关联条目选择弹窗 -->
    <el-dialog v-model="dialogOpen" title="选择关联条目" width="560px" append-to-body @open="resetAndLoad">
      <el-tabs v-model="activeType" @tab-change="resetAndLoad">
        <el-tab-pane v-for="t in sgj_item_type" :key="t.value" :label="t.label" :name="t.value" />
      </el-tabs>
      <el-input
        v-model="keyword"
        placeholder="搜索标题"
        clearable
        class="keyword-input"
        @keyup.enter="resetAndLoad"
        @clear="resetAndLoad"
      >
        <template #append>
          <el-button icon="Search" @click="resetAndLoad" />
        </template>
      </el-input>
      <div class="item-list" @scroll="onListScroll">
        <el-empty v-if="!loading && !items.length" description="该类型暂无条目" :image-size="60" />
        <div
          v-for="item in items"
          :key="item.itemId"
          class="item-row"
          :class="{ active: item.itemId === modelValue }"
          @click="pick(item)"
        >
          <item-cover class="row-cover" :src="item.coverUrl" :item-type="item.itemType" :width="36" :height="48" />
          <div class="row-main">
            <span class="row-title">{{ item.title }}</span>
            <span v-if="rowYear(item)" class="row-sub">{{ rowYear(item) }}</span>
          </div>
          <span class="row-status" :class="item.status === 'DONE' ? 'done' : 'want'">
            {{ item.status === 'DONE' ? '已完成' : '心愿' }}
          </span>
          <el-icon v-if="item.itemId === modelValue" class="row-check"><Check /></el-icon>
        </div>
        <div v-if="loading" class="list-tip">加载中…</div>
        <div v-else-if="items.length && !hasMore" class="list-tip">没有更多了</div>
      </div>
      <template #footer>
        <el-button @click="dialogOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="ItemSelect">
import { ArrowDown, Check, Close } from '@element-plus/icons-vue'
import ItemCover from '@/components/ItemCover/index.vue'
import { getFrontItem, listFrontItem } from '@/api/front/item'
import { useDict } from '@/utils/dict'
import type { SgjItem } from '@/types/api/business/item'

const { sgj_item_type } = useDict('sgj_item_type')

/** 当前已关联的条目ID（v-model） */
const modelValue = defineModel<number | undefined>()

/** 类型元数据 */
const TYPE_META: Record<string, { label: string; icon: string }> = {
  MOVIE: { label: '电影', icon: '🎬' },
  TV: { label: '电视剧', icon: '📺' },
  BOOK: { label: '书籍', icon: '📖' },
  PLACE: { label: '地点', icon: '📍' }
}

const dialogOpen = ref(false)
const activeType = ref<string>('MOVIE')
const keyword = ref('')
const items = ref<SgjItem[]>([])
const loading = ref(false)

/** 滚动分页：PAGE_SIZE 条一页，滚动近底部自动追加 */
const PAGE_SIZE = 20
const pageNum = ref(1)
const hasMore = ref(true)

/** 已选条目标题与类型（用于回显） */
const selectedTitle = ref('')
const selectedType = ref<string>('')

function typeLabel(type?: string): string {
  return (type && TYPE_META[type]?.label) || ''
}

function typeIcon(type?: string): string {
  return (type && TYPE_META[type]?.icon) || '📄'
}

/** 行次行年份：电影取上映年、剧取开播年、书取出版年 */
function rowYear(item: SgjItem): string {
  return String(item.releaseYear || item.startYear || (item.publishDate ? item.publishDate.slice(0, 4) : '') || '')
}

/** 根据 itemId 回显关联条目标题（编辑笔记时） */
function fetchSelectedTitle(itemId: number): void {
  getFrontItem(itemId).then(response => {
    const item = response.data
    if (item) {
      selectedTitle.value = item.title || ''
      selectedType.value = item.itemType || ''
    }
  }).catch(() => {
    // 加载失败时保留兜底展示（已关联条目 #id）
    selectedTitle.value = ''
    selectedType.value = ''
  })
}

/** 打开选择器并加载当前类型列表 */
function openDialog(): void {
  dialogOpen.value = true
}

/** 重置分页后拉取第一页 */
function resetAndLoad(): void {
  items.value = []
  pageNum.value = 1
  hasMore.value = true
  loadItems()
}

/** 按当前类型/关键字加载一页条目并追加 */
function loadItems(): void {
  if (loading.value || !hasMore.value) return
  loading.value = true
  listFrontItem({
    itemType: activeType.value,
    title: keyword.value || undefined,
    pageNum: pageNum.value,
    pageSize: PAGE_SIZE
  }).then(response => {
    const rows = response.data || []
    items.value = items.value.concat(rows)
    hasMore.value = rows.length >= PAGE_SIZE
    pageNum.value += 1
  }).finally(() => {
    loading.value = false
  })
}

/** 列表滚动近底部时加载下一页 */
function onListScroll(e: Event): void {
  const el = e.target as HTMLElement
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 40) {
    loadItems()
  }
}

/** 选中条目：回填 itemId 与标题显示 */
function pick(item: SgjItem): void {
  modelValue.value = item.itemId
  selectedTitle.value = item.title || ''
  selectedType.value = item.itemType || ''
  dialogOpen.value = false
}

/** 取消关联 */
function clear(): void {
  modelValue.value = undefined
  selectedTitle.value = ''
  selectedType.value = ''
}

// 挂载时若已有关联条目则回显标题（编辑场景）
onMounted(() => {
  if (modelValue.value) {
    fetchSelectedTitle(modelValue.value)
  }
})

// 外部变更 itemId 时同步回显（如父组件加载了编辑数据）
watch(modelValue, (val, old) => {
  if (val && val !== old) {
    fetchSelectedTitle(val)
  } else if (!val) {
    selectedTitle.value = ''
    selectedType.value = ''
  }
})
</script>

<style scoped lang="scss">
/*
 * 注意：选择弹窗为 append-to-body，内容会被传送出组件根节点，
 * 弹窗内元素样式不能嵌套在 .item-select 下（后代选择器将匹配不到），必须平铺书写。
 */

.item-select {
  width: 100%;
}

/* 触发框：对齐 el-input 默认规格（高 32px、圆角、边框、悬停变色） */
.select-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  height: 32px;
  padding: 0 11px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  cursor: pointer;
  transition: border-color 0.2s;
  outline: none;

  &:hover,
  &:focus {
    border-color: var(--el-border-color-hover);
  }

  .trigger-placeholder {
    flex: 1;
    font-size: 14px;
    color: var(--el-text-color-placeholder);
  }

  .trigger-arrow {
    color: var(--el-text-color-placeholder);
    font-size: 14px;
  }

  .trigger-icon {
    font-size: 15px;
  }

  .trigger-text {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 14px;
    color: var(--el-text-color-regular);
  }

  .trigger-clear {
    flex-shrink: 0;
    font-size: 14px;
    color: var(--el-text-color-placeholder);
    border-radius: 50%;
    transition: color 0.2s, background 0.2s;

    &:hover {
      color: var(--el-color-white);
      background: var(--el-text-color-placeholder);
    }
  }
}

.keyword-input {
  margin-bottom: 10px;
}

.item-list {
  max-height: 380px;
  overflow-y: auto;
  border: 1px solid var(--sgj-border-card);
  border-radius: 8px;
  padding: 6px;
  min-height: 120px;

  .item-row {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 10px;
    border-radius: 8px;
    cursor: pointer;
    transition: background 0.2s;

    &:hover {
      background: var(--sgj-bg);
    }

    &.active {
      background: var(--sgj-primary-soft);
    }

    .row-cover {
      flex-shrink: 0;
    }

    .row-main {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;

      .row-title {
        font-size: 14px;
        color: var(--sgj-text);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .row-sub {
        font-size: 12px;
        color: var(--sgj-text-4);
      }
    }

    .row-status {
      flex-shrink: 0;
      font-size: 12px;
      padding: 2px 8px;
      border-radius: 10px;

      &.done {
        background: #e8f5e9;
        color: #4caf50;
      }

      &.want {
        background: #fff3e0;
        color: var(--sgj-amber);
      }
    }

    .row-check {
      flex-shrink: 0;
      color: var(--sgj-primary);
    }
  }

  .list-tip {
    padding: 10px 0 6px;
    text-align: center;
    font-size: 12px;
    color: var(--sgj-text-4);
  }
}
</style>
