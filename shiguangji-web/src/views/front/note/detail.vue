<template>
  <div class="note-detail-page">
    <div class="detail-layout">
      <div class="detail-main">
        <div v-loading="loading" class="article-card">
          <template v-if="note">
            <!-- 文章头：冷炭灰胶片带，与笔记列表页 banner 同视觉语言；返回与操作收进带内保持页面整洁 -->
            <header class="article-hero">
              <span class="hero-blob" aria-hidden="true"></span>
              <span class="hero-blob2" aria-hidden="true"></span>
              <div class="hero-top">
                <el-button class="hero-btn" circle :icon="ArrowLeft" title="返回列表" @click="goBack" />
                <div v-if="isLogin" class="hero-actions">
                  <el-button class="hero-btn" circle :icon="EditPen" title="编辑" @click="openEdit" />
                  <el-button class="hero-btn" circle :icon="Delete" title="删除" @click="handleDelete" />
                </div>
              </div>
              <div v-if="tagList.length" class="hero-tags">
                <span v-for="tag in tagList" :key="tag" class="hero-tag">{{ tag }}</span>
              </div>
              <h1 ref="titleRef" class="hero-title">
                <!-- 只命中标题时标题也高亮（与列表卡片同款切段渲染，不用 DOM 注入） -->
                <template v-for="(seg, si) in titleSegments" :key="si">
                  <mark v-if="seg.hit" class="kw-title-hit">{{ seg.text }}</mark>
                  <template v-else>{{ seg.text }}</template>
                </template>
              </h1>
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

            <!-- 正文：文章排版，行宽与行距为长文阅读优化；顶部内嵌可折叠目录（移动端主要导航，桌面端隐藏走侧栏） -->
            <div ref="bodyRef" class="article-body">
              <div v-if="headings.length" class="outline-inline">
                <button class="outline-toggle" @click="outlineOpen = !outlineOpen">
                  <el-icon><List /></el-icon>
                  <span class="toggle-title">目录</span>
                  <span class="toggle-count">{{ headings.length }} 个标题</span>
                  <span v-if="!outlineOpen && activeItem" class="toggle-current">{{ activeItem.text }}</span>
                  <el-icon class="toggle-arrow" :class="{ 'is-open': outlineOpen }"><ArrowDown /></el-icon>
                </button>
                <ul v-show="outlineOpen" class="outline-list inline-list">
                  <li v-for="item in headings" :key="item.id">
                    <a
                      :class="['outline-item', { 'is-active': activeId === item.id, 'is-sub': item.level > 2 }]"
                      :href="`#${item.id}`"
                      @click.prevent="jumpTo(item)"
                    >
                      <span v-if="item.no" class="item-index">{{ String(item.no).padStart(2, '0') }}</span>
                      <span class="item-text">{{ item.text }}</span>
                    </a>
                  </li>
                </ul>
              </div>
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
              >
                <span v-if="item.no" class="item-index">{{ String(item.no).padStart(2, '0') }}</span>
                <span class="item-text">{{ item.text }}</span>
              </a>
            </li>
          </ul>
        </div>
      </aside>
    </div>

    <!-- 移动端浮动目录：内嵌目录滚出视野后出现，点开紧凑面板跳转（桌面端隐藏走侧栏） -->
    <template v-if="headings.length">
      <div v-show="panelOpen" class="panel-mask" @click="panelOpen = false"></div>
      <button v-show="!tocVisible" :class="['outline-fab', { 'is-open': panelOpen }]" @click="panelOpen = !panelOpen">
        <el-icon><List /></el-icon>
        <span>{{ panelOpen ? '收起' : '目录' }}</span>
      </button>
      <div v-show="panelOpen" class="outline-panel">
        <div class="panel-header">
          <span class="panel-title">目录</span>
          <span class="panel-count">{{ headings.length }} 个标题</span>
        </div>
        <ul class="outline-list panel-list">
          <li v-for="item in headings" :key="item.id">
            <a
              :class="['outline-item', { 'is-active': activeId === item.id, 'is-sub': item.level > 2 }]"
              :href="`#${item.id}`"
              @click.prevent="jumpTo(item)"
            >
              <span v-if="item.no" class="item-index">{{ String(item.no).padStart(2, '0') }}</span>
              <span class="item-text">{{ item.text }}</span>
            </a>
          </li>
        </ul>
      </div>
    </template>
    <!-- 搜索承接（issue #31）：带关键词进入时全文高亮，工具条提供上一处/下一处跳转与关闭 -->
    <div v-if="routeKeyword && hitTotal > 0" class="kw-toolbar">
      <template v-if="highlightOn">
        <button class="kw-btn" title="上一处" :disabled="hitTotal < 2" @click="gotoHit(-1)">‹</button>
        <span class="kw-count">{{ currentIndex + 1 }}/{{ hitTotal }}</span>
        <button class="kw-btn" title="下一处" :disabled="hitTotal < 2" @click="gotoHit(1)">›</button>
        <button class="kw-btn kw-off" title="关闭高亮" @click="closeHighlight">✕ 关闭高亮</button>
      </template>
      <button v-else class="kw-btn kw-on" title="开启高亮" @click="openHighlight">⌕ 开启高亮</button>
    </div>
  </div>
