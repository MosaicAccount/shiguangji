<template>
  <el-dialog v-model="dialogVisible" title="自动匹配封面" width="620px" append-to-body>
    <div class="match-toolbar">
      <el-input
        v-model="keyword"
        placeholder="按标题搜索豆瓣封面"
        clearable
        @keyup.enter="doSearch"
        @clear="doSearch"
      />
      <el-button type="primary" :loading="searching" @click="doSearch">搜 索</el-button>
    </div>
    <div v-loading="searching" class="candidate-grid">
      <template v-if="candidates.length">
        <div
          v-for="candidate in candidates"
          :key="candidate.sourceId"
          class="candidate-item"
          :class="{ active: selected && selected.sourceId === candidate.sourceId }"
          @click="selected = candidate"
        >
          <el-image :src="photoUrl(candidate.imageUrl)" fit="cover" class="candidate-cover">
            <template #error>
              <span class="candidate-fallback">无图</span>
            </template>
          </el-image>
          <span class="candidate-title" :title="candidate.title">{{ candidate.title }}</span>
          <span v-if="candidate.year" class="candidate-year">{{ candidate.year }}</span>
        </div>
      </template>
      <el-empty v-else-if="!searching" description="未找到候选，可修改关键词后重搜" :image-size="72" />
    </div>
    <template #footer>
      <el-button type="primary" :disabled="!selected" :loading="importing" @click="confirmSelect">使用选中封面</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="CoverMatchDialog">
import { importItemCover, searchItemCovers } from '@/api/business/cover'
import { photoUrl } from '@/utils/sgj'
import type { CoverCandidate } from '@/types/api/business/item'

const props = defineProps<{
  modelValue: boolean
  /** 条目类型（MOVIE/TV/BOOK） */
  itemType: string
  /** 初始搜索关键词（通常传条目标题） */
  title?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  /** 选中并转存成功：url 为自有存储路径，sourceId 为豆瓣编号（影视落 doubanId 用） */
  (e: 'confirmed', payload: { url: string; sourceId?: string }): void
}>()

const { proxy } = getCurrentInstance() as { proxy: any }

const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const keyword = ref('')
const searching = ref(false)
const importing = ref(false)
const candidates = ref<CoverCandidate[]>([])
const selected = ref<CoverCandidate | null>(null)

watch(
  () => props.modelValue,
  value => {
    if (value) {
      keyword.value = props.title || ''
      selected.value = null
      candidates.value = []
      if (keyword.value) {
        doSearch()
      }
    }
  }
)

function doSearch(): void {
  if (!keyword.value.trim()) {
    proxy.$modal.msgWarning('请输入搜索关键词')
    return
  }
  searching.value = true
  selected.value = null
  searchItemCovers({ itemType: props.itemType, title: keyword.value.trim() }).then(response => {
    candidates.value = response.data || []
  }).catch(() => {
    candidates.value = []
  }).finally(() => {
    searching.value = false
  })
}

/** 点选候选：调转存接口把豆瓣图落到自有存储，成功后回填表单 */
function confirmSelect(): void {
  if (!selected.value) return
  importing.value = true
  importItemCover({
    itemType: props.itemType,
    sourceUrl: selected.value.sourceUrl,
    sourceId: selected.value.sourceId
  }).then(response => {
    emit('confirmed', { url: response.data!.url, sourceId: selected.value!.sourceId })
    proxy.$modal.msgSuccess('封面已匹配')
    dialogVisible.value = false
  }).catch(() => {}).finally(() => {
    importing.value = false
  })
}
</script>

<style scoped lang="scss">
.match-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.candidate-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  min-height: 160px;
  max-height: 380px;
  overflow-y: auto;
  align-content: flex-start;
}
.candidate-item {
  width: 92px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 6px;
  border: 2px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  box-sizing: content-box;

  &.active {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }
}
.candidate-cover {
  width: 80px;
  height: 120px;
  border-radius: 4px;
  background: var(--sgj-primary-soft, #f3f4f6);
}
.candidate-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.candidate-title {
  width: 92px;
  font-size: 12px;
  line-height: 1.3;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.candidate-year {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
</style>
