<template>
  <div class="plan-page page-plan">
    <svg width="0" height="0" style="position: absolute" aria-hidden="true">
      <filter id="rough">
        <feTurbulence type="fractalNoise" baseFrequency="0.02 0.05" numOctaves="2" seed="7" result="n" />
        <feDisplacementMap in="SourceGraphic" in2="n" scale="3.2" xChannelSelector="R" yChannelSelector="G" />
      </filter>
    </svg>

    <section class="hd-banner anim">
      <span class="hd-tape left"></span>
      <span class="hd-tape right amber"></span>
      <div>
        <div class="eyebrow">心愿单</div>
        <h1>心愿<span class="hd-dot"> · </span>想到就排上</h1>
        <p class="desc">影单 / 书单 / 足迹里还没做完的心愿，按你定的日子排在一起：定了日子的在前，没定日子的在最后一组。</p>
      </div>
      <div class="acts">
        <div class="hd-chips" role="group" aria-label="按状态筛选">
          <button type="button" class="hd-chip" :class="{ on: !activeStatus }" @click="switchStatus('')">共 {{ wantTotal + doingTotal }} 项</button>
          <button type="button" class="hd-chip" :class="{ on: activeStatus === 'DOING' }" @click="switchStatus('DOING')">在做 {{ doingTotal }}</button>
          <button type="button" class="hd-chip" :class="{ on: activeStatus === 'WANT' }" @click="switchStatus('WANT')">在想 {{ wantTotal }}</button>
        </div>
      </div>
    </section>

    <div v-loading="loading" class="plan-layout">
      <div class="plan-main">
        <h2 class="hd-sr">心愿清单</h2>

        <div class="groups">
          <section
            v-for="group in groups"
            :key="group.key"
            class="hd-paper sheet"
            :class="'g-' + group.key.toLowerCase()"
          >
            <span class="hd-tape"></span>
            <div class="g-head">
              <h2 class="g-title">
                {{ group.title }}
                <svg viewBox="0 0 100 9" preserveAspectRatio="none" aria-hidden="true">
                  <path d="M2 5 Q12 1 24 4 T48 4 T74 3 T98 5" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
                </svg>
              </h2>
              <span class="g-count">{{ group.items.length }} 项</span>
              <span class="g-hint">{{ group.hint }}</span>
            </div>

            <div
              v-for="item in group.items"
              :key="item.itemId"
              class="row"
              :class="[item.status === 'DOING' ? 'doing' : '', 'g-' + String(item.itemType || '').toLowerCase()]"
              @click="openDetail(item)"
            >
              <button
                type="button"
                class="ck"
                :class="{ doing: item.status === 'DOING' }"
                :disabled="!isLogin"
                :title="!isLogin ? '登录后可操作' : item.status === 'DOING' ? `标记${doneVerb(item.itemType)}` : (item.itemType === 'PLACE' ? '定个日子排上' : `开始${item.itemType === 'BOOK' ? '读' : '看'}`)"
                @click.stop="onCheck(item)"
              >
                <svg viewBox="0 0 26 26" aria-hidden="true">
                  <path class="box" d="M4 6 Q14 3 22 5 Q24 12 22 21 Q12 24 5 21 Q2 13 4 6 Z" />
                  <path class="ink" d="M8 17 Q11 9 15 13 Q17 9 20 8 M9 12 L12 11" />
                  <path class="ghost" d="M7 13 Q11 19 20 7" />
                </svg>
              </button>

              <span class="hd-glyph-round" :class="typeClass(item.itemType)" aria-hidden="true">{{ typeGlyph(item.itemType) }}</span>

              <div class="main">
                <div class="t">{{ item.title }}</div>
                <div class="m">
                  <span class="hd-st" :class="{ doing: item.status === 'DOING' }">{{ statusLabel(item) }}</span>
                  <span>{{ typeName(item.itemType) }}</span>
                  <span v-if="item.startDate" class="when" :class="{ late: group.key === 'overdue' }">
                    {{ planDateLabel(item.startDate.slice(0, 10), today) }}
                  </span>
                </div>
              </div>

              <div class="acts" @click.stop>
                <template v-if="isLogin">
                  <button v-if="item.status === 'WANT' && item.itemType !== 'PLACE'" type="button" class="hd-btn go sm" @click="startDoing(item)">
                    开始{{ item.itemType === 'BOOK' ? '读' : '看' }} →
                  </button>
                  <button v-if="item.status !== 'DONE'" type="button" class="hd-btn sm" :class="item.status === 'DOING' ? 'go' : ''" @click="openComplete(item)">
                    标记{{ doneVerb(item.itemType) }}
                  </button>
                  <el-popover :width="280" trigger="click">
                    <template #reference>
                      <button type="button" class="hd-btn sub sm">{{ item.startDate ? '改日期' : '定个日子' }}</button>
                    </template>
                    <el-date-picker
                      :model-value="item.startDate ? item.startDate.slice(0, 10) : undefined"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="打算哪天开始"
                      :clearable="false"
                      style="width: 100%"
                      @update:model-value="(v: string | null) => v && setPlanDate(item, v)"
                    />
                  </el-popover>
                  <button v-if="item.status === 'DOING'" type="button" class="hd-btn sub sm" @click="backToWant(item)">退回心愿</button>
                </template>
              </div>
            </div>
          </section>
        </div>

        <div v-if="!loading && !loadError && !list.length" class="hd-empty">
          <p v-if="activeStatus">这一档还没有心愿。</p>
          <p v-else>还没有心愿 —— 去影单 / 书单挑一个，填上打算哪天看。</p>
          <div class="acts">
            <button v-if="activeStatus" type="button" class="hd-btn go" @click="switchStatus('')">看全部心愿</button>
            <template v-else>
              <button type="button" class="hd-btn go" @click="router.push('/movie')">去影单</button>
              <button type="button" class="hd-btn" @click="router.push('/book')">去书单</button>
            </template>
          </div>
        </div>

        <div v-if="loadError" class="load-error">
          <span>加载失败，请稍后重试</span>
          <button type="button" class="hd-btn sm" @click="loadData">重试</button>
        </div>

        <div v-if="list.length" class="list-foot">
          <span class="cnt">已展示 {{ list.length }} / 共 {{ total }} 项</span>
          <button v-if="hasMore" type="button" class="hd-btn" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '加载中…' : '加载更多 ↓' }}
          </button>
          <span v-else class="end">—— 已到底部 ——</span>
        </div>
      </div>

      <aside class="side" aria-label="心愿小结">
        <section class="side-block" aria-labelledby="side-summary">
          <h3 class="side-title" id="side-summary">心愿小结 <span class="side-sub">{{ monthLabel }}</span></h3>
          <div class="hd-summary">
            <span class="item">在做 <b>{{ doingTotal }}</b></span>
            <span class="item">在想 <b>{{ wantTotal }}</b></span>
            <span class="item">过期 <b>{{ overdueCount }}</b></span>
          </div>
        </section>

        <section v-if="typeCounts.length" class="side-block" aria-labelledby="side-dist">
          <h3 class="side-title" id="side-dist">按类型分布 <span class="side-sub">{{ wantTotal + doingTotal }} 项</span></h3>
          <div class="hd-paper">
            <span class="hd-tape right"></span>
            <div class="dist">
              <div v-for="row in typeCounts" :key="row.key" class="dist-row">
                <span class="hd-glyph-round" :class="row.key" aria-hidden="true">{{ row.glyph }}</span>
                <span class="bar"><i class="fill" :class="row.key" :style="{ width: row.percent + '%' }"></i></span>
                <b class="dist-num">{{ row.count }}</b>
              </div>
            </div>
          </div>
        </section>

        <section class="side-block" aria-labelledby="side-next">
          <h3 class="side-title" id="side-next">下一个日子 <span class="side-sub">NEXT</span></h3>
          <div class="hd-paper">
            <span class="hd-tape left"></span>
            <div v-if="nextPlan" class="next">
              <span class="next-date"><b>{{ nextPlan.day }}</b><span>{{ nextPlan.month }} 月 · {{ nextPlan.weekday }}</span></span>
              <div class="next-item">
                <span class="hd-cover next-cover">
                  <span class="hd-fallback xs" :class="typeClass(nextPlan.item.itemType)"><span class="fc-motif"></span></span>
                </span>
                <div class="next-txt">
                  <div class="next-title">{{ nextPlan.item.title }}</div>
                  <div class="next-meta">{{ typeName(nextPlan.item.itemType) }} · 还有 {{ nextPlan.days }} 天</div>
                </div>
              </div>
            </div>
            <p v-else class="next-empty">暂时没有排上日子的心愿。</p>
          </div>
        </section>

        <section class="side-block" aria-labelledby="side-note">
          <h3 class="side-title" id="side-note">写给自己 <span class="side-sub">{{ noteDate }}</span></h3>
          <div class="note-card hd-rough">
            <span class="hd-tape"></span>
            <div class="note-top">
              <span class="note-kicker">{{ noteDateText }} · 随手写</span>
              <span class="hd-stamp">本周先做三件</span>
            </div>
            <p class="note-text">这周别贪多：先收掉一个在做，再想下一个。排的日子是给自己的提醒，不是给自己的压力。</p>
          </div>
        </section>
      </aside>
    </div>

    <item-complete-dialog v-model="completeOpen" :item="completeItem" @submitted="onCompleted" />
  </div>
