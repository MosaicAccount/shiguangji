<template>
  <div v-loading="loading" class="front-home page-home">
    <div v-if="loadError" class="load-error">
      <span>加载失败，请稍后重试</span>
      <el-button size="small" round @click="getData">重试</el-button>
    </div>
    <template v-else>
      <section class="hero">
        <div class="hero-card hd-rough">
          <span class="hd-tape"></span>
          <span class="hd-tape right amber"></span>
          <div class="hero-note" aria-hidden="true">
            <div class="n-day">{{ todayMonthDay }}</div>
            <div class="n-wd">{{ todayWeekday }} · 手账第 {{ dayOfYear }} 天</div>
            <div class="n-say">今天也记一笔。</div>
            <svg viewBox="0 0 42 32" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M8 26 Q6 14 16 10 Q26 6 30 14 Q33 21 24 22 Q18 23 18 17"/><path d="M30 14 Q36 12 36 6"/></svg>
          </div>
          <div class="hero-eyebrow">一个人的手账 · {{ currentYear }}</div>
          <h1 class="hero-title">{{ greeting }}，拾光人<span class="hd-dot">。</span></h1>
          <p class="hero-sub">你已经记录了 <b>{{ data?.summary?.allTotal || 0 }}</b> 件值得记住的小事，日子正在被你慢慢写成照片。</p>
          <div class="hero-actions">
            <button class="hero-cta" type="button" @click="scrollToTimeline">开始记录 <span aria-hidden="true">→</span></button>
            <a class="entry-card" href="#" @click.prevent="goTravelMap">
              <span class="ico" aria-hidden="true">✈</span>
              <span><span class="t">旅行足迹地图</span><br><span class="s">{{ mapSummary }}</span></span>
              <span class="arr" aria-hidden="true">↘</span>
            </a>
            <a v-if="isLogin" class="entry-card" href="#" @click.prevent="goReview">
              <span class="ico" aria-hidden="true">✦</span>
              <span><span class="t">年度回顾</span><br><span class="s">把一年攒成一面墙</span></span>
              <span class="arr" aria-hidden="true">↘</span>
            </a>
          </div>
        </div>

        <div class="stat-strip">
          <div class="stat hd-rough"><span class="glyph">影</span><div class="num">{{ doneTotal('MOVIE') + doneTotal('TV') }}</div><div class="label">看过 · 电影 / 剧集</div></div>
          <div class="stat hd-rough"><span class="glyph">书</span><div class="num">{{ doneTotal('BOOK') }}</div><div class="label">读过 · 书籍</div></div>
          <div class="stat hd-rough"><span class="glyph">地</span><div class="num">{{ doneTotal('PLACE') }}</div><div class="label">去过 · 地点</div></div>
          <div class="stat hd-rough"><span class="glyph">文</span><div class="num">{{ data?.summary?.noteTotal || 0 }}</div><div class="label">写过 · 笔记</div></div>
        </div>

        <router-link v-if="isLogin" to="/plan" class="wish-note hd-rough">
          <span class="seal">愿</span>
          <span class="big">{{ wishBarTotal }}</span>
          <span class="txt">心愿 · 在想 {{ data?.summary?.wishTotal || 0 }} / 在做 {{ data?.summary?.doingTotal || 0 }}</span>
          <span class="more">待完成 →</span>
        </router-link>
      </section>

      <div class="content">
        <section id="timeline">
          <div class="section-head">
            <h2>我的时光</h2>
            <span class="line" aria-hidden="true"></span>
            <span class="en">EVERYDAY MEMOIR</span>
          </div>

          <div v-if="!timelineGroups.length" class="hd-empty">
            <p>还没有完成记录 —— 去添加一些想看 / 想读 / 想去的事情吧。</p>
            <div class="acts"><router-link class="hd-btn go" to="/movie">去影单</router-link></div>
          </div>

          <div v-else class="timeline">
            <div v-for="group in timelineGroups" :key="group.date" class="tl-group">
              <div class="tl-date">
                <b>{{ groupMonthDay(group.date) }}</b>{{ groupWeekday(group.date) }}
                <span class="wd">{{ groupYear(group.date) }}</span>
              </div>
              <div class="tl-rail">
                <a
                  v-for="item in group.items"
                  :key="item.itemId"
                  class="tl-item"
                  href="#"
                  @click.prevent="openItemDetail(item)"
                >
                  <div class="tl-card hd-rough">
                    <span class="tl-cover">
                      <img
                        v-if="item.coverUrl && item.itemId !== undefined && !brokenCovers[item.itemId]"
                        :src="photoUrl(item.coverUrl)"
                        :alt="item.title"
                        loading="lazy"
                        @error="markCoverBroken(item.itemId)"
                      />
                      <span v-else class="hd-fallback sm" :class="coverTypeClass(item.itemType)">
                        <span class="fc-body"><span class="fc-title">{{ item.title }}</span></span>
                        <span class="fc-motif"></span>
                      </span>
                    </span>
                    <div class="tl-body">
                      <div class="tl-title">
                        {{ item.title }}
                        <span class="hd-st done">{{ doneLabel(item.itemType) }}</span>
                      </div>
                      <div class="tl-meta">
                        <span>{{ typeName(item.itemType) }}{{ metaSuffix(item) }}</span>
                        <span v-if="item.rating" class="tl-stars" :aria-label="`评分 ${item.rating} 分，满分 5 分`">{{ '★'.repeat(item.rating) }}</span>
                      </div>
                      <div v-if="item.comment" class="tl-comment">{{ item.comment }}</div>
                    </div>
                  </div>
                </a>
              </div>
            </div>

            <div v-if="hasMoreTimeline" class="tl-more">
              <button class="hd-btn" type="button" :disabled="loadingMore" @click="loadMoreTimeline">
                {{ loadingMore ? '加载中…' : '展开更多时光 ↓' }}
              </button>
              <span class="hd-tiny hd-muted">已展示 {{ timelineItems.length }} / 共 {{ timelineTotal }} 条</span>
            </div>
            <div v-else-if="timelineItems.length" class="tl-end">—— 已到底部 · 共 {{ timelineTotal }} 条时光 ——</div>
          </div>
        </section>

        <aside class="aside">
          <section v-if="isLogin" class="hd-paper">
            <span class="hd-tape left"></span>
            <h2 class="card-title">心愿单 <span class="en">WISH LIST</span></h2>
            <div v-if="!wishlist.length" class="empty-tip">暂无心愿</div>
            <div v-for="item in wishlist" :key="item.itemId" class="wish-item">
              <span class="hd-glyph-round" :class="coverTypeClass(item.itemType)">{{ wishGlyph(item.itemType) }}</span>
              <div class="wish-body">
                <div class="wish-title">{{ item.title }}</div>
                <div class="wish-meta">
                  <span class="hd-st" :class="{ doing: item.status === 'DOING' }">{{ statusText(item) }}</span>
                  <plan-date-badge :item="item" @saved="loadWishlist" />
                </div>
              </div>
              <button class="wish-done" type="button" @click="openComplete(item)">完成 →</button>
            </div>
            <router-link v-if="wishlist.length" to="/plan" class="more">查看全部心愿 →</router-link>
          </section>

          <section class="hd-paper">
            <span class="hd-tape right"></span>
            <h2 class="card-title">最近笔记 <span class="en">NOTES</span></h2>
            <div v-if="!data?.recentNotes?.length" class="empty-tip">暂无笔记</div>
            <a
              v-for="note in data?.recentNotes || []"
              :key="note.noteId"
              class="note-item"
              href="#"
              @click.prevent="openNoteDetail(note)"
            >
              <div class="note-title">{{ note.title }}</div>
              <div class="note-meta">{{ note.itemId ? '关联条目：' + (note.itemName || '#' + note.itemId) : '独立笔记' }} · {{ formatDate(note.createTime) }}</div>
            </a>
            <router-link v-if="data?.recentNotes?.length" to="/note" class="more">查看全部笔记 →</router-link>
          </section>
        </aside>
      </div>
    </template>

    <item-complete-dialog v-model="completeOpen" :item="completeItem" @submitted="onCompleted" />
  </div>
