<template>
  <div class="travel-page">
    <div class="page-banner anim">
      <div class="banner-blob" aria-hidden="true"></div>
      <div class="banner-blob2" aria-hidden="true"></div>
      <div class="banner-text">
        <h1>足迹</h1>
        <p>记录你去过和想去的地方。</p>
      </div>
      <div class="banner-stats">
        <span class="stat-chip">去过<b>{{ bannerStats.cities }}</b>城</span>
        <span class="stat-chip">留下<b>{{ bannerStats.total }}</b>张照片</span>
        <span v-if="bannerStats.latest" class="stat-chip">最近<b>{{ bannerStats.latest.title }}</b></span>
      </div>
      <el-button v-if="isLogin" type="primary" class="banner-add" @click="openAdd">＋ 添加想去</el-button>
    </div>

    <div class="filter-bar">
      <div class="filter-group">
        <button class="filter-pill" :class="{ active: mode === 'card' }" @click="switchMode('card')">卡片模式</button>
        <button class="filter-pill" :class="{ active: mode === 'map' }" @click="switchMode('map')">地图模式</button>
      </div>
      <div v-if="isLogin && mode === 'card'" class="filter-group">
        <button
          v-for="(s, si) in sgj_place_status"
          :key="s.value"
          class="filter-pill"
          :class="{ active: activeStatus === s.value }"
          @click="switchStatus(s.value)"
        >{{ s.label }}</button>
      </div>
      <el-input
        v-if="mode === 'card'"
        v-model="searchTitle"
        placeholder="搜索地点"
        clearable
        class="search-input"
        @keyup.enter="loadData"
        @clear="loadData"
      >
        <template #prefix><span class="search-icon">⌕</span></template>
        <template #append>
          <el-button @click="loadData">搜索</el-button>
        </template>
      </el-input>
    </div>

    <!-- 标签筛选：豆瓣式标签行（PLACE 模块，仅卡片模式），点击选中、再点取消 -->
    <tag-pills v-if="mode === 'card'" v-model="searchTag" module="PLACE" @update:model-value="loadData" />

    <!-- 卡片模式 -->
    <template v-if="mode === 'card'">
      <div v-loading="loading" class="card-grid">
        <el-empty v-if="!loading && !list.length && !loadError" description="暂无数据" />
        <div v-if="loadError" class="load-error">
          <span>加载失败，请稍后重试</span>
          <el-button size="small" round @click="loadData">重试</el-button>
        </div>
        <div v-for="(item, idx) in list" :key="item.itemId" class="place-card anim" :style="{ '--d': ((idx % 4) * 30) + 'ms' }" @click="openDetail(item)">
          <div class="card-cover" :style="coverStyle(idx)">
            <!-- ：封面优先用条目封面，无封面回退首张照片；加载失败兜底（隐藏 img 显示占位图标） -->
            <img v-if="cardCover(item) && !isCoverError(item)" :src="cardCover(item)" class="card-cover-img" :alt="item.title" loading="lazy" @error="onCoverError(item)" />
            <template v-else>
              <span class="card-glyph">地</span>
              <span class="card-type-pill">地点</span>
            </template>
            <span v-if="item.photoCount" class="photo-badge">📷 {{ item.photoCount }}</span>
          </div>
          <div class="card-body">
            <div class="card-title">{{ item.title }}</div>
            <div v-if="item.city || item.country" class="card-location">
              {{ [item.city, item.country].filter(Boolean).join(' · ') }}
            </div>
            <div v-if="item.bestSeason" class="card-season">{{ item.bestSeason }}</div>
            <div v-if="item.comment" class="card-comment">{{ item.comment }}</div>
          </div>
          <!-- ：整卡点击已开详情，操作区只留高频操作（去重「查看详情」按钮） -->
          <div class="card-actions" @click.stop>
            <el-button v-if="item.status === 'WANT' && isLogin" type="primary" size="small" round @click="openComplete(item)">
              标记去过
            </el-button>
            <el-button v-if="item.status === 'DONE' && isLogin" type="warning" size="small" round @click="handleUncomplete(item)">
              取消完成
            </el-button>
          </div>
        </div>
      </div>

      <div v-if="hasMore" class="load-more-wrap">
        <el-button :loading="loadingMore" round @click="loadMore">加载更多</el-button>
      </div>
    </template>

    <!-- 地图模式：高德地图 + 照片就近聚合 + 旅行时间线 -->
    <div v-else class="map-mode">
      <div v-loading="mapLoading" class="map-container">
        <div v-if="mapError" class="map-error">{{ mapError }}</div>
        <travel-map
          v-else
          :visited="trajectory?.visited || []"
          :wish="trajectory?.wish || []"
          @select="openDetailById"
        />
      </div>
      <!-- 旅行时间线：按到访年份串联去过的地方，点击回看详情 -->
      <div class="timeline">
        <div class="tl-title">旅行时间线 <span class="tl-sub">按到访年份 · 点击回看</span></div>
        <div class="tl-track">
          <button
            v-for="p in timelinePhotos"
            :key="p.itemId"
            class="tl-item"
            :aria-label="`回看 ${p.title}`"
            @click="openDetailById(p.itemId!)"
          >
            <img v-if="p.cover" class="thumb" :src="photoUrl(p.cover)" :alt="p.title" loading="lazy" />
            <span v-else class="thumb thumb-fallback" aria-hidden="true">📍</span>
            <span class="tl-name">{{ p.title }}</span>
            <span class="tl-date">{{ p.finishDate ? String(p.finishDate).slice(0, 10) : '—' }}</span>
          </button>
          <div v-if="!timelinePhotos.length" class="tl-empty">去过的地方会按年份串在这里</div>
        </div>
      </div>
    </div>

    <!-- 添加想去 -->
    <el-dialog v-model="addOpen" title="添加想去" width="560px" append-to-body>
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="90px">
        <el-form-item label="地点名称" prop="title">
          <!-- 支持名称搜索自动补全，选中直接回填地址/城市/国家与坐标 -->
          <place-search-input v-model="addForm.title" placeholder="输入名称搜索地点，或直接填写" @select="onPlaceSelect" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="addForm.address" placeholder="详细地址（可选）" maxlength="300" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="addForm.city" placeholder="城市（可选）" maxlength="100" />
        </el-form-item>
        <el-form-item label="国家">
          <el-input v-model="addForm.country" placeholder="国家（可选）" maxlength="100" />
        </el-form-item>
        <el-form-item label="经纬度">
          <div class="coord-row">
            <el-input-number
              v-model="addForm.latitude"
              :min="-90"
              :max="90"
              :precision="6"
              :controls="false"
              placeholder="纬度"
              style="width: 130px"
            />
            <el-input-number
              v-model="addForm.longitude"
              :min="-180"
              :max="180"
              :precision="6"
              :controls="false"
              placeholder="经度"
              style="width: 130px"
            />
            <el-button type="primary" plain size="small" @click="openMapPicker">
              🗺 地图选点
            </el-button>
          </div>
          <div class="coord-tip">填写经纬度或地图选点，填写后地点才会出现在旅行地图中</div>
        </el-form-item>
                  <el-form-item label="最佳季节">
            <el-select v-model="addForm.bestSeason" placeholder="选择季节（可选）" clearable style="width: 100%">
              <el-option v-for="s in sgj_best_season" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="addForm.placeCategory" placeholder="选择或输入分类（可选）" clearable filterable allow-create style="width: 100%">
              <el-option v-for="c in sgj_place_category" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签">
            <!-- 标签来自后台标签管理（PLACE 模块），禁止自由输入 -->
            <tag-select v-model="addForm.tags" module="PLACE" placeholder="选择标签（可选）" />
          </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitAdd">确 定</el-button>
        <el-button @click="addOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 地图选点 -->
    <map-picker v-model="pickerOpen" :latitude="addForm.latitude" :longitude="addForm.longitude" @confirm="confirmPick" />

    <!-- 标记去过 -->
    <el-dialog v-model="completeOpen" :title="'标记去过：' + (currentItem?.title || '')" width="520px" append-to-body>
      <el-form ref="completeFormRef" :model="completeForm" label-width="90px">
        <el-form-item label="去过日期">
          <el-date-picker v-model="completeForm.finishDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="回忆">
          <el-input v-model="completeForm.comment" type="textarea" :rows="3" placeholder="写点回忆吧（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitComplete">确 定</el-button>
        <el-button @click="completeOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉：照片 hero + 手账风信息卡 -->
    <el-drawer v-model="detailOpen" size="440px" :with-header="false" class="place-drawer">
      <div v-if="detail" class="drawer-inner">
        <!-- Hero：首图/封面铺底，标题与位置叠底 -->
        <div class="d-hero" :class="{ 'is-empty': !heroImage }">
          <img v-if="heroImage" class="d-hero-img" :src="heroImage" :alt="detail.title" @error="onHeroError" />
          <span v-else class="d-hero-glyph" aria-hidden="true">地</span>
          <div class="d-hero-mask" aria-hidden="true"></div>
          <span class="d-status" :class="detail.status">{{ detail.status === 'DONE' ? '去过' : '想去' }}</span>
          <button class="d-close" aria-label="关闭详情" @click="detailOpen = false">✕</button>
          <div class="d-hero-bottom">
            <h2>{{ detail.title }}</h2>
            <div class="d-meta-line">
              <span>📍 {{ locText }}</span>
              <span v-if="detail.finishDate">{{ String(detail.finishDate).slice(0, 10) }}</span>
            </div>
          </div>
        </div>

        <div class="d-body">
          <!-- 分类 / 最佳季节 -->
          <div v-if="detail.placeCategory || detail.bestSeason" class="d-chips">
            <span v-if="detail.placeCategory" class="chip">{{ selectDictLabel(sgj_place_category, detail.placeCategory) || detail.placeCategory }}</span>
            <span v-if="detail.bestSeason" class="chip chip-season">🍂 {{ selectDictLabel(sgj_best_season, detail.bestSeason) || detail.bestSeason }}</span>
          </div>

          <!-- 详细地址 -->
          <div v-if="detail.address" class="d-addr">
            <span class="ico" aria-hidden="true">📍</span>
            <span>{{ detail.address }}</span>
          </div>

          <!-- 我的回忆 -->
          <div v-if="detail.comment" class="d-memory">
            <div class="d-memory-label">MY MEMORIES · 我的回忆</div>
            <p>{{ detail.comment }}</p>
          </div>

          <!-- 地点照片：扇形相册入口，登录用户可在相册中上传/删除，访客只读浏览 -->
          <div class="detail-photos">
            <div class="detail-photos-head">
              <h3>照片</h3>
              <span v-if="photosArr.length" class="detail-photos-count">{{ photosArr.length }} 张</span>
              <el-button v-if="isLogin" link type="primary" class="detail-photos-add" @click="albumOpen = true">＋ 添加照片</el-button>
            </div>
            <photo-deck v-if="photosArr.length" :photos="photosArr" @open="albumOpen = true" />
            <div v-else class="album-empty">
              <div class="big">🖼</div>
              还没有照片<template v-if="isLogin"><br />点击「添加照片」放上第一张旅途照片</template>
            </div>
          </div>
          <photo-album-dialog
            v-model="albumOpen"
            :photos="photosArr"
            :title="detail?.title || ''"
            :can-manage="isLogin"
            @update:photos="onPhotosChange"
          />

          <item-notes :item-id="detail.itemId" />

          <!-- 经纬度（弱化展示，便于核对坐标） -->
          <div v-if="detail.latitude != null && detail.longitude != null" class="d-coords">
            经纬度 {{ detail.latitude }}, {{ detail.longitude }}
          </div>

          <div v-if="isLogin" class="d-actions">
            <el-button type="primary" round @click="openEditDetail">编辑</el-button>
            <el-button type="danger" plain round @click="handleDelete(detail)">删除</el-button>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 编辑条目 -->
    <item-edit-dialog
      v-model="editOpen"
      :item-id="editingId"
      @saved="handleEdited"
    />
  </div>
