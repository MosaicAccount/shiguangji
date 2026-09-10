<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item :label="titleLabel" prop="title">
        <el-input
          v-model="queryParams.title"
          :placeholder="'请输入' + titleLabel"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 200px">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <!-- 标签筛选：选项来自标签管理（按模块区分），FIND_IN_SET 精确匹配 -->
      <el-form-item label="标签" prop="tags">
        <tag-select
          v-model="queryParams.tags"
          :module="itemType"
          :multiple="false"
          placeholder="选择标签"
          style="width: 200px"
          @update:model-value="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['sgj:item:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['sgj:item:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['sgj:item:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-dropdown v-hasPermi="['sgj:item:list']" @command="handleExport">
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

    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column :label="titleLabel" align="center" prop="title" :show-overflow-tooltip="true" min-width="160" />
      <el-table-column label="封面" align="center" width="80">
        <template #default="scope">
          <!-- 封面预览比例统一 2:3 竖版（与前台列表一致）；无图或加载失败回退类型衬线字 -->
          <item-cover :src="scope.row.coverUrl" :item-type="scope.row.itemType" :width="48" :height="72" />
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'DONE' ? 'success' : 'warning'">
            {{ statusLabel(scope.row.status) }}
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
      <el-table-column label="操作" align="center" fixed="right" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['sgj:item:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['sgj:item:remove']">删除</el-button>
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
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col v-for="field in formFields" :key="field.key" :span="field.span || 12">
            <el-form-item :label="field.label" :prop="field.key">
              <el-input
                v-if="field.type === 'input'"
                v-model="form[field.key]"
                :placeholder="field.placeholder"
                :maxlength="field.maxlength"
              />
              <image-upload
                v-else-if="field.type === 'image'"
                v-model="form[field.key]"
                :limit="field.limit ?? 1"
                :file-type="['png', 'jpg', 'jpeg', 'gif', 'webp']"
              />
              <el-select
                v-else-if="field.type === 'select'"
                v-model="form[field.key]"
                :placeholder="field.placeholder"
                :filterable="field.filterable"
                :allow-create="field.allowCreate"
                clearable
                style="width: 100%"
              >
                <el-option v-for="opt in field.dictType ? (dictMap[field.dictType] || []) : (field.options || [])" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
              <tag-select
                v-else-if="field.type === 'tags'"
                v-model="form[field.key]"
                :module="itemType"
                placeholder="选择标签（可选）"
              />
              <el-input-number
                v-else-if="field.type === 'number'"
                v-model="form[field.key]"
                :min="field.min"
                :max="field.max"
                :precision="field.precision || 0"
                :step="field.step || 1"
                style="width: 100%"
              />
              <!-- 地点名称带搜索：选中候选自动回填地址/城市/国家与坐标 -->
              <place-search-input
                v-else-if="field.type === 'placeTitle'"
                v-model="form[field.key]"
                @select="onPlaceSelect"
              />
              <!-- 经纬度组合控件：手动输入或地图选点自动填入 -->
              <div v-else-if="field.type === 'coord'" class="coord-row">
                <el-input-number v-model="form.latitude" :min="-90" :max="90" :precision="6" :controls="false" placeholder="纬度" style="width: 130px" />
                <el-input-number v-model="form.longitude" :min="-180" :max="180" :precision="6" :controls="false" placeholder="经度" style="width: 130px" />
                <el-button type="primary" plain size="small" @click="coordPickerOpen = true">🗺 地图选点</el-button>
              </div>
              <el-date-picker
                v-else-if="field.type === 'date'"
                v-model="form[field.key]"
                type="date"
                value-format="YYYY-MM-DD"
                :placeholder="field.placeholder"
                style="width: 100%"
              />
              <el-input
                v-else-if="field.type === 'textarea'"
                v-model="form[field.key]"
                type="textarea"
                :rows="field.rows || 3"
                :placeholder="field.placeholder"
              />
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

    <!-- 地图选点（地点表单经纬度自动填入） -->
    <map-picker v-model="coordPickerOpen" :latitude="form.latitude" :longitude="form.longitude" @confirm="onCoordPick" />
  </div>
</template>

<script setup lang="ts" name="ItemManager">
import { listItem, getItem, addItem, updateItem, delItem } from '@/api/business/item'
import { fetchAllRows, downloadJson, downloadCsv, exportDateTag } from '@/utils/exportData'
import { useDict } from '@/utils/dict'
import ItemCover from '@/components/ItemCover/index.vue'
import MapPicker from '@/components/MapPicker/index.vue'
import PlaceSearchInput from '@/components/PlaceSearchInput/index.vue'
import TagSelect from '@/components/TagSelect/index.vue'
import type { PickedPlace, PlaceResult } from '@/utils/map'
import type { SgjItem } from '@/types/api/business/item'
import { parseTime } from '@/utils/sgj'

// 业务数据字典（与后端 sql/init_business.sql 中 sgj_* 字典对应）
const { sgj_item_type, sgj_movie_status, sgj_book_status, sgj_place_status, sgj_movie_genre, sgj_book_genre, sgj_region, sgj_language, sgj_place_category, sgj_best_season } = useDict(
  'sgj_item_type', 'sgj_movie_status', 'sgj_book_status', 'sgj_place_status',
  'sgj_movie_genre', 'sgj_book_genre', 'sgj_region', 'sgj_language', 'sgj_place_category', 'sgj_best_season'
)

// 字典映射表（供表单 select 与展示使用；useDict 返回的是 ref，这里统一解包为数组）
const dictMap = computed<Record<string, any[]>>(() => ({
  sgj_item_type: sgj_item_type.value,
  sgj_movie_status: sgj_movie_status.value,
  sgj_book_status: sgj_book_status.value,
  sgj_place_status: sgj_place_status.value,
  sgj_movie_genre: sgj_movie_genre.value,
  sgj_book_genre: sgj_book_genre.value,
  sgj_region: sgj_region.value,
  sgj_language: sgj_language.value,
  sgj_place_category: sgj_place_category.value,
  sgj_best_season: sgj_best_season.value
}))

const props = defineProps<{
  itemType: 'MOVIE' | 'TV' | 'BOOK' | 'PLACE'
  pageTitle: string
  wantLabel?: string
  doneLabel?: string
}>()

const { proxy } = getCurrentInstance() as { proxy: any }

const dataList = ref<SgjItem[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>('')

const titleLabel = computed(() => props.itemType === 'PLACE' ? '名称' : '标题')

// 条目类型 → 状态字典 映射（电影/电视剧共用影视状态，书籍/地点各自独立）
const statusDictType = computed(() => {
  if (props.itemType === 'BOOK') return 'sgj_book_status'
  if (props.itemType === 'PLACE') return 'sgj_place_status'
  return 'sgj_movie_status'
})

const statusOptions = computed(() => {
  const dict = dictMap.value[statusDictType.value] || []
  return dict.map((d: any) => ({
    value: d.value,
    label: d.value === 'WANT' ? (props.wantLabel || d.label) : d.value === 'DONE' ? (props.doneLabel || d.label) : d.label
  }))
})

interface FieldConfig {
  key: string
  label: string
  type: 'input' | 'select' | 'number' | 'date' | 'textarea' | 'image' | 'tags' | 'coord' | 'placeTitle'
  placeholder?: string
  required?: boolean
  span?: number
  rows?: number
  min?: number
  max?: number
  precision?: number
  step?: number
  maxlength?: number
  options?: { value: string; label: string }[]
  dictType?: string
  allowCreate?: boolean
  filterable?: boolean
  /** 图片数量限制（仅 image 类型；0 表示不限制，默认 1） */
  limit?: number
}

const formFields = computed<FieldConfig[]>(() => {
  const common: FieldConfig[] = [
    { key: 'title', label: titleLabel.value, type: 'input', placeholder: '请输入' + titleLabel.value, required: true, maxlength: 200 },
    { key: 'status', label: '状态', type: 'select', options: statusOptions.value, required: true },
    { key: 'rating', label: '评分', type: 'number', min: 0, max: 10, precision: 1, step: 0.5, placeholder: '0-10' },
    { key: 'tags', label: '标签', type: 'tags' },
    { key: 'coverUrl', label: '封面/图片', type: 'image', span: 24 },
    { key: 'startDate', label: '开始日期', type: 'date', placeholder: '选择日期' },
    { key: 'finishDate', label: '完成日期', type: 'date', placeholder: '选择日期' },
    { key: 'comment', label: '短评', type: 'textarea', rows: 3, placeholder: '个人短评', span: 24 },
    { key: 'remark', label: '备注', type: 'textarea', rows: 2, placeholder: '备注', span: 24 }
  ]

  const typeFields: Record<string, FieldConfig[]> = {
    MOVIE: [
      { key: 'director', label: '导演', type: 'input', maxlength: 200 },
      { key: 'actors', label: '主演', type: 'input', maxlength: 500 },
      { key: 'genre', label: '类型/题材', type: 'select', dictType: 'sgj_movie_genre', allowCreate: true, filterable: true },
      { key: 'region', label: '地区', type: 'select', dictType: 'sgj_region', allowCreate: true, filterable: true },
      { key: 'language', label: '语言', type: 'select', dictType: 'sgj_language', allowCreate: true, filterable: true },
      { key: 'releaseYear', label: '上映年份', type: 'number', min: 1888, max: 2100 },
      { key: 'durationMinutes', label: '片长(分钟)', type: 'number', min: 1, max: 1000 },
      { key: 'imdbId', label: 'IMDb编号', type: 'input', maxlength: 50 },
      { key: 'doubanId', label: '豆瓣编号', type: 'input', maxlength: 50 }
    ],
    TV: [
      { key: 'director', label: '导演', type: 'input', maxlength: 200 },
      { key: 'actors', label: '主演', type: 'input', maxlength: 500 },
      { key: 'genre', label: '类型/题材', type: 'select', dictType: 'sgj_movie_genre', allowCreate: true, filterable: true },
      { key: 'region', label: '地区', type: 'select', dictType: 'sgj_region', allowCreate: true, filterable: true },
      { key: 'language', label: '语言', type: 'select', dictType: 'sgj_language', allowCreate: true, filterable: true },
      { key: 'startYear', label: '开播年份', type: 'number', min: 1888, max: 2100 },
      { key: 'endYear', label: '完结年份', type: 'number', min: 1888, max: 2100 },
      { key: 'seasonCount', label: '季数', type: 'number', min: 1, max: 100 },
      { key: 'episodeCount', label: '总集数', type: 'number', min: 1, max: 10000 },
      { key: 'imdbId', label: 'IMDb编号', type: 'input', maxlength: 50 },
      { key: 'doubanId', label: '豆瓣编号', type: 'input', maxlength: 50 }
    ],
    BOOK: [
      { key: 'author', label: '作者', type: 'input', maxlength: 200 },
      { key: 'publisher', label: '出版社', type: 'input', maxlength: 200 },
      { key: 'publishDate', label: '出版日期', type: 'date' },
      { key: 'isbn', label: 'ISBN', type: 'input', maxlength: 50 },
      { key: 'pages', label: '页数', type: 'number', min: 1, max: 100000 },
      { key: 'genre', label: '分类/题材', type: 'select', dictType: 'sgj_book_genre', allowCreate: true, filterable: true }
    ],
    PLACE: [
      { key: 'address', label: '详细地址', type: 'input', maxlength: 300 },
      { key: 'city', label: '城市', type: 'input', maxlength: 100 },
      { key: 'province', label: '省/州', type: 'input', maxlength: 100 },
      { key: 'country', label: '国家', type: 'input', maxlength: 100 },
      { key: 'coord', label: '经纬度', type: 'coord', span: 24 },
      { key: 'bestSeason', label: '最佳季节', type: 'select', dictType: 'sgj_best_season' },
      { key: 'placeCategory', label: '地点分类', type: 'select', dictType: 'sgj_place_category', allowCreate: true, filterable: true },
      { key: 'photos', label: '照片', type: 'image', span: 24, limit: 0 }
    ]
  }

  // 地点表单的名称字段带搜索：选中候选自动回填地址/城市/国家与坐标
  const fields = [...common, ...(typeFields[props.itemType] || [])]
  if (props.itemType !== 'PLACE') return fields
  return fields.map(f => (f.key === 'title' ? { ...f, type: 'placeTitle' as const } : f))
})

const rules = computed(() => {
  const result: Record<string, any> = {}
  for (const field of formFields.value) {
    if (field.required) {
      result[field.key] = [{
        required: true,
        message: '请输入' + field.label,
        trigger: field.type === 'select' ? 'change' : 'blur'
      }]
    }
  }
  return result
})

const data = reactive({
  form: {} as Record<string, any>,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    title: undefined,
    status: undefined,
    tags: undefined
  }
})

const { form, queryParams } = toRefs(data)

/** 地图选点弹窗 */
const coordPickerOpen = ref(false)

/** 名称搜索选中地点：直接回填名称/地址/城市/国家与坐标，无需地图选点 */
function onPlaceSelect(place: PlaceResult) {
  form.value.title = place.title
  form.value.address = place.address
  form.value.city = place.city
  form.value.country = place.country
  form.value.latitude = place.latitude
  form.value.longitude = place.longitude
}

/** 地图选点确认后回填：以新选地点为准覆盖；逆地理失败缺字段时保留原值 */
function onCoordPick(place: PickedPlace) {
  form.value.latitude = place.latitude
  form.value.longitude = place.longitude
  form.value.title = place.title || form.value.title
  form.value.address = place.address || form.value.address
  form.value.city = place.city || form.value.city
  form.value.country = place.country || form.value.country
}

/** 查询列表 */
function getList() {
  loading.value = true
  listItem({ ...queryParams.value, itemType: props.itemType }).then(response => {
    dataList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 状态显示 */
function statusLabel(status?: string) {
  const item = statusOptions.value.find(opt => opt.value === status)
  return item ? item.label : status || '-'
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    itemType: props.itemType,
    title: undefined,
    status: 'WANT',
    rating: undefined,
    tags: undefined,
    coverUrl: undefined,
    startDate: undefined,
    finishDate: undefined,
    comment: undefined,
    remark: undefined,
    photos: undefined
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
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection: SgjItem[]) {
  ids.value = selection.map(item => item.itemId!)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = '新增' + props.pageTitle
}

/** 修改按钮操作 */
function handleUpdate(row?: SgjItem) {
  reset()
  const itemId = row?.itemId || ids.value[0]
  getItem(itemId).then(response => {
    form.value = { ...response.data, itemType: props.itemType }
    open.value = true
    title.value = '修改' + props.pageTitle
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs['formRef'].validate((valid: boolean) => {
    if (valid) {
      form.value.itemType = props.itemType
      if (form.value.itemId != undefined) {
        updateItem(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功')
          open.value = false
          getList()
        })
      } else {
        addItem(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功')
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row?: SgjItem) {
  const itemIds = row?.itemId || ids.value
  proxy.$modal.confirm('是否确认删除选中的条目？').then(function() {
    return delItem(itemIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

/** 导出进行中（数据导出） */
const exportLoading = ref<boolean>(false)

/** 条目 CSV 导出列（JSON 导出为完整对象数组；CSV 取以下常用字段） */
const itemExportColumns = [
  { label: '条目ID', key: 'itemId' },
  { label: '类型', key: 'itemType' },
  { label: '标题', key: 'title' },
  { label: '状态', key: 'status' },
  { label: '评分', key: 'rating' },
  { label: '短评', key: 'comment' },
  { label: '标签', key: 'tags' },
  { label: '封面', key: 'coverUrl' },
  { label: '开始日期', key: 'startDate' },
  { label: '完成日期', key: 'finishDate' },
  { label: '备注', key: 'remark' },
  { label: '导演', key: 'director' },
  { label: '主演', key: 'actors' },
  { label: '题材', key: 'genre' },
  { label: '地区', key: 'region' },
  { label: '语言', key: 'language' },
  { label: '上映年份', key: 'releaseYear' },
  { label: '片长(分钟)', key: 'durationMinutes' },
  { label: '开播年份', key: 'startYear' },
  { label: '完结年份', key: 'endYear' },
  { label: '季数', key: 'seasonCount' },
  { label: '总集数', key: 'episodeCount' },
  { label: 'IMDb编号', key: 'imdbId' },
  { label: '豆瓣编号', key: 'doubanId' },
  { label: '作者', key: 'author' },
  { label: '出版社', key: 'publisher' },
  { label: '出版日期', key: 'publishDate' },
  { label: 'ISBN', key: 'isbn' },
  { label: '页数', key: 'pages' },
  { label: '详细地址', key: 'address' },
  { label: '城市', key: 'city' },
  { label: '省/州', key: 'province' },
  { label: '国家', key: 'country' },
  { label: '纬度', key: 'latitude' },
  { label: '经度', key: 'longitude' },
  { label: '最佳季节', key: 'bestSeason' },
  { label: '地点分类', key: 'placeCategory' },
  { label: '创建时间', key: 'createTime' },
  { label: '更新时间', key: 'updateTime' }
]

/** 导出按钮操作（command: json/csv；循环分页拉取当前筛选下全量数据，pageSize 取后端上限） */
function handleExport(command: string) {
  proxy.$modal.confirm('是否确认导出当前筛选下的全部' + props.pageTitle + '数据？').then(async () => {
    exportLoading.value = true
    try {
      const rows = await fetchAllRows<SgjItem>((pageNum, pageSize) =>
        listItem({ ...queryParams.value, itemType: props.itemType, pageNum, pageSize })
      )
      if (!rows.length) {
        proxy.$modal.msgWarning('没有可导出的数据')
        return
      }
      const tag = exportDateTag()
      const typeTag = String(props.itemType).toLowerCase()
      if (command === 'csv') {
        downloadCsv(`sgj_item_${typeTag}_${tag}.csv`, itemExportColumns, rows as unknown as Record<string, any>[])
      } else {
        downloadJson(`sgj_item_${typeTag}_${tag}.json`, rows)
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

<style scoped lang="scss">
.coord-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
