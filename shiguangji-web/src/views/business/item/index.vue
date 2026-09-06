<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入标题"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="类型" prop="itemType">
        <el-select v-model="queryParams.itemType" placeholder="类型" clearable style="width: 160px">
          <el-option v-for="opt in sgj_item_type" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 160px">
          <el-option label="想看/想读/想去" value="WANT" />
          <el-option label="已看/已读/去过" value="DONE" />
        </el-select>
      </el-form-item>
      <el-form-item label="标签" prop="tags">
        <el-input
          v-model="queryParams.tags"
          placeholder="标签关键字"
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

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList">
      <el-table-column label="类型" align="center" prop="itemType" width="90">
        <template #default="scope">
          <el-tag :type="typeTag(scope.row.itemType)">{{ typeLabel(scope.row.itemType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标题" align="center" prop="title" :show-overflow-tooltip="true" min-width="180" />
      <el-table-column label="封面" align="center" width="80">
        <template #default="scope">
          <item-cover :src="scope.row.coverUrl" :item-type="scope.row.itemType" :width="48" :height="72" />
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'DONE' ? 'success' : 'warning'">
            {{ statusLabel(scope.row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="评分" align="center" prop="rating" width="80" />
      <el-table-column label="标签" align="center" prop="tags" :show-overflow-tooltip="true" min-width="140" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="100" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)">修改</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <item-edit-dialog v-model="editOpen" :item-id="editId" notes-path="/sgj/note" @saved="getList" />
  </div>
</template>

<script setup lang="ts" name="BusinessItem">
import { listItem } from '@/api/business/item'
import { useDict } from '@/utils/dict'
import { parseTime } from '@/utils/sgj'
import ItemCover from '@/components/ItemCover/index.vue'
import ItemEditDialog from '@/components/ItemEditDialog/index.vue'
import type { SgjItem } from '@/types/api/business/item'

// 业务数据字典（与后端 sql/init_business.sql 中 sgj_* 字典对应）
const { sgj_item_type, sgj_movie_status, sgj_book_status, sgj_place_status } = useDict(
  'sgj_item_type', 'sgj_movie_status', 'sgj_book_status', 'sgj_place_status'
)

const dictMap = computed<Record<string, any[]>>(() => ({
  sgj_movie_status: sgj_movie_status.value,
  sgj_book_status: sgj_book_status.value,
  sgj_place_status: sgj_place_status.value
}))

const dataList = ref<SgjItem[]>([])
const loading = ref<boolean>(true)
const showSearch = ref<boolean>(true)
const total = ref<number>(0)
const editOpen = ref<boolean>(false)
const editId = ref<number>()

const queryRef = ref()
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  itemType: undefined,
  status: undefined,
  title: undefined,
  tags: undefined
})

/** 查询列表（不传 itemType 时后端返回全部类型） */
function getList() {
  loading.value = true
  listItem({ ...queryParams }).then(response => {
    dataList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  queryRef.value.resetFields()
  handleQuery()
}

/** 类型显示 */
function typeLabel(itemType?: string) {
  const item = sgj_item_type.value.find((opt: any) => opt.value === itemType)
  return item ? item.label : itemType || '-'
}

function typeTag(itemType?: string) {
  const item = sgj_item_type.value.find((opt: any) => opt.value === itemType)
  return (item && item.elTagType) || 'primary'
}

/** 状态显示：按条目自身类型取对应状态字典（想看/想读/想去） */
function statusLabel(row: SgjItem) {
  const dictType = row.itemType === 'BOOK' ? 'sgj_book_status'
    : row.itemType === 'PLACE' ? 'sgj_place_status'
    : 'sgj_movie_status'
  const item = (dictMap.value[dictType] || []).find((opt: any) => opt.value === row.status)
  return item ? item.label : row.status || '-'
}

/** 修改按钮操作：复用通用条目编辑弹窗 */
function handleEdit(row: SgjItem) {
  if (!row.itemId) return
  editId.value = row.itemId
  editOpen.value = true
}

getList()
</script>