</template>

<script setup lang="ts" name="FrontTravel">
import { getToken } from '@/utils/auth'
import { useDict } from '@/utils/dict'
import ItemEditDialog from '@/components/ItemEditDialog/index.vue'
import ItemNotes from '@/components/ItemNotes/index.vue'
import MapPicker from '@/components/MapPicker/index.vue'
import PlaceSearchInput from '@/components/PlaceSearchInput/index.vue'
import TagSelect from '@/components/TagSelect/index.vue'
import TagPills from '@/components/TagPills/index.vue'
import TravelMap from '@/components/TravelMap/index.vue'
import PhotoDeck from '@/components/PhotoDeck/index.vue'
import PhotoAlbumDialog from '@/components/PhotoAlbumDialog/index.vue'
import type { PickedPlace, PlaceResult } from '@/utils/map'
import { listFrontItem, getFrontItem, addFrontItem, completeFrontItem, delFrontItem, uncompleteFrontItem, updateFrontItem } from '@/api/front/item'
import { getTravelTrajectory } from '@/api/front/travel'
import { selectDictLabel, photoUrl } from '@/utils/sgj'
import type { SgjItem } from '@/types/api/business/item'
import type { TravelPoint, TravelTrajectory } from '@/types/api/front/travel'

const { sgj_place_status, sgj_place_category, sgj_best_season } = useDict('sgj_place_status', 'sgj_place_category', 'sgj_best_season')

