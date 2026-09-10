<template>
  <div v-loading="loading" class="front-home">
    <div v-if="loadError" class="load-error">
      <span>加载失败，请稍后重试</span>
      <el-button size="small" round @click="getData">重试</el-button>
    </div>
    <template v-else>
    <section class="hero">
      <div class="hero-blob hero-blob-1" aria-hidden="true"></div>
      <div class="hero-blob hero-blob-2" aria-hidden="true"></div>
      <div class="hero-text">
        <h1 class="anim" :style="{ '--d': '0ms' }">{{ greeting }}，拾光人</h1>
        <p class="hero-sub anim" :style="{ '--d': '32ms' }">你已经记录了 {{ data?.summary?.allTotal || 0 }} 件值得记住的小事，</p>
        <p class="hero-sub anim" :style="{ '--d': '64ms' }">日子正在被你慢慢写成照片。</p>
        <div class="hero-actions">
          <!-- 旅行足迹地图入口卡：点击直达地图模式 -->
          <div class="hero-map-entry anim" :style="{ '--d': '96ms' }" @click="goTravelMap">
            <span class="hero-map-icon">✈</span>
            <span class="hero-map-body">
              <span class="hero-map-text">旅行足迹地图</span>
              <span class="hero-map-sub">{{ mapSummary }}</span>
            </span>
            <span class="hero-map-arrow">↘</span>
          </div>
          <button class="hero-cta anim" :style="{ '--d': '128ms' }" type="button" @click="scrollToTimeline">开始记录 →</button>
        </div>
      </div>
      <div class="hero-stats">
        <div class="stat-item anim" :style="{ '--d': '160ms' }">
          <span class="stat-icon">影</span>
          <span class="stat-value">{{ doneTotal('MOVIE') + doneTotal('TV') }}</span>
          <span class="stat-label">看过 · 电影/剧集</span>
        </div>
        <div class="stat-item anim" :style="{ '--d': '192ms' }">
          <span class="stat-icon">书</span>
          <span class="stat-value">{{ doneTotal('BOOK') }}</span>
          <span class="stat-label">读过 · 书籍</span>
        </div>
        <div class="stat-item anim" :style="{ '--d': '224ms' }">
          <span class="stat-icon">地</span>
          <span class="stat-value">{{ doneTotal('PLACE') }}</span>
          <span class="stat-label">去过 · 地点</span>
        </div>
        <div class="stat-item anim" :style="{ '--d': '256ms' }">
          <span class="stat-icon">文</span>
          <span class="stat-value">{{ data?.summary?.noteTotal || 0 }}</span>
          <span class="stat-label">写过 · 笔记</span>
        </div>
        <router-link v-if="isLogin" to="/note" class="wish-bar anim" :style="{ '--d': '288ms' }">
          <span class="wish-bar-icon">愿</span>
          <span class="wish-bar-num">{{ data?.summary?.wishTotal || 0 }}</span>
          <span class="wish-bar-label">心愿 · 想去 / 想读</span>
          <span class="wish-bar-more">待完成 →</span>
        </router-link>
      </div>
    </section>

    <div class="home-content">
      <!-- 沉浸式分段标题 + 胶片齿孔 -->
      <div class="section-head">
        <h2 class="section-title">我的时光</h2>
        <div class="section-strip" aria-hidden="true">
          <span v-for="i in 16" :key="i" class="perf" />
        </div>
        <div class="section-sub">EVERYDAY MEMOIR</div>
      </div>

      <section class="timeline-section">
        <div v-if="!timelineGroups.length" class="empty-tip">还没有完成记录，去添加一些想看/想读/想去的事情吧。</div>
        <div v-for="(group, gi) in timelineGroups" :key="group.date" class="timeline-group">
          <div class="timeline-date">{{ groupDateLabel(group.date) }}</div>
          <div class="timeline-rail">
            <div v-for="(item, ii) in group.items" :key="item.itemId" class="timeline-item" @click="openItemDetail(item)">
              <span class="tl-dot" aria-hidden="true"></span>
              <div class="timeline-card">
                <img
                  v-if="item.coverUrl && item.itemId !== undefined && !brokenCovers[item.itemId]"
                  :src="photoUrl(item.coverUrl)"
                  class="timeline-thumb"
                  :alt="item.title"
                  loading="lazy"
                  @error="markCoverBroken(item.itemId)"
                />
                <span v-else class="timeline-cover" :style="coverStyle(gi * 2 + ii)">{{ coverGlyph(item.itemType) }}</span>
                <div class="timeline-body">
                  <div class="timeline-title">{{ item.title }}</div>
                  <div class="timeline-meta">{{ typeName(item.itemType) }}{{ metaSuffix(item) }}</div>
                  <span v-if="item.rating" class="timeline-rating">★ {{ item.rating }}</span>
                  <div v-if="item.comment" class="timeline-comment">{{ item.comment }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页加载：渐进式“加载更多”，保持时间线的阅读节奏 -->
        <div v-if="hasMoreTimeline" class="timeline-more">
          <button class="timeline-more-btn" type="button" :disabled="loadingMore" @click="loadMoreTimeline">
            {{ loadingMore ? '加载中…' : '展开更多时光 ↓' }}
          </button>
          <span class="timeline-more-count">已展示 {{ timelineItems.length }} / 共 {{ timelineTotal }} 条</span>
        </div>
        <div v-else-if="timelineItems.length" class="timeline-end">—— 已到底部 · 共 {{ timelineTotal }} 条时光 ——</div>
      </section>

      <aside class="side-section">
        <section v-if="isLogin" class="wishlist-card">
          <h2 class="card-title">心愿单 · WISH LIST</h2>
          <div v-if="!data?.wishlist || !data.wishlist.length" class="empty-tip">暂无心愿</div>
          <div v-for="item in data?.wishlist || []" :key="item.itemId" class="wish-item">
            <span class="wish-icon" :class="'wish-icon-' + item.itemType">{{ wishGlyph(item.itemType) }}</span>
            <div class="wish-body">
              <div class="wish-title">{{ item.title }}</div>
              <div class="wish-status">{{ wishLabel(item.itemType) }}</div>
            </div>
            <el-button link class="wish-complete" @click="handleComplete(item)">完成 →</el-button>
          </div>
        </section>

        <section class="notes-card">
          <h2 class="card-title">最近笔记 · NOTES</h2>
          <div v-if="!data?.recentNotes || !data.recentNotes.length" class="empty-tip">暂无笔记</div>
          <div v-for="note in data?.recentNotes || []" :key="note.noteId" class="note-item" @click="openNoteDetail(note)">
            <div class="note-title">{{ note.title }}</div>
            <div class="note-meta">
              {{ note.itemId ? '关联条目 #' + note.itemId : '独立笔记' }} · {{ formatDate(note.createTime) }}
            </div>
          </div>
          <router-link v-if="data?.recentNotes?.length" to="/note" class="note-more">查看全部笔记 →</router-link>
        </section>
      </aside>
    </div>
    </template>
  </div>
</template>

<script setup lang="ts" name="FrontHome">
import { getToken } from '@/utils/auth'
import { photoUrl } from '@/utils/sgj'
import { getFrontHomeData, listFrontHomeTimeline } from '@/api/front/home'
import { completeFrontItem } from '@/api/front/item'
import type { FrontHomeData } from '@/types/api/front/home'
import type { SgjItem } from '@/types/api/business/item'
import type { SgjNote } from '@/types/api/business/note'

/** 时间线每页条数（与后端 /app/home/index 首屏条数一致） */
const TIMELINE_PAGE_SIZE = 10

/** 是否登录（访客隐藏心愿单等个人数据） */
const isLogin = computed(() => !!getToken())

const { proxy } = getCurrentInstance() as { proxy: any }
const router = useRouter()

const data = ref<FrontHomeData | null>(null)
const loading = ref<boolean>(false)
const loadError = ref<boolean>(false)

/** 已完成时间线：首屏 + “加载更多”累积 */
const timelineItems = ref<SgjItem[]>([])
const timelineTotal = ref<number>(0)
const loadingMore = ref<boolean>(false)
const hasMoreTimeline = computed(() => timelineItems.value.length < timelineTotal.value)

/** 封面加载失败兜底：记录挂掉封面，改渲染衬线字色块 */
const brokenCovers = ref<Record<string | number, boolean>>({})

function markCoverBroken(itemId?: string | number): void {
  if (itemId !== undefined) brokenCovers.value[itemId] = true
}

/** 日期分组标签：设计稿为「YYYY / MM」衬线大字 */
function groupDateLabel(date: string): string {
  const m = /^(\d{4})-(\d{2})/.exec(date)
  return m ? `${m[1]} / ${m[2]}` : date
}

const typeNames: Record<string, string> = {
  MOVIE: '电影',
  TV: '电视剧',
  BOOK: '书籍',
  PLACE: '地点'
}

const typeName = (type?: string) => typeNames[type || ''] || ''

/** 时间线 meta 后缀：年份/导演/作者等兜底 */
function metaSuffix(item: SgjItem): string {
  if (item.itemType === 'MOVIE' || item.itemType === 'TV') {
    const parts = [item.director, item.releaseYear || item.startYear].filter(Boolean)
    return parts.length ? ' · ' + parts.join(' · ') : ''
  }
  if (item.itemType === 'BOOK') return item.author ? ' · ' + item.author : ''
  if (item.itemType === 'PLACE') {
    const parts = [item.city, item.country].filter(Boolean)
    return parts.length ? ' · ' + parts.join(' · ') : ''
  }
  return ''
}

/** 封面占位衬线字（设计稿：封面色块 + 衬线字） */
const coverGlyph = (type?: string): string => {
  switch (type) {
    case 'MOVIE': return '影'
    case 'TV': return '剧'
    case 'BOOK': return '书'
    case 'PLACE': return '旅'
    default: return '拾'
  }
}

const wishGlyph = (type?: string): string => {
  switch (type) {
    case 'MOVIE': return '影'
    case 'TV': return '剧'
    case 'BOOK': return '书'
    case 'PLACE': return '地'
    default: return '愿'
  }
}

/** 封面占位色块：按索引轮换灰陶浅/鼠尾草浅/灰铜浅/中性 */
const coverStyle = (index: number): Record<string, string> => {
  const soft = ['var(--sgj-primary-soft)', 'var(--sgj-moss-soft)', 'var(--sgj-amber-soft)', 'var(--sgj-cover)']
  const ink = ['var(--sgj-primary-dark)', 'var(--sgj-moss)', 'var(--sgj-amber)', 'var(--sgj-text-2)']
  const i = index % soft.length
  return { background: soft[i], color: ink[i] }
}

/** 地图入口副标题 */
const mapSummary = '87 个地点 · 把远方连成一条线'

/** 条目类型 → 前台列表页路径（时间线点击跳转用） */
const typePaths: Record<string, string> = {
  MOVIE: '/movie',
  TV: '/movie',
  BOOK: '/book',
  PLACE: '/travel'
}

/**
 *  最小方案：时间线条目点击 → 跳转对应类型列表页并携带
 * ?itemId= 自动打开该条详情（列表页已具备抽屉打开逻辑）。
 */
function openItemDetail(item: SgjItem): void {
  if (!item.itemId) return
  const path = typePaths[item.itemType || ''] || '/movie'
  router.push({ path, query: { itemId: String(item.itemId) } })
}

/**  最近笔记点击 → 跳转笔记页并携带 ?noteId= 自动打开详情抽屉 */
function openNoteDetail(note: SgjNote): void {
  if (!note.noteId) return
  router.push({ path: '/note', query: { noteId: String(note.noteId) } })
}

/**  旅行足迹地图入口卡：直达 /travel?mode=map */
function goTravelMap(): void {
  router.push({ path: '/travel', query: { mode: 'map' } })
}

/** 开始记录：平滑滚动到时间线区块 */
function scrollToTimeline(): void {
  const el = document.querySelector('.home-content')
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function doneTotal(type: string): number {
  return data.value?.summary?.done?.[type] || 0
}

function wishLabel(type?: string): string {
  if (type === 'BOOK') return '想读'
  if (type === 'PLACE') return '想去'
  return '想看'
}

/** 标记完成按钮文案：看过/读过/去过 */
function doneLabel(type?: string): string {
  if (type === 'BOOK') return '读过'
  if (type === 'PLACE') return '去过'
  return '看过'
}

/** 一键标记完成：默认完成日期取当天，成功后从心愿单移除并刷新 */
function handleComplete(item: SgjItem): void {
  if (!item.itemId) return
  const now = new Date()
  const finishDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  completeFrontItem(item.itemId, { finishDate }).then(() => {
    proxy.$modal.msgSuccess(`已标记${doneLabel(item.itemType)}`)
    if (data.value?.wishlist) {
      data.value.wishlist = data.value.wishlist.filter(w => w.itemId !== item.itemId)
    }
    getData()
  }).catch(() => {})
}

function formatDate(time?: string): string {
  if (!time) return ''
  return time.slice(0, 10)
}

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早安'
  if (hour < 18) return '午安'
  return '晚安'
})

