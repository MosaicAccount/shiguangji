<template>
  <div class="book-page">
    <div class="page-banner anim">
      <div class="banner-blob" aria-hidden="true"></div>
      <div class="banner-blob2" aria-hidden="true"></div>
      <div class="banner-text">
        <h1>书单</h1>
        <p>记录你读过的书和想读的书。</p>
      </div>
      <el-button v-if="isLogin" type="primary" class="banner-add" @click="openAdd">＋ 添加想读</el-button>
    </div>

    <div class="filter-bar">
      <div v-if="isLogin" class="filter-group">
        <button
          v-for="(s, si) in sgj_book_status"
          :key="s.value"
          class="filter-pill anim"
          :style="{ '--d': ((si + 1) * 30) + 'ms' }"
          :class="{ active: activeStatus === s.value }"
          @click="switchStatus(s.value)"
        >{{ s.label }}</button>
      </div>
      <el-input
        v-model="searchTitle"
        placeholder="搜索书名"
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

    <div v-loading="loading" class="card-grid">
      <el-empty v-if="!loading && !list.length && !loadError" description="暂无数据" />
      <div v-if="loadError" class="load-error">
        <span>加载失败，请稍后重试</span>
        <el-button size="small" round @click="loadData">重试</el-button>
      </div>
      <div v-for="(item, idx) in list" :key="item.itemId" class="book-card anim" :style="{ '--d': ((idx % 4) * 30) + 'ms' }" @click="openDetail(item)">
        <div class="card-cover" :style="coverStyle(idx)">
          <!-- ：封面加载失败兜底（隐藏 img 显示占位图标） -->
          <img v-if="item.coverUrl && !isCoverError(item)" :src="item.coverUrl" class="card-cover-img" :alt="item.title" loading="lazy" @error="onCoverError(item)" />
          <template v-else>
            <span class="card-glyph">书</span>
            <span class="card-type-pill">书籍</span>
          </template>
        </div>
        <div class="card-body">
          <div class="card-title">{{ item.title }}</div>
          <div v-if="item.author" class="card-author">{{ item.author }}</div>
          <div class="card-meta">
            <span v-if="item.rating" class="rating-pill">★ {{ item.rating }}</span>
          </div>
          <div v-if="item.comment" class="card-comment">{{ item.comment }}</div>
        </div>
        <!-- ：整卡点击已开详情，操作区只留高频操作（去重「查看详情」按钮） -->
        <div class="card-actions" @click.stop>
          <el-button v-if="item.status === 'WANT' && isLogin" type="primary" size="small" round @click="openComplete(item)">
            标记已读
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
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitAdd">确 定</el-button>
        <el-button @click="addOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 标记已读 -->
    <el-dialog v-model="completeOpen" :title="'标记已读：' + (currentItem?.title || '')" width="520px" append-to-body>
      <el-form ref="completeFormRef" :model="completeForm" label-width="80px">
        <el-form-item label="读完日期">
          <el-date-picker v-model="completeForm.finishDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="评分">
          <el-rate v-model="completeForm.rating" :max="10" show-score />
        </el-form-item>
        <el-form-item label="读后感">
          <el-input v-model="completeForm.comment" type="textarea" :rows="3" placeholder="写点读后感吧（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitComplete">确 定</el-button>
        <el-button @click="completeOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailOpen" :title="detail?.title || '详情'" size="420px">
      <div v-if="detail" class="detail-content">
        <div class="detail-cover">
          <img v-if="detail.coverUrl && !isCoverError(detail)" :src="detail.coverUrl" class="detail-cover-img" :alt="detail.title" @error="onCoverError(detail)" />
          <div v-else class="detail-icon">书</div>
        </div>
        <!-- 6.2 详情抽屉头部 meta 行：类型图标 + 状态 tag -->
        <div class="detail-meta">
          <span class="detail-type-icon">📖</span>
          <el-tag :type="detail.status === 'DONE' ? 'success' : 'warning'" size="small">
            {{ detail.status === 'DONE' ? '已读' : '想读' }}
          </el-tag>
        </div>
        <h2>{{ detail.title }}</h2>
        <div v-if="detail.rating" class="detail-rating">⭐ {{ detail.rating }}</div>
        <el-descriptions :column="1" border class="detail-desc">
          <el-descriptions-item label="作者">{{ detail.author || '-' }}</el-descriptions-item>
          <el-descriptions-item label="出版社">{{ detail.publisher || '-' }}</el-descriptions-item>
          <el-descriptions-item label="出版日期">{{ detail.publishDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="ISBN">{{ detail.isbn || '-' }}</el-descriptions-item>
          <el-descriptions-item label="页数">{{ detail.pages || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ selectDictLabel(sgj_book_genre, detail.genre) || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.comment" class="detail-comment">
          <h3>我的读后感</h3>
          <p>{{ detail.comment }}</p>
        </div>
        <item-notes :item-id="detail.itemId" />
        <div v-if="isLogin" class="detail-actions">
          <el-button type="primary" round @click="openEditDetail">编辑</el-button>
          <el-button type="danger" round @click="handleDelete(detail)">删除</el-button>
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

<script setup lang="ts" name="FrontBook">
import { getToken } from '@/utils/auth'
import { selectDictLabel } from '@/utils/sgj'
import { useDict } from '@/utils/dict'
import ItemEditDialog from '@/components/ItemEditDialog/index.vue'
import ItemNotes from '@/components/ItemNotes/index.vue'
import { listFrontItem, getFrontItem, addFrontItem, completeFrontItem, delFrontItem, uncompleteFrontItem } from '@/api/front/item'
import type { SgjItem } from '@/types/api/business/item'

const { sgj_book_status, sgj_book_genre } = useDict('sgj_book_status', 'sgj_book_genre')

const { proxy } = getCurrentInstance() as { proxy: any }
const router = useRouter()
const route = useRoute()

/** 是否登录（访客只读，登录后可管理） */
const isLogin = computed(() => !!getToken())

const activeStatus = ref<'WANT' | 'DONE'>(isLogin.value ? 'WANT' : 'DONE')
const searchTitle = ref('')
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
  author: undefined,
  publisher: undefined,
  publishDate: undefined,
  isbn: undefined,
  genre: undefined
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

/** 卡片封面占位色块：按索引轮换灰陶浅/鼠尾草浅/灰铜浅/中性 */
function coverStyle(index: number): Record<string, string> {
  const soft = ['var(--sgj-primary-soft)', 'var(--sgj-moss-soft)', 'var(--sgj-amber-soft)', 'var(--sgj-cover)']
  const ink = ['var(--sgj-primary-dark)', 'var(--sgj-moss)', 'var(--sgj-amber)', 'var(--sgj-text-2)']
  const i = index % soft.length
  return { background: soft[i], color: ink[i] }
}

const completeForm = reactive({
  finishDate: undefined,
  rating: undefined,
  comment: undefined
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
    genre: undefined
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
    proxy.$modal.msgSuccess('已标记已读')
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
 */
function handleRouteQuery(): void {
  const raw = route.query.itemId
  if (!raw) return
  const itemId = Number(raw)
  if (!itemId) return
  openDetailById(itemId)
  router.replace({ path: '/book', query: {} })
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
.book-page {
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

}

html.dark .page-banner {
  background: var(--sgj-bg-deep);
}

html.dark .detail-content .detail-icon {
  color: var(--sgj-primary-light);
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

.book-card {
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--sgj-shadow-sm);
  cursor: pointer;
  transition: box-shadow 0.16s, transform 0.16s;
  display: flex;
  flex-direction: column;

  &:hover {
    transform: translateY(-4px);
    box-shadow: var(--sgj-shadow-hover);
  }

  .card-cover {
    /* ：封面统一 2:3 竖向比例（书封） */
    position: relative;
    aspect-ratio: 2 / 3;
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
      font-size: 56px;
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

    .card-author {
      font-size: 12px;
      color: var(--sgj-text-3);
      margin-bottom: 8px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .card-meta {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 6px;

      /* 评分：灰铜浅底胶囊 */
      .rating-pill {
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

.detail-content {
  text-align: center;

  /* 6.2 详情抽屉头部 meta 行：类型图标 + 状态 tag */
  .detail-meta {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin: 12px 0 4px;

    .detail-type-icon {
      font-size: 20px;
    }
  }

  .detail-cover {
    display: flex;
    align-items: center;
    justify-content: center;

    .detail-cover-img {
      max-width: 100%;
      max-height: 320px;
      border-radius: 12px;
      box-shadow: 0 6px 20px rgba(166, 83, 54, 0.18);
    }
  }

  .detail-icon {
    /* 无封面/封面失效占位：衬线字浅色块，与列表卡片字形占位同语言 */
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 132px;
    height: 184px;
    border-radius: 12px;
    background: var(--sgj-primary-soft);
    color: var(--sgj-primary-dark);
    font-family: var(--sgj-font-serif);
    font-size: 56px;
    font-weight: 700;
  }

  h2 {
    color: var(--sgj-text);
    margin: 8px 0;
    font-family: var(--sgj-font-serif);
  }

  .detail-rating {
    color: var(--sgj-amber);
    font-size: 18px;
    margin-bottom: 16px;
  }

  .detail-desc {
    margin-top: 16px;
    text-align: left;
  }

  .detail-comment {
    margin-top: 20px;
    text-align: left;

    h3 {
      color: var(--sgj-text-2);
      margin-bottom: 8px;
    }

    p {
      color: var(--sgj-text);
      line-height: 1.6;
    }
  }

  .detail-actions {
    margin-top: 24px;
    display: flex;
    justify-content: center;
    gap: 12px;
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

  .detail-actions {
    flex-wrap: wrap;
  }
}
</style>