const { proxy } = getCurrentInstance() as { proxy: any }
const router = useRouter()
const route = useRoute()

/** 是否登录（访客只读，登录后可管理） */
const isLogin = computed(() => !!getToken())

const mode = ref<'card' | 'map'>('card')
/** 访客默认只看已完成内容 */
const activeStatus = ref<'WANT' | 'DONE'>(isLogin.value ? 'WANT' : 'DONE')
const searchTitle = ref('')
/** 标签筛选（选择即查询，仅卡片模式） */
const searchTag = ref('')
const list = ref<SgjItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const loadError = ref(false)
/** 分页 */
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const hasMore = computed(() => list.value.length < total.value)

const mapLoading = ref(false)
const mapError = ref('')
const trajectory = ref<TravelTrajectory | null>(null)
const albumOpen = ref(false)

const addOpen = ref(false)
const completeOpen = ref(false)
const detailOpen = ref(false)
const editOpen = ref(false)
const editingId = ref<number>()
const currentItem = ref<SgjItem | null>(null)
const detail = ref<SgjItem | null>(null)

const pickerOpen = ref(false)

const addForm = reactive({
  title: undefined as string | undefined,
  address: undefined as string | undefined,
  city: undefined as string | undefined,
  country: undefined as string | undefined,
  latitude: undefined as number | undefined,
  longitude: undefined as number | undefined,
  bestSeason: undefined as string | undefined,
  placeCategory: undefined as string | undefined,
  tags: undefined as string | undefined
})

