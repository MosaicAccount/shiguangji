<template>
  <div class="movie-page page-movie">
    <section class="hd-banner anim">
      <span class="hd-tape left"></span>
      <span class="hd-tape right amber"></span>
      <div>
        <div class="eyebrow">光影收藏</div>
        <h1>影单</h1>
        <p class="desc">慢慢看，认真记——你知道看过些什么。</p>
      </div>
      <div class="acts">
        <button v-if="isLogin" class="hd-btn go lg" type="button" @click="openAdd">＋ 添加观影</button>
      </div>
    </section>

    <div class="hd-summary">
      <span class="item">看过 <b>{{ doneCount }}</b> 部</span><span class="sep" aria-hidden="true">·</span>
      <span class="item">在看 <b>{{ doingCount }}</b> 部</span><span class="sep" aria-hidden="true">·</span>
      <span class="item">想看 <b>{{ wishCount }}</b> 部</span>
      <span class="recent">最近 <b>{{ recentTitle || '—' }}</b></span>
    </div>

    <div class="hd-toolbar">
      <div class="hd-chips" role="group" aria-label="类型">
        <button
          v-for="t in movieTypes"
          :key="t.value"
          class="hd-chip"
          :class="{ on: activeType === t.value }"
          type="button"
          @click="switchType(t.value as 'MOVIE' | 'TV')"
        >{{ t.label }}</button>
      </div>
      <div v-if="isLogin" class="hd-chips" role="group" aria-label="状态">
        <button
          v-for="s in sgj_movie_status"
          :key="s.value"
          class="hd-chip"
          :class="{ on: activeStatus === s.value }"
          type="button"
          @click="switchStatus(s.value)"
        >{{ s.label }}</button>
      </div>
      <div class="hd-search hd-input-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 Q18 18 21 21"/></svg>
        <input
          v-model="searchTitle"
          class="hd-input"
          type="search"
          placeholder="搜索标题"
          aria-label="搜索标题"
          @keyup.enter="loadData"
        />
      </div>
      <div class="right"><span class="hd-tiny hd-muted">共 {{ total }} 部</span></div>
    </div>

    <tag-pills v-model="searchTag" :module="activeType" class="tag-filter" @update:model-value="loadData" />

    <div v-loading="loading" class="hd-grid movie-grid">
      <div v-if="!loading && !list.length && !loadError" class="hd-empty">
        <p>暂无数据 —— 先把想看的那部记下来。</p>
        <div class="acts">
          <button v-if="isLogin" class="hd-btn go" type="button" @click="openAdd">＋ 添加观影</button>
        </div>
      </div>
      <div v-if="loadError" class="load-error">
        <span>加载失败，请稍后重试</span>
        <button class="hd-btn sm" type="button" @click="loadData">重试</button>
      </div>

      <article
        v-for="(item, idx) in list"
        :key="item.itemId"
        class="hd-card movie-card hd-rough anim"
        :style="{ '--d': ((idx % 4) * 30) + 'ms' }"
        @click="openDetail(item)"
      >
        <span class="hd-tape"></span>
        <a class="hd-cover" href="#" @click.prevent="openDetail(item)">
          <template v-if="item.coverUrl && !isCoverError(item)">
            <img
            :src="photoUrl(item.coverUrl)"
            :alt="item.title"
            loading="lazy"
            @error="onCoverError(item)"
          />
            <span class="cover-shade" aria-hidden="true"></span>
            <span class="cover-txt">
              <span class="cover-title">{{ item.title }}</span>
              <span class="cover-meta">{{ cardMeta(item).replace(/^ · /, '') }}</span>
            </span>
          </template>
          <span v-else class="hd-fallback" :class="activeType === 'MOVIE' ? 'movie' : 'tv'">
            <span class="fc-band">{{ activeType === 'MOVIE' ? '电影' : '电视剧' }}</span>
            <span class="fc-body">
              <span class="fc-title">{{ item.title }}</span>
              <span class="fc-meta">{{ cardMeta(item).replace(/^ · /, '') }}</span>
            </span>
            <span class="fc-motif"></span>
          </span>
        </a>
        <div class="body">
          <h3 class="title">
            <span class="t-title">{{ item.title }}</span>
            <span class="hd-st" :class="{ done: item.status === 'DONE', doing: item.status === 'DOING' }">{{ statusLabel(item) }}</span>
          </h3>
          <div class="meta-row"><span>{{ cardMeta(item).replace(/^ · /, '') }}</span></div>
          <div class="stars-row">
            <plan-date-badge :item="item" @saved="loadData" />
            <span v-if="item.rating" class="hd-stars" :aria-label="`评分 ${item.rating} 分，满分 10 分`">{{ starText(item.rating) }}</span>
            <span v-else class="no-rating">未评分</span>
          </div>
          <p v-if="item.comment" class="card-comment">{{ item.comment }}</p>
          <div class="card-acts" @click.stop>
            <button v-if="item.status === 'WANT' && isLogin" class="hd-btn sm" type="button" @click="startDoing(item)">开始看 →</button>
            <button v-if="item.status !== 'DONE' && isLogin" class="hd-btn sm go" type="button" @click="openComplete(item)">标记看过</button>
            <button v-if="item.status === 'DONE' && isLogin" class="hd-btn sm sub" type="button" @click="handleUncomplete(item)">取消完成</button>
          </div>
        </div>
      </article>
    </div>

    <div v-if="hasMore" class="load-more">
      <button class="hd-btn" type="button" :disabled="loadingMore" @click="loadMore">{{ loadingMore ? '加载中…' : '加载更多 ↓' }}</button>
      <span class="hd-tiny hd-muted">已展示 {{ list.length }} / 共 {{ total }} 部</span>
    </div>
    <div v-else-if="list.length" class="grid-end">—— 已到底部 · 共 {{ total }} 部 ——</div>

    <!-- 添加想看 -->
    <el-dialog v-model="addOpen" title="添加想看" width="520px" append-to-body>
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="addForm.title" placeholder="请输入影视名称" maxlength="200" />
        </el-form-item>
        <el-form-item label="导演" prop="director">
          <el-input v-model="addForm.director" placeholder="导演（可选）" maxlength="200" />
        </el-form-item>
        <el-form-item label="主演" prop="actors">
          <el-input v-model="addForm.actors" placeholder="主演（可选）" maxlength="500" />
        </el-form-item>
        <el-form-item label="年份" prop="releaseYear" v-if="activeType === 'MOVIE'">
          <el-input-number v-model="addForm.releaseYear" :min="1888" :max="2100" :controls="false" placeholder="上映年份" style="width: 100%" />
        </el-form-item>
        <template v-else>
          <el-form-item label="开播年份" prop="startYear">
            <el-input-number v-model="addForm.startYear" :min="1888" :max="2100" :controls="false" placeholder="开播年份" style="width: 100%" />
          </el-form-item>
          <el-form-item label="完结年份" prop="endYear">
            <el-input-number v-model="addForm.endYear" :min="1888" :max="2100" :controls="false" placeholder="完结年份（连载中可留空）" style="width: 100%" />
          </el-form-item>
          <el-form-item label="季数" prop="seasonCount">
            <el-input-number v-model="addForm.seasonCount" :min="1" :max="100" :controls="false" placeholder="季数" style="width: 100%" />
          </el-form-item>
          <el-form-item label="总集数" prop="episodeCount">
            <el-input-number v-model="addForm.episodeCount" :min="1" :max="10000" :controls="false" placeholder="总集数" style="width: 100%" />
          </el-form-item>
        </template>
        <el-form-item label="类型" prop="genre">
          <el-select v-model="addForm.genre" placeholder="选择或输入题材（可选）" clearable filterable allow-create style="width: 100%">
            <el-option v-for="g in sgj_movie_genre" :key="g.value" :label="g.label" :value="g.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <!-- 标签来自后台标签管理，按条目类型区分，禁止自由输入 -->
          <tag-select v-model="addForm.tags" :module="activeType" placeholder="选择标签（可选）" />
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="addForm.startDate" type="date" value-format="YYYY-MM-DD" placeholder="打算哪天看（可选）" :clearable="false" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitAdd">确 定</el-button>
        <el-button @click="addOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 标记看过（统一完成弹窗，文案与字段随类型参数化） -->
    <item-complete-dialog v-model="completeOpen" :item="currentItem" @submitted="onCompleted" />

    <!-- 详情抽屉：封面铺底 hero + 手账风信息卡（与足迹页同风格） -->
    <media-detail-drawer
      v-model="detailOpen"
      :item="detail"
      :glyph="activeType === 'MOVIE' ? '影' : '剧'"
      done-label="看过"
      want-label="想看"
      doing-label="在看"
      :hero-meta="heroMeta"
      memory-label="MY THOUGHTS · 我的短评"
      :can-operate="isLogin"
      @edit="openEditDetail"
      @delete="handleDelete(detail!)"
    >
      <div v-if="chipList.length" class="md-chips">
        <span v-for="chip in chipList" :key="chip" class="md-chip">{{ chip }}</span>
      </div>
      <div v-if="infoRows.length" class="md-rows">
        <div v-for="row in infoRows" :key="row.label" class="md-row">
          <span class="md-ico" aria-hidden="true">{{ row.ico }}</span>
          <span class="md-label">{{ row.label }}</span>
          <span>{{ row.value }}</span>
        </div>
      </div>
    </media-detail-drawer>

    <!-- 编辑条目 -->
    <item-edit-dialog
      v-model="editOpen"
      :item-id="editingId"
      @saved="handleEdited"
    />
  </div>