</template>

<script setup lang="ts" name="FrontPlan">
import { getToken } from '@/utils/auth'
import { useDict } from '@/utils/dict'
import { groupPlanItems, planDateLabel, localDateKey } from '@/utils/plan'
import { itemStatusLabel } from '@/utils/itemStatus'
import { listPlanItems, updateFrontItem } from '@/api/front/item'
import ItemCompleteDialog from '@/components/ItemCompleteDialog/index.vue'
// 手绘皮肤：与设计图共用的唯一来源（handdrawn.css）；字体为自托管霞鹜文楷分片子集
// （跨设备一致，结论 107）。字体 CSS 随本路由懒加载，浏览器按 unicode-range 只取命中的分片。
import '@/assets/fonts/handdrawn/lxgw-wenkai/lxgw-wenkai.css'
import type { SgjItem } from '@/types/api/business/item'

/**
 * 心愿页（0.0.3 FR-003-4）：未完成 WANT/DOING 的全类型汇总。
 * 排序口径在服务端（start_date 升序、无日期置后），分组口径在前端（utils/plan.ts，设计 结论 78）。
 * 状态流转全部复用既有端点：开始/退回/改日期走 PUT /app/item/{id}，完成走统一完成弹窗。
 *
 * 胶囊筛选（issue #92）：页头「共/在做/在想」即筛选开关，status 由服务端单档过滤；
 * 数字用响应的 wantTotal/doingTotal 全量分档，不受筛选与分页影响。
 *
 * 皮肤（0.0.6）：形制层（hd-* 令牌与类）来自全局皮肤
 * src/assets/styles/handdrawn/handdrawn-site.css（由 layout/front/index.vue 统一 import），
 * 本页不再 import 页级 handdrawn.css（0.0.3 做法，随令牌升格退役）；
 * 组件样式块只放 .page-plan 作用域内的版面规则，设计图为
 * docs/design/mockups/handdrawn-0.0.6/plan.html。
 */

