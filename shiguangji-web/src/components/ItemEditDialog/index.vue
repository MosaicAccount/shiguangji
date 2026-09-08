<template>
  <el-dialog v-model="dialogVisible" :title="title" width="620px" append-to-body>
    <el-form ref="editFormRef" :model="form" :rules="rules" label-width="90px">
      <el-row>
        <el-col :span="12">
          <el-form-item :label="titleLabel" prop="title">
            <el-input v-model="form.title" :placeholder="'请输入' + titleLabel" maxlength="200" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="评分" prop="rating">
            <el-input-number v-model="form.rating" :min="0" :max="10" :precision="1" :step="0.5" :controls="false" placeholder="0-10" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="标签" prop="tags">
            <!-- 标签来自后台标签管理，按条目类型区分，禁止自由输入 -->
            <tag-select v-model="form.tags" :module="itemType" placeholder="选择标签（可选）" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="封面图" prop="coverUrl">
            <el-input v-model="form.coverUrl" placeholder="图片地址（可选）" maxlength="500" />
          </el-form-item>
        </el-col>

        <template v-if="itemType === 'MOVIE' || itemType === 'TV'">
          <el-col :span="12">
            <el-form-item label="导演" prop="director">
              <el-input v-model="form.director" placeholder="导演（可选）" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主演" prop="actors">
              <el-input v-model="form.actors" placeholder="主演（可选）" maxlength="500" />
            </el-form-item>
          </el-col>
          <template v-if="itemType === 'MOVIE'">
            <el-col :span="12">
              <el-form-item label="上映年份" prop="releaseYear">
                <el-input-number v-model="form.releaseYear" :min="1888" :max="2100" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
          </template>
          <template v-else>
            <el-col :span="12">
              <el-form-item label="开播年份" prop="startYear">
                <el-input-number v-model="form.startYear" :min="1888" :max="2100" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="完结年份" prop="endYear">
                <el-input-number v-model="form.endYear" :min="1888" :max="2100" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="季数" prop="seasonCount">
                <el-input-number v-model="form.seasonCount" :min="1" :max="100" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="总集数" prop="episodeCount">
                <el-input-number v-model="form.episodeCount" :min="1" :max="10000" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
          </template>
          <el-col :span="12">
            <el-form-item label="类型" prop="genre">
              <el-input v-model="form.genre" placeholder="如：科幻、剧情（可选）" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地区" prop="region">
              <el-input v-model="form.region" placeholder="地区（可选）" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="语言" prop="language">
              <el-input v-model="form.language" placeholder="语言（可选）" maxlength="100" />
            </el-form-item>
          </el-col>
        </template>

        <template v-else-if="itemType === 'BOOK'">
          <el-col :span="12">
            <el-form-item label="作者" prop="author">
              <el-input v-model="form.author" placeholder="作者（可选）" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出版社" prop="publisher">
              <el-input v-model="form.publisher" placeholder="出版社（可选）" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出版日期" prop="publishDate">
              <el-date-picker v-model="form.publishDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="ISBN" prop="isbn">
              <el-input v-model="form.isbn" placeholder="ISBN（可选）" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="页数" prop="pages">
              <el-input-number v-model="form.pages" :min="1" :max="100000" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="genre">
              <el-input v-model="form.genre" placeholder="如：小说、技术（可选）" maxlength="200" />
            </el-form-item>
          </el-col>
        </template>

        <template v-else-if="itemType === 'PLACE'">
          <el-col :span="12">
            <el-form-item label="详细地址" prop="address">
              <el-input v-model="form.address" placeholder="详细地址（可选）" maxlength="300" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="城市" prop="city">
              <el-input v-model="form.city" placeholder="城市（可选）" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="省/州" prop="province">
              <el-input v-model="form.province" placeholder="省/州（可选）" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="国家" prop="country">
              <el-input v-model="form.country" placeholder="国家（可选）" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="经纬度">
              <!-- 手动输入或地图选点自动填入 -->
              <div class="coord-row">
                <el-input-number v-model="form.latitude" :min="-90" :max="90" :precision="6" :controls="false" placeholder="纬度" style="width: 130px" />
                <el-input-number v-model="form.longitude" :min="-180" :max="180" :precision="6" :controls="false" placeholder="经度" style="width: 130px" />
                <el-button type="primary" plain size="small" @click="pickerOpen = true">🗺 地图选点</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最佳季节" prop="bestSeason">
              <el-input v-model="form.bestSeason" placeholder="如：春季、秋季（可选）" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="placeCategory">
              <el-input v-model="form.placeCategory" placeholder="如：自然、人文、美食（可选）" maxlength="100" />
            </el-form-item>
          </el-col>
        </template>

        <el-col :span="24">
          <el-form-item label="短评" prop="comment">
            <el-input v-model="form.comment" type="textarea" :rows="3" placeholder="个人短评（可选）" maxlength="1000" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button v-if="props.itemId" @click="viewNotes">查看关联笔记</el-button>
      <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </el-dialog>

  <!-- 地图选点（地点表单经纬度自动填入） -->
  <map-picker v-model="pickerOpen" :latitude="form.latitude" :longitude="form.longitude" @confirm="onCoordPick" />
