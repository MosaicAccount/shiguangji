<template>
  <div class="movie-page">
    <div class="page-banner anim">
      <div class="banner-blob" aria-hidden="true"></div>
      <div class="banner-blob2" aria-hidden="true"></div>
      <div class="banner-text">
        <h1>影单 · 光影收藏</h1>
        <p>慢慢看，认真记——你知道看过些什么。</p>
      </div>
      <el-button v-if="isLogin" type="primary" class="banner-add" @click="openAdd">＋ 添加观影</el-button>
    </div>

    <div class="filter-bar">
      <div class="filter-group">
        <button
          v-for="t in movieTypes"
          :key="t.value"
          class="filter-pill anim"
          :style="{ '--d': (Number(t.value === 'TV') * 30) + 'ms' }"
          :class="{ active: activeType === t.value }"
          @click="switchType(t.value as 'MOVIE' | 'TV')"
        >{{ t.label }}</button>
      </div>
      <div v-if="isLogin" class="filter-group">
        <button
          v-for="(s, si) in sgj_movie_status"
          :key="s.value"
          class="filter-pill anim"
          :style="{ '--d': ((si + 2) * 30) + 'ms' }"
          :class="{ active: activeStatus === s.value }"
          @click="switchStatus(s.value)"
        >{{ s.label }}</button>
      </div>
      <el-input
        v-model="searchTitle"
        placeholder="搜索标题"
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

    <!-- 标签筛选：豆瓣式标签行（模块跟随当前类型），点击选中、再点取消 -->
    <tag-pills v-model="searchTag" :module="activeType" @update:model-value="loadData" />

    <div v-loading="loading" class="card-grid">
      <el-empty v-if="!loading && !list.length && !loadError" description="暂无数据" />
      <div v-if="loadError" class="load-error">
        <span>加载失败，请稍后重试</span>
        <el-button size="small" round @click="loadData">重试</el-button>
      </div>
      <div v-for="(item, idx) in list" :key="item.itemId" class="movie-card anim" :style="{ '--d': ((idx % 4) * 30) + 'ms' }" @click="openDetail(item)">
        <div class="card-cover" :style="coverStyle(idx)">
          <img v-if="item.coverUrl && !isCoverError(item)" :src="photoUrl(item.coverUrl)" class="card-cover-img" :alt="item.title" loading="lazy" @error="onCoverError(item)" />
          <template v-else>
            <span class="card-glyph">{{ coverGlyph }}</span>
          </template>
          <span class="card-type-pill">{{ activeType === 'MOVIE' ? '电影' : '电视剧' }}</span>
        </div>
        <div class="card-body">
          <div class="card-title">{{ item.title }}</div>
          <div class="card-meta">
            <span class="card-meta-text">{{ cardMeta(item) }}</span>
            <span v-if="item.rating" class="rating-pill">★ {{ item.rating }}</span>
          </div>
          <div v-if="item.comment" class="card-comment">{{ item.comment }}</div>
        </div>
        <div class="card-actions" @click.stop>
          <el-button v-if="item.status === 'WANT' && isLogin" type="primary" size="small" round @click="openComplete(item)">
            标记看过
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

    <div v-if="!hasMore && list.length" class="grid-end">—— 已到底部 · 共 {{ total }} 部 ——</div>

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
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitAdd">确 定</el-button>
        <el-button @click="addOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 标记看过 -->
    <el-dialog v-model="completeOpen" :title="'标记看过：' + (currentItem?.title || '')" width="520px" append-to-body>
      <el-form ref="completeFormRef" :model="completeForm" label-width="80px">
        <el-form-item label="看完日期">
          <el-date-picker v-model="completeForm.finishDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="评分">
          <el-rate v-model="completeForm.rating" :max="10" show-score />
        </el-form-item>
        <el-form-item label="短评">
          <el-input v-model="completeForm.comment" type="textarea" :rows="3" placeholder="写点短评吧（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitComplete">确 定</el-button>
        <el-button @click="completeOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉：封面铺底 hero + 手账风信息卡（与足迹页同风格） -->
    <media-detail-drawer
      v-model="detailOpen"
      :item="detail"
      :glyph="activeType === 'MOVIE' ? '影' : '剧'"
      done-label="看过"
      want-label="想看"
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
import MediaDetailDrawer from '@/components/MediaDetailDrawer/index.vue'
import TagSelect from '@/components/TagSelect/index.vue'
import TagPills from '@/components/TagPills/index.vue'
import { listFrontItem, getFrontItem, addFrontItem, completeFrontItem, updateFrontItem, delFrontItem, uncompleteFrontItem } from '@/api/front/item'
import type { SgjItem } from '@/types/api/business/item'

const { sgj_item_type, sgj_movie_status, sgj_movie_genre, sgj_region, sgj_language } = useDict('sgj_item_type', 'sgj_movie_status', 'sgj_movie_genre', 'sgj_region', 'sgj_language')