const { sgj_movie_status, sgj_book_status, sgj_place_status } = useDict('sgj_movie_status', 'sgj_book_status', 'sgj_place_status')

const { proxy } = getCurrentInstance() as { proxy: any }
const router = useRouter()

const isLogin = computed(() => !!getToken())

const list = ref<SgjItem[]>([])
const total = ref(0)
const loading = ref(false)
const loadingMore = ref(false)
const loadError = ref(false)
const pageNum = ref(1)
/** 每页条数：10 条（原 20 条时少量数据一页装完，「加载更多」永远不出现，等于没有分页） */
const pageSize = 10
const hasMore = computed(() => list.value.length < total.value)

/** 分组基准日：每次取数时定格为“用户眼里的今天”，避免跨零点后分组漂移 */
const today = ref(localDateKey(new Date()))

const completeOpen = ref(false)
const completeItem = ref<SgjItem | null>(null)

const groups = computed(() => groupPlanItems(list.value, today.value))

/** 过期组里的条数（侧栏小结） */
const overdueCount = computed(() => groups.value.find(g => g.key === 'overdue')?.items.length || 0)

/** 按类型分布：影（含剧）/ 书 / 地，宽度按最大项占比 */
const TYPE_META: Record<string, { glyph: string; label: string }> = {
  MOVIE: { glyph: '影', label: '电影 / 剧集' },
  TV: { glyph: '剧', label: '剧集' },
  BOOK: { glyph: '书', label: '书籍' },
  PLACE: { glyph: '地', label: '地点' }
}
const typeCounts = computed(() => {
  const counts: Record<string, number> = {}
  for (const item of list.value) {
    const key = item.itemType === 'TV' ? 'MOVIE' : (item.itemType || '')
    if (!['MOVIE', 'BOOK', 'PLACE'].includes(key)) continue
    counts[key] = (counts[key] || 0) + 1
  }
  const rows = Object.entries(counts).map(([key, count]) => ({ key, count, ...TYPE_META[key] }))
  const max = Math.max(1, ...rows.map(r => r.count))
  return rows.map(r => ({ ...r, percent: Math.round((r.count / max) * 100) }))
})