/** ：封面加载失败兜底——记录失败的 itemId，模板隐藏 img 显示占位图标 */
const coverErrorIds = ref<Set<number>>(new Set())
function onCoverError(item: SgjItem): void {
  if (item.itemId != null) {
    coverErrorIds.value.add(item.itemId)
  }
}
function isCoverError(item: SgjItem): boolean {
  return item.itemId != null && coverErrorIds.value.has(item.itemId)
}

/** 状态胶囊切换 */
function switchStatus(status: 'WANT' | 'DONE'): void {
  if (activeStatus.value === status) return
  activeStatus.value = status
  loadData()
}

/** 模式胶囊切换（原 el-radio-group 逻辑） */
function switchMode(next: 'card' | 'map'): void {
  if (mode.value === next) return
  mode.value = next
  handleModeChange()
}

/** 卡片封面占位色块：按索引轮换灰陶浅/鼠尾草浅/灰铜浅/中性 */
function coverStyle(index: number): Record<string, string> {
  const soft = ['var(--sgj-primary-soft)', 'var(--sgj-moss-soft)', 'var(--sgj-amber-soft)', 'var(--sgj-cover)']
  const ink = ['var(--sgj-primary-dark)', 'var(--sgj-moss)', 'var(--sgj-amber)', 'var(--sgj-text-2)']
  const i = index % soft.length
  return { background: soft[i], color: ink[i] }
}

/** 读取主题 CSS 变量（供 ECharts 取当前亮/暗色） */
function themeColor(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#A85F52'
}

const completeForm = reactive({
  finishDate: undefined,
  comment: undefined
})

const addRules = {
  title: [{ required: true, message: '请输入地点名称', trigger: 'blur' }]
}

function loadData(): Promise<void> {
  loading.value = true
  loadError.value = false
  pageNum.value = 1
  return listFrontItem({
    itemType: 'PLACE',
    status: activeStatus.value,
    title: searchTitle.value || undefined,
    tags: searchTag.value || undefined,
    pageNum: pageNum.value,
    pageSize: pageSize
  }).then(response => {
    list.value = response.data || []
    total.value = (response as any).total || 0
  }).catch(() => {
    loadError.value = true
  }).finally(() => {
    loading.value = false
  })
}

/** 加载更多 */
function loadMore(): void {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  pageNum.value += 1
  listFrontItem({
    itemType: 'PLACE',
    status: activeStatus.value,
    title: searchTitle.value || undefined,
    tags: searchTag.value || undefined,
    pageNum: pageNum.value,
    pageSize: pageSize
  }).then(response => {
    list.value = list.value.concat(response.data || [])
    total.value = (response as any).total || 0
  }).catch(() => {
    pageNum.value -= 1
  }).finally(() => {
    loadingMore.value = false
  })
}

async function handleModeChange(): Promise<void> {
  if (mode.value === 'map' && !trajectory.value) {
    await loadMapData()
  }
}

async function loadMapData(): Promise<void> {
  mapLoading.value = true
  mapError.value = ''
  try {
    const response = await getTravelTrajectory()
    trajectory.value = response.data || null
  } catch {
    mapError.value = '地图数据加载失败，请稍后重试'
  } finally {
    mapLoading.value = false
  }
}

/** 横幅统计：去过城市数（去重）/ 照片总数 / 最近到访地点 */
const bannerStats = computed(() => {
  const visited = trajectory.value?.visited || []
  const total = [...visited, ...(trajectory.value?.wish || [])].reduce((s, p) => s + (p.photoCount || 0), 0)
  const cities = new Set(visited.map(p => p.city || p.title || '').filter(Boolean))
  // 轨迹接口已按到访时间升序排序，末位即最近
  const latest = visited.length ? visited[visited.length - 1] : null
  return { cities: cities.size, total, latest }
})