</template>

<script setup lang="ts" name="FrontNoteDetail">
import { ArrowDown, ArrowLeft, Delete, EditPen, List } from '@element-plus/icons-vue'
import MarkdownViewer from '@/components/MarkdownViewer/index.vue'
import { getFrontNote, delFrontNote } from '@/api/front/note'
import { applyKeywordHighlights, clearKeywordHighlights, splitByKeyword } from '@/utils/sgj'
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

/** 大纲（issue #26）：渲染完成后从正文 DOM 提取 h1-h3 并回填锚点 id；no 为章节编号（仅 h1/h2） */
interface OutlineItem {
  id: string
  text: string
  level: number
  no?: number
}
const bodyRef = ref<HTMLElement | null>(null)
const headings = ref<OutlineItem[]>([])
/** 内嵌目录展开状态（移动端；桌面端走侧栏不受影响） */
const outlineOpen = ref(false)
/** 浮动目录面板展开状态 */
const panelOpen = ref(false)
/** 内嵌目录是否在视口内：在视野中时隐藏浮动入口，避免两份目录同时出现 */
const tocVisible = ref(true)
/** 当前滚动所在章节（大纲高亮） */
const activeId = ref('')
const activeItem = computed(() => headings.value.find(item => item.id === activeId.value) || null)

watch(note, () => {
  nextTick(() => {
    const root = bodyRef.value
    if (!root) {
      headings.value = []
      return
    }
    let chapter = 0
    headings.value = Array.from(root.querySelectorAll('h1, h2, h3'))
      .map((el, index) => {
        el.id = `note-h-${index}`
        return { id: el.id, text: (el.textContent || '').trim(), level: Number(el.tagName.slice(1)) }
      })
      .filter(item => item.text)
      .map(item => (item.level > 2 ? item : { ...item, no: ++chapter }))
  })
})

/** 搜索承接（issue #31）：路由带 keyword 时全文高亮，工具条跳转与开关 */
const routeKeyword = computed(() => String(route.query.keyword || '').trim())
const highlightOn = ref(false)
/** 用户手动关闭后，路由关键词不再自动拉起高亮 */
const userClosed = ref(false)
const hitEls = ref<HTMLElement[]>([])
const hitTotal = ref(0)
const currentIndex = ref(0)
const titleRef = ref<HTMLElement | null>(null)

/** 标题切段：高亮开启时按关键词切（与列表卡片同款字面量切分），关闭时整段原样 */
const titleSegments = computed(() =>
  splitByKeyword(note.value?.title, highlightOn.value ? routeKeyword.value : '')
)