/** 条目类型 → 手绘形制类名 */
function typeClass(type?: string): string {
  switch (type) {
    case 'MOVIE': return 'movie'
    case 'TV': return 'tv'
    case 'BOOK': return 'book'
    case 'PLACE': return 'place'
    default: return 'note'
  }
}

/** 下一个日子：startDate 最近（含今天）的一条 */
const nextPlan = computed(() => {
  const todayKey = today.value
  const upcoming = list.value
    .filter(i => i.startDate && i.startDate.slice(0, 10) >= todayKey)
    .sort((a, b) => String(a.startDate).localeCompare(String(b.startDate)))[0]
  if (!upcoming?.startDate) return null
  const d = new Date(upcoming.startDate.slice(0, 10))
  if (Number.isNaN(d.getTime())) return null
  const days = Math.max(0, Math.ceil((d.getTime() - new Date(todayKey).getTime()) / 86400000))
  const weekday = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  return { item: upcoming, day: String(d.getDate()), month: d.getMonth() + 1, weekday, days }
})

/** 侧栏日期文案 */
const now = new Date()
const monthLabel = `${now.getMonth() + 1} 月`
const noteDate = `${now.getMonth() + 1}.${now.getDate()}`
const noteDateText = `${now.getMonth() + 1} 月 ${now.getDate()} 日`

/** 胶囊筛选档位（issue #92）：''=全部 / WANT=在想 / DOING=在做；服务端筛选，分页口径不被「只筛当前页」破坏 */
const activeStatus = ref<'' | 'WANT' | 'DOING'>('')
/** 胶囊数字：全量分档（响应 wantTotal/doingTotal）。旧口径按当前页 count，超过一页（10 条）后三数必对不上 */
const wantTotal = ref(0)
const doingTotal = ref(0)

function loadData(): Promise<void> {
  loading.value = true
  loadError.value = false
  pageNum.value = 1
  today.value = localDateKey(new Date())
  return listPlanItems({ status: activeStatus.value || undefined, pageNum: pageNum.value, pageSize }).then(response => {
    list.value = response.data || []
    total.value = Number(response.total) || 0
    wantTotal.value = Number(response.wantTotal) || 0
    doingTotal.value = Number(response.doingTotal) || 0
  }).catch(() => {
    loadError.value = true
  }).finally(() => {
    loading.value = false
  })
}

/** 胶囊切换：同档不重复取数（与影单页状态胶囊同一交互） */
function switchStatus(status: '' | 'WANT' | 'DOING'): void {
  if (activeStatus.value === status) return
  activeStatus.value = status
  loadData()
}

/** 状态文案：字典优先，查不到用中文兜底（utils/itemStatus.ts，绝不显示裸枚举） */
const dictTable = computed<Record<string, any[]>>(() => ({
  sgj_movie_status: sgj_movie_status.value,
  sgj_book_status: sgj_book_status.value,
  sgj_place_status: sgj_place_status.value
}))
function statusLabel(item: SgjItem): string {
  return itemStatusLabel(item, dictTable.value)
}

function loadMore(): void {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  pageNum.value += 1
  // 翻页带同一档位：不带 status 的话第二页会混进其他档的条目
  listPlanItems({ status: activeStatus.value || undefined, pageNum: pageNum.value, pageSize }).then(response => {
    list.value = list.value.concat(response.data || [])
    total.value = Number(response.total) || 0
  }).catch(() => {
    pageNum.value -= 1
  }).finally(() => {
    loadingMore.value = false
  })
}