/** 旅行时间线数据（轨迹接口按到访时间升序） */
const timelinePhotos = computed<TravelPoint[]>(() => trajectory.value?.visited || [])

/** 详情相册照片数组（顺序即展示顺序） */
const photosArr = computed<string[]>(() => (detail.value?.photos ? detail.value.photos.split(',').filter(Boolean) : []))

/** 抽屉 hero 背景：首张照片 > 封面 > 列表首图，全缺时显示字形占位 */
const heroFailed = ref(false)
const heroImage = computed<string>(() => {
  if (heroFailed.value) return ''
  const d = detail.value
  if (!d) return ''
  if (photosArr.value.length) return photoUrl(photosArr.value[0])
  if (d.coverUrl) return d.coverUrl
  if (d.photoCover) return photoUrl(d.photoCover)
  return ''
})
watch(() => detail.value?.itemId, () => { heroFailed.value = false })
function onHeroError(): void {
  heroFailed.value = true
}

/** 位置串：城市/省州/国家去空拼接 */
const locText = computed(() => {
  const d = detail.value
  if (!d) return ''
  return [d.city, d.province, d.country].filter(Boolean).join(' · ') || '未定位'
})

/** 相册上传/删除后整体保存；失败时回读详情恢复一致 */
function onPhotosChange(next: string[]): void {
  if (!detail.value?.itemId) return
  const joined = next.join(',')
  updateFrontItem(detail.value.itemId, { photos: joined }).then(() => {
    if (detail.value) detail.value.photos = joined
  }).catch(() => {
    getFrontItem(detail.value!.itemId!).then(r => {
      detail.value = r.data || null
    }).catch(() => {})
  })
}

/** 卡片封面：条目封面优先，缺失时回退首张照片 */
function cardCover(item: SgjItem): string {
  if (item.coverUrl) return item.coverUrl
  return item.photoCover ? photoUrl(item.photoCover) : ''
}

function openAdd(): void {
  Object.assign(addForm, {
    title: undefined,
    address: undefined,
    city: undefined,
    country: undefined,
    latitude: undefined,
    longitude: undefined,
    bestSeason: undefined,
    placeCategory: undefined,
    tags: undefined
  })
  addOpen.value = true
}

function openMapPicker(): void {
  pickerOpen.value = true
}

/** 名称搜索选中地点：直接回填名称/地址/城市/国家与坐标，无需地图选点 */
function onPlaceSelect(place: PlaceResult): void {
  addForm.title = place.title
  addForm.address = place.address
  addForm.city = place.city
  addForm.country = place.country
  addForm.latitude = place.latitude
  addForm.longitude = place.longitude
}

/** 地图选点确认后回填：以新选地点为准覆盖；逆地理失败缺字段时保留原值 */
function confirmPick(place: PickedPlace): void {
  addForm.latitude = place.latitude
  addForm.longitude = place.longitude
  addForm.title = place.title || addForm.title
  addForm.address = place.address || addForm.address
  addForm.city = place.city || addForm.city
  addForm.country = place.country || addForm.country
}

function submitAdd(): void {
  proxy.$refs['addFormRef'].validate((valid: boolean) => {
    if (!valid) return
    const data = {
      ...addForm,
      itemType: 'PLACE',
      status: 'WANT'
    } as SgjItem
    addFrontItem(data).then(() => {
      addOpen.value = false
      proxy.$modal.msgSuccess('添加成功')
      // B-03：刷新列表后反查新条目，提供"去写笔记"入口
      loadData().then(() => {
        const created = list.value.find(it => it.title === data.title && it.itemType === data.itemType)
        if (created?.itemId) {
          goWriteNote(created.itemId, created.title || '')
        }
      })
    }).catch(() => {})
  })
}

/** B-03：提示并跳转笔记页快速新建笔记（携带关联条目参数） */
function goWriteNote(itemId: number, title: string): void {
  proxy.$modal.confirm(`是否立即为「${title}」写一篇笔记？`, '添加成功', {
    confirmButtonText: '去写笔记',
    cancelButtonText: '暂不'
  }).then(() => {
    router.push({ path: '/note', query: { itemId: String(itemId), title: title, write: '1' } })
  }).catch(() => {})
}

function openComplete(item: SgjItem): void {
  currentItem.value = item
  Object.assign(completeForm, {
    finishDate: undefined,
    comment: undefined
  })
  completeOpen.value = true
}

function submitComplete(): void {
  if (!currentItem.value?.itemId) return
  completeFrontItem(currentItem.value.itemId, completeForm).then(() => {
    proxy.$modal.msgSuccess('已标记去过')
    completeOpen.value = false
    loadData()
  }).catch(() => {})
}