const timelineGroups = computed(() => {
  const map = new Map<string, SgjItem[]>()
  for (const item of timelineItems.value) {
    const key = item.finishDate || formatDate(item.updateTime) || '未知日期'
    if (!map.has(key)) {
      map.set(key, [])
    }
    map.get(key)!.push(item)
  }
  return Array.from(map.entries())
    .map(([date, items]) => ({ date, items }))
    .sort((a, b) => b.date.localeCompare(a.date))
})

/** 加载更多：按当前已展示条数推算下一页，追加到时间线 */
function loadMoreTimeline(): void {
  if (loadingMore.value || !hasMoreTimeline.value) return
  loadingMore.value = true
  const nextPage = Math.floor(timelineItems.value.length / TIMELINE_PAGE_SIZE) + 1
  listFrontHomeTimeline({ pageNum: nextPage, pageSize: TIMELINE_PAGE_SIZE }).then(response => {
    const rows = response.data || []
    timelineItems.value = timelineItems.value.concat(rows)
    if (response.total) {
      timelineTotal.value = Number(response.total)
    }
  }).catch(() => {}).finally(() => {
    loadingMore.value = false
  })
}

function getData(): void {
  loading.value = true
  loadError.value = false
  getFrontHomeData().then(response => {
    data.value = response.data || null
    timelineItems.value = response.data?.timeline || []
    timelineTotal.value = response.data?.timelineTotal || timelineItems.value.length
  }).catch(() => {
    loadError.value = true
  }).finally(() => {
    loading.value = false
  })
}

