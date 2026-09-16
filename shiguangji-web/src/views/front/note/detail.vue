<template>
  <div class="note-detail-page">
    <!-- 顶栏：返回 + 登录态操作 -->
    <div class="detail-toolbar">
      <el-button class="back-btn" circle :icon="ArrowLeft" @click="goBack" />
      <div v-if="note && isLogin" class="toolbar-actions">
        <el-button type="primary" round size="small" @click="openEdit">编辑</el-button>
        <el-button type="danger" round size="small" @click="handleDelete">删除</el-button>
      </div>
    </div>

    <div v-loading="loading" class="article-card">
      <template v-if="note">
        <!-- 文章头：冷炭灰胶片带，与笔记列表页 banner 同视觉语言 -->
        <header class="article-hero">
          <span class="hero-blob" aria-hidden="true"></span>
          <span class="hero-blob2" aria-hidden="true"></span>
          <div v-if="tagList.length" class="hero-tags">
            <span v-for="tag in tagList" :key="tag" class="hero-tag">{{ tag }}</span>
          </div>
          <h1 class="hero-title">{{ note.title }}</h1>
          <div class="hero-meta">
            <!--  图标区分：🔗 关联笔记 / 📝 独立笔记 -->
            <span v-if="note.itemId" class="hero-link">🔗 {{ note.itemName || '#' + note.itemId }}</span>
            <span v-else class="hero-link">📝 独立笔记</span>
            <!-- 公开状态仅登录态展示 -->
            <el-tag v-if="isLogin" :type="note.isPublic === '1' ? 'success' : 'info'" size="small" effect="plain">
              {{ note.isPublic === '1' ? '公开' : '私密' }}
            </el-tag>
            <span class="hero-time">{{ formatTime(note.updateTime || note.createTime) }}</span>
          </div>
        </header>

        <!-- 正文：文章排版，行宽与行距为长文阅读优化 -->
        <div class="article-body">
          <markdown-viewer :content="bodyContent" />
          <div class="article-end" aria-hidden="true">· 完 ·</div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts" name="FrontNoteDetail">
import { ArrowLeft } from '@element-plus/icons-vue'
import MarkdownViewer from '@/components/MarkdownViewer/index.vue'
import { getFrontNote, delFrontNote } from '@/api/front/note'
import type { SgjNote } from '@/types/api/business/note'
import { getToken } from '@/utils/auth'

const { proxy } = getCurrentInstance() as { proxy: any }
const route = useRoute()
const router = useRouter()

/** 是否登录（访客只读，登录后可管理） */
const isLogin = computed(() => !!getToken())

const loading = ref(false)
const note = ref<SgjNote | null>(null)

/** 逗号拼接标签拆成胶囊展示 */
const tagList = computed(() =>
  (note.value?.tags || '').split(',').map(tag => tag.trim()).filter(Boolean)
)

/** 正文若以与标题相同的一级标题开头则去重，避免文章头与正文标题重复 */
const bodyContent = computed(() => {
  const content = note.value?.content
  const title = note.value?.title
  if (!content || !title) return content
  const escaped = title.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return content.replace(new RegExp(`^\\s*#\\s*${escaped}\\s*\\r?\\n`), '')
})

loadNote()

/** 按 ?noteId= 加载笔记，缺参/不存在/加载失败均回列表页 */
function loadNote(): void {
  const noteId = Number(route.query.noteId)
  if (!noteId) {
    router.replace('/note')
    return
  }
  loading.value = true
  getFrontNote(noteId).then(response => {
    if (!response.data) {
      proxy.$modal.msgError('笔记不存在或已删除')
      router.replace('/note')
      return
    }
    note.value = response.data
  }).catch(() => {
    router.replace('/note')
  }).finally(() => {
    loading.value = false
  })
}

/** 返回列表页 */
function goBack(): void {
  router.replace('/note')
}