const { proxy } = getCurrentInstance() as { proxy: any }
const router = useRouter()
const route = useRoute()

/** 是否登录（访客只读，登录后可管理） */
const isLogin = computed(() => !!getToken())

/** 影视页类型选项：仅取字典中的电影/电视剧，排除书籍/地点 */
const movieTypes = computed(() => (sgj_item_type.value || []).filter(t => t.value === 'MOVIE' || t.value === 'TV'))

const activeType = ref<'MOVIE' | 'TV'>('MOVIE')
/** 访客默认只看已完成内容 */
const activeStatus = ref<'WANT' | 'DONE'>(isLogin.value ? 'WANT' : 'DONE')
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

const completeForm = reactive({
  finishDate: undefined,
  rating: undefined,
  comment: undefined
})

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

/** 状态胶囊切换 */
function switchStatus(status: 'WANT' | 'DONE'): void {
  if (activeStatus.value === status) return
  activeStatus.value = status
  loadData()
}

/** 封面占位衬线字 */
const coverGlyph = computed(() => (activeType.value === 'MOVIE' ? '影' : '剧'))

/** 详情抽屉 hero meta：评分之外追加年份信息 */
const heroMeta = computed<string[]>(() => {
  const d = detail.value
  if (!d) return []
  if (activeType.value === 'MOVIE') {
    return d.releaseYear ? [`${d.releaseYear} 年上映`] : []
  }
  const span = [d.startYear, d.endYear].filter(Boolean).join('-')
  return span ? [`${span} 年`] : []
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
  Object.assign(completeForm, {
    finishDate: undefined,
    rating: undefined,
    comment: undefined
  })
  completeOpen.value = true
}

function submitComplete(): void {
  if (!currentItem.value?.itemId) return
  completeFrontItem(currentItem.value.itemId, completeForm).then(() => {
    proxy.$modal.msgSuccess('已标记看过')
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
function handleRouteQuery(): void {
  const raw = route.query.itemId
  if (!raw) return
  const itemId = Number(raw)
  if (!itemId) return
  openDetailById(itemId)
  router.replace({ path: '/movie', query: {} })
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
handleRouteQuery()
</script>

<style scoped lang="scss">
.movie-page {
  color: var(--sgj-text);
  font-family: var(--sgj-font-sans);
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

/* ===== 影单横幅：冷炭灰 studio 底（1248x176 · 24 圆角）+ 衬线标题 ===== */
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

}

html.dark .page-banner {
  background: var(--sgj-bg-deep);
}

/* ===== 筛选胶囊（radius 17） ===== */
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
    padding: 0 20px;
    height: 34px;
    border: 1px solid var(--sgj-border);
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

.grid-end {
  text-align: center;
  margin: 8px 0 16px;
  font-size: 12px;
  letter-spacing: 2px;
  color: var(--sgj-text-4);
}

/* ===== 画廊网格卡片（288x272 · 18 圆角） ===== */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(288px, 1fr));
  gap: 24px 24px;
  min-height: 200px;
}

.movie-card {
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: 18px;
  overflow: hidden;
  box-shadow: var(--sgj-shadow-sm);
  cursor: pointer;
  transition: box-shadow 0.16s, transform 0.16s cubic-bezier(0.23, 1, 0.32, 1);
  display: flex;
  flex-direction: column;

  &:hover {
    transform: translateY(-4px);
    box-shadow: var(--sgj-shadow-hover);
  }

  .card-cover {
    position: relative;
    height: 140px;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    margin: 14px;
    border-radius: 12px;

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
      top: 10px;
      left: 10px;
      padding: 0 10px;
      height: 20px;
      display: inline-flex;
      align-items: center;
      border-radius: 10px;
      background: var(--sgj-bg-card);
      color: var(--sgj-text-2);
      font-size: 11px;
      opacity: 0.92;
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
      margin-bottom: 6px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .card-meta {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;

      .card-meta-text {
        font-size: 12px;
        color: var(--sgj-text-3);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      /* 评分：灰铜浅底胶囊 */
      .rating-pill {
        flex-shrink: 0;
        padding: 1px 10px;
        border-radius: 11px;
        background: var(--sgj-amber-soft);
        color: var(--sgj-amber);
        font-family: var(--sgj-font-serif);
        font-weight: 700;
        font-size: 12px;
      }
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


/* 移动端最小适配：筛选换行、搜索框占满整行、卡片操作按钮可换行 */
@media (max-width: 768px) {
  .page-banner {
    padding: 24px 20px;
  }

  .filter-bar {
    .search-input {
      width: 100%;
      margin-left: 0;
    }
  }

  /*  移动端卡片栅格：minmax(160px,1fr) */
  .card-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 12px;
  }

  .card-actions {
    flex-wrap: wrap;

    .el-button + .el-button {
      margin-left: 0;
    }
  }
}
</style>