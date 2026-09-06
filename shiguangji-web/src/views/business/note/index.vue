<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入笔记标题"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="关联ID" prop="itemId">
        <el-input-number
          v-model="queryParams.itemId"
          placeholder="关联条目ID"
          :min="1"
          :controls="false"
          style="width: 200px"
        />
      </el-form-item>
      <!-- ：公开筛选（后端 SgjNoteMapper 已支持 isPublic 精确查询） -->
      <!-- 标签筛选：选项来自标签管理（NOTE 模块），FIND_IN_SET 精确匹配 -->
      <el-form-item label="标签" prop="tags">
        <tag-select
          v-model="queryParams.tags"
          module="NOTE"
          :multiple="false"
          placeholder="选择标签"
          style="width: 200px"
          @update:model-value="handleQuery"
        />
      </el-form-item>
      <el-form-item label="公开" prop="isPublic">
        <el-select v-model="queryParams.isPublic" placeholder="公开状态" clearable style="width: 200px">
          <el-option label="公开" value="1" />
          <el-option label="私密" value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['sgj:note:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['sgj:note:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['sgj:note:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-dropdown v-hasPermi="['sgj:note:list']" @command="handleExport">
          <el-button type="warning" plain icon="Download" :loading="exportLoading">
            导出<el-icon class="el-icon--right"><arrow-down /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="json">导出 JSON</el-dropdown-item>
              <el-dropdown-item command="csv">导出 CSV</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="noteList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="笔记ID" align="center" prop="noteId" width="80" />
      <el-table-column label="标题" align="center" prop="title" :show-overflow-tooltip="true" min-width="180" />
      <!-- ：内容摘要列（Markdown 转纯文本截断 40 字） -->
      <el-table-column label="内容摘要" align="left" min-width="220">
        <template #default="scope">
          <span class="note-summary">{{ noteSummary(scope.row.content) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="关联条目ID" align="center" prop="itemId" width="110">
        <template #default="scope">
          <span>{{ scope.row.itemId ?? '独立笔记' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="标签" align="center" prop="tags" :show-overflow-tooltip="true" min-width="140" />
      <el-table-column label="公开" align="center" prop="isPublic" width="90">
        <template #default="scope">
          <!-- 快捷切换公开状态，失败自动回滚 -->
          <el-switch
            v-model="scope.row.isPublic"
            active-value="1"
            inactive-value="0"
            @change="handlePublicChange(scope.row)"
            v-hasPermi="['sgj:note:edit']"
          />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['sgj:note:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['sgj:note:remove']">删除</el-button>
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

    <el-dialog :title="title" v-model="open" width="820px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="笔记标题" prop="title">
              <el-input v-model="form.title" placeholder="请输入笔记标题" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联条目ID" prop="itemId">
              <el-input-number
                v-model="form.itemId"
                placeholder="可选，留空为独立笔记"
                :min="1"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="公开状态">
              <!-- 公开/私密：默认私密，公开后访客可见 -->
              <el-radio-group v-model="form.isPublic">
                <el-radio-button value="0">私密</el-radio-button>
                <el-radio-button value="1">公开</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标签" prop="tags">
              <!-- 标签来自后台标签管理（NOTE 模块），禁止自由输入 -->
              <tag-select v-model="form.tags" module="NOTE" placeholder="选择标签（可选）" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="内容" prop="content">
              <markdown-editor v-model="form.content" :rows="14" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Note">
import MarkdownEditor from '@/components/MarkdownEditor/index.vue'
import TagSelect from '@/components/TagSelect/index.vue'
import { listNote, getNote, addNote, updateNote, delNote } from '@/api/business/note'
import { fetchAllRows, downloadJson, downloadCsv, exportDateTag } from '@/utils/exportData'
import type { SgjNote } from '@/types/api/business/note'
import { parseTime } from '@/utils/sgj'

const { proxy } = getCurrentInstance() as { proxy: any }

const noteList = ref<SgjNote[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>('')

const data = reactive({
  form: {} as SgjNote,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    title: undefined,
    itemId: undefined,
    tags: undefined,
    isPublic: undefined
  },
  rules: {
    title: [{ required: true, message: '笔记标题不能为空', trigger: 'blur' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** ：Markdown 转纯文本摘要（与前台 ItemNotes.noteSummary 同逻辑），截断 40 字 */
function noteSummary(content?: string): string {
  if (!content) return '-'
  const plain = content
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[#>*`~\-_|]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return plain.length > 40 ? plain.slice(0, 40) + '…' : (plain || '-')
}

/** 查询列表 */
function getList() {
  loading.value = true
  listNote(queryParams.value).then(response => {
    noteList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    noteId: undefined,
    itemId: undefined,
    title: undefined,
    content: undefined,
    tags: undefined,
    remark: undefined,
    isPublic: '0'
  }
  proxy.resetForm('formRef')
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.value.itemId = undefined
  queryParams.value.isPublic = undefined
  queryParams.value.tags = undefined
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection: SgjNote[]) {
  ids.value = selection.map(item => item.noteId!)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = '新增笔记'
}

/** 修改按钮操作 */
function handleUpdate(row?: SgjNote) {
  reset()
  const noteId = row?.noteId || ids.value[0]
  getNote(noteId).then(response => {
    form.value = response.data || {}
    // 公开状态归一化：仅接受 '0'/'1'，空值按私密处理（存量数据兜底）
    form.value.isPublic = form.value.isPublic === '1' ? '1' : '0'
    open.value = true
    title.value = '修改笔记'
  })
}

/** 切换公开状态：列表快捷开关，失败回滚 */
function handlePublicChange(row: SgjNote) {
  updateNote({ ...row }).then(() => {
    proxy.$modal.msgSuccess(row.isPublic === '1' ? '已设为公开' : '已设为私密')
  }).catch(() => {
    row.isPublic = row.isPublic === '1' ? '0' : '1'
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs['formRef'].validate((valid: boolean) => {
    if (valid) {
      if (form.value.noteId != undefined) {
        updateNote(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功')
          open.value = false
          getList()
        })
      } else {
        addNote(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功')
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row?: SgjNote) {
  const noteIds = row?.noteId || ids.value
  proxy.$modal.confirm('是否确认删除选中的笔记？').then(function() {
    return delNote(noteIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

/** 导出进行中（数据导出） */
const exportLoading = ref<boolean>(false)

/** 笔记 CSV 导出列（JSON 导出为完整对象数组；CSV 取以下常用字段） */
const noteExportColumns = [
  { label: '笔记ID', key: 'noteId' },
  { label: '关联条目ID', key: 'itemId' },
  { label: '标题', key: 'title' },
  { label: '标签', key: 'tags' },
  { label: '内容', key: 'content' },
  { label: '备注', key: 'remark' },
  { label: '创建者', key: 'createBy' },
  { label: '创建时间', key: 'createTime' },
  { label: '更新时间', key: 'updateTime' }
]

/** 导出按钮操作（command: json/csv；循环分页拉取当前筛选下全量数据，pageSize 取后端上限） */
function handleExport(command: string) {
  proxy.$modal.confirm('是否确认导出当前筛选下的全部笔记数据？').then(async () => {
    exportLoading.value = true
    try {
      const rows = await fetchAllRows<SgjNote>((pageNum, pageSize) =>
        listNote({ ...queryParams.value, pageNum, pageSize })
      )
      if (!rows.length) {
        proxy.$modal.msgWarning('没有可导出的数据')
        return
      }
      const tag = exportDateTag()
      if (command === 'csv') {
        downloadCsv(`sgj_note_${tag}.csv`, noteExportColumns, rows as unknown as Record<string, any>[])
      } else {
        downloadJson(`sgj_note_${tag}.json`, rows)
      }
      proxy.$modal.msgSuccess('导出成功，共 ' + rows.length + ' 条')
    } catch (e) {
      // 失败提示由 request 拦截器统一弹出，这里仅记录便于排查
      console.error('导出失败', e)
    } finally {
      exportLoading.value = false
    }
  }).catch(() => {})
}

getList()
</script>

<style scoped>
/* ：内容摘要列（单行省略） */
.note-summary {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #606266;
}
</style>
