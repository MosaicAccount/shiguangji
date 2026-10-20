<template>
  <div class="travel-page page-travel">
    <section class="hd-banner anim">
      <span class="hd-tape left"></span>
      <span class="hd-tape right amber"></span>
      <div>
        <div class="eyebrow">去过的远方</div>
        <h1>足迹</h1>
        <p class="desc">记录你去过和想去的地方。</p>
      </div>
      <div class="acts">
        <button v-if="isLogin" class="hd-btn go lg" type="button" @click="openAdd">＋ 添加想去</button>
      </div>
    </section>

    <div class="hd-summary">
      <span class="item">去过 <b>{{ bannerStats.cities }}</b> 城</span><span class="sep" aria-hidden="true">·</span>
      <span class="item">留下 <b>{{ bannerStats.total }}</b> 张照片</span><span class="sep" aria-hidden="true">·</span>
      <span class="item">想去 <b>{{ wishCount }}</b> 处</span>
      <span v-if="bannerStats.latest" class="recent">最近 <b>{{ bannerStats.latest.title }}</b></span>
    </div>

    <div class="hd-toolbar">
      <div v-if="isLogin" class="hd-chips" role="group" aria-label="状态">
        <button
          v-for="s in sgj_place_status"
          :key="s.value"
          class="hd-chip"
          :class="{ on: activeStatus === s.value }"
          type="button"
          @click="switchStatus(s.value)"
        >{{ s.label }}</button>
      </div>
      <div class="hd-search hd-input-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 Q18 18 21 21"/></svg>
        <input v-model="searchTitle" class="hd-input" type="search" placeholder="搜索地点" aria-label="搜索地点" @keyup.enter="loadData" />
      </div>
      <div class="right"><span class="hd-tiny hd-muted">共 {{ total }} 处</span></div>
    </div>

    <section class="hd-section map-sec" aria-labelledby="map-title">
      <div class="hd-section-head">
        <h2 class="hd-h2" id="map-title">旅行地图</h2>
        <span class="sub">去过的地点连起来，就是这一年的路线。</span>
      </div>

      <div class="map-layout">
        <figure class="hd-paper map-frame">
          <span class="hd-tape left"></span>
          <span class="hd-tape right"></span>
          <div v-loading="mapLoading" class="map-container">
            <div v-if="mapError" class="map-error">{{ mapError }}</div>
            <travel-map
              v-else
              :visited="trajectory?.visited || []"
              :wish="trajectory?.wish || []"
              :height="430"
              class="real-map"
              @select="openDetailById"
            />
          </div>
        </figure>

        <div class="tl-col">
          <h3 class="tl-h">旅行时间线 <span class="tl-h-sub">按到访年份 · 点击回看</span></h3>
          <div v-for="group in timelineByYear" :key="group.year" class="tl-year-group">
            <div class="tl-year">{{ group.year }}</div>
            <button
              v-for="p in group.items"
              :key="p.itemId"
              class="tl-row"
              type="button"
              :aria-label="`回看 ${p.title}`"
              @click="openDetailById(p.itemId!)"
            >
              <span class="tl-thumb">
                <img v-if="p.cover" :src="photoUrl(p.cover)" :alt="p.title" loading="lazy" />
                <span v-else class="hd-fallback place xs"><span class="fc-motif"></span></span>
              </span>
              <span class="tl-body">
                <span class="tl-name">{{ p.title }}</span>
                <span class="tl-date">{{ p.finishDate ? String(p.finishDate).slice(0, 10) : '—' }}</span>
              </span>
            </button>
          </div>
          <div v-if="!timelinePhotos.length" class="tl-empty">去过的地方会按年份串在这里</div>
        </div>
      </div>
    </section>

    <section class="hd-section" aria-labelledby="place-cards-title">
      <div class="hd-section-head">
        <h2 class="hd-h2" id="place-cards-title">地点卡片</h2>
        <span class="sub">地图看方位，卡片看细节；点地图上的点可以定位到下面这张卡。</span>
      </div>

      <tag-pills v-model="searchTag" module="PLACE" class="tag-filter" @update:model-value="loadData" />

      <div v-loading="loading" class="hd-grid place-grid">
        <div v-if="!loading && !list.length && !loadError" class="hd-empty">
          <p>还没有地点 —— 先把想去的那个地方记下来。</p>
          <div class="acts">
            <button v-if="isLogin" class="hd-btn go" type="button" @click="openAdd">＋ 添加想去</button>
          </div>
        </div>
        <div v-if="loadError" class="load-error">
          <span>加载失败，请稍后重试</span>
          <button class="hd-btn sm" type="button" @click="loadData">重试</button>
        </div>

        <article
          v-for="(item, idx) in list"
          :key="item.itemId"
          class="hd-card place-card hd-rough anim"
          :style="{ '--d': ((idx % 4) * 30) + 'ms' }"
          @click="openDetail(item)"
        >
          <span class="hd-tape"></span>
          <a class="hd-cover" href="#" @click.prevent="openDetail(item)">
            <img
              v-if="cardCover(item) && !isCoverError(item)"
              :src="cardCover(item)"
              :alt="item.title"
              loading="lazy"
              @error="onCoverError(item)"
            />
            <span v-else class="hd-fallback place">
              <span class="fc-band">地点</span>
              <span class="fc-body">
                <span class="fc-title">{{ item.title }}</span>
                <span class="fc-meta">{{ [item.city, item.country].filter(Boolean).join(' · ') }}</span>
              </span>
              <span class="fc-motif"></span>
            </span>
            <span v-if="item.photoCount" class="photo-badge">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><rect x="3" y="7" width="18" height="13" rx="2"/><circle cx="12" cy="13.5" r="3.6"/><path d="M8 7 L9.5 4.5 H14.5 L16 7"/></svg>
              {{ item.photoCount }}
            </span>
          </a>
          <div class="body">
            <h3 class="title">
              {{ item.title }}
              <span class="hd-st" :class="{ done: item.status === 'DONE' }">{{ statusLabel(item) }}</span>
            </h3>
            <div v-if="item.city || item.country" class="meta-row">
              <span>{{ [item.city, item.country].filter(Boolean).join(' · ') }}</span>
            </div>
            <div class="stars-row">
              <plan-date-badge :item="item" @saved="onPlanDateSaved" />
              <span v-if="item.bestSeason" class="hd-tag plain">最佳 {{ item.bestSeason }}</span>
            </div>
            <p v-if="item.comment" class="card-comment">{{ item.comment }}</p>
            <div class="card-acts" @click.stop>
              <button v-if="item.status === 'WANT' && isLogin" class="hd-btn sm go" type="button" @click="openComplete(item)">标记去过</button>
              <button v-if="item.status === 'DONE' && isLogin" class="hd-btn sm sub" type="button" @click="handleUncomplete(item)">取消完成</button>
            </div>
          </div>
        </article>
      </div>

      <div v-if="hasMore" class="load-more">
        <button class="hd-btn" type="button" :disabled="loadingMore" @click="loadMore">{{ loadingMore ? '加载中…' : '加载更多 ↓' }}</button>
        <span class="hd-tiny hd-muted">已展示 {{ list.length }} / 共 {{ total }} 处</span>
      </div>
      <div v-else-if="list.length" class="grid-end">—— 已到底部 · 共 {{ total }} 处 ——</div>
    </section>

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
          <el-form-item label="计划日期">
            <el-date-picker v-model="addForm.startDate" type="date" value-format="YYYY-MM-DD" placeholder="打算哪天去（可选）" :clearable="false" style="width: 100%" />
          </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitAdd">确 定</el-button>
        <el-button @click="addOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 地图选点 -->
    <map-picker v-model="pickerOpen" :latitude="addForm.latitude" :longitude="addForm.longitude" @confirm="confirmPick" />

    <!-- 标记去过（统一完成弹窗：地点无评分，label-width 90px 的差异由组件按类型给出） -->
    <item-complete-dialog v-model="completeOpen" :item="currentItem" @submitted="onCompleted" />

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
          <!-- 分类 / 最佳季节 / 计划日期 -->
          <div v-if="detail.placeCategory || detail.bestSeason || detail.startDate" class="d-chips">
            <span v-if="detail.placeCategory" class="chip">{{ selectDictLabel(sgj_place_category, detail.placeCategory) || detail.placeCategory }}</span>
            <span v-if="detail.bestSeason" class="chip chip-season">🍂 {{ selectDictLabel(sgj_best_season, detail.bestSeason) || detail.bestSeason }}</span>
            <span v-if="detail.startDate" class="chip chip-plan">📅 计划 {{ detail.startDate.slice(0, 10) }}</span>
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
import ItemCompleteDialog from '@/components/ItemCompleteDialog/index.vue'
import PlanDateBadge from '@/components/PlanDateBadge/index.vue'
import type { PickedPlace, PlaceResult } from '@/utils/map'
import { listFrontItem, getFrontItem, addFrontItem, delFrontItem, uncompleteFrontItem, updateFrontItem } from '@/api/front/item'
import { itemStatusLabel } from '@/utils/itemStatus'
import { getTravelTrajectory } from '@/api/front/travel'
import { selectDictLabel, photoUrl } from '@/utils/sgj'
import type { SgjItem, ItemStatus } from '@/types/api/business/item'
import type { TravelPoint, TravelTrajectory } from '@/types/api/front/travel'