getData()
</script>

<style scoped lang="scss">
.front-home {
  color: var(--sgj-text-2);
  font-family: var(--sgj-font-sans);
}

.load-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 0;
  color: var(--sgj-text-4);
}

/* 进入动效（设计稿：240ms · 同组错峰 32ms · prefers-reduced-motion 降级） */
.anim {
  opacity: 0;
  animation: sgj-rise 0.24s cubic-bezier(0.23, 1, 0.32, 1) forwards;
  animation-delay: var(--d, 0ms);
}

@keyframes sgj-rise {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .anim {
    animation-duration: 0.16s;
  }
}

/* ===== Hero：左文案 + 右统计矩阵（设计稿） ===== */
.hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 452px;
  gap: 48px;
  padding: 56px 0 24px;
  margin-bottom: 24px;

  .hero-blob {
    position: absolute;
    border-radius: 50%;
    pointer-events: none;
  }

  .hero-blob-1 {
    right: 96px;
    top: 0;
    width: 340px;
    height: 340px;
    background: var(--sgj-primary-soft);
  }

  .hero-blob-2 {
    right: -40px;
    top: 260px;
    width: 160px;
    height: 160px;
    background: var(--sgj-moss-soft);
  }

  .hero-text {
    position: relative;
    z-index: 1;
    align-self: center;

    h1 {
      margin: 0 0 14px;
      color: var(--sgj-text);
      font-family: var(--sgj-font-serif);
      font-size: 52px;
      font-weight: 700;
      line-height: 1.4;
    }

    .hero-sub {
      margin: 0;
      color: var(--sgj-text-2);
      font-family: var(--sgj-font-serif);
      font-size: 20px;
      line-height: 1.35;
    }

    .hero-actions {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-top: 36px;
      flex-wrap: wrap;
    }
  }

  /* 地图入口卡：320x62 胶囊 */
  .hero-map-entry {
    display: inline-flex;
    align-items: center;
    gap: 12px;
    padding: 0 22px;
    height: 62px;
    background: var(--sgj-bg-card);
    border: 1px solid var(--sgj-border-card);
    border-radius: 31px;
    cursor: pointer;
    transition: transform 0.14s cubic-bezier(0.23, 1, 0.32, 1);
    box-shadow: var(--sgj-shadow-sm);

    .hero-map-icon {
      font-size: 20px;
      color: var(--sgj-primary);
    }

    .hero-map-body {
      display: flex;
      flex-direction: column;
      line-height: 1.25;
    }

    .hero-map-text {
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 14px;
      color: var(--sgj-text);
    }

    .hero-map-sub {
      font-size: 11px;
      color: var(--sgj-text-3);
      margin-top: 2px;
    }

    .hero-map-arrow {
      font-size: 17px;
      color: var(--sgj-primary);
    }

    &:hover {
      transform: translateY(-2px);
    }
  }

  /* 主 CTA：160x48 灰陶红胶囊 */
  .hero-cta {
    border: none;
    cursor: pointer;
    padding: 0 30px;
    height: 48px;
    border-radius: 24px;
    background: var(--sgj-primary);
    color: #fff;
    font-size: 13px;
    font-weight: 500;
    letter-spacing: 1px;
    transition: background 0.14s, transform 0.14s cubic-bezier(0.23, 1, 0.32, 1);

    &:hover {
      background: var(--sgj-primary-dark);
      transform: translateY(-2px);
    }

    &:active {
      transform: scale(0.97);
    }
  }

  /* 统计矩阵：2x2 + 宽心愿条 */
  .hero-stats {
    position: relative;
    z-index: 1;
    display: grid;
    grid-template-columns: repeat(2, 216px);
    grid-template-rows: repeat(2, auto) auto;
    justify-content: end;
    gap: 18px;

    .stat-item {
      background: var(--sgj-bg-card);
      border: 1px solid var(--sgj-border-card);
      border-radius: 16px;
      padding: 20px;
      width: 216px;
      box-sizing: border-box;

      .stat-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 40px;
        height: 40px;
        border-radius: 12px;
        font-family: var(--sgj-font-serif);
        font-weight: 700;
        font-size: 18px;
        color: var(--sgj-primary-dark);
        background: var(--sgj-primary-soft);
      }

      .stat-value {
        display: block;
        margin-top: 14px;
        font-family: var(--sgj-font-serif);
        font-weight: 700;
        font-size: 34px;
        line-height: 1.2;
        color: var(--sgj-text);
      }

      .stat-label {
        display: block;
        margin-top: 6px;
        font-size: 13px;
        color: var(--sgj-text-3);
      }
    }

    /* 心愿条：452x96 灰铜 */
    .wish-bar {
      grid-column: 1 / -1;
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 20px;
      background: var(--sgj-bg-card);
      border: 1px solid var(--sgj-border-card);
      border-radius: 18px;
      text-decoration: none;
      transition: box-shadow 0.16s;

      &:hover {
        box-shadow: var(--sgj-shadow-hover);
      }

      .wish-bar-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 56px;
        height: 56px;
        border-radius: 14px;
        font-family: var(--sgj-font-serif);
        font-weight: 700;
        font-size: 20px;
        color: var(--sgj-amber);
        background: var(--sgj-amber-soft);
        flex-shrink: 0;
      }

      .wish-bar-num {
        font-family: var(--sgj-font-serif);
        font-weight: 700;
        font-size: 30px;
        color: var(--sgj-amber);
      }

      .wish-bar-label {
        flex: 1;
        font-size: 12px;
        color: var(--sgj-text-3);
      }

      .wish-bar-more {
        font-size: 12px;
        font-weight: 500;
        color: var(--sgj-primary);
        white-space: nowrap;
      }
    }
  }
}