/** 跳转独立编辑页，保存后由编辑页回列表 */
function openEdit(): void {
  if (!note.value?.noteId) return
  router.push({ path: '/note/edit', query: { noteId: String(note.value.noteId) } })
}

function handleDelete(): void {
  const noteId = note.value?.noteId
  if (!noteId) return
  proxy.$modal.confirm('是否确认删除该笔记？').then(() => {
    return delFrontNote(noteId)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    goBack()
  }).catch(() => {})
}

function formatTime(time?: string): string {
  if (!time) return ''
  return time.replace('T', ' ').slice(0, 16)
}
</script>

<style scoped lang="scss">
.note-detail-page {
  max-width: 860px;
  margin: 0 auto;
  color: var(--sgj-text);
}

.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;

  .back-btn {
    border-color: var(--sgj-border-card);
    background: var(--sgj-bg-card);
    color: var(--sgj-text-2);

    &:hover {
      border-color: var(--sgj-primary-light);
      color: var(--sgj-primary);
    }
  }

  .toolbar-actions {
    display: flex;
    gap: 8px;
  }
}

.article-card {
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: var(--sgj-radius-xl);
  box-shadow: var(--sgj-shadow-card);
  overflow: hidden;
  min-height: 320px;
}

/* 文章头：冷炭灰胶片带（亮暗主题下均保持深色，做法同列表页 banner） */
.article-hero {
  position: relative;
  padding: 34px 40px 30px;
  background: #282e2c;
  color: #e7ece9;
  overflow: hidden;

  .hero-blob {
    position: absolute;
    right: -50px;
    top: -110px;
    width: 260px;
    height: 260px;
    border-radius: 50%;
    background: rgba(168, 95, 82, 0.35);
    pointer-events: none;
  }

  .hero-blob2 {
    position: absolute;
    left: 180px;
    bottom: -100px;
    width: 190px;
    height: 190px;
    border-radius: 50%;
    background: rgba(93, 111, 102, 0.4);
    pointer-events: none;
  }

  .hero-tags {
    position: relative;
    z-index: 1;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 16px;

    .hero-tag {
      padding: 3px 12px;
      border-radius: var(--sgj-radius-pill);
      font-size: 12px;
      letter-spacing: 0.5px;
      color: #e3c9a0;
      background: rgba(177, 132, 88, 0.22);
      border: 1px solid rgba(198, 154, 103, 0.35);
    }
  }

  .hero-title {
    position: relative;
    z-index: 1;
    margin: 0;
    font-family: var(--sgj-font-serif);
    font-weight: 700;
    font-size: 30px;
    line-height: 1.4;
    word-break: break-word;
  }

  .hero-meta {
    position: relative;
    z-index: 1;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
    margin-top: 14px;
    font-size: 13px;
    color: #aeb8b3;

    .hero-link {
      padding: 2px 10px;
      border-radius: var(--sgj-radius-pill);
      background: rgba(255, 255, 255, 0.08);
    }
  }
}

html.dark .article-hero {
  background: var(--sgj-bg-deep);
}

/* 正文：比通用 markdown 排版更大字号与行距，适合长文阅读 */
.article-body {
  padding: 30px 40px 26px;

  :deep(.markdown-body) {
    font-size: 15px;
    line-height: 1.9;
    color: var(--sgj-text);

    :deep(h1),
    :deep(h2) {
      margin-top: 26px;
    }
  }

  .article-end {
    margin-top: 36px;
    text-align: center;
    font-size: 13px;
    letter-spacing: 4px;
    color: var(--sgj-text-4);
  }
}

/* 移动端最小适配：收窄内边距、标题降级 */
@media (max-width: 768px) {
  .article-hero {
    padding: 22px 18px 20px;

    .hero-title {
      font-size: 22px;
    }

    .hero-tags {
      margin-bottom: 12px;
    }
  }

  .article-body {
    padding: 20px 16px 18px;
  }
}
</style>