const { sgj_place_status, sgj_place_category, sgj_best_season } = useDict('sgj_place_status', 'sgj_place_category', 'sgj_best_season')

const { proxy } = getCurrentInstance() as { proxy: any }
const router = useRouter()
const route = useRoute()

/** 是否登录（访客只读，登录后可管理） */
const isLogin = computed(() => !!getToken())

const mode = ref<'card' | 'map'>('card')
/** 访客默认只看已完成内容 */
const activeStatus = ref<ItemStatus>(isLogin.value ? 'WANT' : 'DONE')
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
  tags: undefined as string | undefined,
  /** 计划日期（0.0.3）：留空即纯心愿；地点不引入中间态，计划 = 想去 + 日期（D-5） */
  startDate: undefined as string | undefined
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

/** 状态胶囊切换（地点仅两态：字典未加 DOING，胶囊自动只有 想去/去过 两枚，D-5） */
function switchStatus(status: ItemStatus): void {
  if (activeStatus.value === status) return
  activeStatus.value = status
  loadData()
}

/** 计划日期改完刷新列表（update_time 排序会因更新而前置） */
function onPlanDateSaved(): void {
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

/** 想去地点数（小结条用） */
const wishCount = computed(() => (trajectory.value?.wish || []).length)

/** 时间线按到访年份倒序分组（同年按接口顺序） */
const timelineByYear = computed(() => {
  const groups: { year: string; items: TravelPoint[] }[] = []
  for (const p of timelinePhotos.value) {
    const year = p.finishDate ? String(p.finishDate).slice(0, 4) : '未标记年份'
    let group = groups.find(g => g.year === year)
    if (!group) { group = { year, items: [] }; groups.push(group) }
    group.items.push(p)
  }
  return groups.sort((a, b) => (a.year < b.year ? 1 : a.year > b.year ? -1 : 0))
})

/** 状态胶囊文案：字典优先，缺失时用类型兜底 */
function statusLabel(item: SgjItem): string {
  return itemStatusLabel(item, { sgj_place_status: sgj_place_status.value })
}

/** 详情相册照片数组（顺序即展示顺序） */
const photosArr = computed<string[]>(() => (detail.value?.photos ? detail.value.photos.split(',').filter(Boolean) : []))

/** 抽屉 hero 背景：首张照片 > 封面 > 列表首图，全缺时显示字形占位 */
const heroFailed = ref(false)
const heroImage = computed<string>(() => {
  if (heroFailed.value) return ''
  const d = detail.value
  if (!d) return ''
  if (photosArr.value.length) return photoUrl(photosArr.value[0])
  if (d.coverUrl) return photoUrl(d.coverUrl)
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
  if (item.coverUrl) return photoUrl(item.coverUrl)
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
  completeOpen.value = true
}

/** 统一完成弹窗提交成功：提示与刷新留在页面侧（文案与原页内实现逐字一致） */
function onCompleted(): void {
  proxy.$modal.msgSuccess('已标记去过')
  loadData()
  // 地图轨迹含“去过”分组，完成态变化后同步刷新
  trajectory.value = null
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
  // 0.0.3：跳转可带 status 预选胶囊（地点只有 想去/去过 两态，其余值忽略）；
  // onMounted 里 loadData() 先于本函数执行，胶囊变了要补一次 loadData
  const qStatus = route.query.status
  if ((qStatus === 'WANT' || qStatus === 'DONE') && activeStatus.value !== qStatus) {
    activeStatus.value = qStatus
    loadData()
  }

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
  if (rawId || qStatus || route.query.mode) {
    router.replace({ path: '/travel', query: {} })
  }
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
  loadMapData()
  // 横幅统计与时间线常驻需要轨迹数据；地图组件由模式切换时挂载
  loadMapData()
  handleRouteQuery()
})
</script>

<style scoped lang="scss">
/* 0.0.6 足迹：样式取自设计图 travel.html（.page-travel 作用域），形制来自 handdrawn-site.css */

/* ===== 足迹：地图是主角——纸框 + 抖线把它「裱」进手账，瓦片本身不手绘 ===== */
.page-travel .hd-banner .hd-tape.left { left: 15%; width: 92px }
.page-travel .hd-banner .hd-tape.right { left: 88% }
.page-travel .banner-main { min-width: 0 }
.page-travel .stat-chip { font-size: 13px; line-height: 1.95; padding: 0 10px; color: var(--hd-ink-soft); border-color: var(--hd-line-soft) }
.page-travel .stat-chip b { font-weight: 400; color: var(--hd-ink); font-size: 15px; padding: 0 2px }
.page-travel .hd-search { max-width: 280px }

/* 地图区：左边裱框地图，右边时间线 */
.page-travel .map-layout { display: grid; grid-template-columns: minmax(0, 1fr) 292px; gap: 34px; margin-top: 20px; align-items: start }
.page-travel .map-frame { margin: 0; padding: 15px 15px 12px; border-radius: 5px }
.page-travel .map-frame > .hd-tape.left { left: 15%; top: -11px; width: 92px }
.page-travel .map-frame > .hd-tape.right { left: 84%; top: -10px }
.page-travel .map-frame > .hd-tape-corner { top: -12px; right: -12px }

.page-travel .map-canvas-wrap { position: relative }
.page-travel .map-canvas {
  position: relative; aspect-ratio: 700 / 430; overflow: hidden;
  background: var(--hd-paper);
  border: 2px solid var(--hd-line);
  border-radius: 4px 2px 5px 2px / 2px 5px 2px 4px;
}
.page-travel .map-art { display: block; width: 100%; height: 100% }
.page-travel .map-art pattern circle { fill: var(--hd-ink-faint); opacity: .42 }
.page-travel .map-art .map-grid path { fill: none; stroke: var(--hd-line-soft); stroke-width: 1.2; stroke-dasharray: 3 9; opacity: .9 }
.page-travel .map-art .map-sea { fill: var(--hd-moss-soft); stroke: none }
.page-travel .map-art .map-coast { fill: none; stroke: var(--hd-line); stroke-width: 2.6; stroke-dasharray: 10 7; stroke-linecap: round }
.page-travel .map-art .map-island { fill: var(--hd-amber-soft); stroke: var(--hd-line); stroke-width: 2.4; stroke-linejoin: round }
.page-travel .map-art .map-isle path { fill: var(--hd-amber-soft); stroke: var(--hd-line); stroke-width: 2; stroke-linejoin: round }
.page-travel .map-art .map-river path { fill: none; stroke: var(--hd-olive); stroke-width: 2.4; stroke-dasharray: 2 8; stroke-linecap: round; opacity: .6 }
.page-travel .map-art .map-route path { fill: none; stroke: var(--hd-ochre); stroke-width: 2.6; stroke-dasharray: 8 7; stroke-linecap: round; opacity: .8 }
.page-travel .map-art .map-hill path { fill: none; stroke: var(--hd-ink-faint); stroke-width: 2; stroke-linecap: round; opacity: .6 }
.page-travel .map-art .map-wave path { fill: none; stroke: var(--hd-olive); stroke-width: 1.8; stroke-linecap: round; opacity: .5 }
.page-travel .map-rough {
  position: absolute; inset: -2px; pointer-events: none;
  border: 2.4px solid var(--hd-line);
  border-radius: 6px 3px 7px 3px / 3px 7px 3px 6px;
  filter: url(#rough);
}

/* 图钉：HTML 层，缩到 390 也不缩字 */
.page-travel .map-pin {
  position: absolute; z-index: 2; transform: translate(-50%, -50%);
  width: 28px; height: 28px; display: grid; place-items: center;
  font-size: 14px; line-height: 1; color: var(--hd-amber-ink);
  background: var(--hd-paper); border: 2px solid var(--hd-amber-ink);
  border-radius: 50% 46% 52% 48% / 48% 52% 46% 54%;
  box-shadow: 1px 2px 0 var(--hd-line-soft);
}
.page-travel .map-pin.wish { color: var(--hd-ink-soft); border-color: var(--hd-ink-soft); border-style: dashed; background: var(--hd-paper-2) }

.page-travel .map-legend {
  position: absolute; left: 10px; bottom: 10px; z-index: 3;
  display: flex; align-items: center; gap: 12px;
  padding: 2px 10px; font-size: 13px; color: var(--hd-ink-soft);
  background: var(--hd-paper); border: 1.6px solid var(--hd-line-soft);
  border-radius: 9px 3px 8px 3px / 3px 8px 3px 9px;
}
.page-travel .lg-dot { width: 12px; height: 12px; margin-right: 5px; display: inline-block; vertical-align: -1px;
  background: var(--hd-amber-ink); border: 1.8px solid var(--hd-amber-ink); border-radius: 50% 46% 52% 48% / 48% 52% 46% 54%; }
.page-travel .lg-dot.wish { background: var(--hd-paper-2); border: 1.8px dashed var(--hd-ink-soft) }

.page-travel .map-compass { position: absolute; right: 10px; bottom: 8px; z-index: 3; display: flex; flex-direction: column; align-items: center;
  color: var(--hd-ink-soft); background: var(--hd-paper); border-radius: 50%; padding: 3px; }
.page-travel .map-compass svg { width: 38px; height: 38px; display: block }
.page-travel .map-compass i { font-style: normal; font-size: 13px; line-height: 1; letter-spacing: 1px; margin-top: 1px }

.page-travel .map-index {
  margin-top: 11px; padding-top: 10px; border-top: 1.5px dashed var(--hd-line-soft);
  display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 3px 14px;
  font-size: 13px; color: var(--hd-ink-soft); line-height: 1.6;
}
.page-travel .map-index b { font-weight: 400; color: var(--hd-ink-faint); margin-right: 4px }

/* 旅行时间线 */
.page-travel .tl-col { min-width: 0 }
.page-travel .tl-h { font-size: 19px; letter-spacing: 1px; display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap }
.page-travel .tl-h-sub { font-size: 13px; color: var(--hd-ink-faint); letter-spacing: 1px }
.page-travel .tl-year { margin-top: 16px; display: flex; align-items: center; gap: 8px; font-size: 13px; letter-spacing: 2px; color: var(--hd-ink-faint) }
.page-travel .tl-year::after { content: ""; flex: 1; border-top: 1.5px dashed var(--hd-line-soft) }
.page-travel .tl-row { display: flex; align-items: center; gap: 12px; padding: 9px 0; text-decoration: none; color: var(--hd-ink); border-bottom: 1.5px dashed var(--hd-line-soft) }
.page-travel .tl-row:last-of-type { border-bottom: 0 }
.page-travel .tl-row:hover .tl-name { color: var(--hd-red) }
.page-travel .tl-thumb { flex: none; width: 52px; height: 52px; aspect-ratio: auto; box-shadow: none }
.page-travel .tl-txt { min-width: 0; display: flex; flex-direction: column }
.page-travel .tl-name { font-size: 15px; line-height: 1.45; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.page-travel .tl-date { font-size: 13px; color: var(--hd-ink-soft) }

/* 地点卡片（地图下方的同一批数据） */
.page-travel .place-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); margin-top: 22px }
.page-travel .place-card {
  display: flex; flex-direction: column; min-width: 0;
  background-color: var(--hd-paper); background-image: var(--hd-grain);
  border-radius: 6px; box-shadow: var(--hd-shadow);
  padding: 10px 10px 14px;
  transition: transform .14s, box-shadow .14s;
}
.page-travel .place-card:hover { transform: translateY(-3px) rotate(0); box-shadow: var(--hd-shadow-lift) }
.page-travel .place-card > .hd-tape { z-index: 3; top: -8px; width: 64px; height: 18px }
.page-travel .place-card:nth-child(even) > .hd-tape { left: 32%; transform: translateX(-50%) rotate(2.6deg) }
.page-travel .place-card:nth-child(3n) > .hd-tape { left: 68%; transform: translateX(-50%) rotate(-1.2deg) }
.page-travel .place-card .hd-cover { aspect-ratio: 16 / 10; z-index: 1 }
.page-travel .place-card .hd-cover .hd-fallback { transition: transform .25s }
.page-travel .place-card:hover .hd-fallback { transform: scale(1.03) }
.page-travel .place-card .hd-fallback { padding: 10px 11px 11px }
.page-travel .place-card .hd-fallback .fc-band { font-size: 13px; line-height: 1.4; letter-spacing: 1px }
.page-travel .place-card .hd-fallback .fc-title { font-size: 16px; line-height: 1.3; -webkit-line-clamp: 2 }
.page-travel .place-card .hd-fallback .fc-meta { font-size: 13px; margin-top: 2px; opacity: .84 }
.page-travel .map-note { margin-top: 14px; font-size: 13px; line-height: 1.7; color: var(--hd-ink-soft) }
.page-travel .photo-num {
  position: absolute; right: 8px; top: 8px; z-index: 2;
  display: inline-flex; align-items: center; gap: 4px;
  padding: 1px 8px 1px 6px; font-size: 13px; color: var(--hd-ink);
  background: var(--hd-paper); border: 1.6px solid var(--hd-line);
  border-radius: 9px 3px 8px 3px / 3px 8px 3px 9px;
}
.page-travel .photo-num svg { width: 14px; height: 14px; color: var(--hd-amber-ink) }
.page-travel .photo-num b { font-weight: 400 }
.page-travel .place-card .body { display: flex; flex-direction: column; flex: 1; padding: 12px 2px 0 }
.page-travel .place-card .title { font-size: 18.5px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap }
.page-travel .place-card .title .hd-st { margin-left: auto; flex: none; font-size: 13px }
.page-travel .meta-row { margin-top: 4px; font-size: 13.5px; color: var(--hd-ink-soft); display: flex; flex-wrap: wrap; align-items: center; gap: 4px 7px }
.page-travel .meta-row .sep { color: var(--hd-ink-faint) }
.page-travel .season-row { margin-top: 7px; display: flex; flex-wrap: wrap; gap: 6px }
.page-travel .card-comment {
  margin-top: 8px; font-size: 14px; line-height: 1.7; color: var(--hd-ink-soft);
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
  min-height: 3.4em;
}
.page-travel .card-acts { margin-top: auto; padding-top: 12px; display: flex; gap: 8px; flex-wrap: wrap }

@media (max-width: 960px) {
  .page-travel .map-layout { grid-template-columns: minmax(0, 1fr); gap: 26px }
  .page-travel .place-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 26px 18px }
  .page-travel .map-index { grid-template-columns: repeat(2, minmax(0, 1fr)) }
}
@media (max-width: 720px) {
  .page-travel .map-frame { padding: 11px 11px 10px }
  .page-travel .map-pin { width: 26px; height: 26px; font-size: 13px }
  .page-travel .map-legend { left: 8px; bottom: 8px; gap: 9px; padding: 1px 8px }
  .page-travel .map-compass { right: 8px; bottom: 6px }
  .page-travel .map-compass svg { width: 32px; height: 32px }
  .page-travel .place-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 22px 14px; margin-top: 18px }
  .page-travel .place-card { padding: 8px 8px 12px }
  .page-travel .place-card .title { font-size: 16.5px }
  .page-travel .card-comment { font-size: 13.5px; min-height: 3.3em; -webkit-line-clamp: 3 }
  .page-travel .card-acts .hd-btn { flex: 1 1 100% }
  .page-travel .hd-banner .hd-tape.left { left: 22% }
  .page-travel .hd-banner .hd-tape.right { left: 78% }
}