/* ===== 沉浸式分段标题 ===== */
.home-content {
  scroll-margin-top: 96px;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;

  .section-title {
    font-family: var(--sgj-font-serif);
    font-size: 30px;
    font-weight: 700;
    color: var(--sgj-text);
    margin: 0;
    flex-shrink: 0;
  }

  .section-strip {
    flex: 1;
    display: flex;
    gap: 14px;
    justify-content: center;
    min-width: 0;

    .perf {
      width: 26px;
      height: 10px;
      border-radius: 5px;
      background: var(--sgj-bg-deep);
      flex-shrink: 0;
    }
  }

  .section-sub {
    font-size: 11px;
    letter-spacing: 4px;
    color: var(--sgj-text-4);
    flex-shrink: 0;
  }
}

/* ===== 时间线：日期 左栏 + 轨道圆点 + 卡片（设计稿） ===== */
.timeline-section {
  min-width: 0;
}

/* 加载更多 / 到底提示 */
.timeline-more {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 8px 0 12px;

  .timeline-more-btn {
    padding: 0 34px;
    height: 44px;
    border-radius: 22px;
    background: transparent;
    border: 1px solid var(--sgj-primary);
    color: var(--sgj-primary);
    font-size: 13px;
    font-weight: 500;
    letter-spacing: 2px;
    cursor: pointer;
    transition: background 0.16s, color 0.16s, transform 0.16s cubic-bezier(0.23, 1, 0.32, 1);

    &:hover:not(:disabled) {
      background: var(--sgj-primary);
      color: #fff;
      transform: translateY(-2px);
    }

    &:active:not(:disabled) {
      transform: scale(0.97);
    }

    &:disabled {
      opacity: 0.6;
      cursor: default;
    }
  }

  .timeline-more-count {
    font-size: 11px;
    letter-spacing: 1px;
    color: var(--sgj-text-4);
  }
}

