<template>
  <div class="plan-page">
    <!-- 手绘抖线滤镜：只套在边框/分隔线/按钮框上，文字层不套（保证可读） -->
    <svg width="0" height="0" style="position:absolute" aria-hidden="true">
      <filter id="plan-rough">
        <feTurbulence type="fractalNoise" baseFrequency="0.02 0.05" numOctaves="2" seed="7" result="n" />
        <feDisplacementMap in="SourceGraphic" in2="n" scale="3.2" xChannelSelector="R" yChannelSelector="G" />
      </filter>
    </svg>

    <header class="hd-head">
      <h1>心愿<span class="dot"> · </span>想到就排上</h1>
      <p>影单 / 书单 / 足迹里<strong>还没做完</strong>的心愿，按你定的日子排在一起：定了日子的在前，没定日子的在最后一组。</p>
      <div class="hd-chips">
        <span class="hd-chip">共 {{ total }} 项</span>
        <span class="hd-chip doing">在做 {{ doingCount }}</span>
        <span class="hd-chip">在想 {{ wantCount }}</span>
      </div>
    </header>

    <div v-loading="loading" class="plan-body">
      <!-- 空态：手绘涂鸦 + 引导（设计图 handdrawn-checklist.html） -->
      <div v-if="!loading && !loadError && !list.length" class="hd-empty">
        <svg viewBox="0 0 150 96" fill="none" stroke-width="2.4" stroke-linecap="round" aria-hidden="true">
          <path d="M20 66 Q60 58 96 64 Q120 67 134 60" stroke="var(--hd-ink-soft)" />
          <path d="M40 40 l6 14 M46 40 l-6 14 M58 36 l7 18 M65 37 l-7 16" stroke="var(--hd-red)" />
          <path d="M96 30 q6 -12 14 -2 q8 -10 14 2 q6 8 -14 16 q-20 -8 -14 -16z" stroke="var(--hd-amber)" />
          <path d="M28 24 q10 -8 18 0 M112 78 q12 -6 20 2" stroke="var(--hd-ink-soft)" stroke-dasharray="3 6" />
        </svg>
        <p>还没有心愿 —— 去影单 / 书单挑一个，填上打算哪天看。</p>
        <div class="hd-empty-acts">
          <button type="button" class="hdbtn go" @click="router.push('/movie')">去影单</button>
          <button type="button" class="hdbtn" @click="router.push('/book')">去书单</button>
        </div>
      </div>
      <div v-if="loadError" class="hd-load-error">
        <span>加载失败，请稍后重试</span>
        <button type="button" class="hdbtn" @click="loadData">重试</button>
      </div>

      <!-- 一组一张「清单纸」：胶带 + 抖线纸框，行与行之间手绘虚线（issue #84，设计图已确认） -->
      <section v-for="group in groups" :key="group.key" class="hd-sheet" :class="'g-' + group.key">
        <div class="tape" aria-hidden="true"></div>
        <div class="paper">
          <div class="lines" aria-hidden="true"><div class="frame"></div></div>

          <div class="g-head">
            <h2 class="g-title">
              {{ group.title }}
              <svg viewBox="0 0 100 9" preserveAspectRatio="none" aria-hidden="true">
                <path d="M2 5 Q12 1 24 4 T48 4 T74 3 T98 5" stroke="currentColor" stroke-width="2.4" fill="none" stroke-linecap="round" opacity=".85" />
              </svg>
            </h2>
            <span class="g-count">{{ group.items.length }} 项</span>
            <span class="g-hint">{{ group.hint }}</span>
          </div>

          <div class="rows">
            <template v-for="(item, idx) in group.items" :key="item.itemId">
              <div class="hd-row" :class="{ doing: item.status === 'DOING' }" @click="openDetail(item)">
                <!-- 方框即状态也是动作：空=在想，涂鸦痕=在做；点它执行该行主操作 -->
                <button
                  type="button"
                  class="ck"
                  :class="{ doing: item.status === 'DOING' }"
                  :disabled="!isLogin"
                  :title="!isLogin ? '登录后可操作' : item.status === 'DOING' ? `标记${doneVerb(item.itemType)}` : (item.itemType === 'PLACE' ? '定个日子排上' : `开始${item.itemType === 'BOOK' ? '读' : '看'}`)"
                  @click.stop="onCheck(item)"
                >
                  <svg viewBox="0 0 26 26" aria-hidden="true">
                    <path d="M4 6 Q14 3 22 5 Q24 12 22 21 Q12 24 5 21 Q2 13 4 6 Z" fill="none" stroke-width="2.2" stroke-linejoin="round"
                          :stroke="item.status === 'DOING' ? 'var(--hd-red)' : 'var(--hd-ink-soft)'" />
                    <path v-if="item.status === 'DOING'" d="M8 17 Q11 9 15 13 Q17 9 20 8 M9 12 L12 11" fill="none" stroke="var(--hd-red)" stroke-width="2" stroke-linecap="round" />
                    <path v-else class="ghost" d="M6 14 Q12 20 21 7" fill="none" stroke="var(--hd-red)" stroke-width="2.4" stroke-linecap="round" />
                  </svg>
                </button>

                <span class="glyph" :class="'glyph-' + item.itemType">
                  <svg class="ring" viewBox="0 0 34 34" aria-hidden="true"><ellipse cx="17" cy="17" rx="14.5" ry="13.5" fill="none" stroke-width="1.8" /></svg>
                  <span>{{ typeGlyph(item.itemType) }}</span>
                </span>

                <div class="main">
                  <div class="t">{{ item.title }}</div>
                  <div class="m">
                    <span class="st" :class="{ doing: item.status === 'DOING' }">{{ statusLabel(item) }}</span>
                    <span>{{ typeName(item.itemType) }}</span>
                    <span v-if="item.startDate" class="when" :class="{ late: group.key === 'overdue' }">
                      {{ planDateLabel(item.startDate.slice(0, 10), today) }}
                    </span>
                    <!-- 无日期不标「未排期」：组名「还没定日子」已经说了 -->
                  </div>
                </div>

                <div class="acts" @click.stop>
                  <template v-if="isLogin">
                    <button v-if="item.status === 'WANT' && item.itemType !== 'PLACE'" type="button" class="hdbtn go" @click="startDoing(item)">
                      开始{{ item.itemType === 'BOOK' ? '读' : '看' }} →
                    </button>
                    <button v-if="item.status !== 'DONE'" type="button" class="hdbtn" :class="item.status === 'DOING' ? 'go' : ''" @click="openComplete(item)">
                      标记{{ doneVerb(item.itemType) }}
                    </button>
                    <el-popover :width="280" trigger="click">
                      <template #reference>
                        <button type="button" class="hdbtn sub">📅 {{ item.startDate ? '改日期' : '定个日子' }}</button>
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
                    <button v-if="item.status === 'DOING'" type="button" class="hdbtn sub" @click="backToWant(item)">退回心愿</button>
                  </template>
                </div>
              </div>
              <div v-if="idx < group.items.length - 1" class="rsep" aria-hidden="true"></div>
            </template>
          </div>
        </div>
      </section>

      <div v-if="list.length" class="list-foot">
        <span class="cnt">已展示 {{ list.length }} / 共 {{ total }} 项</span>
        <button v-if="hasMore" type="button" class="hdbtn" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? '加载中…' : '加载更多 ↓' }}
        </button>
        <span v-else class="end">—— 已到底部 ——</span>
      </div>
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
// 手绘字体（龙藏）分片子集：随本路由懒加载，浏览器按 unicode-range 只取命中的分片（结论 99）
import '@/assets/fonts/handdrawn/longcang/longcang.css'
import type { SgjItem } from '@/types/api/business/item'

