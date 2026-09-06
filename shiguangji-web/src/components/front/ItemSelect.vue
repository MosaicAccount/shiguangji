<template>
  <div class="item-select">
    <!-- 已选状态展示（含取消关联） -->
    <div v-if="modelValue && selectedTitle" class="selected">
      <span class="selected-icon">{{ typeIcon(selectedType) }}</span>
      <span class="selected-text">已关联：{{ selectedTitle }}（{{ typeLabel(selectedType) }}）</span>
      <el-button link type="danger" size="small" @click.stop="clear">取消关联</el-button>
    </div>
    <!-- 已选但标题未加载出来时的兜底展示 -->
    <div v-else-if="modelValue" class="selected">
      <span class="selected-text">已关联条目 #{{ modelValue }}</span>
      <el-button link type="danger" size="small" @click.stop="clear">取消关联</el-button>
    </div>
    <!-- 未选：打开选择器 -->
    <el-button v-else class="pick-btn" @click="openDialog">选择关联条目（可选）</el-button>

    <!-- 关联条目选择弹窗 -->
    <el-dialog v-model="dialogOpen" title="选择关联条目" width="560px" append-to-body>
      <el-tabs v-model="activeType" @tab-change="loadItems">
        <el-tab-pane v-for="t in sgj_item_type" :key="t.value" :label="t.label" :name="t.value" />
      </el-tabs>
      <el-input
        v-model="keyword"
        placeholder="搜索标题"
        clearable
        class="keyword-input"
        @keyup.enter="loadItems"
        @clear="loadItems"
      >
        <template #append>
          <el-button icon="Search" @click="loadItems" />
        </template>
      </el-input>
      <div v-loading="loading" class="item-list">
        <el-empty v-if="!loading && !items.length" description="该类型暂无条目" :image-size="60" />
        <div
          v-for="item in items"
          :key="item.itemId"
          class="item-row"
          :class="{ active: item.itemId === modelValue }"
          @click="pick(item)"
        >
          <span class="row-icon">{{ typeIcon(activeType) }}</span>
          <span class="row-title">{{ item.title }}</span>
          <span class="row-status" :class="item.status === 'DONE' ? 'done' : 'want'">
            {{ item.status === 'DONE' ? '已完成' : '心愿' }}
          </span>
          <el-icon v-if="item.itemId === modelValue" class="row-check"><Check /></el-icon>
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="ItemSelect">
import { Check } from '@element-plus/icons-vue'
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

/** 已选条目标题与类型（用于回显） */
const selectedTitle = ref('')
const selectedType = ref<string>('')

function typeLabel(type?: string): string {
  return (type && TYPE_META[type]?.label) || ''
}

function typeIcon(type?: string): string {
  return (type && TYPE_META[type]?.icon) || '📄'
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
  loadItems()
}

/** 按当前类型/关键字加载条目列表 */
function loadItems(): void {
  loading.value = true
  listFrontItem({
    itemType: activeType.value,
    title: keyword.value || undefined,
    pageNum: 1,
    pageSize: 50
  }).then(response => {
    items.value = response.data || []
  }).finally(() => {
    loading.value = false
  })
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
.item-select {
  width: 100%;

  .selected {
    display: flex;
    align-items: center;
    gap: 8px;
    background: var(--sgj-bg);
    border: 1px solid var(--sgj-border);
    border-radius: 8px;
    padding: 8px 12px;

    .selected-icon {
      font-size: 16px;
    }

    .selected-text {
      flex: 1;
      font-size: 14px;
      color: var(--sgj-text);
    }
  }

  .pick-btn {
    width: 100%;
  }

  .keyword-input {
    margin-bottom: 10px;
  }

  .item-list {
    max-height: 320px;
    overflow-y: auto;
    border: 1px solid var(--sgj-border-card);
    border-radius: 8px;
    padding: 4px;
    min-height: 80px;

    .item-row {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 12px;
      border-radius: 6px;
      cursor: pointer;
      transition: background 0.2s;

      &:hover {
        background: var(--sgj-bg);
      }

      &.active {
        background: var(--sgj-primary-soft);
      }

      .row-icon {
        font-size: 15px;
      }

      .row-title {
        flex: 1;
        font-size: 14px;
        color: var(--sgj-text);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .row-status {
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
        color: var(--sgj-primary);
      }
    }
  }
}
</style>