.timeline-end {
  text-align: center;
  padding: 4px 0 12px;
  font-size: 12px;
  letter-spacing: 1.5px;
  color: var(--sgj-text-4);
}

.timeline-group {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 36px;
  width: 100%;
  margin-bottom: 48px;

  .timeline-date {
    text-align: right;
    font-family: var(--sgj-font-serif);
    font-weight: 700;
    font-size: 22px;
    color: var(--sgj-text);
    padding-top: 8px;
    white-space: nowrap;
  }

  .timeline-rail {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 24px;
    padding-left: 30px;

    &::before {
      content: '';
      position: absolute;
      left: 9px;
      top: 0;
      bottom: 0;
      width: 2px;
      background: var(--sgj-border);
    }
  }

  .timeline-item {
    position: relative;

    .tl-dot {
      position: absolute;
      left: -30px;
      top: 26px;
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: var(--sgj-primary);

      &::after {
        content: '';
        position: absolute;
        left: 6px;
        top: 6px;
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: var(--sgj-bg-card);
      }
    }

    .timeline-card {
      position: relative;
      z-index: 1;
      display: flex;
      gap: 14px;
      background: var(--sgj-bg-card);
      border: 1px solid var(--sgj-border-card);
      border-radius: 16px;
      padding: 14px;
      cursor: pointer;
      transition: box-shadow 0.16s, transform 0.16s;

      &:hover {
        box-shadow: var(--sgj-shadow-hover);
        transform: translateY(-2px);
      }

      /* 封面色块：120x120 */
      .timeline-cover {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 120px;
        height: 120px;
        border-radius: 12px;
        flex-shrink: 0;
        font-family: var(--sgj-font-serif);
        font-size: 40px;
        line-height: 1;
      }

      .timeline-thumb {
        width: 120px;
        height: 120px;
        object-fit: cover;
        border-radius: 12px;
        flex-shrink: 0;
      }

      .timeline-body {
        flex: 1;
        min-width: 0;

        .timeline-title {
          font-family: var(--sgj-font-serif);
          font-weight: 700;
          font-size: 16px;
          color: var(--sgj-text);
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .timeline-meta {
          font-size: 12px;
          color: var(--sgj-text-3);
          margin-top: 6px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        /* 评分：灰铜浅底胶囊 */
        .timeline-rating {
          display: inline-block;
          margin-top: 12px;
          padding: 1px 10px;
          border-radius: 11px;
          background: var(--sgj-amber-soft);
          color: var(--sgj-amber);
          font-family: var(--sgj-font-serif);
          font-weight: 700;
          font-size: 12px;
        }

        .timeline-comment {
          font-size: 12px;
          color: var(--sgj-text-2);
          margin-top: 10px;
          line-height: 1.5;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }
  }
}

/* ===== 右侧：心愿单 / 最近笔记（radius 20） ===== */
.side-section {
  display: none;
}

/* 桌面端：区块标题横贯顶部，时间线居左、心愿/笔记固定在右侧栏（设计稿） */
@media (min-width: 1101px) {
  .home-content {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 360px;
    grid-template-areas:
      'head head'
      'timeline side';
    gap: 0 40px;
    align-items: start;
  }

  .section-head {
    grid-area: head;
  }

  .timeline-section {
    grid-area: timeline;
    min-width: 0;
  }

  .timeline-group {
    grid-template-columns: 120px minmax(0, 1fr);
  }

  .side-section {
    grid-area: side;
    display: flex;
    flex-direction: column;
    gap: 24px;
    position: sticky;
    top: 96px;
  }
}

.card-title {
  font-family: var(--sgj-font-serif);
  font-size: 16px;
  font-weight: 700;
  color: var(--sgj-text);
  margin: 0 0 16px;
}

.wishlist-card,
.notes-card {
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: 20px;
  padding: 24px;
  box-shadow: var(--sgj-shadow-sm);
}

.wish-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--sgj-border-card);

  &:last-child {
    border-bottom: none;
  }

  .wish-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: 10px;
    font-family: var(--sgj-font-serif);
    font-weight: 700;
    font-size: 14px;
    color: var(--sgj-primary-dark);
    background: var(--sgj-primary-soft);
    flex-shrink: 0;

    &.wish-icon-BOOK {
      color: var(--sgj-moss);
      background: var(--sgj-moss-soft);
    }

    &.wish-icon-PLACE {
      color: var(--sgj-amber);
      background: var(--sgj-amber-soft);
    }
  }

  .wish-body {
    flex: 1;
    min-width: 0;

    .wish-title {
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 14px;
      color: var(--sgj-text);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .wish-status {
      font-size: 11px;
      color: var(--sgj-text-3);
      margin-top: 2px;
    }
  }

  .wish-complete {
    white-space: nowrap;
    flex-shrink: 0;
    font-size: 12px;
    color: var(--sgj-primary);
  }
}

.note-item {
  padding: 8px 0;
  border-bottom: 1px solid var(--sgj-border-card);
  cursor: pointer;

  &:last-child {
    border-bottom: none;
  }

  .note-title {
    font-family: var(--sgj-font-serif);
    font-weight: 500;
    font-size: 14px;
    color: var(--sgj-text);
  }

  .note-meta {
    font-size: 11px;
    color: var(--sgj-text-3);
    margin-top: 4px;
  }
}

.note-more {
  display: block;
  text-align: right;
  margin-top: 12px;
  font-size: 12px;
  color: var(--sgj-primary);
  text-decoration: none;
}

.empty-tip {
  color: var(--sgj-text-4);
  text-align: center;
  padding: 30px 0;
  font-size: 13px;
}

@media (max-width: 1100px) {
  .hero {
    grid-template-columns: 1fr;
    gap: 8px;

    .hero-blob-1 {
      right: 40px;
      width: 220px;
      height: 220px;
    }

    .hero-text h1 {
      font-size: 40px;
    }
  }

  .hero-stats {
    margin-top: 40px;
  }

  /* 窄屏改为横向滚动？不——改为 2 列网格 */
  .hero-stats {
    grid-template-columns: repeat(2, 1fr);
    width: 100%;

    .stat-item {
      width: auto;
    }
  }

  .timeline-group {
    grid-template-columns: 120px minmax(0, 1fr);
    gap: 20px;
  }

  .section-head .section-strip {
    display: none;
  }
}

/* 移动端（设计稿 04）：hero 改冷炭灰横幅，隐藏统计矩阵 / 心愿条，标题 28px */
@media (max-width: 768px) {
  .hero {
    padding: 24px 20px;
    border-radius: 16px;
    background: #282e2c;
    display: block;

    .hero-blob {
      display: none;
    }

    .hero-text h1 {
      font-size: 28px;
      color: #e7ece9;
    }

    .hero-sub {
      font-size: 13px;
      color: #aeb8b3;
    }

    .hero-actions {
      margin-top: 20px;
    }

    .hero-map-entry {
      border-color: var(--sgj-border);
    }

    .hero-map-text {
      color: #e7ece9;
    }

    .hero-cta {
      padding: 0 22px;
    }

    .hero-stats {
      display: none;
    }
  }

  .section-head .section-sub {
    display: none;
  }

  .timeline-group {
    grid-template-columns: 96px minmax(0, 1fr);
    gap: 12px;

    .timeline-date {
      font-size: 17px;
    }
  }

  /* 与基础规则 .timeline-group .timeline-item .timeline-card 保持同级优先级才能覆盖 */
  .timeline-group .timeline-item .timeline-card {
    padding: 10px;

    .timeline-cover,
    .timeline-thumb {
      width: 64px;
      height: 64px;
      font-size: 24px;
    }
  }
}
</style>