function openDetail(item: SgjItem): void {
  if (!item.itemId) return
  getFrontItem(item.itemId).then(response => {
    detail.value = response.data || null
    detailOpen.value = true
  }).catch(() => {})
}

/** 照片张数（详情抽屉展示） */
const photoCount = computed(() => {
  const photos = detail.value?.photos
  return photos ? photos.split(',').filter(Boolean).length : 0
})

/**
 * 照片变更即保存：上传/删除/拖拽排序后 ImageUpload 都会抛出完整逗号串，
 * 后端按 item 整体替换照片表（空串表示清空）；非本人条目由后端 canOperate 拦截
 */
function savePhotos(photos: string): void {
  if (!detail.value?.itemId) return
  updateFrontItem(detail.value.itemId, { photos }).catch(() => {
    // 保存失败时回读详情，恢复展示与库一致（错误提示由拦截器统一弹出）
    getFrontItem(detail.value!.itemId!).then(r => {
      detail.value = r.data || null
    }).catch(() => {})
  })
}

/**
 *  配套：按 itemId 直接打开详情（首页时间线跳转 /travel?itemId= 时调用）
 */
function openDetailById(itemId: number): void {
  getFrontItem(itemId).then(response => {
    detail.value = response.data || null
    detailOpen.value = true
  }).catch(() => {})
}

/**
 * / 配套：读取路由参数
 * - ?itemId=：自动打开对应地点详情（首页时间线跳转）
 * - ?mode=map：直达地图模式（首页「旅行足迹地图」入口卡）
 */
function handleRouteQuery(): void {
  const rawId = route.query.itemId
  if (rawId) {
    const itemId = Number(rawId)
    if (itemId) {
      openDetailById(itemId)
    }
  }
  if (route.query.mode === 'map') {
    mode.value = 'map'
    loadMapData()
  }
  router.replace({ path: '/travel', query: {} })
}

/** 打开编辑（详情抽屉内） */
function openEditDetail(): void {
  if (!detail.value?.itemId) return
  editingId.value = detail.value.itemId
  editOpen.value = true
}

/** 编辑保存成功 */
function handleEdited(): void {
  loadData()
  if (detail.value?.itemId) {
    getFrontItem(detail.value.itemId).then(response => {
      detail.value = response.data || null
    }).catch(() => {})
  }
}

/** 取消完成（DONE -> WANT） */
function handleUncomplete(item: SgjItem): void {
  if (!item.itemId) return
  uncompleteFrontItem(item.itemId).then(() => {
    proxy.$modal.msgSuccess('已取消完成')
    loadData()
  }).catch(() => {})
}

/** 删除条目（二次确认） */
function handleDelete(item?: SgjItem): void {
  const itemId = item?.itemId
  if (!itemId) return
  proxy.$modal.confirm('是否确认删除该条目？删除后可在后台回收站恢复。').then(() => {
    return delFrontItem(itemId)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    detailOpen.value = false
    loadData()
  }).catch(() => {})
}

onMounted(() => {
  loadData()
  // 横幅统计与时间线常驻需要轨迹数据；地图组件由模式切换时挂载
  loadMapData()
  handleRouteQuery()
})
</script>

<style scoped lang="scss">
.travel-page {
  color: var(--sgj-text);
}

.load-more-wrap {
  display: flex;
  justify-content: center;
  margin: 20px 0;
}

.load-error {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 0;
  color: var(--sgj-text-4);
}

.page-banner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 56px;
  height: 176px;
  margin-bottom: 24px;
  border-radius: 24px;
  background: #282e2c;
  color: #e7ece9;
  overflow: hidden;

  .banner-blob {
    position: absolute;
    right: -40px;
    top: -120px;
    width: 300px;
    height: 300px;
    border-radius: 50%;
    background: rgba(168, 95, 82, 0.35);
    pointer-events: none;
  }

  .banner-blob2 {
    position: absolute;
    right: 140px;
    bottom: -90px;
    width: 200px;
    height: 200px;
    border-radius: 50%;
    background: rgba(93, 111, 102, 0.4);
    pointer-events: none;
  }

  .banner-text {
    position: relative;
    z-index: 1;

    h1 {
      margin: 0;
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 36px;
      line-height: 1.3;
    }

    p {
      margin: 12px 0 0;
      font-size: 14px;
      color: #aeb8b3;
      letter-spacing: 0.5px;
    }
  }

  .banner-add {
    position: relative;
    z-index: 1;
    height: 46px;
    padding: 0 28px;
    border-radius: 23px;
    font-size: 13px;
    font-weight: 500;
    letter-spacing: 1px;
  }

  .banner-stats {
    position: relative;
    z-index: 1;
    flex: 1;
    min-width: 240px;
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
    align-items: center;

    .stat-chip {
      padding: 7px 14px;
      border-radius: 999px;
      background: rgba(231, 236, 233, 0.1);
      border: 1px solid rgba(231, 236, 233, 0.22);
      font-size: 12.5px;
      color: #d8e0db;

      b {
        color: #f0c9a8;
        font-family: var(--sgj-font-serif);
        font-size: 15px;
        margin: 0 3px;
      }
    }
  }
}