// 直连详情时组件可能先于路由解析完成挂载（此时 query 为空），须监听 routeKeyword 就绪后自动开启
watch(routeKeyword, kw => {
  if (kw && !userClosed.value) highlightOn.value = true
}, { immediate: true })

function applyHighlight(): void {
  // 只在正文渲染区高亮：内嵌目录的条目文本与正文重复，标亮目录反而是噪音
  const bodyRoot = bodyRef.value?.querySelector('.markdown-viewer') as HTMLElement | null
  clearKeywordHighlights(bodyRoot)
  if (!highlightOn.value) {
    hitEls.value = []
    hitTotal.value = 0
    return
  }
  const bodyHits = applyKeywordHighlights(bodyRoot, routeKeyword.value)
  // 标题命中在前、正文在后（文档序）；标题标记由模板渲染，正文标记由 DOM 注入产生
  const titleMarks = titleRef.value
    ? Array.from(titleRef.value.querySelectorAll<HTMLElement>('mark.kw-title-hit'))
    : []
  hitEls.value = [...titleMarks, ...bodyHits]
  hitTotal.value = hitEls.value.length
  currentIndex.value = 0
  if (hitEls.value.length) {
    hitEls.value[0].classList.add('is-current')
    // 进入详情即到达第一处；块居中可避开吸顶导航
    hitEls.value[0].scrollIntoView({ block: 'center' })
  }
}

function gotoHit(delta: number): void {
  if (!hitEls.value.length) return
  currentIndex.value = (currentIndex.value + delta + hitEls.value.length) % hitEls.value.length
  const el = hitEls.value[currentIndex.value]
  hitEls.value.forEach((m, i) => m.classList.toggle('is-current', i === currentIndex.value))
  el.scrollIntoView({ block: 'center', behavior: 'smooth' })
}

function closeHighlight(): void {
  userClosed.value = true
  highlightOn.value = false
}

function openHighlight(): void {
  userClosed.value = false
  highlightOn.value = true
}

watch([note, highlightOn, routeKeyword], () => nextTick(applyHighlight))

/** 点击目录：滚动到对应标题（顶部让出 72px 吸顶导航 + 余量），并立即高亮 */
const OUTLINE_TOP_OFFSET = 90
function jumpTo(item: OutlineItem): void {
  const el = document.getElementById(item.id)
  if (!el) return
  const max = Math.max(document.documentElement.scrollHeight - window.innerHeight, 0)
  const target = Math.min(el.getBoundingClientRect().top + window.scrollY - OUTLINE_TOP_OFFSET, max)
  const startY = window.scrollY
  window.scrollTo({ top: target, behavior: 'smooth' })
  // 兜底：部分内嵌 webview 帧调度暂停导致 smooth 动画不推进，150ms 未起步则瞬时跳转
  window.setTimeout(() => {
    if (Math.abs(window.scrollY - startY) < 1 && Math.abs(target - startY) >= 1) {
      window.scrollTo(0, target)
    }
  }, 150)
  activeId.value = item.id
  panelOpen.value = false
}

/** 滚动高亮：取视口上部（导航下方）最后越线的标题；触底时高亮最后一项；同步内嵌目录可见性 */
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
  const tocRect = bodyRef.value?.querySelector('.outline-inline')?.getBoundingClientRect()
  tocVisible.value = !tocRect || (tocRect.bottom > 0 && tocRect.top < window.innerHeight)
}

onMounted(() => window.addEventListener('scroll', updateActive, { passive: true }))
onBeforeUnmount(() => window.removeEventListener('scroll', updateActive))
watch(headings, () => nextTick(updateActive))

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
  padding: 22px 40px 30px;
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
}

/* 带内顶行：返回 + 操作（幽灵圆钮，避免与大纲侧栏顶部对齐打架） */
.hero-top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;

  .hero-actions {
    display: flex;
    gap: 10px;
  }

  .hero-btn {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(255, 255, 255, 0.22);
    color: #e7ece9;

    &:hover,
    &:focus {
      background: rgba(255, 255, 255, 0.16);
      border-color: rgba(255, 255, 255, 0.4);
      color: #fff;
    }
  }

  .hero-actions .hero-btn:last-child:hover,
  .hero-actions .hero-btn:last-child:focus {
    color: #f2b3a6;
  }
}