/**
 * 心愿页（0.0.3 FR-003-4）：未完成 WANT/DOING 的全类型汇总。
 * 排序口径在服务端（start_date 升序、无日期置后），分组口径在前端（utils/plan.ts，
 * 设计 结论 78）。状态流转全部复用既有端点：开始/退回/改日期走 PUT /app/item/{id}，
 * 完成走统一完成弹窗（ItemCompleteDialog）。
 *
 * 皮肤（issue #84，设计图 docs/design/mockups/plan-0.0.3/handdrawn-checklist.html 已确认）：
 * 手绘清单——一组一张纸（胶带+抖线滤镜）、手绘方框=状态+动作、手写字体分片自托管；
 * 信息架构与行为不变，只换视觉。
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
const wantCount = computed(() => list.value.filter(i => i.status === 'WANT').length)
const doingCount = computed(() => list.value.filter(i => i.status === 'DOING').length)

/** 状态文案：字典优先，查不到用中文兜底（utils/itemStatus.ts，绝不显示裸枚举） */
const dictTable = computed<Record<string, any[]>>(() => ({
  sgj_movie_status: sgj_movie_status.value,
  sgj_book_status: sgj_book_status.value,
  sgj_place_status: sgj_place_status.value
}))
function statusLabel(item: SgjItem): string {
  return itemStatusLabel(item, dictTable.value)
}