</template>

<script setup lang="ts" name="FrontMovie">
import { getToken } from '@/utils/auth'
import { selectDictLabel, photoUrl } from '@/utils/sgj'
import { useDict } from '@/utils/dict'
import ItemEditDialog from '@/components/ItemEditDialog/index.vue'
import ItemCompleteDialog from '@/components/ItemCompleteDialog/index.vue'
import PlanDateBadge from '@/components/PlanDateBadge/index.vue'
import MediaDetailDrawer from '@/components/MediaDetailDrawer/index.vue'
import TagSelect from '@/components/TagSelect/index.vue'
import TagPills from '@/components/TagPills/index.vue'
import { listFrontItem, getFrontItem, addFrontItem, updateFrontItem, delFrontItem, uncompleteFrontItem } from '@/api/front/item'
import { getFrontHomeData } from '@/api/front/home'
import { itemStatusLabel } from '@/utils/itemStatus'
import { starText } from '@/utils/rating'
import type { FrontHomeSummary } from '@/types/api/front/home'
import type { SgjItem, ItemStatus } from '@/types/api/business/item'
import { planDateLabel, localDateKey } from '@/utils/plan'

const { sgj_item_type, sgj_movie_status, sgj_movie_genre, sgj_region, sgj_language } = useDict('sgj_item_type', 'sgj_movie_status', 'sgj_movie_genre', 'sgj_region', 'sgj_language')