const TYPE_NAMES: Record<string, string> = { MOVIE: '电影', TV: '剧集', BOOK: '书籍', PLACE: '地点' }
const TYPE_GLYPHS: Record<string, string> = { MOVIE: '影', TV: '剧', BOOK: '书', PLACE: '地' }
const DONE_VERBS: Record<string, string> = { BOOK: '已读', PLACE: '去过' }
/** 类型圆圈与字的颜色：与设计图内联的 stroke 完全一致（影视=陶红 / 书籍=鼠尾草 / 地点=琥珀） */
const TYPE_COLORS: Record<string, string> = { MOVIE: 'var(--hd-red)', TV: 'var(--hd-red)', BOOK: 'var(--hd-moss)', PLACE: 'var(--hd-amber)' }

const typeName = (type?: string) => TYPE_NAMES[type || ''] || ''
const typeGlyph = (type?: string) => TYPE_GLYPHS[type || ''] || '记'
const doneVerb = (type?: string) => DONE_VERBS[type || ''] || '看过'
const glyphColor = (type?: string) => TYPE_COLORS[type || ''] || 'var(--hd-ink-soft)'

/** WANT → DOING：开始做 */
function startDoing(item: SgjItem): void {
  if (!item.itemId) return
  updateFrontItem(item.itemId, { status: 'DOING' }).then(() => {
    proxy.$modal.msgSuccess(`开始${item.itemType === 'BOOK' ? '读' : '看'}：${item.title || ''}`)
    loadData()
  }).catch(() => {})
}

/** DOING → WANT：退回心愿 */
function backToWant(item: SgjItem): void {
  if (!item.itemId) return
  updateFrontItem(item.itemId, { status: 'WANT' }).then(() => {
    proxy.$modal.msgSuccess('已退回心愿')
    loadData()
  }).catch(() => {})
}

/** 定/改计划日期（WANT + 日期 = 计划，不是一个新状态，设计 结论 72） */
function setPlanDate(item: SgjItem, date: string): void {
  if (!item.itemId) return
  updateFrontItem(item.itemId, { startDate: date }).then(() => {
    proxy.$modal.msgSuccess(`计划日期：${date}`)
    loadData()
  }).catch(() => {})
}

function openComplete(item: SgjItem): void {
  completeItem.value = item
  completeOpen.value = true
}

function onCompleted(): void {
  proxy.$modal.msgSuccess(`已标记${doneVerb(completeItem.value?.itemType)}`)
  loadData()
}

/**
 * 清单方框的点击 = 该行当前的主操作（与按钮同一行为）：
 * 在想 → 开始做；在做 → 打开完成弹窗；地点不给「开始」（结论 87），给引导提示。
 */
function onCheck(item: SgjItem): void {
  if (!isLogin.value) return
  if (item.status === 'DOING') {
    openComplete(item)
    return
  }
  if (item.itemType === 'PLACE') {
    proxy.$modal.msgWarning('地点用「定个日子」排计划，完成点「标记去过」')
    return
  }
  startDoing(item)
}

/** 点行本体跳到对应模块并自动开详情：带上 type/status 预选胶囊——
 *  影视页默认「电影 + 想看」，不带的话电视剧或「在看」的条目落过去列表里看不到（结论 97） */
function openDetail(item: SgjItem): void {
  if (!item.itemId) return
  const path = item.itemType === 'BOOK' ? '/book' : item.itemType === 'PLACE' ? '/travel' : '/movie'
  router.push({
    path,
    query: { itemId: String(item.itemId), type: item.itemType, status: item.status || undefined }
  })
}

onMounted(() => {
  loadData()
})
</script>

<!-- 形制规则全在全局皮肤 handdrawn-site.css；这里只放本页版面规则与应用件（加载容器、失败重试、到底部标记） -->

<style scoped lang="scss">
/* 0.0.6 心愿：样式取自设计图 plan.html（.page-plan 作用域），形制来自 handdrawn-site.css */

/* ===== 心愿页：860 单列 → 1120 宽两栏（左清单 760 + 右侧栏 300，gap 44） =====
   1120 的壳两侧各留 8px，内容正好 1104 = 760 + 300 + 44；≤960 侧栏落到清单下方，≤720 单列 */
.page-plan { --hd-gutter: 8px }
.page-plan .plan-layout {
  display: grid; grid-template-columns: minmax(0, 760px) 300px;
  gap: 44px; margin-top: 24px; align-items: start; justify-content: center;
}
.page-plan .plan-main { min-width: 0 }
.page-plan .groups { display: flex; flex-direction: column; gap: 22px }

