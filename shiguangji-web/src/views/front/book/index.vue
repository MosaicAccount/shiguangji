<template>
  <div class="book-page page-book">
    <section class="hd-banner anim">
      <span class="hd-tape left"></span>
      <span class="hd-tape right amber"></span>
      <div>
        <div class="eyebrow">纸页之间</div>
        <h1>书单</h1>
        <p class="desc">记录你读过的书和想读的书。</p>
      </div>
      <div class="acts">
        <button v-if="isLogin" class="hd-btn go lg" type="button" @click="openAdd">＋ 添加想读</button>
      </div>
    </section>

    <div class="hd-summary">
      <span class="item">读过 <b>{{ doneCount }}</b> 本</span><span class="sep" aria-hidden="true">·</span>
      <span class="item">在读 <b>{{ doingCount }}</b> 本</span><span class="sep" aria-hidden="true">·</span>
      <span class="item">想读 <b>{{ wishCount }}</b> 本</span>
      <span class="recent">最近 <b>{{ recentTitle || '—' }}</b></span>
    </div>

    <div class="hd-toolbar">
      <div v-if="isLogin" class="hd-chips" role="group" aria-label="状态">
        <button
          v-for="s in sgj_book_status"
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
          placeholder="搜索书名"
          aria-label="搜索书名"
          @keyup.enter="loadData"
        />
      </div>
      <div class="right"><span class="hd-tiny hd-muted">共 {{ total }} 本</span></div>
    </div>

    <tag-pills v-model="searchTag" module="BOOK" class="tag-filter" @update:model-value="loadData" />

    <div v-loading="loading" class="hd-grid book-grid">
      <div v-if="!loading && !list.length && !loadError" class="hd-empty">
        <p>暂无数据 —— 先把想读的那本记下来。</p>
        <div class="acts">
          <button v-if="isLogin" class="hd-btn go" type="button" @click="openAdd">＋ 添加想读</button>
        </div>
      </div>
      <div v-if="loadError" class="load-error">
        <span>加载失败，请稍后重试</span>
        <button class="hd-btn sm" type="button" @click="loadData">重试</button>
      </div>

      <article
        v-for="(item, idx) in list"
        :key="item.itemId"
        class="hd-card book-card hd-rough anim"
        :style="{ '--d': ((idx % 4) * 30) + 'ms' }"
        @click="openDetail(item)"
      >
        <span class="hd-tape"></span>
        <a class="hd-cover" href="#" @click.prevent="openDetail(item)">
          <img
            v-if="item.coverUrl && !isCoverError(item)"
            :src="photoUrl(item.coverUrl)"
            :alt="item.title"
            loading="lazy"
            @error="onCoverError(item)"
          />
          <span v-else class="hd-fallback book">
            <span class="fc-band">书籍</span>
            <span class="fc-body">
              <span class="fc-title">{{ item.title }}</span>
              <span class="fc-meta">{{ item.author || '' }}</span>
            </span>
            <span class="fc-motif"></span>
          </span>
        </a>
        <div class="body">
          <h3 class="title">
            {{ item.title }}
            <span class="hd-st" :class="{ done: item.status === 'DONE', doing: item.status === 'DOING' }">{{ statusLabel(item) }}</span>
          </h3>
          <div class="meta-row"><span>{{ item.author || '—' }}</span></div>
          <div class="stars-row">
            <plan-date-badge :item="item" @saved="loadData" />
            <span v-if="item.rating" class="hd-stars" :aria-label="`评分 ${item.rating} 分，满分 5 分`">{{ '★'.repeat(item.rating) }}</span>
            <span v-else class="no-rating">未评分</span>
          </div>
          <p v-if="item.comment" class="card-comment">{{ item.comment }}</p>
          <div class="card-acts" @click.stop>
            <button v-if="item.status === 'WANT' && isLogin" class="hd-btn sm" type="button" @click="startDoing(item)">开始读 →</button>
            <button v-if="item.status !== 'DONE' && isLogin" class="hd-btn sm go" type="button" @click="openComplete(item)">标记已读</button>
            <button v-if="item.status === 'DONE' && isLogin" class="hd-btn sm sub" type="button" @click="handleUncomplete(item)">取消完成</button>
          </div>
        </div>
      </article>
    </div>

    <div v-if="hasMore" class="load-more">
      <button class="hd-btn" type="button" :disabled="loadingMore" @click="loadMore">{{ loadingMore ? '加载中…' : '加载更多 ↓' }}</button>
      <span class="hd-tiny hd-muted">已展示 {{ list.length }} / 共 {{ total }} 本</span>
    </div>
    <div v-else-if="list.length" class="grid-end">—— 已到底部 · 共 {{ total }} 本 ——</div>

    <!-- 添加想读 -->
    <el-dialog v-model="addOpen" title="添加想读" width="560px" append-to-body>
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="80px">
        <el-form-item label="书名" prop="title">
          <el-input v-model="addForm.title" placeholder="请输入书名" maxlength="200" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="addForm.author" placeholder="作者（可选）" maxlength="200" />
        </el-form-item>
        <el-form-item label="出版社">
          <el-input v-model="addForm.publisher" placeholder="出版社（可选）" maxlength="200" />
        </el-form-item>
        <el-form-item label="出版日期">
          <el-date-picker v-model="addForm.publishDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="ISBN">
          <el-input v-model="addForm.isbn" placeholder="ISBN（可选）" maxlength="50" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="addForm.genre" placeholder="选择或输入分类（可选）" clearable filterable allow-create style="width: 100%">
            <el-option v-for="g in sgj_book_genre" :key="g.value" :label="g.label" :value="g.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <!-- 标签来自后台标签管理（BOOK 模块），禁止自由输入 -->
          <tag-select v-model="addForm.tags" module="BOOK" placeholder="选择标签（可选）" />
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="addForm.startDate" type="date" value-format="YYYY-MM-DD" placeholder="打算哪天读（可选）" :clearable="false" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitAdd">确 定</el-button>
        <el-button @click="addOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 标记已读（统一完成弹窗，文案与字段随类型参数化） -->
    <item-complete-dialog v-model="completeOpen" :item="currentItem" @submitted="onCompleted" />

    <!-- 详情抽屉：封面铺底 hero + 手账风信息卡（与足迹页同风格） -->
    <media-detail-drawer
      v-model="detailOpen"
      :item="detail"
      glyph="书"
      done-label="已读"
      want-label="想读"
      doing-label="在读"
      :hero-meta="heroMeta"
      memory-label="MY THOUGHTS · 我的读后感"
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