const { proxy } = getCurrentInstance() as { proxy: any }
const router = useRouter()
const route = useRoute()

/** 是否登录（访客只读，登录后可管理） */
const isLogin = computed(() => !!getToken())

/** 影视页类型选项：仅取字典中的电影/电视剧，排除书籍/地点 */
const movieTypes = computed(() => (sgj_item_type.value || []).filter(t => t.value === 'MOVIE' || t.value === 'TV'))

const activeType = ref<'MOVIE' | 'TV'>('MOVIE')
/** 访客默认只看已完成内容；登录后含三态胶囊（字典驱动，0.0.3 起多一枚「在看」） */
const activeStatus = ref<ItemStatus>(isLogin.value ? 'WANT' : 'DONE')
const searchTitle = ref('')
/** 标签筛选（选择即查询；切换类型时由 switchType 清空并随列表刷新） */
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

/** 页头小结条：取首页汇总的按类型口径（看过 / 在看 / 想看），不另造接口 */
const homeSummary = ref<FrontHomeSummary | null>(null)
const recentTitle = ref('')
const doneCount = computed(() => (homeSummary.value?.done?.MOVIE || 0) + (homeSummary.value?.done?.TV || 0))
const doingCount = computed(() => (homeSummary.value?.doing?.MOVIE || 0) + (homeSummary.value?.doing?.TV || 0))
const wishCount = computed(() => (homeSummary.value?.wish?.MOVIE || 0) + (homeSummary.value?.wish?.TV || 0))

function loadSummary(): void {
  getFrontHomeData().then(response => {
    homeSummary.value = response.data?.summary || null
    recentTitle.value = response.data?.timeline?.[0]?.title || ''
  }).catch(() => {
    // 小结条失败不阻塞列表
  })
}

/** 状态胶囊文案：字典优先，缺失时用类型兜底（itemStatusLabel） */
function statusLabel(item: SgjItem): string {
  return itemStatusLabel(item, { sgj_movie_status: sgj_movie_status.value })
}

