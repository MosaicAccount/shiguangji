<template>
  <div class="note-page">
    <div class="page-banner anim">
      <div class="banner-blob" aria-hidden="true"></div>
      <div class="banner-blob2" aria-hidden="true"></div>
      <div class="banner-text">
        <h1>笔记</h1>
        <p>记录你的思考与学习笔记。</p>
      </div>
      <el-button v-if="isLogin" type="primary" class="banner-add" @click="openAdd">＋ 写笔记</el-button>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="searchTitle"
        placeholder="搜索笔记标题"
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

    <!-- 标签筛选：豆瓣式标签行（NOTE 模块），点击选中、再点取消 -->
    <tag-pills v-model="searchTag" module="NOTE" @update:model-value="loadData" />

    <div v-if="filterItemId" class="filter-tip">
      <span>正在查看条目 #{{ filterItemId }} 的关联笔记</span>
      <el-button link type="primary" size="small" @click="clearFilter">查看全部笔记</el-button>
    </div>

    <div v-loading="loading" class="note-list">
      <el-empty v-if="!loading && !list.length && !loadError" description="暂无笔记" />
      <div v-if="loadError" class="load-error">
        <span>加载失败，请稍后重试</span>
        <el-button size="small" round @click="loadData">重试</el-button>
      </div>
      <div v-for="note in list" :key="note.noteId" class="note-card" @click="openDetail(note)">
        <div class="note-header">
          <div class="note-title">
            {{ note.title }}
            <!-- 公开标识仅登录态展示 -->
            <el-tag v-if="isLogin && note.isPublic === '1'" size="small" type="success" class="public-tag">公开</el-tag>
          </div>
          <div class="note-actions" @click.stop>
            <el-button v-if="isLogin" link type="primary" size="small" @click="openEdit(note)">编辑</el-button>
            <el-button v-if="isLogin" link type="danger" size="small" @click="handleDelete(note)">删除</el-button>
          </div>
        </div>
        <div v-if="note.content" class="note-preview">
          <markdown-viewer :content="note.content" />
        </div>
        <div class="note-meta">
          <!--  图标区分：🔗 关联笔记 / 📝 独立笔记 -->
          <span v-if="note.itemId" class="note-link">🔗 关联条目 #{{ note.itemId }}</span>
          <span v-else class="note-link">📝 独立笔记</span>
          <span v-if="note.tags" class="note-tags">{{ note.tags }}</span>
          <span class="note-time">{{ formatTime(note.updateTime || note.createTime) }}</span>
        </div>
      </div>
    </div>

    <div v-if="hasMore" class="load-more-wrap">
      <el-button :loading="loadingMore" round @click="loadMore">加载更多</el-button>
    </div>

    <!-- 新增/编辑笔记 -->
    <el-dialog v-model="editOpen" :title="form.noteId ? '编辑笔记' : '写笔记'" width="720px" append-to-body>
      <el-form ref="editFormRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入笔记标题" maxlength="200" />
        </el-form-item>
        <el-form-item label="关联条目">
          <item-select v-model="form.itemId" :key="form.noteId || 'new'" />
        </el-form-item>
        <el-form-item label="标签">
          <!-- 标签来自后台标签管理（NOTE 模块），禁止自由输入 -->
          <tag-select v-model="form.tags" module="NOTE" placeholder="选择标签（可选）" />
        </el-form-item>
        <!-- 公开/私密：默认私密，公开后访客可见 -->
        <el-form-item label="公开状态">
          <el-radio-group v-model="form.isPublic">
            <el-radio-button value="0">私密</el-radio-button>
            <el-radio-button value="1">公开</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容">
          <markdown-editor v-model="form.content" :rows="12" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="editOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailOpen" :title="detail?.title || '笔记详情'" size="520px">
      <div v-if="detail" class="detail-content">
        <!--  图标区分：🔗 关联笔记 / 📝 独立笔记 -->
        <div v-if="detail.itemId" class="detail-link">🔗 关联条目 #{{ detail.itemId }}</div>
        <div v-else class="detail-link">📝 独立笔记</div>
        <!-- 公开状态仅登录态展示 -->
        <div v-if="isLogin" class="detail-public">
          <el-tag :type="detail.isPublic === '1' ? 'success' : 'info'" size="small">
            {{ detail.isPublic === '1' ? '公开' : '私密' }}
          </el-tag>
        </div>
        <div v-if="detail.tags" class="detail-tags">🏷 {{ detail.tags }}</div>
        <div class="detail-body">
          <markdown-viewer :content="detail.content" />
        </div>
        <!--  ①：详情抽屉补编辑/删除（登录态），与列表操作对齐 -->
        <div v-if="isLogin" class="detail-actions">
          <el-button type="primary" round size="small" @click="openEdit(detail)">编辑</el-button>
          <el-button type="danger" round size="small" @click="handleDelete(detail)">删除</el-button>
        </div>
        <div class="detail-time">{{ formatTime(detail.updateTime || detail.createTime) }}</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts" name="FrontNote">