/* 四组纸片清单 */
.page-plan .sheet { padding: 18px 24px 12px }
.page-plan .sheet > .hd-tape { top: -9px; width: 84px }
.page-plan .g-head { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; padding: 0 2px 6px }
.page-plan .g-title { font-size: 23px; letter-spacing: 1px; display: inline-flex; flex-direction: column; align-items: stretch }
.page-plan .g-title svg { width: 100%; height: 8px; margin-top: 1px; color: var(--hd-line) }
.page-plan .g-count {
  font-size: 13.5px; color: var(--hd-ink-soft); padding: 0 8px;
  border: 1.5px solid var(--hd-line-soft); border-radius: 9px 3px 8px 3px / 3px 8px 3px 9px;
}
.page-plan .g-hint { margin-left: auto; font-size: 13px; color: var(--hd-ink-faint); text-align: right }

.page-plan .row {
  display: grid; grid-template-columns: 30px 30px minmax(0, 1fr) auto;
  gap: 12px; align-items: center; padding: 10px 2px;
}
.page-plan .row + .row { border-top: 1.5px dashed var(--hd-line-soft) }
.page-plan .row:hover { background: var(--hd-paper-2); border-radius: var(--hd-radius-sm) }

/* 勾选圈：空=在想，涂鸦=在做；点它执行该行主操作 */
.page-plan .ck {
  width: 30px; height: 30px; padding: 0; border: 0; background: none;
  display: grid; place-items: center; color: var(--hd-ink-soft); border-radius: 50%;
}
.page-plan .ck svg { width: 27px; height: 27px }
.page-plan .ck .box { fill: none; stroke: currentColor; stroke-width: 2.2; stroke-linejoin: round }
.page-plan .ck .ink { fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; opacity: 0 }
.page-plan .ck .ghost { fill: none; stroke: var(--hd-red); stroke-width: 2.4; stroke-linecap: round; opacity: 0 }
.page-plan .ck.doing { color: var(--hd-red) }
.page-plan .ck.doing .ink { opacity: 1 }
.page-plan .ck:hover .ghost { opacity: .5 }

.page-plan .row .hd-glyph-round { width: 30px; height: 30px; font-size: 14px }

