<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" class="mb8" show-icon>
      回收站保留已删除的条目与笔记：可恢复回原列表对应状态，或彻底删除（不可恢复）。删除时间即数据进入回收站的时间。
    </el-alert>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="条目回收站" name="item" />
      <el-tab-pane label="笔记回收站" name="note" />
    </el-tabs>

    <el-form :inline="true" label-width="68px">
      <el-form-item label="标题">
        <el-input
          v-model="keyword"
          placeholder="请输入标题关键字"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 条目回收站 -->
    <el-table v-show="activeTab === 'item'" v-loading="itemLoading" :data="pagedItems">
      <!-- 6.2：空态 el-empty -->
      <template #empty>
        <el-empty description="回收站暂无条目" />
      </template>
      <el-table-column label="封面" align="center" width="80">
        <template #default="scope">
          <!-- 无图或加载失败回退类型衬线字，避免「加载失败」/占位横杠 -->
          <item-cover :src="scope.row.coverUrl" :item-type="scope.row.itemType" :width="48" :height="64" />
        </template>
      </el-table-column>
      <el-table-column label="标题" align="center" prop="title" :show-overflow-tooltip="true" min-width="160" />
      <el-table-column label="类型" align="center" prop="itemType" width="90">
        <template #default="scope">
          <el-tag>{{ typeLabel(scope.row.itemType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'DONE' ? 'success' : 'warning'">
            {{ statusLabel(scope.row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="评分" align="center" prop="rating" width="70" />
      <el-table-column label="标签" align="center" prop="tags" :show-overflow-tooltip="true" min-width="120" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="删除时间" align="center" prop="updateTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.updateTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="180" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="RefreshLeft" @click="handleRestoreItem(scope.row)" v-hasPermi="['sgj:recycle:list']">恢复</el-button>
          <el-button link type="danger" icon="Delete" @click="handlePurgeItem(scope.row)" v-hasPermi="['sgj:recycle:list']">彻底删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination
      v-show="activeTab === 'item' && itemTotal > 0"
      :total="itemTotal"
      v-model:page="itemPage.pageNum"
      v-model:limit="itemPage.pageSize"
    />

    <!-- 笔记回收站 -->
    <el-table v-show="activeTab === 'note'" v-loading="noteLoading" :data="pagedNotes">
      <!-- 6.2：空态 el-empty -->
      <template #empty>
        <el-empty description="回收站暂无笔记" />
      </template>
      <el-table-column label="笔记ID" align="center" prop="noteId" width="80" />
      <el-table-column label="标题" align="center" prop="title" :show-overflow-tooltip="true" min-width="180" />
      <el-table-column label="关联条目ID" align="center" prop="itemId" width="110">
        <template #default="scope">
          <span>{{ scope.row.itemId ?? '独立笔记' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="标签" align="center" prop="tags" :show-overflow-tooltip="true" min-width="120" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="删除时间" align="center" prop="updateTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.updateTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="180" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="RefreshLeft" @click="handleRestoreNote(scope.row)" v-hasPermi="['sgj:recycle:list']">恢复</el-button>
          <el-button link type="danger" icon="Delete" @click="handlePurgeNote(scope.row)" v-hasPermi="['sgj:recycle:list']">彻底删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination
      v-show="activeTab === 'note' && noteTotal > 0"
      :total="noteTotal"
      v-model:page="notePage.pageNum"
      v-model:limit="notePage.pageSize"
    />
  </div>
</template>

<script setup lang="ts" name="Recycle">
import { listRecycleItem, restoreRecycleItem, purgeRecycleItem, listRecycleNote, restoreRecycleNote, purgeRecycleNote } from '@/api/business/recycle'
import { useDict } from '@/utils/dict'
import ItemCover from '@/components/ItemCover/index.vue'
import type { SgjItem } from '@/types/api/business/item'
import type { SgjNote } from '@/types/api/business/note'
import { parseTime } from '@/utils/sgj'

// 业务数据字典（条目类型展示用）
const { sgj_item_type } = useDict('sgj_item_type')

const { proxy } = getCurrentInstance() as { proxy: any }

const activeTab = ref<'item' | 'note'>('item')
const keyword = ref<string>('')

const itemList = ref<SgjItem[]>([])
const noteList = ref<SgjNote[]>([])
const itemLoading = ref<boolean>(false)
const noteLoading = ref<boolean>(false)

// 后端回收站接口不分页（返回当前可见范围全量），前端按关键字过滤后分页展示
const itemPage = reactive({ pageNum: 1, pageSize: 10 })
const notePage = reactive({ pageNum: 1, pageSize: 10 })

const filteredItems = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return itemList.value
  return itemList.value.filter(row => (row.title || '').toLowerCase().includes(k))
})
const filteredNotes = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return noteList.value
  return noteList.value.filter(row => (row.title || '').toLowerCase().includes(k))
})
const itemTotal = computed(() => filteredItems.value.length)
const noteTotal = computed(() => filteredNotes.value.length)
const pagedItems = computed(() =>
  filteredItems.value.slice((itemPage.pageNum - 1) * itemPage.pageSize, itemPage.pageNum * itemPage.pageSize)
)
const pagedNotes = computed(() =>
  filteredNotes.value.slice((notePage.pageNum - 1) * notePage.pageSize, notePage.pageNum * notePage.pageSize)
)

/** 条目类型显示（字典 sgj_item_type） */
function typeLabel(itemType?: string) {
  const hit = (sgj_item_type.value || []).find((d: any) => d.value === itemType)
  return hit ? hit.label : (itemType || '-')
}

/** 条目状态显示（WANT/DONE 按类型措辞：想看/看过、想读/读过、想去/去过） */
const statusWording: Record<string, [string, string]> = {
  MOVIE: ['想看', '看过'],
  TV: ['想看', '看过'],
  BOOK: ['想读', '读过'],
  PLACE: ['想去', '去过']
}
function statusLabel(row: SgjItem) {
  const wording = statusWording[row.itemType || ''] || ['想看', '看过']
  return row.status === 'DONE' ? wording[1] : wording[0]
}

/** 查询回收站列表（条目+笔记） */
function getList() {
  itemLoading.value = true
  noteLoading.value = true
  listRecycleItem().then(response => {
    itemList.value = response.data || []
  }).finally(() => {
    itemLoading.value = false
  })
  listRecycleNote().then(response => {
    noteList.value = response.data || []
  }).finally(() => {
    noteLoading.value = false
  })
}

/** 搜索按钮操作（重新拉取最新回收站数据，并回到第一页） */
function handleQuery() {
  itemPage.pageNum = 1
  notePage.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  keyword.value = ''
  handleQuery()
}

/** 恢复条目（二次确认） */
function handleRestoreItem(row: SgjItem) {
  const name = row.title || ('条目#' + row.itemId)
  proxy.$modal.confirm('确定恢复「' + name + '」吗？恢复后将回到原列表对应状态。').then(() => {
    return restoreRecycleItem(row.itemId!)
  }).then(() => {
    proxy.$modal.msgSuccess('恢复成功，已回到原列表')
    getList()
  }).catch(() => {})
}

/** 彻底删除条目（二次确认，物理删除不可恢复） */
function handlePurgeItem(row: SgjItem) {
  const name = row.title || ('条目#' + row.itemId)
  proxy.$modal.confirm('彻底删除后不可恢复，确定彻底删除「' + name + '」吗？').then(() => {
    return purgeRecycleItem(row.itemId!)
  }).then(() => {
    proxy.$modal.msgSuccess('彻底删除成功')
    getList()
  }).catch(() => {})
}

/** 恢复笔记（二次确认） */
function handleRestoreNote(row: SgjNote) {
  const name = row.title || ('笔记#' + row.noteId)
  proxy.$modal.confirm('确定恢复「' + name + '」吗？恢复后将回到笔记列表。').then(() => {
    return restoreRecycleNote(row.noteId!)
  }).then(() => {
    proxy.$modal.msgSuccess('恢复成功，已回到笔记列表')
    getList()
  }).catch(() => {})
}

/** 彻底删除笔记（二次确认，物理删除不可恢复） */
function handlePurgeNote(row: SgjNote) {
  const name = row.title || ('笔记#' + row.noteId)
  proxy.$modal.confirm('彻底删除后不可恢复，确定彻底删除「' + name + '」吗？').then(() => {
    return purgeRecycleNote(row.noteId!)
  }).then(() => {
    proxy.$modal.msgSuccess('彻底删除成功')
    getList()
  }).catch(() => {})
}

getList()

// 本页被 keep-alive 缓存，重新进入时不会重跑 setup，需在 activated 钩子里重新拉取，
// 否则刚删除进回收站的记录不可见；首次激活紧跟 mounted，跳过避免重复请求
let firstActivated = true
onActivated(() => {
  if (firstActivated) {
    firstActivated = false
    return
  }
  getList()
})
</script>