const addOpen = ref(false)
const completeOpen = ref(false)
const detailOpen = ref(false)
const editOpen = ref(false)
const editingId = ref<number>()
const currentItem = ref<SgjItem | null>(null)
const detail = ref<SgjItem | null>(null)

const addForm = reactive({
  title: undefined,
  director: undefined,
  actors: undefined,
  releaseYear: undefined,
  startYear: undefined,
  endYear: undefined,
  seasonCount: undefined,
  episodeCount: undefined,
  genre: undefined,
  tags: undefined as string | undefined,
  /** 计划日期（0.0.3）：留空即纯心愿 */
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

const addRules = {
  title: [{ required: true, message: '请输入影视名称', trigger: 'blur' }]
}

function loadData(): Promise<void> {
  loading.value = true
  loadError.value = false
  pageNum.value = 1
  return listFrontItem({
    itemType: activeType.value,
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
    itemType: activeType.value,
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

function handleTypeChange(): void {
  // 访客只浏览已完成内容，登录用户切换类型回到"想看"；标签跟随模块切换需重选
  activeStatus.value = isLogin.value ? 'WANT' : 'DONE'
  searchTag.value = ''
  loadData()
}

/** 类型胶囊切换（设计稿 pill） */
function switchType(type: 'MOVIE' | 'TV'): void {
  if (activeType.value === type) return
  activeType.value = type
  handleTypeChange()
}

/** 状态胶囊切换（字典驱动，三枚：想看 / 在看 / 看过） */
function switchStatus(status: ItemStatus): void {
  if (activeStatus.value === status) return
  activeStatus.value = status
  loadData()
}

/** WANT → DOING：开始看（0.0.3 状态流转复用 PUT /app/item/{id}，不新增端点） */
function startDoing(item: SgjItem): void {
  if (!item.itemId) return
  updateFrontItem(item.itemId, { status: 'DOING' }).then(() => {
    proxy.$modal.msgSuccess(`开始看：${item.title || ''}`)
    loadData()
  }).catch(() => {})
}

/** 封面占位衬线字 */
const coverGlyph = computed(() => (activeType.value === 'MOVIE' ? '影' : '剧'))

/** 详情抽屉 hero meta：评分之外追加年份与计划日期 */
const heroMeta = computed<string[]>(() => {
  const d = detail.value
  if (!d) return []
  const meta: string[] = []
  if (d.startDate) {
    meta.push(`计划 ${planDateLabel(d.startDate.slice(0, 10), localDateKey(new Date()))}`)
  }
  if (activeType.value === 'MOVIE') {
    if (d.releaseYear) meta.push(`${d.releaseYear} 年上映`)
  } else {
    const span = [d.startYear, d.endYear].filter(Boolean).join('-')
    if (span) meta.push(`${span} 年`)
  }
  return meta
})

/** 详情抽屉分类 chips：类型/地区/语言（script 内调用需手动解包字典 ref） */
const chipList = computed<string[]>(() => {
  const d = detail.value
  if (!d) return []
  return [selectDictLabel(sgj_movie_genre.value, d.genre), selectDictLabel(sgj_region.value, d.region), selectDictLabel(sgj_language.value, d.language)]
    .filter((v): v is string => !!v)
})

/** 详情抽屉信息行：导演/主演与剧集信息 */
const infoRows = computed<{ ico: string; label: string; value: string }[]>(() => {
  const d = detail.value
  if (!d) return []
  const rows: { ico: string; label: string; value: string }[] = []
  if (d.director) rows.push({ ico: '🎬', label: '导演', value: d.director })
  if (d.actors) rows.push({ ico: '👥', label: '主演', value: d.actors })
  if (activeType.value === 'MOVIE') {
    if (d.releaseYear) rows.push({ ico: '📅', label: '上映年份', value: String(d.releaseYear) })
  } else {
    if (d.startYear) rows.push({ ico: '📅', label: '开播年份', value: String(d.startYear) })
    if (d.endYear) rows.push({ ico: '🏁', label: '完结年份', value: String(d.endYear) })
    if (d.seasonCount) rows.push({ ico: '📚', label: '季数', value: d.seasonCount + ' 季' })
    if (d.episodeCount) rows.push({ ico: '🎞', label: '总集数', value: d.episodeCount + ' 集' })
  }
  return rows
})

/** 卡片封面占位色块：按索引轮换灰陶浅/鼠尾草浅/灰铜浅/中性 */
function coverStyle(index: number): Record<string, string> {
  const soft = ['var(--sgj-primary-soft)', 'var(--sgj-moss-soft)', 'var(--sgj-amber-soft)', 'var(--sgj-cover)']
  const ink = ['var(--sgj-primary-dark)', 'var(--sgj-moss)', 'var(--sgj-amber)', 'var(--sgj-text-2)']
  const i = index % soft.length
  return { background: soft[i], color: ink[i] }
}

/** 卡片 meta 行：年份/导演 兜底 */
function cardMeta(item: SgjItem): string {
  if (activeType.value === 'MOVIE') {
    return item.director ? `${item.director} · ${item.releaseYear || ''}`.replace(/ · $/, '') : (item.releaseYear ? String(item.releaseYear) : '')
  }
  const parts = [item.startYear, item.endYear].filter(Boolean).join('-') || '连载' + (item.seasonCount ? ` · ${item.seasonCount}季` : '')
  return parts
}

function openAdd(): void {
  Object.assign(addForm, {
    title: undefined,
    director: undefined,
    actors: undefined,
    releaseYear: undefined,
    startYear: undefined,
    endYear: undefined,
    seasonCount: undefined,
    episodeCount: undefined,
    genre: undefined,
    tags: undefined
  })
  addOpen.value = true
}

function submitAdd(): void {
  proxy.$refs['addFormRef'].validate((valid: boolean) => {
    if (!valid) return
    const data = {
      ...addForm,
      itemType: activeType.value,
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
  proxy.$modal.msgSuccess('已标记看过')
  loadData()
}

function openDetail(item: SgjItem): void {
  if (!item.itemId) return
  getFrontItem(item.itemId).then(response => {
    detail.value = response.data || null
    detailOpen.value = true
  }).catch(() => {})
}

/**
 *  配套：按 itemId 直接打开详情（首页时间线跳转 /movie?itemId= 时调用）
 */
function openDetailById(itemId: number): void {
  getFrontItem(itemId).then(response => {
    detail.value = response.data || null
    detailOpen.value = true
  }).catch(() => {})
}

/**
 *  配套：读取路由 ?itemId=，存在则自动打开对应条目详情
 * （首页时间线最小方案：跳转到对应类型列表页并携带 itemId 自动开详情）
 */
/**
 *  配套：读取路由 ?itemId=，存在则自动打开对应条目详情（计划页 / 首页时间线跳转）
 *
 * 0.0.3：跳转可带 type / status 预选胶囊——不带的话抽屉开了、列表里却看不到它
 * （影视页默认「电影 + 想看」，电视剧或「在看」的条目落在别的胶囊下）。
 * 注意 setup 里 loadData() 先于本函数执行，胶囊变了要补一次 loadData。
 */
function handleRouteQuery(): void {
  const qType = route.query.type
  const qStatus = route.query.status
  let filterChanged = false
  if ((qType === 'MOVIE' || qType === 'TV') && activeType.value !== qType) {
    activeType.value = qType
    filterChanged = true
  }
  if ((qStatus === 'WANT' || qStatus === 'DOING' || qStatus === 'DONE') && activeStatus.value !== qStatus) {
    activeStatus.value = qStatus
    filterChanged = true
  }
  if (filterChanged) {
    loadData()
  }

  const raw = route.query.itemId
  if (raw) {
    const itemId = Number(raw)
    if (itemId) {
      openDetailById(itemId)
    }
  }
  if (raw || qType || qStatus) {
    router.replace({ path: '/movie', query: {} })
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

loadData()
loadSummary()
handleRouteQuery()
</script>

<style scoped lang="scss">
/* 0.0.6 影单：样式取自设计图 movie.html（.page-movie 作用域），形制来自 handdrawn-site.css */

/* ===== 影单：封面统一 3:4 海报，桌面四列扫读；纸卡 + 胶带 + 抖线 ===== */
.page-movie .hd-banner .hd-tape.left { left: 17%; width: 92px }
.page-movie .hd-banner .hd-tape.right { left: 87% }
.page-movie .hd-search { max-width: 300px }
.page-movie .movie-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); margin-top: 26px }

.page-movie .movie-card {
  display: flex; flex-direction: column; min-width: 0;
  background-color: var(--hd-paper); background-image: var(--hd-grain);
  border-radius: 6px; box-shadow: var(--hd-shadow);
  padding: 10px 10px 14px;
  transition: transform .14s, box-shadow .14s;
}
.page-movie .movie-card:hover { transform: translateY(-3px) rotate(0); box-shadow: var(--hd-shadow-lift) }
.page-movie .movie-card > .hd-tape { z-index: 3; top: -8px; width: 64px; height: 18px }
.page-movie .movie-card:nth-child(even) > .hd-tape { left: 32%; transform: translateX(-50%) rotate(2.6deg) }
.page-movie .movie-card:nth-child(3n) > .hd-tape { left: 68%; transform: translateX(-50%) rotate(-1.2deg) }
.page-movie .movie-card .hd-cover { aspect-ratio: 3 / 4; z-index: 1 }
.page-movie .movie-card .hd-cover .hd-fallback { transition: transform .25s }
.page-movie .movie-card:hover .hd-fallback { transform: scale(1.03) }
.page-movie .movie-card .hd-fallback .fc-band { font-size: 13px; line-height: 1.4; letter-spacing: 1px }
.page-movie .movie-card .hd-fallback .fc-meta { font-size: 13px; opacity: .84 }
.page-movie .movie-card .body { display: flex; flex-direction: column; flex: 1; padding: 12px 2px 0 }
.page-movie .movie-card .title { font-size: 18.5px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap }
.page-movie .movie-card .title .hd-st { margin-left: auto; flex: none; font-size: 13px }
.page-movie .meta-row { margin-top: 4px; font-size: 13.5px; color: var(--hd-ink-soft); display: flex; flex-wrap: wrap; align-items: center; gap: 4px 7px }
.page-movie .meta-row .sep { color: var(--hd-ink-faint) }
.page-movie .stars-row { margin-top: 6px; display: flex; align-items: center; gap: 6px; min-height: 21px }
.page-movie .hd-stars svg { fill: none; stroke: currentColor; stroke-width: 1.6; stroke-linejoin: round }
.page-movie .hd-stars svg.on { fill: currentColor; stroke-width: 1 }
.page-movie .no-rating { font-size: 13px; color: var(--hd-ink-faint) }
.page-movie .card-comment {
  margin-top: 8px; font-size: 14px; line-height: 1.7; color: var(--hd-ink-soft);
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
  min-height: 3.4em;
}
.page-movie .card-acts { margin-top: auto; padding-top: 12px; display: flex; gap: 8px; flex-wrap: wrap }
.page-movie .load-more { margin-top: 44px; display: flex; flex-direction: column; align-items: center; gap: 10px }

@media (max-width: 960px) {
  .page-movie .movie-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 26px 18px }
}
@media (max-width: 720px) {
  .page-movie .movie-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 22px 14px; margin-top: 20px }
  .page-movie .movie-card { padding: 8px 8px 12px }
  .page-movie .movie-card .title { font-size: 16.5px }
  .page-movie .card-comment { font-size: 13.5px; min-height: 3.3em; -webkit-line-clamp: 3 }
  .page-movie .card-acts .hd-btn { flex: 1 1 100% }
  .page-movie .hd-banner .hd-tape.left { left: 24% }
  .page-movie .hd-banner .hd-tape.right { left: 76% }
}

/* 产品补充：真实封面图、标签行、加载态与空态 */
.page-movie .movie-card .hd-cover img { width: 100%; height: 100%; object-fit: cover; display: block }
.page-movie .tag-filter { margin-top: 16px }
.page-movie .grid-end { margin-top: 34px; text-align: center; font-size: 13.5px; color: var(--hd-ink-soft) }
.page-movie .load-error {
  grid-column: 1 / -1; display: flex; flex-direction: column; align-items: center;
  gap: 12px; padding: 48px 0; color: var(--hd-ink-soft);
}
.page-movie .hd-empty { grid-column: 1 / -1 }

/* 卡片骨架：标题一行（状态章右侧固定）、按钮贴底、短评两行截断 */
.page-movie .hd-card { display: flex; flex-direction: column }
.page-movie .hd-card .body { display: flex; flex-direction: column; flex: 1; min-width: 0; gap: 0 }
.page-movie .hd-card .title { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 8px }
.page-movie .hd-card .title .t-title { min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.page-movie .hd-card .title .hd-st { flex: none }
.page-movie .hd-card .meta-row { min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.page-movie .hd-card .stars-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap }
.page-movie .hd-card .card-comment { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden }
.page-movie .hd-card .card-acts { margin-top: auto }
</style>