</template>

<script setup lang="ts" name="FrontHome">
import { getToken } from '@/utils/auth'
import { photoUrl } from '@/utils/sgj'
import { itemStatusLabel } from '@/utils/itemStatus'
import { getFrontHomeData, listFrontHomeTimeline } from '@/api/front/home'
import { listPlanItems } from '@/api/front/item'
import ItemCompleteDialog from '@/components/ItemCompleteDialog/index.vue'
import PlanDateBadge from '@/components/PlanDateBadge/index.vue'
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

/** 心愿单完成弹窗（统一组件） */
const completeOpen = ref(false)
const completeItem = ref<SgjItem | null>(null)

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

/**  最近笔记点击 → 跳转独立笔记详情页 */
function openNoteDetail(note: SgjNote): void {
  if (!note.noteId) return
  router.push({ path: '/note/detail', query: { noteId: String(note.noteId) } })
}

/**  旅行足迹地图入口卡：直达 /travel?mode=map */
function goTravelMap(): void {
  router.push({ path: '/travel', query: { mode: 'map' } })
}

/** 年度回顾（私人内容，路由守卫兜底；此处仅登录态渲染入口） */
function goReview(): void {
  router.push('/review')
}

/** 开始记录：平滑滚动到时间线区块 */
function scrollToTimeline(): void {
  const el = document.querySelector('.home-content')
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function doneTotal(type: string): number {
  return data.value?.summary?.done?.[type] || 0
}

/** 心愿单状态文案：字典优先、中文兜底（utils/itemStatus.ts），不再写死一份措辞 */
function statusText(item: SgjItem): string {
  return itemStatusLabel(item)
}

/** 心愿条数字：在想 + 在做（summary 三态显式拆列，0.0.3 设计 结论 75/76） */
const wishBarTotal = computed(() => (data.value?.summary?.wishTotal || 0) + (data.value?.summary?.doingTotal || 0))

/** 标记完成提示文案：与各模块页逐字一致（原 handleComplete 的“读过”与书单页“已读”不一致，一并收敛） */
function doneMessage(type?: string): string {
  if (type === 'BOOK') return '已标记已读'
  if (type === 'PLACE') return '已标记去过'
  return '已标记看过'
}

/** 心愿单「完成 →」：打开统一完成弹窗（FR-003-7：日期默认今天但可改，补记不再被静默写成当天） */
function openComplete(item: SgjItem): void {
  completeItem.value = item
  completeOpen.value = true
}

/** 统一完成弹窗提交成功：从心愿单移除并刷新（心愿单与汇总都要更新） */
function onCompleted(): void {
  const type = completeItem.value?.itemType
  const doneId = completeItem.value?.itemId
  if (doneId) {
    wishlist.value = wishlist.value.filter(w => w.itemId !== doneId)
  }
  proxy.$modal.msgSuccess(doneMessage(type))
  loadWishlist()
  getData()
}

// ------------------------------------------------------------------
// 心愿单：与心愿页（/plan）同一个数据源（/app/item/plan/list），排序口径完全一致。
// 侧栏只取首屏一页，完整列表由「查看全部心愿 →」进 /plan（与「查看全部笔记 →」同一模式）。
// ------------------------------------------------------------------

/** 心愿单首屏条数 */
const WISHLIST_PAGE_SIZE = 10
const wishlist = ref<SgjItem[]>([])

/** 拉首屏一页（完成、改日期后刷新） */
function loadWishlist(): Promise<void> {
  return listPlanItems({ pageNum: 1, pageSize: WISHLIST_PAGE_SIZE }).then(response => {
    wishlist.value = response.data || []
  }).catch(() => {})
}

function formatDate(time?: string): string {
  if (!time) return ''
  return time.slice(0, 10)
}

/** 手账扉页日期（设计图：MM.DD + 周几 + 年内第 N 天） */
const now = new Date()
const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const currentYear = now.getFullYear()
const todayMonthDay = `${String(now.getMonth() + 1).padStart(2, '0')}.${String(now.getDate()).padStart(2, '0')}`
const todayWeekday = weekdays[now.getDay()]
const dayOfYear = Math.floor((now.getTime() - new Date(currentYear, 0, 0).getTime()) / 86400000)

function parseDate(value: string): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(value)
  if (!m) return null
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
  return Number.isNaN(d.getTime()) ? null : d
}

