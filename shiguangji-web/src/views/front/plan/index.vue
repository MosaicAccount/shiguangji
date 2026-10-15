<template>
  <div class="plan-page">
    <!-- 手绘抖线滤镜：与设计图同名同参数（handdrawn.css 里的 filter:url(#rough) 指向它） -->
    <svg width="0" height="0" style="position:absolute" aria-hidden="true">
      <filter id="rough">
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
      <div class="groups">
        <section
          v-for="(group, gi) in groups"
          :key="group.key"
          class="sheet"
          :class="[gi % 2 === 1 ? 'alt' : '', 'g-' + group.key.toLowerCase()]"
        >
          <div class="tape"></div>
          <div class="paper">
            <div class="lines"><div class="frame"></div></div>

            <div class="g-head">
              <h2 class="g-title">
                {{ group.title }}
                <svg viewBox="0 0 100 9" preserveAspectRatio="none">
                  <path d="M2 5 Q12 1 24 4 T48 4 T74 3 T98 5" stroke="currentColor" stroke-width="2.4" fill="none" stroke-linecap="round" opacity=".85" />
                </svg>
              </h2>
              <span class="g-count">{{ group.items.length }} 项</span>
              <span class="g-hint">{{ group.hint }}</span>
            </div>

            <div class="rows">
              <template v-for="(item, idx) in group.items" :key="item.itemId">
                <div
                  class="row"
                  :class="[item.status === 'DOING' ? 'doing' : '', 'g-' + String(item.itemType || '').toLowerCase()]"
                  @click="openDetail(item)"
                >
                  <!-- 方框即状态也是动作：空=在想，涂鸦痕=在做；点它执行该行主操作 -->
                  <button
                    type="button"
                    class="ck"
                    :class="{ doing: item.status === 'DOING' }"
                    :disabled="!isLogin"
                    :title="!isLogin ? '登录后可操作' : item.status === 'DOING' ? `标记${doneVerb(item.itemType)}` : (item.itemType === 'PLACE' ? '定个日子排上' : `开始${item.itemType === 'BOOK' ? '读' : '看'}`)"
                    @click.stop="onCheck(item)"
                  >
                    <svg viewBox="0 0 26 26">
                      <path d="M4 6 Q14 3 22 5 Q24 12 22 21 Q12 24 5 21 Q2 13 4 6 Z" fill="none"
                            :stroke="item.status === 'DOING' ? 'var(--hd-red)' : 'var(--hd-ink-soft)'" stroke-width="2.2" stroke-linejoin="round" />
                      <path v-if="item.status === 'DOING'" d="M8 17 Q11 9 15 13 Q17 9 20 8 M9 12 L12 11" fill="none" stroke="var(--hd-red)" stroke-width="2" stroke-linecap="round" />
                      <path class="ghost" d="M6 14 Q12 20 21 7" fill="none" stroke="var(--hd-red)" stroke-width="2.4" stroke-linecap="round" />
                    </svg>
                  </button>

                  <span class="glyph">
                    <svg class="ring" viewBox="0 0 34 34"><ellipse cx="17" cy="17" rx="14.5" ry="13.5" fill="none" stroke-width="1.8" /></svg>
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
                <div v-if="idx < group.items.length - 1" class="rsep"></div>
              </template>
            </div>
          </div>
        </section>
      </div>

      <div v-if="!loading && !loadError && !list.length" class="empty">
        <svg viewBox="0 0 150 96" fill="none" stroke-width="2.4" stroke-linecap="round">
          <path d="M20 66 Q60 58 96 64 Q120 67 134 60" stroke="var(--hd-ink-soft)" />
          <path d="M40 40 l6 14 M46 40 l-6 14 M58 36 l7 18 M65 37 l-7 16" stroke="var(--hd-red)" />
          <path d="M96 30 q6 -12 14 -2 q8 -10 14 2 q6 8 -14 16 q-20 -8 -14 -16z" stroke="var(--hd-amber)" />
          <path d="M28 24 q10 -8 18 0 M112 78 q12 -6 20 2" stroke="var(--hd-ink-soft)" stroke-dasharray="3 6" />
        </svg>
        <p>还没有心愿 —— 去影单 / 书单挑一个，填上打算哪天看。</p>
        <div class="acts">
          <button type="button" class="hdbtn go" @click="router.push('/movie')">去影单</button>
          <button type="button" class="hdbtn" @click="router.push('/book')">去书单</button>
        </div>
      </div>

      <div v-if="loadError" class="load-error">
        <span>加载失败，请稍后重试</span>
        <button type="button" class="hdbtn" @click="loadData">重试</button>
      </div>

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
// 手绘皮肤：与设计图共用的唯一来源（handdrawn.css）；字体为马善政分片子集（结论 99）
import './handdrawn.css'
import '@/assets/fonts/handdrawn/mashanzheng/mashanzheng.css'
import type { SgjItem } from '@/types/api/business/item'

/**
 * 心愿页（0.0.3 FR-003-4）：未完成 WANT/DOING 的全类型汇总。
 * 排序口径在服务端（start_date 升序、无日期置后），分组口径在前端（utils/plan.ts，设计 结论 78）。
 * 状态流转全部复用既有端点：开始/退回/改日期走 PUT /app/item/{id}，完成走统一完成弹窗。
 *
 * 皮肤（issue #84）：样式全部来自 ./handdrawn.css —— 它是设计图
 * docs/design/mockups/plan-0.0.3/handdrawn-checklist.html 与产品共用的唯一来源，
 * 类名也与设计图逐一对齐（sheet/paper/row/ck/glyph/hdbtn…），因此「实现即设计图」，
 * 不存在两份样式各自漂移；组件样式块只放设计图没有的应用件。
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

<!-- 只放设计图里没有的应用件（加载容器、失败重试、到底部标记）；皮肤规则全在 handdrawn.css -->
<style lang="scss">
.plan-page {
  .plan-body { min-height: 240px; }

  .list-foot .end { font-size: 14px; color: var(--hd-ink-faint); }

  .load-error {
    display: flex; align-items: center; justify-content: center; gap: 12px;
    padding: 32px 0; color: var(--hd-ink-soft);
  }
}
</style>