<script setup lang="ts" name="FrontBook">
import { getToken } from '@/utils/auth'
import { selectDictLabel, photoUrl } from '@/utils/sgj'
import { useDict } from '@/utils/dict'
import ItemEditDialog from '@/components/ItemEditDialog/index.vue'
import ItemCompleteDialog from '@/components/ItemCompleteDialog/index.vue'
import PlanDateBadge from '@/components/PlanDateBadge/index.vue'
import MediaDetailDrawer from '@/components/MediaDetailDrawer/index.vue'
import TagSelect from '@/components/TagSelect/index.vue'
import TagPills from '@/components/TagPills/index.vue'
import { listFrontItem, getFrontItem, addFrontItem, delFrontItem, uncompleteFrontItem, updateFrontItem } from '@/api/front/item'
import { getFrontHomeData } from '@/api/front/home'
import { itemStatusLabel } from '@/utils/itemStatus'
import type { FrontHomeSummary } from '@/types/api/front/home'
import type { SgjItem, ItemStatus } from '@/types/api/business/item'
import { planDateLabel, localDateKey } from '@/utils/plan'

const { sgj_book_status, sgj_book_genre } = useDict('sgj_book_status', 'sgj_book_genre')

const { proxy } = getCurrentInstance() as { proxy: any }
const router = useRouter()
const route = useRoute()

/** 是否登录（访客只读，登录后可管理） */
const isLogin = computed(() => !!getToken())

const activeStatus = ref<ItemStatus>(isLogin.value ? 'WANT' : 'DONE')
const searchTitle = ref('')
/** 标签筛选（选择即查询） */
const searchTag = ref('')
const list = ref<SgjItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const loadError = ref(false)
/** 分页 */
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)

/** 页头小结条：取首页汇总的 BOOK 口径，不另造接口 */
const homeSummary = ref<FrontHomeSummary | null>(null)
const recentTitle = ref('')
const doneCount = computed(() => homeSummary.value?.done?.BOOK || 0)
const doingCount = computed(() => homeSummary.value?.doing?.BOOK || 0)
const wishCount = computed(() => homeSummary.value?.wish?.BOOK || 0)