.page-plan .main { min-width: 0 }
.page-plan .t { font-size: 17px; line-height: 1.45; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.page-plan .m { margin-top: 2px; display: flex; align-items: center; flex-wrap: wrap; gap: 4px 8px; font-size: 13px; color: var(--hd-ink-soft) }
.page-plan .m .hd-st { font-size: 13px }
.page-plan .when { color: var(--hd-ink-faint) }
.page-plan .when.late { color: var(--hd-red) }

.page-plan .acts { display: flex; gap: 6px; flex-wrap: wrap; justify-content: flex-end }
.page-plan .acts .hd-btn { padding: 2px 10px; font-size: 13px; gap: 4px }
.page-plan .acts .hd-btn svg { width: 13px; height: 13px }

.page-plan .list-foot {
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
  margin-top: 18px; padding: 0 4px; font-size: 14px; color: var(--hd-ink-soft);
}
.page-plan .list-foot .end { font-size: 13px; color: var(--hd-ink-faint) }

/* 右侧栏：宽屏贴顶，≤960 落到清单下方 */
.page-plan .side { position: sticky; top: 80px; display: flex; flex-direction: column; gap: 26px; min-width: 0 }
.page-plan .side .hd-paper { padding: 14px 18px 15px }
.page-plan .side-title { display: flex; align-items: baseline; gap: 8px; font-size: 18px; letter-spacing: 1px; margin: 14px 2px 8px }
.page-plan .side-sub { font-size: 13px; letter-spacing: 2px; color: var(--hd-ink-faint) }
.page-plan .side .hd-summary { margin-top: 0; padding: 8px 12px; gap: 6px 14px; font-size: 13.5px }
.page-plan .side .hd-summary .item b { font-size: 19px }

/* 按类型分布：手绘横条 */
.page-plan .dist { display: grid; gap: 10px }
.page-plan .dist-row { display: grid; grid-template-columns: 30px minmax(0, 1fr) 26px; align-items: center; gap: 10px; font-size: 13.5px }
.page-plan .dist-row .hd-glyph-round { width: 30px; height: 30px; font-size: 14px }
.page-plan .bar { height: 12px; border: 1.6px solid var(--hd-line-soft); border-radius: 8px 3px 7px 3px / 3px 7px 3px 8px; background: var(--hd-paper-2); overflow: hidden }
.page-plan .fill { display: block; height: 100%; border-radius: 6px 2px 5px 2px; filter: url(#rough) }
.page-plan .fill.movie { background: var(--hd-brick) }
.page-plan .fill.book { background: var(--hd-olive) }
.page-plan .fill.place { background: var(--hd-ochre) }
.page-plan .dist-num { font-size: 17px; color: var(--hd-ink); text-align: right; font-weight: 400 }

/* 下一个日子：日期戳 + 小卡片 */
.page-plan .next { display: grid; grid-template-columns: 86px minmax(0, 1fr); gap: 12px; align-items: center }
.page-plan .next-date {
  padding: 6px 4px 5px; text-align: center; background: var(--hd-paper);
  border: 2px solid var(--hd-red); color: var(--hd-red);
  border-radius: 6px 2px 7px 3px / 3px 7px 2px 6px; transform: rotate(-3deg);
}
.page-plan .next-date b { display: block; font-size: 27px; line-height: 1.15; letter-spacing: 1px; font-weight: 400 }
.page-plan .next-date span { display: block; font-size: 13px; line-height: 1.5; white-space: nowrap }
.page-plan .next-item { display: flex; align-items: center; gap: 10px; min-width: 0 }
.page-plan .next-cover { flex: none; width: 44px; height: 58px; aspect-ratio: auto }
.page-plan .next-txt { min-width: 0 }
.page-plan .next-title { font-size: 15px; line-height: 1.4; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.page-plan .next-meta { margin-top: 2px; font-size: 13px; color: var(--hd-ink-soft) }

/* 手写批注 + 印章 */
.page-plan .note-card {
  position: relative; background-color: var(--hd-paper-3); background-image: var(--hd-grain);
  padding: 16px 18px 18px; border-radius: 4px; box-shadow: var(--hd-shadow);
  transform: rotate(-.6deg);
}
.page-plan .note-card > .hd-tape { top: -10px; width: 66px }
.page-plan .note-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px }
.page-plan .note-kicker { font-size: 13px; color: var(--hd-ink-faint); letter-spacing: 1px; padding-top: 3px }
.page-plan .note-card .hd-stamp { font-size: 13px; letter-spacing: 1px; flex: none }
.page-plan .note-text { margin-top: 10px; font-size: 14.5px; line-height: 1.85; color: var(--hd-ink-soft) }

/* 移动端 */
@media (max-width: 960px) {
  .page-plan { --hd-gutter: 18px }
  .page-plan .plan-layout { grid-template-columns: minmax(0, 1fr); gap: 26px }
  .page-plan .side { position: static; display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 18px; align-items: start }
}
@media (max-width: 720px) {
  .page-plan .hd-banner .hd-tape.left { left: 24%; width: 76px }
  .page-plan .hd-banner .hd-tape.right { left: 76% }
  .page-plan .hd-banner h1 { font-size: 31px }
  .page-plan .sheet { padding: 16px 16px 10px }
  .page-plan .g-title { font-size: 20px }
  .page-plan .g-hint { margin-left: 0; flex-basis: 100%; text-align: left }
  .page-plan .row { grid-template-columns: 28px 28px minmax(0, 1fr); row-gap: 8px; padding: 10px 0 }
  .page-plan .row .hd-glyph-round { width: 28px; height: 28px; font-size: 13px }
  .page-plan .ck { width: 28px; height: 28px }
  .page-plan .ck svg { width: 25px; height: 25px }
  .page-plan .acts { grid-column: 1 / -1; justify-content: flex-start; padding-left: 2px }
  .page-plan .t { font-size: 16px }
  .page-plan .side { grid-template-columns: minmax(0, 1fr) }
  .page-plan .list-foot { flex-direction: column; align-items: flex-start; gap: 4px }
}

/* 产品补充：加载态、空态与错误态 */
.page-plan .load-error {
  display: flex; align-items: center; justify-content: center; gap: 12px;
  padding: 40px 0; color: var(--hd-ink-soft);
}
.page-plan .hd-empty { margin-top: 24px }
.page-plan .next-empty { margin: 0; padding: 6px 2px; font-size: 13.5px; color: var(--hd-ink-soft) }

</style>