function groupMonthDay(value: string): string {
  const d = parseDate(value)
  return d ? `${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')}` : value
}

function groupWeekday(value: string): string {
  const d = parseDate(value)
  return d ? weekdays[d.getDay()] : ''
}

function groupYear(value: string): string {
  const d = parseDate(value)
  return d ? String(d.getFullYear()) : ''
}

/** 条目类型 → hd-fallback / hd-glyph-round 的形制类 */
function coverTypeClass(type?: string): string {
  switch (type) {
    case 'MOVIE': return 'movie'
    case 'TV': return 'tv'
    case 'BOOK': return 'book'
    case 'PLACE': return 'place'
    default: return 'note'
  }
}

/** 时间线条目的完成态文案（顶栏统计口径：看过 / 读过 / 去过） */
function doneLabel(type?: string): string {
  if (type === 'BOOK') return '读过'
  if (type === 'PLACE') return '去过'
  return '看过'
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
// 心愿单独立取数（访客隐藏该卡片，不必为空请求买单）
if (isLogin.value) {
  loadWishlist()
}
</script>

<style scoped lang="scss">
/* 0.0.6 首页：样式取自设计图 home.html（.page-home 作用域），共享形制来自 handdrawn-site.css */

/* ===== 首页 ===== */
.page-home .hero { position: relative; padding: 34px 0 6px }
.page-home .hero-card {
  position: relative; background: var(--hd-paper); padding: 38px 40px 30px;
  border-radius: 4px; box-shadow: var(--hd-shadow-lift);
}
.page-home .hero-card .hd-tape { top: -12px; width: 110px }
.page-home .hero-card .hd-tape.right { top: -10px; right: -18px; left: auto }
.page-home .hero-eyebrow { font-size: 13px; letter-spacing: 4px; color: var(--hd-red) }
.page-home .hero-title { margin-top: 12px; font-size: 46px; letter-spacing: 3px; line-height: 1.2 }
.page-home .hero-sub { margin-top: 14px; font-size: 18px; line-height: 1.85; color: var(--hd-ink-soft); max-width: 620px }
.page-home .hero-sub b { color: var(--hd-ink); font-weight: 400; border-bottom: 2px dashed var(--hd-red-soft) }
.page-home .hero-actions { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 24px }
.page-home .hero-cta {
  border: 2px solid var(--hd-ink); background: var(--hd-ink); color: var(--hd-paper);
  padding: 9px 22px; font-size: 17px; letter-spacing: 2px;
  border-radius: 12px 4px 10px 5px / 5px 10px 4px 12px;
  display: inline-flex; align-items: center; gap: 8px;
  box-shadow: 3px 4px 0 var(--hd-line-soft);
}
.page-home .hero-cta:hover { background: var(--hd-red); border-color: var(--hd-red); color: var(--hd-paper); transform: translateY(-1px) rotate(-.5deg) }
.page-home .entry-card {
  display: inline-flex; align-items: center; gap: 12px; text-decoration: none;
  border: 2px solid var(--hd-line); background: var(--hd-paper); padding: 7px 16px;
  border-radius: 10px 4px 12px 5px / 5px 12px 4px 10px; color: var(--hd-ink);
  transition: border-color .12s, transform .12s;
}
.page-home .entry-card:hover { border-color: var(--hd-red); transform: translateY(-1px) rotate(.4deg) }
.page-home .entry-card .ico { font-size: 19px; color: var(--hd-red) }
.page-home .entry-card .t { font-size: 16px; line-height: 1.3 }
.page-home .entry-card .s { font-size: 13px; color: var(--hd-ink-soft) }
.page-home .entry-card .arr { color: var(--hd-ink-faint); font-size: 15px }

/* 扉页便签：不是数据，是手账上的日期戳 */
.page-home .hero-note {
  position: absolute; right: 36px; top: 34px; width: 196px; padding: 14px 16px 12px;
  background: var(--hd-paper-3); transform: rotate(2.4deg);
  box-shadow: var(--hd-shadow); border-radius: 3px;
}
.page-home .hero-note::before {
  content: ""; position: absolute; top: -9px; left: 50%; width: 58px; height: 18px;
  transform: translateX(-50%) rotate(-2deg); background: var(--hd-tape);
  clip-path: polygon(0 4%, 5% 0, 95% 2%, 100% 6%, 98% 96%, 93% 100%, 4% 98%, 0 94%);
}
.page-home .hero-note .n-day { font-size: 30px; line-height: 1.1; letter-spacing: 1px }
.page-home .hero-note .n-wd { font-size: 13px; color: var(--hd-ink-soft); margin-top: 2px }
.page-home .hero-note .n-say { font-size: 14.5px; color: var(--hd-ink-soft); margin-top: 8px; padding-top: 8px; border-top: 1.5px dashed var(--hd-line-soft) }
.page-home .hero-note svg { position: absolute; right: 10px; bottom: 8px; width: 42px; height: 32px; color: var(--hd-amber-ink) }

/* 统计：四枚贴纸 + 一张心愿便签 */
.page-home .stat-strip { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-top: 22px }
.page-home .stat {
  position: relative; background: var(--hd-paper); padding: 14px 10px 12px; text-align: center;
  border-radius: 8px 3px 9px 4px / 4px 9px 3px 8px; box-shadow: var(--hd-shadow);
}
.page-home .stat .glyph {
  width: 34px; height: 34px; margin: 0 auto 2px; display: grid; place-items: center;
  font-size: 18px; border: 2px solid currentColor;
  border-radius: 50% 46% 52% 48% / 48% 52% 46% 54%;
}
.page-home .stat:nth-child(1) .glyph { color: var(--hd-red) }
.page-home .stat:nth-child(2) .glyph { color: var(--hd-moss) }
.page-home .stat:nth-child(3) .glyph { color: var(--hd-amber-ink) }
.page-home .stat:nth-child(4) .glyph { color: var(--hd-ink-soft) }
.page-home .stat .num { font-size: 30px; line-height: 1.1; letter-spacing: 1px }
.page-home .stat .label { font-size: 13px; color: var(--hd-ink-soft); margin-top: 2px }
.page-home .wish-note {
  position: relative; margin-top: 18px; background: var(--hd-paper-3); padding: 12px 18px;
  border-radius: 3px; transform: rotate(-.5deg);
  box-shadow: var(--hd-shadow); display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
  text-decoration: none; color: var(--hd-ink);
}
.page-home .wish-note::before { content: ""; position: absolute; top: -9px; left: 34px; width: 62px; height: 18px; transform: rotate(-3deg); background: var(--hd-tape-2);
  clip-path: polygon(0 5%, 5% 0, 95% 2%, 100% 7%, 97% 95%, 92% 100%, 4% 97%, 0 93%); }
.page-home .wish-note .seal { color: var(--hd-red); border: 2px solid var(--hd-red); border-radius: 6px 2px 7px 3px / 3px 7px 2px 6px; padding: 0 7px; font-size: 14px }
.page-home .wish-note .big { font-size: 24px }
.page-home .wish-note .txt { font-size: 14.5px; color: var(--hd-ink-soft) }
.page-home .wish-note .more { margin-left: auto; font-size: 14px; color: var(--hd-red) }

/* 我的时光 */
.page-home .content { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 44px; margin-top: 44px }
.page-home .section-head { position: relative; display: flex; align-items: baseline; gap: 14px; flex-wrap: wrap; padding: 0 2px }
.page-home .section-head h2 { font-size: 27px; letter-spacing: 2px }
.page-home .section-head .en { font-size: 13px; letter-spacing: 3px; color: var(--hd-ink-soft) }
.page-home .section-head .line { flex: 1; min-width: 40px; border-top: 2px dashed var(--hd-line-soft); transform: translateY(-6px) }
.page-home .timeline { margin-top: 18px }
.page-home .tl-group { display: grid; grid-template-columns: 76px minmax(0, 1fr); gap: 18px; padding: 18px 0 6px }
.page-home .tl-date { font-size: 15px; color: var(--hd-ink-soft); text-align: right; padding-top: 6px; line-height: 1.5 }
.page-home .tl-date b { display: block; font-size: 22px; font-weight: 400; color: var(--hd-ink); letter-spacing: 1px }
.page-home .tl-date .wd { display: block; margin-top: 2px; font-size: 13px; color: var(--hd-ink-soft) }
.page-home .tl-rail { position: relative; border-left: 2px dashed var(--hd-line-soft); padding-left: 26px; display: flex; flex-direction: column; gap: 18px }
.page-home .tl-item { position: relative; text-decoration: none; color: var(--hd-ink) }
.page-home .tl-item::before {
  content: ""; position: absolute; left: -33px; top: 24px; width: 13px; height: 13px;
  border: 2px solid var(--hd-red); background: var(--hd-paper);
  border-radius: 50% 46% 52% 48% / 48% 52% 46% 54%;
}
.page-home .tl-card {
  position: relative; background: var(--hd-paper); padding: 14px 16px;
  border-radius: 5px; box-shadow: var(--hd-shadow); display: flex; gap: 16px;
  transition: transform .12s, box-shadow .12s;
}
.page-home .tl-item:nth-child(odd) .tl-card { transform: rotate(-.25deg) }
.page-home .tl-item:nth-child(even) .tl-card { transform: rotate(.2deg) }
.page-home .tl-item:hover .tl-card { transform: translateY(-2px) rotate(0); box-shadow: var(--hd-shadow-lift) }
.page-home .tl-cover {
  flex: none; width: 84px; height: 108px; position: relative; overflow: hidden;
  border: 2px solid var(--hd-line); border-radius: 4px 2px 5px 2px / 2px 5px 2px 4px;
  background: var(--hd-paper-3);
  display: grid; place-items: center;
}
.page-home .tl-cover img { width: 100%; height: 100%; object-fit: cover; display: block }
.page-home .tl-cover .tape { display: none }
.page-home .tl-body { min-width: 0; flex: 1 }
.page-home .tl-title { font-size: 19px; line-height: 1.45; display: flex; align-items: center; gap: 8px; flex-wrap: wrap }
.page-home .tl-meta { margin-top: 3px; font-size: 13px; color: var(--hd-ink-soft); display: flex; flex-wrap: wrap; gap: 4px 10px; align-items: center }
.page-home .tl-comment { margin-top: 7px; font-size: 14.5px; color: var(--hd-ink-soft); line-height: 1.65; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden }
.page-home .tl-stars { color: var(--hd-amber-ink); font-size: 13px; letter-spacing: 1px }
.page-home .tl-more { display: flex; flex-direction: column; align-items: center; gap: 8px; margin: 26px 0 0; padding-left: 26px }
.page-home .tl-end { text-align: center; font-size: 13px; color: var(--hd-ink-faint); margin-top: 26px; letter-spacing: 1px }

/* 侧栏 */
.page-home .aside { display: flex; flex-direction: column; gap: 26px }
.page-home .aside .hd-paper { padding: 16px 18px }
.page-home .aside .card-title { display: flex; align-items: baseline; gap: 8px; font-size: 18px; letter-spacing: 1px }
.page-home .aside .card-title .en { font-size: 10.5px; letter-spacing: 2px; color: var(--hd-ink-faint) }
.page-home .wish-item { display: flex; align-items: center; gap: 10px; padding: 9px 0; border-bottom: 1.5px dashed var(--hd-line-soft) }
.page-home .wish-item:last-of-type { border-bottom: 0 }
.page-home .wish-glyph {
  flex: none; width: 27px; height: 27px; display: grid; place-items: center; font-size: 13px;
  border: 1.8px solid currentColor; border-radius: 50% 46% 52% 48% / 48% 52% 46% 54%;
}
.page-home .wish-glyph.movie, .page-home .wish-glyph.tv { color: var(--hd-red) }
.page-home .wish-glyph.book { color: var(--hd-moss) }
.page-home .wish-glyph.place { color: var(--hd-amber-ink) }
.page-home .wish-body { min-width: 0; flex: 1 }
.page-home .wish-title { font-size: 15.5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.page-home .wish-meta { display: flex; align-items: center; gap: 6px; margin-top: 1px; font-size: 13px; color: var(--hd-ink-soft); flex-wrap: wrap }
.page-home .wish-done { border: 0; background: none; color: var(--hd-ink-soft); font-size: 13px; padding: 2px }
.page-home .wish-done:hover { color: var(--hd-red) }
.page-home .aside .more { display: block; margin-top: 10px; font-size: 13.5px; color: var(--hd-ink-soft); text-decoration: none }
.page-home .aside .more:hover { color: var(--hd-red) }
.page-home .note-item { padding: 9px 0; border-bottom: 1.5px dashed var(--hd-line-soft); text-decoration: none; color: var(--hd-ink); display: block }
.page-home .note-item:last-of-type { border-bottom: 0 }
.page-home .note-item:hover .note-title { color: var(--hd-red) }
.page-home .note-title { font-size: 15.5px; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden }
.page-home .note-meta { margin-top: 2px; font-size: 13px; color: var(--hd-ink-soft) }

@media (max-width: 960px) {
  .page-home .hero-note { display: none }
  .page-home .content { grid-template-columns: minmax(0, 1fr); gap: 30px }
  .page-home .aside { display: grid; grid-template-columns: 1fr 1fr; gap: 20px }
  .page-home .hero-title { font-size: 38px }
}
@media (max-width: 720px) {
  .page-home .hero { padding-top: 16px }
  .page-home .hero-card { padding: 26px 20px 22px }
  .page-home .hero-title { font-size: 30px; letter-spacing: 2px }
  .page-home .hero-sub { font-size: 16px }
  .page-home .stat-strip { grid-template-columns: repeat(2, 1fr); gap: 12px }
  .page-home .content { margin-top: 30px }
  .page-home .tl-group { grid-template-columns: minmax(0, 1fr); gap: 10px }
  .page-home .tl-date { text-align: left; display: flex; align-items: baseline; gap: 8px; padding-top: 0 }
  .page-home .tl-date b { font-size: 19px }
  .page-home .tl-rail { padding-left: 20px }
  .page-home .tl-item::before { left: -27px }
  .page-home .tl-cover { width: 66px; height: 88px }
  .page-home .tl-title { font-size: 17px }
  .page-home .aside { grid-template-columns: 1fr }
  .page-home .wish-note .more { margin-left: 0 }
}

.load-error {
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  padding: 60px 0; color: var(--hd-ink-soft);
}
.tl-more .hd-tiny { margin-top: 2px; }
</style>