function loadSummary(): void {
  getFrontHomeData().then(response => {
    homeSummary.value = response.data?.summary || null
    recentTitle.value = response.data?.timeline?.find(i => i.itemType === 'BOOK')?.title || ''
  }).catch(() => {
    // 小结条失败不阻塞列表
  })
}

/** 状态胶囊文案：字典优先，缺失时用类型兜底 */
function statusLabel(item: SgjItem): string {
  return itemStatusLabel(item, { sgj_book_status: sgj_book_status.value })
}
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
  author: undefined,
  publisher: undefined,
  publishDate: undefined,
  isbn: undefined,
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

/** 状态胶囊切换 */
function switchStatus(status: ItemStatus): void {
  if (activeStatus.value === status) return
  activeStatus.value = status
  loadData()
}

/** WANT → DOING：开始读（0.0.3 状态流转复用 PUT /app/item/{id}，不新增端点） */
function startDoing(item: SgjItem): void {
  if (!item.itemId) return
  updateFrontItem(item.itemId, { status: 'DOING' }).then(() => {
    proxy.$modal.msgSuccess(`开始读：${item.title || ''}`)
    loadData()
  }).catch(() => {})
}

/** 卡片封面占位色块：按索引轮换灰陶浅/鼠尾草浅/灰铜浅/中性 */
function coverStyle(index: number): Record<string, string> {
  const soft = ['var(--sgj-primary-soft)', 'var(--sgj-moss-soft)', 'var(--sgj-amber-soft)', 'var(--sgj-cover)']
  const ink = ['var(--sgj-primary-dark)', 'var(--sgj-moss)', 'var(--sgj-amber)', 'var(--sgj-text-2)']
  const i = index % soft.length
  return { background: soft[i], color: ink[i] }
}

/** 详情抽屉 hero meta：评分之外追加作者 */
/** 详情抽屉 hero meta：作者之外追加计划日期 */
const heroMeta = computed<string[]>(() => {
  const meta: string[] = []
  if (detail.value?.author) meta.push(detail.value.author)
  if (detail.value?.startDate) {
    meta.push(`计划 ${planDateLabel(detail.value.startDate.slice(0, 10), localDateKey(new Date()))}`)
  }
  return meta
})

/** 详情抽屉分类 chips（script 内调用需手动解包字典 ref） */
const chipList = computed<string[]>(() => {
  const d = detail.value
  if (!d) return []
  return [selectDictLabel(sgj_book_genre.value, d.genre)].filter((v): v is string => !!v)
})

/** 详情抽屉信息行：出版信息 */
const infoRows = computed<{ ico: string; label: string; value: string }[]>(() => {
  const d = detail.value
  if (!d) return []
  const rows: { ico: string; label: string; value: string }[] = []
  if (d.publisher) rows.push({ ico: '🏛', label: '出版社', value: d.publisher })
  if (d.publishDate) rows.push({ ico: '📅', label: '出版日期', value: String(d.publishDate).slice(0, 10) })
  if (d.isbn) rows.push({ ico: '🔖', label: 'ISBN', value: d.isbn })
  if (d.pages) rows.push({ ico: '📃', label: '页数', value: d.pages + ' 页' })
  return rows
})

const addRules = {
  title: [{ required: true, message: '请输入书名', trigger: 'blur' }]
}

