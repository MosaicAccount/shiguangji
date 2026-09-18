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
        v-model="searchKeyword"
        placeholder="搜索标题与正文"
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
      <span>正在查看条目「{{ filterItemName || '#' + filterItemId }}」的关联笔记</span>
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
          <!-- 删除角标与标题首行对齐；编辑入口统一在详情页 -->
          <el-button
            v-if="isLogin"
            class="note-delete"
            circle
            size="small"
            :icon="Delete"
            title="删除"
            @click.stop="handleDelete(note)"
          />
        </div>
        <div v-if="note.content" class="note-preview">
          <markdown-viewer :content="previewContent(note)" />
        </div>
        <div class="note-meta">
          <!--  图标区分：🔗 关联笔记 / 📝 独立笔记 -->
          <span v-if="note.itemId" class="note-link">🔗 {{ note.itemName || '#' + note.itemId }}</span>
          <span v-else class="note-link">📝 独立笔记</span>
          <span v-if="note.tags" class="note-tags">{{ note.tags }}</span>
          <span class="note-time">{{ formatTime(note.updateTime || note.createTime) }}</span>
        </div>
      </div>
    </div>

    <div v-if="hasMore" class="load-more-wrap">
      <el-button :loading="loadingMore" round @click="loadMore">加载更多</el-button>
    </div>
  </div>
</template>

<script setup lang="ts" name="FrontNote">
import { getToken } from '@/utils/auth'
import { Delete } from '@element-plus/icons-vue'
import MarkdownViewer from '@/components/MarkdownViewer/index.vue'
import TagPills from '@/components/TagPills/index.vue'
import { listFrontNote, delFrontNote } from '@/api/front/note'
import { getFrontItem } from '@/api/front/item'
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
const searchKeyword = ref('')
/** 标签筛选（TagPills 点击选中、再点取消后触发 loadData） */
const searchTag = ref('')
/** 分页 */
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const hasMore = computed(() => list.value.length < total.value)
/** B-03：按关联条目过滤展示（从条目编辑弹窗"查看关联笔记"跳转而来） */
const filterItemId = ref<number | undefined>(undefined)
/** 过滤条目的名称（展示用，取不到时回退 #id） */
const filterItemName = ref('')

/** 组装列表查询参数；关键词不足 2 字时提示并中止（ngram_token_size=2，单字切不出 token 搜不到） */
function buildQuery() {
  const keyword = searchKeyword.value.trim()
  if (keyword && keyword.length < 2) {
    proxy.$modal.msgWarning('关键词请至少输入 2 个字')
    return null
  }
  return {
    keyword: keyword || undefined,
    tags: searchTag.value || undefined,
    itemId: filterItemId.value,
    pageNum: pageNum.value,
    pageSize: pageSize
  }
}

function loadData(): void {
  const query = buildQuery()
  if (!query) return
  loading.value = true
  loadError.value = false
  pageNum.value = 1
  listFrontNote(query).then(response => {
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
  pageNum.value += 1
  const query = buildQuery()
  if (!query) {
    pageNum.value -= 1
    return
  }
  loadingMore.value = true
  listFrontNote(query).then(response => {
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
  filterItemName.value = ''
  loadData()
}

/** 按条目过滤时回查条目名称用于展示 */
function fetchFilterItemName(itemId: number): void {
  getFrontItem(itemId).then(response => {
    filterItemName.value = response.data?.title || ''
  }).catch(() => {
    filterItemName.value = ''
  })
}

function formatTime(time?: string): string {
  if (!time) return ''
  return time.replace('T', ' ').slice(0, 16)
}

/** 预览正文：与标题相同的首个一级标题去重，避免卡片标题与预览重复 */
function previewContent(note: SgjNote): string {
  const content = note.content || ''
  if (!note.title) return content
  const escaped = note.title.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return content.replace(new RegExp(`^\\s*#\\s*${escaped}\\s*\\r?\\n`), '')
}

/** 跳转独立编辑页（新增/编辑共用，验收：单独的 markdown 编辑页面） */
function openAdd(): void {
  router.push('/note/edit')
}/**
 * B-03/：处理路由参数
 * - /note?itemId=x&write=1：条目页"去写笔记"跳转，转独立编辑页并预填关联条目
 * - /note?itemId=x：条目编辑弹窗"查看关联笔记"跳转，列表按该条目过滤展示
 * - /note?noteId=x：旧版详情链接（首页最近笔记曾用），重定向到独立详情页
 */
function handleRouteQuery(): void {
  const rawNoteId = route.query.noteId
  if (rawNoteId) {
    const noteId = Number(rawNoteId)
    router.replace(noteId ? { path: '/note/detail', query: { noteId: String(noteId) } } : { path: '/note', query: {} })
    return
  }
  const raw = route.query.itemId
  if (!raw) return
  const itemId = Number(raw)
  if (!itemId) return
  if (route.query.write === '1') {
    // 写笔记语义：仅登录用户跳转独立编辑页，预填关联条目
    if (isLogin.value) {
      router.replace({ path: '/note/edit', query: { itemId: String(itemId) } })
      return
    }
  } else {
    // 查看语义：按条目过滤笔记列表
    filterItemId.value = itemId
    fetchFilterItemName(itemId)
    loadData()
  }
  router.replace({ path: '/note', query: {} })
}

watch(
  () => route.query.itemId,
  () => handleRouteQuery()
)

/** 跳转独立详情页（验收：笔记内容单独页面展示，不再用抽屉） */
function openDetail(note: SgjNote): void {
  if (!note.noteId) return
  router.push({ path: '/note/detail', query: { noteId: String(note.noteId) } })
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
    align-items: flex-start;
    gap: 10px;

    .note-title {
      flex: 1;
      min-width: 0;
      font-family: var(--sgj-font-serif);
      font-weight: 500;
      font-size: 16px;
      line-height: 24px;
      color: var(--sgj-text);
      word-break: break-word;

      .public-tag {
        margin-left: 8px;
        font-weight: 400;
      }
    }

    /* 删除角标：与标题首行垂直居中对齐，不随长标题换行 */
    .note-delete {
      flex-shrink: 0;
      margin-top: 0;
      border-color: var(--sgj-border-card);
      background: var(--sgj-bg);
      color: var(--sgj-text-3);

      &:hover {
        border-color: var(--sgj-danger);
        background: var(--sgj-bg-card);
        color: var(--sgj-danger);
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
}
</style>