html.dark .page-banner {
  background: var(--sgj-bg-deep);
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;

  .filter-group {
    display: flex;
    gap: 8px;
  }

  .filter-pill {
    padding: 8px 18px;
    border: 1px solid var(--sgj-border-card);
    border-radius: 17px;
    background: var(--sgj-bg-card);
    color: var(--sgj-text-2);
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.16s;

    &:hover {
      border-color: var(--sgj-primary);
      color: var(--sgj-primary);
    }

    &.active {
      background: var(--sgj-primary);
      border-color: var(--sgj-primary);
      color: #fff;
    }
  }

  .search-input {
    width: 220px;
    margin-left: auto;
  }
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.place-card {
  background: var(--sgj-bg-card);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(23, 27, 26, 0.06);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  display: flex;
  flex-direction: column;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(23, 27, 26, 0.12);
  }

  .card-cover {
    position: relative;
    aspect-ratio: 16 / 10;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;

    .card-cover-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .card-glyph {
      font-family: var(--sgj-font-serif);
      font-size: 44px;
      line-height: 1;
    }

    .card-type-pill {
      position: absolute;
      top: 12px;
      left: 12px;
      padding: 2px 10px;
      border-radius: 10px;
      background: var(--sgj-bg-card);
      color: var(--sgj-text-2);
      font-size: 11px;
    }

    .photo-badge {
      position: absolute;
      top: 12px;
      right: 12px;
      padding: 2px 10px;
      border-radius: 999px;
      background: rgba(40, 46, 44, 0.78);
      color: #f0ece2;
      font-size: 11px;
    }
  }

  .card-body {
    padding: 16px;
    flex: 1;

    .card-title {
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 16px;
      color: var(--sgj-text);
      margin-bottom: 4px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .card-location {
      font-size: 12px;
      color: var(--sgj-text-3);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .card-season {
      display: inline-block;
      margin-top: 8px;
      padding: 1px 10px;
      border-radius: 11px;
      background: var(--sgj-moss-soft);
      color: var(--sgj-moss);
      font-size: 11px;
    }

    .card-comment {
      font-size: 12px;
      color: var(--sgj-text-2);
      line-height: 1.5;
      margin-top: 8px;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
  }

  .card-actions {
    padding: 0 16px 16px;
    display: flex;
    gap: 8px;
  }
}

.map-mode {
  .map-container {
    min-height: 420px;
    position: relative;
    border-radius: 16px;
    overflow: hidden;

    .map-error {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--sgj-text-4);
      z-index: 180;
      background: var(--sgj-bg-card);
    }
  }
}

/* 旅行时间线：圆形照片珠按年份串在虚线上 */
.timeline {
  margin-top: 18px;

  .tl-title {
    font: 700 15px/1 var(--sgj-font-serif);
    color: var(--sgj-text-2);
    margin-bottom: 12px;

    .tl-sub {
      font: 400 12px/1 var(--sgj-font);
      color: var(--sgj-text-4);
      margin-left: 8px;
    }
  }

  .tl-track {
    position: relative;
    display: flex;
    gap: 26px;
    overflow-x: auto;
    padding: 6px 4px 14px;

    &::before {
      content: '';
      position: absolute;
      left: 30px;
      right: 30px;
      top: 34px;
      border-top: 2px dashed rgba(168, 95, 82, 0.35);
    }
  }

  .tl-item {
    position: relative;
    flex: none;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    width: 92px;
    padding: 0;
    background: none;
    border: 0;
    cursor: pointer;

    .thumb {
      width: 56px;
      height: 56px;
      border-radius: 50%;
      border: 3px solid #fff;
      box-shadow: 0 3px 10px rgba(23, 27, 26, 0.2);
      object-fit: cover;
      background: var(--sgj-bg-card);
      transition: transform 0.18s ease;
    }

    .thumb-fallback {
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
    }

    &:hover .thumb {
      transform: translateY(-3px) scale(1.05);
    }

    .tl-name {
      font-size: 12.5px;
      font-weight: 600;
      color: var(--sgj-text);
      max-width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .tl-date {
      font-size: 11px;
      color: var(--sgj-text-4);
      font-family: var(--sgj-font-serif);
    }
  }

  .tl-empty {
    color: var(--sgj-text-4);
    font-size: 13px;
    padding: 12px 0;
  }
}

.coord-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.coord-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--sgj-text-4);
}

/* 详情抽屉（hero + 手账信息卡）；容器由全局 .place-drawer 规则去内边距 */
.drawer-inner {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  text-align: left;
}

/* Hero：首图/封面铺底，标题叠底 */
.d-hero {
  position: relative;
  height: 216px;
  flex: none;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
  background: linear-gradient(160deg, var(--sgj-primary-soft), var(--sgj-cover));

  &.is-empty .d-hero-glyph {
    font-family: var(--sgj-font-serif);
    font-size: 96px;
    font-weight: 700;
    color: var(--sgj-primary-dark);
    opacity: 0.28;
  }
}

.d-hero-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.d-hero-glyph {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.d-hero-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(23, 27, 26, 0.08) 0%, rgba(23, 27, 26, 0) 35%, rgba(23, 27, 26, 0.66) 100%);
}