function loadData(): Promise<void> {
  loading.value = true
  loadError.value = false
  pageNum.value = 1
  today.value = localDateKey(new Date())
  return listPlanItems({ pageNum: pageNum.value, pageSize }).then(response => {
    list.value = response.data || []
    total.value = Number(response.total) || 0
  }).catch(() => {
    loadError.value = true
  }).finally(() => {
    loading.value = false
  })
}

function loadMore(): void {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  pageNum.value += 1
  listPlanItems({ pageNum: pageNum.value, pageSize }).then(response => {
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

const typeName = (type?: string) => TYPE_NAMES[type || ''] || ''
const typeGlyph = (type?: string) => TYPE_GLYPHS[type || ''] || '记'
const doneVerb = (type?: string) => DONE_VERBS[type || ''] || '看过'

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

<!-- 非 scoped 并以 .plan-page 作命名空间：手绘令牌要挂 html.dark（换墨），与 MediaDetailDrawer 同一先例 -->
<style lang="scss">
.plan-page {
  /* 手绘令牌（日间 = 纸 + 钢笔；派生自站内冷雾胶片调色板） */
  --hd-ink: #3B342C;
  --hd-ink-soft: #8A8075;
  --hd-ink-faint: #B7AE9F;
  --hd-paper: #FFFEF8;
  --hd-line: rgba(59, 52, 44, 0.5);
  --hd-line-soft: rgba(59, 52, 44, 0.28);
  --hd-red: #A85F52;
  --hd-red-soft: rgba(168, 95, 82, 0.12);
  --hd-amber: #B18458;
  --hd-moss: #5D6F66;
  --hd-tape: rgba(168, 95, 82, 0.16);
  /* 字体用法（结论 101，用户实测拍板）：手写体只做「标题时刻」——页头 / 组名 / 空态；
     正文（条目、日期、按钮、说明）回到站内衬线体。手写体铺满全页时小字号下显乱（实测「好丑」），
     手绘感由纸 / 方框 / 抖线承担，不依赖正文也用手写。 */
  --hd-hand: 'Long Cang', var(--sgj-font-serif);
  --hd-body: var(--sgj-font-serif);

  max-width: 1080px;
  margin: 0 auto;
  padding: 8px 24px 48px;
  font-family: var(--hd-body);
  color: var(--hd-ink);
  font-size: 16px;
  line-height: 1.65;
}

html.dark .plan-page {
  /* 夜间 = 黑板 + 粉笔：同一骨架换墨不换结构 */
  --hd-ink: #EDE8DC;
  --hd-ink-soft: #A6A093;
  --hd-ink-faint: #7C766B;
  --hd-paper: #2A302C;
  --hd-line: rgba(237, 232, 220, 0.55);
  --hd-line-soft: rgba(237, 232, 220, 0.26);
  --hd-red: #C98F82;
  --hd-red-soft: rgba(201, 143, 130, 0.16);
  --hd-amber: #C9A472;
  --hd-moss: #93A99B;
  --hd-tape: rgba(201, 143, 130, 0.14);
}

/* ---------------- 页头 ---------------- */
.plan-page .hd-head {
  padding: 18px 4px 6px;

  h1 { margin: 0; font-size: 34px; font-weight: 400; letter-spacing: 2px; font-family: var(--hd-hand); .dot { color: var(--hd-red); } }
  p { margin: 6px 0 0; font-size: 16px; color: var(--hd-ink-soft); max-width: 640px; }
}

.plan-page .hd-chips {
  display: flex; gap: 10px; margin-top: 12px; flex-wrap: wrap;

  .hd-chip {
    border: 2px solid var(--hd-line);
    border-radius: 14px 4px 16px 5px / 5px 16px 4px 14px;
    padding: 0 12px; font-size: 14px; background: var(--hd-paper);
    &.doing { border-color: var(--hd-red); color: var(--hd-red); background: var(--hd-red-soft); }
  }
}

/* ---------------- 一组一张纸 ---------------- */
.plan-page .hd-sheet { position: relative; margin-top: 32px;
  &:nth-of-type(odd) { transform: rotate(-0.35deg); }
  &:nth-of-type(even) { transform: rotate(0.3deg); }
}
.plan-page .hd-sheet .tape {
  position: absolute; top: -11px; left: 50%; width: 92px; height: 24px;
  transform: translateX(-50%) rotate(-2deg);
  background: var(--hd-tape);
  border-left: 1px dashed rgba(255, 255, 255, 0.5);
  border-right: 1px dashed rgba(255, 255, 255, 0.5);
  clip-path: polygon(0 4%, 5% 0, 95% 2%, 100% 6%, 98% 96%, 93% 100%, 4% 98%, 0 94%);
}
.plan-page .hd-sheet:nth-of-type(even) .tape { transform: translateX(-50%) rotate(1.6deg); left: 58%; }

.plan-page .paper {
  position: relative; background: var(--hd-paper); border-radius: 6px;
  padding: 4px 20px 10px; box-shadow: 2px 3px 0 var(--hd-line-soft);

  .lines { position: absolute; inset: 0; pointer-events: none; filter: url(#plan-rough);
    .frame { position: absolute; inset: 0; border: 2.2px solid var(--hd-line); border-radius: 10px 4px 12px 5px / 5px 12px 4px 10px; }
  }
}

/* 组标题：身份色 + 手绘下划线（currentColor 跟随组色） */
.plan-page .g-head { display: flex; align-items: baseline; gap: 12px; padding: 14px 2px 4px; position: relative; z-index: 1; flex-wrap: wrap;
  .g-title { margin: 0; font-size: 24px; font-weight: 400; position: relative; display: inline-block; font-family: var(--hd-hand);
    svg { position: absolute; left: -4px; right: -10px; bottom: -6px; width: calc(100% + 14px); height: 9px; } }
  .g-count { font-size: 14px; color: var(--hd-ink-soft); border: 1.8px solid var(--hd-line-soft); border-radius: 10px 3px 8px 4px / 4px 8px 3px 10px; padding: 0 9px; }
  .g-hint { font-size: 14px; color: var(--hd-ink-soft); }
}
.plan-page .g-overdue .g-title { color: var(--hd-red); }
.plan-page .g-thisweek .g-title { color: var(--hd-amber); }
.plan-page .g-later .g-title { color: var(--hd-moss); }

/* ---------------- 清单行 ---------------- */
.plan-page .rows { position: relative; z-index: 1; }
.plan-page .rsep { height: 0; border-bottom: 2px dashed var(--hd-line-soft); margin: 0 12px; filter: url(#plan-rough); }

.plan-page .hd-row {
  display: flex; align-items: center; gap: 14px; padding: 13px 2px 14px; cursor: pointer;
  transition: background 0.16s, border-radius 0.16s;
  &:nth-child(odd) { transform: rotate(-0.12deg); }
  &:nth-child(even) { transform: rotate(0.1deg); }
  &:hover { background: var(--sgj-bg); border-radius: 8px; }

  .ck {
    flex: none; width: 26px; height: 26px; padding: 0; background: none; border: 0; cursor: pointer;
    svg { width: 100%; height: 100%; display: block; }
    .ghost { opacity: 0; transition: opacity 0.15s; }
    &:hover:not(:disabled) .ghost { opacity: 0.4; }
    &.doing .ghost { display: none; }
    &:disabled { cursor: default; }
  }

  .glyph {
    flex: none; width: 34px; height: 34px; display: flex; align-items: center; justify-content: center;
    font-size: 17px; position: relative;
    .ring { position: absolute; inset: 0; filter: url(#plan-rough); ellipse { stroke: currentColor; } }
    > span { position: relative; }
    &.glyph-MOVIE, &.glyph-TV { color: var(--hd-red); }
    &.glyph-BOOK { color: var(--hd-moss); }
    &.glyph-PLACE { color: var(--hd-amber); }
  }

  .main { flex: 1; min-width: 0;
    .t { font-size: 18px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .m { display: flex; align-items: center; gap: 10px; margin-top: 2px; font-size: 13.5px; color: var(--hd-ink-soft); flex-wrap: wrap;
      .st { padding: 0 8px; border-radius: 10px 3px 9px 4px / 4px 9px 3px 10px; border: 1.6px solid var(--hd-line-soft);
        &.doing { border-color: var(--hd-red); color: var(--hd-red); background: var(--hd-red-soft); } }
      .when { font-size: 14px; &.late { color: var(--hd-red); } }
    }
  }

  .acts { flex: none; display: flex; gap: 8px; align-items: center; }
}

/* 手绘按钮：推进 = 整块涂墨；其余 = 描边抖框 */
.plan-page .hdbtn {
  border: 2px solid var(--hd-line);
  border-radius: 12px 4px 10px 5px / 5px 10px 4px 12px;
  background: var(--hd-paper); color: var(--hd-ink);
  padding: 3px 13px; font-size: 15px; font-family: inherit; line-height: 1.5;
  cursor: pointer; filter: url(#plan-rough); transition: transform 0.12s;
  &:hover { transform: translateY(-1px) rotate(-0.6deg); }
  &.go { background: var(--hd-ink); color: var(--hd-paper); border-color: var(--hd-ink); }
  &.sub { border-color: var(--hd-line-soft); color: var(--hd-ink-soft); }
  &:disabled { cursor: default; opacity: 0.6; }
}
html.dark .plan-page .hdbtn.go { color: #262B29; }

/* ---------------- 空态 / 加载失败 ---------------- */
.plan-page .hd-empty { text-align: center; padding: 64px 10px 40px;
  svg { width: 150px; height: 96px; margin-bottom: 6px; }
  p { font-size: 22px; margin: 0 0 18px; font-family: var(--hd-hand); }
  .hd-empty-acts { display: flex; gap: 14px; justify-content: center; }
}
.plan-page .hd-load-error { display: flex; align-items: center; gap: 12px; justify-content: center; padding: 32px 0; color: var(--hd-ink-soft); }

/* ---------------- 列表脚 ---------------- */
.plan-page .list-foot { display: flex; flex-direction: column; align-items: center; gap: 12px; margin-top: 30px;
  .cnt { font-size: 15px; color: var(--hd-ink-soft); }
  .end { font-size: 14px; color: var(--hd-ink-faint); }
}

/* ---------------- 移动端 ---------------- */
@media (max-width: 768px) {
  .plan-page { padding: 4px 12px 28px; }
  .plan-page .hd-head h1 { font-size: 27px; }
  .plan-page .hd-row { flex-wrap: wrap;
    .acts { width: 100%; justify-content: flex-end; gap: 6px; padding-top: 8px; border-top: 1px dashed var(--hd-line-soft);
      .hdbtn { flex: 1; padding: 5px 6px; } }
  }
}

@media (prefers-reduced-motion: reduce) {
  .plan-page * { transition: none !important; animation: none !important; }
}
</style>
