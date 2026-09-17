<template>
  <div class="note-detail-page">
    <div class="detail-layout">
      <div class="detail-main">
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
            <div ref="bodyRef" class="article-body">
              <markdown-viewer :content="bodyContent" />
              <div class="article-end" aria-hidden="true">· 完 ·</div>
            </div>
          </template>
        </div>
      </div>

      <!-- 桌面端大纲：文章右侧 sticky 跟随 -->
      <aside v-if="headings.length" class="detail-aside">
        <div class="outline-card">
          <div class="outline-title">大纲</div>
          <ul class="outline-list">
            <li v-for="item in headings" :key="item.id">
              <a
                :class="['outline-item', { 'is-active': activeId === item.id, 'is-sub': item.level > 2 }]"
                :href="`#${item.id}`"
                @click.prevent="jumpTo(item)"
              >{{ item.text }}</a>
            </li>
          </ul>
        </div>
      </aside>
    </div>

    <!-- 移动端大纲：浮动按钮 + 抽屉 -->
    <el-button v-if="headings.length" class="outline-fab" circle :icon="List" @click="outlineOpen = true" />
    <el-drawer v-model="outlineOpen" title="大纲" size="280px" append-to-body>
      <ul class="outline-list outline-drawer-body">
        <li v-for="item in headings" :key="item.id">
          <a
            :class="['outline-item', { 'is-active': activeId === item.id, 'is-sub': item.level > 2 }]"
            :href="`#${item.id}`"
            @click.prevent="jumpTo(item)"
          >{{ item.text }}</a>
        </li>
      </ul>
    </el-drawer>
  </div>
</template>

<script setup lang="ts" name="FrontNoteDetail">
import { ArrowLeft, List } from '@element-plus/icons-vue'
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

/** 大纲（issue #26）：渲染完成后从正文 DOM 提取 h1-h3 并回填锚点 id */
interface OutlineItem {
  id: string
  text: string
  level: number
}
const bodyRef = ref<HTMLElement | null>(null)
const headings = ref<OutlineItem[]>([])
const outlineOpen = ref(false)
/** 当前滚动所在章节（大纲高亮） */
const activeId = ref('')

watch(note, () => {
  nextTick(() => {
    const root = bodyRef.value
    if (!root) {
      headings.value = []
      return
    }
    headings.value = Array.from(root.querySelectorAll('h1, h2, h3'))
      .map((el, index) => {
        el.id = `note-h-${index}`
        return { id: el.id, text: (el.textContent || '').trim(), level: Number(el.tagName.slice(1)) }
      })
      .filter(item => item.text)
  })
})

/** 点击大纲：滚动到对应标题（顶部让出 72px 吸顶导航 + 余量），并立即高亮 */
const OUTLINE_TOP_OFFSET = 90
function jumpTo(item: OutlineItem): void {
  const el = document.getElementById(item.id)
  if (!el) return
  const top = el.getBoundingClientRect().top + window.scrollY - OUTLINE_TOP_OFFSET
  window.scrollTo({ top, behavior: 'smooth' })
  activeId.value = item.id
  outlineOpen.value = false
}

/** 滚动高亮：取视口上部（导航下方）最后越线的标题；触底时高亮最后一项 */
function updateActive(): void {
  if (!headings.value.length) return
  let current = ''
  for (const item of headings.value) {
    const el = document.getElementById(item.id)
    if (el && el.getBoundingClientRect().top <= OUTLINE_TOP_OFFSET + 10) current = item.id
  }
  const atBottom = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2
  if (atBottom) current = headings.value[headings.value.length - 1].id
  activeId.value = current
}

onMounted(() => window.addEventListener('scroll', updateActive, { passive: true }))
onBeforeUnmount(() => window.removeEventListener('scroll', updateActive))
watch(headings, () => nextTick(updateActive))

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
  margin: 0 auto;
  color: var(--sgj-text);
}

.detail-main {
  max-width: 860px;
  margin: 0 auto;
}

/* 大纲侧栏：仅桌面端（≥1200px）与文章并排，sticky 跟随滚动 */
.detail-aside {
  display: none;
}

@media (min-width: 1200px) {
  .detail-layout {
    display: flex;
    align-items: flex-start;
    justify-content: center;
    gap: 24px;
  }

  .detail-main {
    flex: 0 1 860px;
    min-width: 0;
    margin: 0;
  }

  .detail-aside {
    display: block;
    width: 200px;
    flex-shrink: 0;
    position: sticky;
    top: 96px;
  }
}

/* 大纲列表：桌面侧栏与移动端抽屉共用 */
.outline-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.outline-item {
  display: block;
  max-width: 100%;
  padding: 6px 10px;
  border-left: 2px solid transparent;
  font-size: 13px;
  line-height: 1.5;
  color: var(--sgj-text-3);
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.16s, border-color 0.16s;

  &:hover {
    color: var(--sgj-text);
  }

  &.is-active {
    color: var(--sgj-primary);
    border-left-color: var(--sgj-primary);
    font-weight: 500;
  }

  &.is-sub {
    padding-left: 26px;
  }
}

.outline-card {
  max-height: calc(100vh - 140px);
  overflow: auto;
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: var(--sgj-radius-lg);
  padding: 14px 12px 14px 16px;

  .outline-title {
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 2px;
    color: var(--sgj-text-3);
    margin-bottom: 8px;
  }
}

.outline-drawer-body {
  padding: 4px 4px 4px 12px;
}

/* 移动端大纲浮动按钮（桌面端隐藏，走侧栏） */
.outline-fab {
  position: fixed;
  right: 16px;
  bottom: 76px;
  z-index: 90;
  width: 42px;
  height: 42px;
  border-color: var(--sgj-border-card);
  background: var(--sgj-bg-card);
  color: var(--sgj-text-2);
  box-shadow: var(--sgj-shadow-hover);

  @media (min-width: 1200px) {
    display: none;
  }
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