.d-status {
  position: absolute;
  top: 14px;
  left: 14px;
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  backdrop-filter: blur(4px);

  &.DONE {
    background: rgba(93, 111, 102, 0.92);
  }

  &.WANT {
    background: rgba(192, 138, 62, 0.92);
  }
}

.d-close {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 0;
  background: rgba(255, 255, 255, 0.88);
  color: var(--sgj-text);
  font-size: 14px;
  cursor: pointer;

  &:hover {
    background: #fff;
  }
}

.d-hero-bottom {
  position: relative;
  z-index: 1;
  width: 100%;
  padding: 0 18px 14px;

  h2 {
    margin: 0;
    font: 700 24px/1.3 var(--sgj-font-serif);
    color: #fff;
    text-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .d-meta-line {
    margin-top: 6px;
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
    font-size: 12.5px;
    color: rgba(255, 255, 255, 0.94);
  }
}

.d-body {
  padding: 16px 18px 26px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
}

.d-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;

  .chip {
    padding: 3px 12px;
    border-radius: 999px;
    background: var(--sgj-primary-soft);
    color: var(--sgj-primary-dark);
    font-size: 12px;
  }

  .chip-season {
    background: var(--sgj-moss-soft);
    color: var(--sgj-moss);
  }
}

.d-addr {
  display: flex;
  gap: 8px;
  font-size: 13px;
  color: var(--sgj-text-2);
  line-height: 1.7;

  .ico {
    flex: none;
  }
}

/* 我的回忆：手账引言卡 */
.d-memory {
  padding: 13px 16px 15px;
  background: var(--sgj-bg-card);
  border-radius: 4px 16px 16px 16px;
  border-left: 3px solid var(--sgj-primary);

  .d-memory-label {
    font-size: 10.5px;
    letter-spacing: 2px;
    color: var(--sgj-text-4);
    margin-bottom: 6px;
  }

  p {
    margin: 0;
    font-family: var(--sgj-font-serif);
    font-size: 13.5px;
    line-height: 1.8;
    color: var(--sgj-text);
  }
}

.d-coords {
  font-size: 11px;
  color: var(--sgj-text-4);
  letter-spacing: 0.5px;
}

.d-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: auto;
  padding-top: 8px;
}

/* 地点照片区：标题行与扇形相册入口 */
.detail-photos {
  text-align: left;

  .detail-photos-head {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin-bottom: 8px;

    h3 {
      color: var(--sgj-text-2);
      margin: 0;
    }

    .detail-photos-count {
      font-size: 12px;
      color: var(--sgj-text-4);
    }

    .detail-photos-add {
      margin-left: auto;
      font-size: 12px;
    }
  }

  .album-empty {
    margin-top: 4px;
    padding: 26px 16px;
    border: 1.5px dashed var(--sgj-border-card);
    border-radius: 14px;
    text-align: center;
    color: var(--sgj-text-4);
    font-size: 13px;
    line-height: 1.8;

    .big {
      font-size: 26px;
    }
  }
}

/* 移动端最小适配：工具栏换行、搜索框占满整行、卡片操作按钮可换行、地图高度收敛 */
@media (max-width: 768px) {
  .toolbar {
    flex-wrap: wrap;
    gap: 8px;

    .el-input {
      width: 100% !important;
    }
  }

  /*  移动端卡片栅格：minmax(160px,1fr) */
  .card-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 12px;
  }

  .card-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;

    .el-button + .el-button {
      margin-left: 0;
    }
  }

  /* 地图高度收敛由 TravelMap 组件内部处理 */
  .timeline {
    display: none;
  }

  .d-hero {
    height: 180px;
  }

  .d-actions {
    flex-wrap: wrap;
  }
}
</style>

<style lang="scss">
/* 详情抽屉容器（el-drawer 传送至 body，scoped 无法作用）：去内边距、限宽 */
.place-drawer {
  .el-drawer__body {
    padding: 0;
  }
}

</style>