.article-hero .hero-tags {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;

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

.article-hero .hero-title {
  position: relative;
  z-index: 1;
  margin: 0;
  font-family: var(--sgj-font-serif);
  font-weight: 700;
  font-size: 30px;
  line-height: 1.4;
  word-break: break-word;

  /* 深色标题带上的命中标记（琥珀半透明底，当前处主题色反白） */
  mark.kw-title-hit {
    background: rgba(177, 132, 88, 0.32);
    color: #f0e0c8;
    border-radius: 3px;
    padding: 0 2px;

    &.is-current {
      background: var(--sgj-primary);
      color: #fff;
    }
  }
}

.article-hero .hero-meta {
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

.outline-card {
  max-height: calc(100vh - 140px);
  overflow: auto;
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: var(--sgj-radius-lg);
  padding: 14px 10px;

  .outline-title {
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 2px;
    color: var(--sgj-text-3);
    margin: 2px 10px 8px;
  }
}

/* 大纲条目：圆角行 + 章节编号，当前章节陶红浅底高亮（桌面侧栏与移动端弹层共用） */
.outline-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.outline-item {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 100%;
  padding: 7px 10px;
  border-radius: var(--sgj-radius-md);
  font-size: 13px;
  line-height: 1.5;
  color: var(--sgj-text-2);
  text-decoration: none;
  transition: background 0.16s, color 0.16s;

  .item-index {
    flex-shrink: 0;
    font-size: 11px;
    letter-spacing: 0.5px;
    color: var(--sgj-text-4);
    font-variant-numeric: tabular-nums;
  }

  .item-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &:hover {
    background: var(--sgj-bg);
    color: var(--sgj-text);
  }

  &.is-active {
    background: var(--sgj-primary-soft);
    color: var(--sgj-primary);
    font-weight: 600;

    .item-index {
      color: var(--sgj-primary);
    }
  }

  &.is-sub {
    padding-left: 35px;
    font-size: 12.5px;
  }
}

/* 内嵌目录（移动端）：正文开头的可折叠块，全宽无遮挡；桌面端隐藏走侧栏 */
.outline-inline {
  margin-bottom: 24px;
  background: var(--sgj-bg);
  border: 1px solid var(--sgj-border-card);
  border-radius: var(--sgj-radius-lg);
  overflow: hidden;
}

.outline-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 12px 14px;
  border: none;
  background: transparent;
  color: var(--sgj-text);
  font-size: 14px;
  cursor: pointer;

  .toggle-title {
    font-weight: 600;
  }

  .toggle-count {
    font-size: 12px;
    color: var(--sgj-text-4);
  }

  .toggle-current {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    text-align: right;
    font-size: 12px;
    color: var(--sgj-primary);
  }

  .toggle-arrow {
    flex-shrink: 0;
    color: var(--sgj-text-3);
    transition: transform 0.2s;

    &.is-open {
      transform: rotate(180deg);
    }
  }
}

.inline-list {
  max-height: 46vh;
  overflow: auto;
  padding: 4px 8px 10px;
  border-top: 1px solid var(--sgj-border-card);
}

/* 目录滚动区隐藏滚动条（保留滚动能力），观感更干净 */
.inline-list,
.panel-list,
.outline-card {
  scrollbar-width: none;
  -ms-overflow-style: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

/* 桌面端隐藏内嵌目录（侧栏已提供） */
@media (min-width: 1200px) {
  .outline-inline {
    display: none;
  }
}

/* 移动端浮动目录：内嵌目录滚出视野后的入口 + 紧凑面板（不整屏遮挡） */
.panel-mask {
  position: fixed;
  inset: 0;
  z-index: 98;
  background: rgba(23, 27, 26, 0.2);
}

.outline-fab {
  position: fixed;
  right: 16px;
  bottom: calc(76px + env(safe-area-inset-bottom));
  z-index: 99;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 16px;
  border: none;
  border-radius: var(--sgj-radius-pill);
  background: #282e2c;
  color: #e7ece9;
  font-size: 13px;
  letter-spacing: 1px;
  box-shadow: var(--sgj-shadow-hover);
  cursor: pointer;

  &:active {
    transform: scale(0.96);
  }

  @media (min-width: 1200px) {
    display: none;
  }
}

/* 夜间模式：页面深灰与胶片色胶囊几乎同色，改用亮色胶囊反色保证可辨识 */
html.dark .outline-fab {
  background: var(--sgj-text);
  color: var(--sgj-bg);
}

.outline-panel {
  position: fixed;
  right: 16px;
  bottom: calc(124px + env(safe-area-inset-bottom));
  z-index: 99;
  display: flex;
  flex-direction: column;
  width: min(320px, calc(100vw - 32px));
  max-height: 52vh;
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: var(--sgj-radius-lg);
  box-shadow: var(--sgj-shadow-hover);
  overflow: hidden;

  @media (min-width: 1200px) {
    display: none;
  }

  .panel-header {
    display: flex;
    align-items: baseline;
    gap: 8px;
    padding: 12px 14px 10px;
    border-bottom: 1px solid var(--sgj-border-card);

    .panel-title {
      font-family: var(--sgj-font-serif);
      font-size: 16px;
      font-weight: 700;
      color: var(--sgj-text);
    }

    .panel-count {
      font-size: 12px;
      color: var(--sgj-text-4);
    }
  }

  .panel-list {
    overflow: auto;
    padding: 6px 8px 10px;
  }
}

/* 搜索承接：全文命中标记（mark 为运行时注入，须 :deep 才能命中）；当前处用主题色反白 */
.article-body {
  :deep(mark.kw-hit) {
    background: var(--sgj-amber-soft);
    color: var(--sgj-amber);
    border-radius: 3px;
    padding: 0 1px;

    &.is-current {
      background: var(--sgj-primary);
      color: #fff;
    }
  }
}

/* 命中导航工具条：底部居中悬浮，视觉语言与目录浮动按钮同源；两种主题下均保持深色胶囊
   （正文/工具条都压在浅色卡片上时用深底最稳，夜间主题只加深底色并补边框区分层次） */
.kw-toolbar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(24px + env(safe-area-inset-bottom));
  z-index: 99;
  display: flex;
  align-items: center;
  gap: 2px;
  height: 38px;
  padding: 0 8px;
  border-radius: var(--sgj-radius-pill);
  background: #282e2c;
  border: 1px solid rgba(231, 236, 233, 0.12);
  color: #e7ece9;
  box-shadow: var(--sgj-shadow-hover);

  .kw-btn {
    display: inline-flex;
    align-items: center;
    height: 28px;
    min-width: 28px;
    padding: 0 9px;
    border: none;
    border-radius: var(--sgj-radius-pill);
    background: transparent;
    color: #e7ece9;
    font-size: 14px;
    cursor: pointer;

    &:hover:not(:disabled) {
      background: rgba(255, 255, 255, 0.12);
    }

    &:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
  }

  .kw-count {
    min-width: 58px;
    text-align: center;
    font-size: 12px;
    color: #aeb8b3;
    font-variant-numeric: tabular-nums;
  }

  .kw-off {
    font-size: 12px;
    letter-spacing: 0.5px;
    color: #f2b3a6;
  }

  .kw-on {
    gap: 4px;
    padding: 0 14px;
    font-size: 13px;
    letter-spacing: 1px;
  }
}

html.dark .kw-toolbar {
  background: var(--sgj-bg-deep);
  border-color: var(--sgj-border);
}

/* 移动端最小适配：收窄内边距、标题降级 */
@media (max-width: 768px) {
  .article-hero {
    padding: 16px 18px 20px;

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