function loadData(): Promise<void> {
  loading.value = true
  loadError.value = false
  pageNum.value = 1
  return listFrontItem({
    itemType: 'BOOK',
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
    itemType: 'BOOK',
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

function openAdd(): void {
  Object.assign(addForm, {
    title: undefined,
    author: undefined,
    publisher: undefined,
    publishDate: undefined,
    isbn: undefined,
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
      itemType: 'BOOK',
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
  proxy.$modal.msgSuccess('已标记已读')
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
 *  配套：按 itemId 直接打开详情（首页时间线跳转 /book?itemId= 时调用）
 */
function openDetailById(itemId: number): void {
  getFrontItem(itemId).then(response => {
    detail.value = response.data || null
    detailOpen.value = true
  }).catch(() => {})
}

/**
 *  配套：读取路由 ?itemId=，存在则自动打开对应条目详情
 * 0.0.3：跳转可带 status 预选胶囊（「在读」的条目不在默认「想读」列表里）；
 * setup 里 loadData() 先于本函数执行，胶囊变了要补一次 loadData。
 */
function handleRouteQuery(): void {
  const qStatus = route.query.status
  let filterChanged = false
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
  if (raw || qStatus) {
    router.replace({ path: '/book', query: {} })
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
/* 0.0.6 书单：样式取自设计图 book.html（.page-book 作用域），形制来自 handdrawn-site.css */

/* ===== 书单：封面统一 2:3，书脊用内阴影做一条暗边；纸卡 + 胶带 + 抖线 ===== */
.page-book .hd-banner .hd-tape.left { left: 18%; width: 90px }
.page-book .hd-banner .hd-tape.right { left: 86% }
.page-book .hd-search { max-width: 300px }
.page-book .book-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); margin-top: 26px }

.page-book .book-card {
  display: flex; flex-direction: column; min-width: 0;
  background-color: var(--hd-paper); background-image: var(--hd-grain);
  border-radius: 6px; box-shadow: var(--hd-shadow);
  padding: 10px 10px 14px;
  transition: transform .14s, box-shadow .14s;
}
.page-book .book-card:hover { transform: translateY(-3px) rotate(0); box-shadow: var(--hd-shadow-lift) }
.page-book .book-card > .hd-tape { z-index: 3; top: -8px; width: 64px; height: 18px }
.page-book .book-card:nth-child(even) > .hd-tape { left: 32%; transform: translateX(-50%) rotate(2.6deg) }
.page-book .book-card:nth-child(3n) > .hd-tape { left: 68%; transform: translateX(-50%) rotate(-1.2deg) }
.page-book .book-card .hd-cover { aspect-ratio: 2 / 3; z-index: 1 }
.page-book .book-card .hd-cover .hd-fallback { transition: transform .25s }
.page-book .book-card:hover .hd-fallback { transform: scale(1.03) }
.page-book .book-card .hd-fallback .fc-band { font-size: 13px; line-height: 1.4; letter-spacing: 1px }
.page-book .book-card .hd-fallback .fc-meta { font-size: 13px; opacity: .84 }
.page-book .book-card .body { display: flex; flex-direction: column; flex: 1; padding: 12px 2px 0 }
.page-book .book-card .title { font-size: 18.5px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap }
.page-book .book-card .title .hd-st { margin-left: auto; flex: none; font-size: 13px }
.page-book .meta-row { margin-top: 4px; font-size: 13.5px; color: var(--hd-ink-soft); display: flex; flex-wrap: wrap; align-items: center; gap: 4px 7px }
.page-book .meta-row .sep { color: var(--hd-ink-faint) }
.page-book .stars-row { margin-top: 6px; display: flex; align-items: center; gap: 6px; min-height: 21px }
.page-book .hd-stars svg { fill: none; stroke: currentColor; stroke-width: 1.6; stroke-linejoin: round }
.page-book .hd-stars svg.on { fill: currentColor; stroke-width: 1 }
.page-book .no-rating { font-size: 13px; color: var(--hd-ink-faint) }
.page-book .card-comment {
  margin-top: 8px; font-size: 14px; line-height: 1.7; color: var(--hd-ink-soft);
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
  min-height: 3.4em;
}
.page-book .card-acts { margin-top: auto; padding-top: 12px; display: flex; gap: 8px; flex-wrap: wrap }
.page-book .load-more { margin-top: 44px; display: flex; flex-direction: column; align-items: center; gap: 10px }

@media (max-width: 960px) {
  .page-book .book-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 26px 18px }
}
@media (max-width: 720px) {
  .page-book .book-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 22px 14px; margin-top: 20px }
  .page-book .book-card { padding: 8px 8px 12px }
  .page-book .book-card .title { font-size: 16.5px }
  .page-book .card-comment { font-size: 13.5px; min-height: 3.3em; -webkit-line-clamp: 3 }
  .page-book .card-acts .hd-btn { flex: 1 1 100% }
  .page-book .hd-banner .hd-tape.left { left: 24% }
  .page-book .hd-banner .hd-tape.right { left: 76% }
}

/* 产品补充：真实封面图、标签行、加载态与空态 */
.page-book .book-card .hd-cover img { width: 100%; height: 100%; object-fit: cover; display: block }
.page-book .tag-filter { margin-top: 16px }
.page-book .grid-end { margin-top: 34px; text-align: center; font-size: 13.5px; color: var(--hd-ink-soft) }
.page-book .load-error {
  grid-column: 1 / -1; display: flex; flex-direction: column; align-items: center;
  gap: 12px; padding: 48px 0; color: var(--hd-ink-soft);
}
.page-book .hd-empty { grid-column: 1 / -1 }
</style>
