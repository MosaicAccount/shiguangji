<template>
  <div class="note-detail-page page-detail">
    <div class="detail-layout">
      <div class="detail-main">
        <div v-loading="loading" class="article-card hd-paper hd-grain">
          <span class="hd-tape" aria-hidden="true"></span>
          <template v-if="note">
            <!-- 文章头：冷炭灰胶片带，与笔记列表页 banner 同视觉语言；返回与操作收进带内保持页面整洁 -->
            <header class="article-hero">
              <span class="hero-blob" aria-hidden="true"></span>
              <span class="hero-blob2" aria-hidden="true"></span>
              <div class="hero-top">
                <el-button class="hero-btn" circle :icon="ArrowLeft" title="返回列表" @click="goBack" />
                <div v-if="isLogin" class="hero-actions">
                  <el-button v-if="canExport" class="hero-btn" circle :icon="Download" title="导出 Markdown" @click="handleExport" />
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
            <!-- 正文点击交给事件委托：互链的 a.note-link 在这里接管跳转（DOM 是渲染后注入的，无法逐个绑事件） -->
            <div ref="bodyRef" class="article-body" @click="onBodyClick">
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
              <markdown-viewer :content="note?.body ?? ''" />
              <div class="article-end" aria-hidden="true">· 完 ·</div>
              <!-- 反向链接（0.0.5 FR-005-2）：正文末尾统一位置，桌面/移动一致；无来源时整块不渲染 -->
              <note-backlinks :backlinks="note?.backlinks" :same-title-count="note?.sameTitleCount" />
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
import { ArrowDown, ArrowLeft, Delete, Download, EditPen, List } from '@element-plus/icons-vue'
import MarkdownViewer from '@/components/MarkdownViewer/index.vue'
import NoteBacklinks from '@/components/NoteBacklinks/index.vue'
import { getFrontNote, delFrontNote, noteExportUrl } from '@/api/front/note'
import { applyKeywordHighlights, clearKeywordHighlights, splitByKeyword } from '@/utils/sgj'
import { linkifyNoteTokens } from '@/utils/noteLinks'
import type { SgjNote } from '@/types/api/business/note'
import { getToken } from '@/utils/auth'
import useUserStore from '@/store/modules/user'

const { proxy } = getCurrentInstance() as { proxy: any }
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 是否登录（访客只读，登录后可管理） */
const isLogin = computed(() => !!getToken())
/**
 * 导出入口的显隐：登录 + 这篇是我的（或管理员）。
 * 口径与后端 canOperate 一致（create_by 存的是登录名，userStore.name 就是它）；
 * 显隐只是观感，真正的门在导出接口自己（不靠前端藏按钮）
 */
const canExport = computed(() =>
  isLogin.value && (note.value?.createBy === userStore.name || userStore.roles.includes('admin'))
)

const loading = ref(false)
const note = ref<SgjNote | null>(null)

/** 逗号拼接标签拆成胶囊展示 */
const tagList = computed(() =>
  (note.value?.tags || '').split(',').map(tag => tag.trim()).filter(Boolean)
)

/** 正文预处理（前言剥离与标题去重）已由后端下发 body 字段，见 SgjNoteServiceImpl.buildBody */

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
    // 互链先于大纲与关键词高亮：高亮会把文本节点切进 mark，切完再找 raw 就找不全
    linkifyNoteTokens(root.querySelector('.markdown-viewer') as HTMLElement | null, note.value?.links)
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
  // 高亮重开会切开文本节点，先补一次互链（幂等：已替换的 raw 不再存在于文本里）
  linkifyNoteTokens(bodyRoot, note.value?.links)
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

/**
 * 互链跳转是「同组件换 query」（/note/detail?noteId=A → ?noteId=B），Vue 会复用组件实例，
 * 必须监听 noteId 重载——0.0.3 之前没有这条路径，漏掉的表现是点了链接地址变了、内容不变。
 */
watch(() => route.query.noteId, (value, old) => {
  if (!value || value === old || !Number(value)) return
  loadNote()
})

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