/* 产品补充：真实地图组件、卡片封面、时间线列、加载态 */
.page-travel .map-frame { position: relative; padding: 10px; overflow: hidden }
.page-travel .map-frame .map-container { position: relative; height: 430px; border-radius: 3px; overflow: hidden }
.page-travel .map-frame .map-error { display: grid; place-items: center; height: 430px; color: var(--hd-ink-soft) }
.page-travel .real-map { display: block; width: 100% }
.page-travel .place-card .hd-cover img { width: 100%; height: 100%; object-fit: cover; display: block }
.page-travel .tag-filter { margin-top: 16px }
.page-travel .grid-end { margin-top: 34px; text-align: center; font-size: 13.5px; color: var(--hd-ink-soft) }
.page-travel .tl-year-group { margin-top: 10px }
.page-travel .tl-year { margin-top: 14px }
.page-travel .tl-row { width: 100%; border: 0; background: none; text-align: left; cursor: pointer }
.page-travel .tl-thumb { flex: none; width: 46px; height: 58px; overflow: hidden; border: 1.6px solid var(--hd-line); border-radius: 3px 1px 4px 2px; position: relative; display: block }
.page-travel .tl-thumb img { width: 100%; height: 100%; object-fit: cover; display: block }
.page-travel .tl-body { min-width: 0; display: flex; flex-direction: column; gap: 2px }
.page-travel .tl-name { font-size: 15px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.page-travel .tl-date { font-size: 13px; color: var(--hd-ink-soft) }
.page-travel .load-error { grid-column: 1 / -1; display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 48px 0; color: var(--hd-ink-soft) }
.page-travel .hd-empty { grid-column: 1 / -1 }

</style>
