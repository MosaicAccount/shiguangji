<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="模块" prop="module">
        <el-select v-model="queryParams.module" placeholder="全部模块" clearable style="width: 200px">
          <el-option v-for="m in sgj_tag_module" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="标签名称" prop="tagName">
        <el-input
          v-model="queryParams.tagName"
          placeholder="请输入标签名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 200px">
          <el-option label="启用" value="0" />
          <el-option label="停用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['sgj:tag:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['sgj:tag:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['sgj:tag:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="tagList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="标签ID" align="center" prop="tagId" width="80" />
      <el-table-column label="所属模块" align="center" prop="module" width="100">
        <template #default="scope">
          <el-tag :type="moduleTagType(scope.row.module)">{{ moduleLabel(scope.row.module) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标签名称" align="center" prop="tagName" min-width="140" />
      <el-table-column label="排序" align="center" prop="sort" width="80" />
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <!-- 快捷切换启停，失败自动回滚 -->
          <el-switch
            v-model="scope.row.status"
            active-value="0"
            inactive-value="1"
            @change="handleStatusChange(scope.row)"
            v-hasPermi="['sgj:tag:edit']"
          />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" min-width="140" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['sgj:tag:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['sgj:tag:remove']">删除</el-button>
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

    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="所属模块" prop="module">
          <el-select v-model="form.module" placeholder="选择模块" style="width: 100%">
            <el-option v-for="m in sgj_tag_module" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签名称" prop="tagName">
          <el-input v-model="form.tagName" placeholder="请输入标签名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="显示排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button value="0">启用</el-radio-button>
            <el-radio-button value="1">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
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

<script setup lang="ts" name="Tag">
import { listTag, getTag, addTag, updateTag, delTag } from '@/api/business/tag'
import { useDict } from '@/utils/dict'
import type { SgjTag } from '@/types/api/business/tag'
import { parseTime } from '@/utils/sgj'

const { sgj_tag_module } = useDict('sgj_tag_module')

const { proxy } = getCurrentInstance() as { proxy: any }

const tagList = ref<SgjTag[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>('')

const data = reactive({
  form: {} as SgjTag,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    module: undefined,
    tagName: undefined,
    status: undefined
  },
  rules: {
    module: [{ required: true, message: '请选择所属模块', trigger: 'change' }],
    tagName: [{ required: true, message: '标签名称不能为空', trigger: 'blur' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 模块字典 code → 中文名 */
function moduleLabel(module?: string): string {
  const found = (sgj_tag_module.value || []).find(m => m.value === module)
  return found ? found.label : module || '-'
}

/** 模块 el-tag 配色（与条目类型字典 elTagClass 大致对应） */
function moduleTagType(module?: string): 'primary' | 'warning' | 'success' | 'info' | 'default' {
  switch (module) {
    case 'MOVIE': return 'primary'
    case 'TV': return 'warning'
    case 'BOOK': return 'success'
    case 'PLACE': return 'info'
    default: return 'default'
  }
}

/** 查询列表 */
function getList() {
  loading.value = true
  listTag(queryParams.value).then(response => {
    tagList.value = response.rows
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
    tagId: undefined,
    module: undefined,
    tagName: undefined,
    sort: 0,
    status: '0',
    remark: undefined
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
  queryParams.value.module = undefined
  queryParams.value.status = undefined
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection: SgjTag[]) {
  ids.value = selection.map(item => item.tagId!)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = '新增标签'
}

/** 修改按钮操作 */
function handleUpdate(row?: SgjTag) {
  reset()
  const tagId = row?.tagId || ids.value[0]
  getTag(tagId).then(response => {
    form.value = response.data || {}
    open.value = true
    title.value = '修改标签'
  })
}

/** 切换启用状态：列表快捷开关，失败回滚 */
function handleStatusChange(row: SgjTag) {
  updateTag({ ...row }).then(() => {
    proxy.$modal.msgSuccess(row.status === '0' ? '已启用' : '已停用')
  }).catch(() => {
    row.status = row.status === '0' ? '1' : '0'
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs['formRef'].validate((valid: boolean) => {
    if (valid) {
      if (form.value.tagId != undefined) {
        updateTag(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功')
          open.value = false
          getList()
        })
      } else {
        addTag(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功')
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row?: SgjTag) {
  const tagIds = row?.tagId || ids.value
  proxy.$modal.confirm('删除标签仅移除标签定义，已使用该标签的记录会保留原文字。是否确认删除？').then(function() {
    return delTag(tagIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

getList()
</script>