/**
 * 正文点击的事件委托：互链的 a.note-link 是渲染后注入的 DOM，无法逐个绑事件。
 * 跳转不带旧关键词——新篇不带上一篇的搜索高亮打开。
 */
function onBodyClick(event: MouseEvent): void {
  const target = event.target as Element | null
  const anchor = target?.closest?.('a.note-link') as HTMLAnchorElement | null
  if (!anchor) return
  event.preventDefault()
  const noteId = Number(anchor.dataset.noteId)
  if (noteId) {
    router.push({ path: '/note/detail', query: { noteId: String(noteId) } })
  }
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

/** 导出为 Markdown 文件（服务端现场生成，带 noteId 的 front-matter，改完可以再导回来） */
function handleExport(): void {
  const noteId = note.value?.noteId
  if (!noteId) return
  proxy.$download.file(noteExportUrl(noteId))
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
/* 0.0.6 笔记详情：长文阅读页——纸面 + 手绘标题线 + 目录，正文排版由 MarkdownViewer 承担 */
.note-detail-page {
  max-width: 1100px;
  margin: 0 auto;
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 780px) 214px;
  gap: 54px;
  justify-content: center;
  align-items: start;
  margin-top: 8px;
}
.detail-main { min-width: 0 }
.detail-aside { position: sticky; top: 96px }

.article-card {
  position: relative;
  padding: 34px 52px 44px;
  border-radius: 4px;
  box-shadow: var(--hd-shadow-lift);
  scrollbar-width: none;
}
.article-card::-webkit-scrollbar { width: 0; height: 0; display: none }
.article-card::after {
  content: "";
  position: absolute;
  inset: -2px;
  pointer-events: none;
  border: 2.3px solid var(--hd-line);
  border-radius: inherit;
  filter: url(#rough);
}
.article-card > .hd-tape {
  top: -12px;
  left: 50%;
  transform: translateX(-50%) rotate(-2deg);
  width: 112px;
}
.article-hero {
  position: relative;
  padding: 8px 6px 22px;
  border-bottom: 2px dashed var(--hd-line-soft);
}
.hero-blob, .hero-blob2 { display: none }

.hero-top { display: flex; align-items: center; gap: 8px; flex-wrap: wrap }
.hero-actions { margin-left: auto; display: flex; gap: 4px }
.hero-btn {
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1.8px solid transparent;
  border-radius: 9px 3px 8px 3px / 3px 8px 3px 9px;
  background: none;
  color: var(--hd-ink-soft);
  &:hover {
    border-color: var(--hd-line-soft);
    color: var(--hd-red);
    background: none;
  }
}
.hero-actions .hero-btn:last-child:hover,
.hero-actions .hero-btn:last-child:focus { color: var(--hd-red); border-color: var(--hd-line-soft) }

.article-hero .hero-tags { margin-top: 18px; display: flex; gap: 8px; flex-wrap: wrap }
.hero-tag {
  font-size: 13px;
  padding: 2px 10px;
  color: var(--hd-ink-soft);
  border: 1.5px solid var(--hd-line-soft);
  border-radius: 9px 3px 8px 3px / 3px 8px 3px 9px;
}
.article-hero .hero-title {
  margin-top: 12px;
  font-size: 40px;
  line-height: 1.32;
  letter-spacing: 1.5px;
  color: var(--hd-ink);
}
.article-hero .hero-meta {
  margin-top: 16px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 14px;
  font-size: 14px;
  color: var(--hd-ink-soft);
}
.hero-link { color: var(--hd-red) }
.hero-time { color: var(--hd-ink-faint) }
.kw-title-hit { background: var(--hd-amber-soft); color: var(--hd-amber-ink) }

.article-body { margin-top: 22px }
.article-end {
  margin-top: 46px;
  text-align: center;
  font-size: 14px;
  letter-spacing: 5px;
  color: var(--hd-ink-faint);
}

/* 桌面大纲 */
.outline-card {
  padding: 16px 18px;
  background-color: var(--hd-paper-2);
  border: 1.6px dashed var(--hd-line-soft);
  border-radius: 5px 2px 6px 3px / 3px 6px 2px 5px;
  box-shadow: var(--hd-shadow-sm);
}
.outline-title { font-size: 15px; letter-spacing: 2px; color: var(--hd-ink-soft) }
.outline-list { margin: 10px 0 0; padding: 0; list-style: none }
.outline-item {
  display: flex;
  gap: 8px;
  padding: 5px 0;
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--hd-ink-soft);
  text-decoration: none;
  &.is-sub { padding-left: 16px; font-size: 13px }
  &.is-active { color: var(--hd-red) }
  &:hover { color: var(--hd-red) }
}
.item-index { color: var(--hd-ink-soft); font-variant-numeric: tabular-nums }

/* 移动内嵌目录 */
.outline-inline { display: none }
.outline-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  font-size: 14px;
  color: var(--hd-ink-soft);
  background: var(--hd-paper-2);
  border: 1.5px dashed var(--hd-line-soft);
  border-radius: 4px 2px 5px 3px / 3px 5px 2px 4px;
}
.toggle-count, .toggle-current { font-size: 13px; color: var(--hd-ink-faint) }
.toggle-arrow { margin-left: auto; transition: transform .15s; &.is-open { transform: rotate(180deg) } }
.inline-list { margin: 10px 0 0; padding: 0 0 0 4px }