</template>

<script setup lang="ts" name="ItemEditDialog">
import { getFrontItem, updateFrontItem } from '@/api/front/item'
import MapPicker from '@/components/MapPicker/index.vue'
import TagSelect from '@/components/TagSelect/index.vue'
import type { PickedPlace } from '@/utils/map'
import type { SgjItem } from '@/types/api/business/item'

const props = defineProps<{
  modelValue: boolean
  itemId?: number
  /** 查看关联笔记的跳转地址（默认前台笔记页） */
  notesPath?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'saved'): void
}>()

const { proxy } = getCurrentInstance() as { proxy: any }
const router = useRouter()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const itemType = ref<'MOVIE' | 'TV' | 'BOOK' | 'PLACE'>('MOVIE')
const submitting = ref(false)

/** 地图选点弹窗 */
const pickerOpen = ref(false)

/** 地图选点确认后回填：坐标覆盖，名称/地址/城市/国家只补空值不覆盖已输入 */
function onCoordPick(place: PickedPlace): void {
  form.latitude = place.latitude
  form.longitude = place.longitude
  form.title = form.title || place.title
  form.address = form.address || place.address
  form.city = form.city || place.city
  form.country = form.country || place.country
}

const form = reactive<Record<string, any>>({})
const editFormRef = ref()

const title = computed(() => '编辑' + titleLabel.value)
const titleLabel = computed(() => itemType.value === 'PLACE' ? '名称' : '标题')

const rules = {
  title: [{ required: true, message: '请输入' + titleLabel.value, trigger: 'blur' }]
}

watch(
  () => props.modelValue,
  value => {
    if (value && props.itemId) {
      getFrontItem(props.itemId).then(response => {
        const data = response.data || {}
        itemType.value = (data.itemType as any) || 'MOVIE'
        Object.keys(form).forEach(key => delete form[key])
        Object.assign(form, data)
      }).catch(() => {})
    }
  }
)

function submitForm(): void {
  if (!props.itemId) return
  editFormRef.value.validate((valid: boolean) => {
    if (!valid) return
    submitting.value = true
    updateFrontItem(props.itemId!, { ...form }).then(() => {
      proxy.$modal.msgSuccess('修改成功')
      dialogVisible.value = false
      emit('saved')
    }).catch(() => {}).finally(() => {
      submitting.value = false
    })
  })
}

/** B-03：跳转笔记页展示该条目关联笔记 */
function viewNotes(): void {
  if (!props.itemId) return
  dialogVisible.value = false
  router.push({ path: props.notesPath || '/note', query: { itemId: String(props.itemId) } })
}
</script>

<style scoped lang="scss">
.coord-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