import { getToken } from '@/utils/auth'
import MarkdownEditor from '@/components/MarkdownEditor/index.vue'
import MarkdownViewer from '@/components/MarkdownViewer/index.vue'
import ItemSelect from '@/components/front/ItemSelect.vue'
import TagSelect from '@/components/TagSelect/index.vue'
import TagPills from '@/components/TagPills/index.vue'
import { listFrontNote, getFrontNote, addFrontNote, updateFrontNote, delFrontNote } from '@/api/front/note'
import type { SgjNote } from '@/types/api/business/note'

const { proxy } = getCurrentInstance() as { proxy: any }
const route = useRoute()
const router = useRouter()

/** 是否登录（访客只读，登录后可管理） */
const isLogin = computed(() => !!getToken())

const list = ref<SgjNote[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const loadError = ref(false)
const searchTitle = ref('')
/** 标签筛选（TagPills 点击选中、再点取消后触发 loadData） */
const searchTag = ref('')
/** 分页 */
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const hasMore = computed(() => list.value.length < total.value)
/** B-03：按关联条目过滤展示（从条目编辑弹窗"查看关联笔记"跳转而来） */
const filterItemId = ref<number | undefined>(undefined)

const editOpen = ref(false)
const detailOpen = ref(false)
const detail = ref<SgjNote | null>(null)

const form = reactive<SgjNote>({
  noteId: undefined,
  itemId: undefined,
  title: undefined,
  content: undefined,
  tags: undefined,
  isPublic: '0'
})

const rules = {
  title: [{ required: true, message: '请输入笔记标题', trigger: 'blur' }]
}

function loadData(): void {
  loading.value = true
  loadError.value = false
  pageNum.value = 1
  listFrontNote({
    title: searchTitle.value || undefined,
    tags: searchTag.value || undefined,
    itemId: filterItemId.value,
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
  listFrontNote({
    title: searchTitle.value || undefined,
    tags: searchTag.value || undefined,
    itemId: filterItemId.value,
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

/** 清除条目过滤，恢复全部笔记 */
function clearFilter(): void {
  filterItemId.value = undefined
  loadData()
}

function formatTime(time?: string): string {
  if (!time) return ''
  return time.replace('T', ' ').slice(0, 16)
}

function resetForm(): void {
  Object.assign(form, {
    noteId: undefined,
    itemId: undefined,
    title: undefined,
    content: undefined,
    tags: undefined,
    isPublic: '0'
  })
}

function openAdd(): void {
  resetForm()
  editOpen.value = true
}

/**
 * B-03/：处理路由参数
 * - /note?itemId=x&write=1：条目页"去写笔记"跳转，自动打开新增弹窗并预填关联条目
 * - /note?itemId=x：条目编辑弹窗"查看关联笔记"跳转，列表按该条目过滤展示
 * - /note?noteId=x：首页最近笔记跳转，自动打开对应笔记详情抽屉
 */
function handleRouteQuery(): void {
  const rawNoteId = route.query.noteId
  if (rawNoteId) {
    const noteId = Number(rawNoteId)
    if (noteId) {
      getFrontNote(noteId).then(response => {
        detail.value = response.data || null
        detailOpen.value = true
      }).catch(() => {})
    }
    router.replace({ path: '/note', query: {} })
    return
  }
  const raw = route.query.itemId
  if (!raw) return
  const itemId = Number(raw)
  if (!itemId) return
  if (route.query.write === '1') {
    // 写笔记语义：仅登录用户自动打开新增弹窗，预填的关联条目可被用户修改（选择器）
    if (isLogin.value) {
      resetForm()
      form.itemId = itemId
      editOpen.value = true
    }
  } else {
    // 查看语义：按条目过滤笔记列表
    filterItemId.value = itemId
    loadData()
  }
  router.replace({ path: '/note', query: {} })
}

watch(
  () => route.query.itemId,
  () => handleRouteQuery()
)

function openEdit(note: SgjNote): void {
  Object.assign(form, note)
  // 公开状态归一化：仅接受 '0'/'1'，空值按私密处理（存量数据兜底）
  form.isPublic = note.isPublic === '1' ? '1' : '0'
  editOpen.value = true
}

function submitForm(): void {
  proxy.$refs['editFormRef'].validate((valid: boolean) => {
    if (!valid) return
    const request = form.noteId ? updateFrontNote(form) : addFrontNote(form)
    request.then(() => {
      proxy.$modal.msgSuccess(form.noteId ? '修改成功' : '新增成功')
      editOpen.value = false
      loadData()
    }).catch(() => {})
  })
}

function openDetail(note: SgjNote): void {
  if (!note.noteId) return
  getFrontNote(note.noteId).then(response => {
    detail.value = response.data || null
    detailOpen.value = true
  }).catch(() => {})
}

function handleDelete(note: SgjNote): void {
  if (!note.noteId) return
  proxy.$modal.confirm('是否确认删除该笔记？').then(() => {
    return delFrontNote(note.noteId!)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    loadData()
  }).catch(() => {})
}

loadData()
handleRouteQuery()
</script>

<style scoped lang="scss">
.note-page {
  color: var(--sgj-text);
}

.load-more-wrap {
  display: flex;
  justify-content: center;
  margin: 20px 0;
}

.load-error {
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

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 20px;

  .search-input {
    width: 260px;
  }
}

.filter-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--sgj-bg);
  border: 1px solid var(--sgj-border);
  border-radius: 12px;
  padding: 8px 14px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--sgj-primary);
}

.note-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 200px;
}

.note-card {
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: 16px;
  padding: 18px;
  box-shadow: var(--sgj-shadow-sm);
  cursor: pointer;
  transition: box-shadow 0.16s, transform 0.16s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--sgj-shadow-hover);
  }

  .note-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 8px;

    .note-title {
      font-family: var(--sgj-font-serif);
      font-weight: 500;
      font-size: 16px;
      color: var(--sgj-text);

      .public-tag {
        margin-left: 8px;
        font-weight: 400;
      }
    }
  }

  .note-preview {
    margin-top: 8px;
    font-size: 13px;
    color: var(--sgj-text-3);
    line-height: 1.6;
    max-height: 96px;
    overflow: hidden;
    position: relative;

    &::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      height: 32px;
      background: linear-gradient(transparent, var(--sgj-bg-card));
      pointer-events: none;
    }
  }

  .note-meta {
    margin-top: 10px;
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    font-size: 12px;
    color: var(--sgj-text-4);

    .note-tags {
      color: var(--sgj-amber);
    }
  }
}

.detail-content {
  .detail-public {
    margin-bottom: 8px;
  }

  .detail-link,
  .detail-tags {
    font-size: 13px;
    color: var(--sgj-primary-light);
    margin-bottom: 8px;
  }

  .detail-body {
    background: var(--sgj-bg);
    border-radius: var(--sgj-radius-md);
    padding: 16px;
    line-height: 1.7;
    font-size: var(--sgj-font-base);
    color: var(--sgj-text);
    max-height: 560px;
    overflow-y: auto;
  }

  /*  ①：抽屉内操作区 */
  .detail-actions {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }

  .detail-time {
    margin-top: 16px;
    font-size: var(--sgj-font-xs);
    color: var(--sgj-text-4);
    text-align: right;
  }
}

/* 移动端最小适配：搜索框占满整行、过滤提示与卡片标题行可换行 */
@media (max-width: 768px) {
  .toolbar {
    justify-content: flex-start;

    .el-input {
      width: 100% !important;
    }
  }

  .filter-tip {
    flex-wrap: wrap;
    gap: 6px;
  }

  .note-header {
    flex-wrap: wrap;
    gap: 6px;
  }
}
</style>