/* 移动浮动目录 */
.panel-mask { position: fixed; inset: 0; z-index: 300; background: rgba(20, 18, 16, .35) }
.outline-fab {
  position: fixed;
  right: 16px;
  bottom: 84px;
  z-index: 320;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  font-size: 13.5px;
  color: var(--hd-ink);
  background-color: var(--hd-paper);
  border: 2px solid var(--hd-line);
  border-radius: 12px 4px 11px 5px / 5px 11px 4px 12px;
  box-shadow: var(--hd-shadow);
  &.is-open { color: var(--hd-red) }
}
.outline-panel {
  position: fixed;
  left: 12px;
  right: 12px;
  bottom: 136px;
  z-index: 310;
  max-height: 52vh;
  overflow: auto;
  padding: 14px 16px;
  background-color: var(--hd-paper);
  border: 2px solid var(--hd-line);
  border-radius: 6px 3px 7px 3px / 3px 7px 3px 6px;
}
.panel-header { display: flex; align-items: baseline; gap: 10px; font-size: 15px; color: var(--hd-ink-soft) }
.panel-count { font-size: 13px; color: var(--hd-ink-faint) }
.panel-list { margin-top: 8px }
.panel-list .outline-item { padding: 7px 0; font-size: 14px }

/* 搜索承接条 */
.kw-toolbar {
  position: sticky;
  bottom: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: max-content;
  margin: 18px auto 0;
  padding: 6px 12px;
  font-size: 13.5px;
  color: var(--hd-ink-soft);
  background-color: var(--hd-paper);
  border: 1.6px solid var(--hd-line-soft);
  border-radius: 10px 4px 9px 4px / 4px 9px 4px 10px;
  box-shadow: var(--hd-shadow-sm);
}
.kw-btn {
  padding: 2px 8px;
  font-size: 13.5px;
  color: var(--hd-ink-soft);
  background: none;
  border: 1.4px solid var(--hd-line-soft);
  border-radius: 6px 2px 5px 2px / 2px 5px 2px 6px;
  &:disabled { opacity: .45 }
}
.kw-count { font-variant-numeric: tabular-nums; color: var(--hd-ink) }
.kw-off, .kw-on { color: var(--hd-red); border-color: var(--hd-red-soft) }

@media (max-width: 1100px) {
  .detail-layout { grid-template-columns: minmax(0, 1fr) }
  .article-card { padding: 24px 22px 32px }
  .detail-aside { display: none }
  .outline-inline { display: block; margin-top: 20px }
}
@media (max-width: 720px) {
  .article-hero .hero-title { font-size: 28px }
  .article-hero { padding: 4px 2px 18px }
}
</style>
