<template>
  <div class="plan-page">
    <div class="page-banner anim">
      <div class="banner-blob" aria-hidden="true"></div>
      <div class="banner-blob2" aria-hidden="true"></div>
      <div class="banner-text">
        <h1>计划 · 想到就排上</h1>
        <p>共 {{ total }} 项 · 在想 {{ wantCount }} · 在做 {{ doingCount }}</p>
      </div>
    </div>

    <div v-loading="loading" class="plan-body">
      <el-empty
        v-if="!loading && !loadError && !list.length"
        description="还没有计划 —— 去影单 / 书单挑一个，填上打算哪天看。"
      >
        <div class="empty-actions">
          <el-button type="primary" round @click="router.push('/movie')">去影单</el-button>
          <el-button round @click="router.push('/book')">去书单</el-button>
        </div>
      </el-empty>
      <div v-if="loadError" class="load-error">
        <span>加载失败，请稍后重试</span>
        <el-button size="small" round @click="loadData">重试</el-button>
      </div>

      <template v-for="group in groups" :key="group.key">
        <div class="group-head anim">
          <h2 class="group-title" :class="'group-' + group.key">{{ group.title }}</h2>
          <span class="group-count">{{ group.items.length }} 项</span>
          <span v-if="group.hint" class="group-hint">{{ group.hint }}</span>
        </div>
        <div class="plan-list">
          <div
            v-for="(item, idx) in group.items"
            :key="item.itemId"
            class="plan-card anim"
            :style="{ '--d': ((idx % 6) * 30) + 'ms' }"
            @click="openDetail(item)"
          >
            <span class="plan-glyph" :class="'glyph-' + item.itemType">{{ typeGlyph(item.itemType) }}</span>
            <div class="plan-main">
              <div class="plan-title">{{ item.title }}</div>
              <div class="plan-meta">
                <span class="status-tag" :class="'st-' + item.status">{{ statusLabel(item) }}</span>
                <span class="type-name">{{ typeName(item.itemType) }}</span>
                <span v-if="item.startDate" class="date-label" :class="{ overdue: group.key === 'overdue' }">
                  {{ planDateLabel(item.startDate.slice(0, 10), today) }}
                </span>
                <span v-else class="date-label empty">未排期</span>
              </div>
            </div>
            <div class="plan-actions" @click.stop>
              <template v-if="isLogin">
                <el-popover v-if="!item.startDate || group.key === 'unscheduled' || group.key === 'overdue'" :width="280" trigger="click">
                  <template #reference>
                    <el-button link size="small" class="act-date">📅 {{ item.startDate ? '改日期' : '定个日子' }}</el-button>
                  </template>
                  <el-date-picker
                    :model-value="item.startDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="打算哪天开始"
                    style="width: 100%"
                    @update:model-value="(v: string | null) => v && setPlanDate(item, v)"
                  />
                </el-popover>
                <el-button v-if="item.status === 'WANT'" link size="small" class="act-start" @click="startDoing(item)">开始 →</el-button>
                <el-button v-if="item.status === 'DOING'" link size="small" class="act-back" @click="backToWant(item)">退回心愿</el-button>
                <el-button type="primary" link size="small" @click="openComplete(item)">标记{{ doneVerb(item.itemType) }}</el-button>
              </template>
            </div>
          </div>
        </div>
      </template>

      <div v-if="hasMore" class="load-more-wrap">
        <el-button :loading="loadingMore" round @click="loadMore">加载更多</el-button>
      </div>
      <div v-if="!hasMore && list.length" class="grid-end">—— 已到底部 · 共 {{ total }} 项 ——</div>
    </div>

    <item-complete-dialog v-model="completeOpen" :item="completeItem" @submitted="onCompleted" />
  </div>
</template>

<script setup lang="ts" name="FrontPlan">
import { getToken } from '@/utils/auth'
import { useDict } from '@/utils/dict'
import { groupPlanItems, planDateLabel, localDateKey } from '@/utils/plan'
import { listPlanItems, getFrontItem, updateFrontItem } from '@/api/front/item'
import ItemCompleteDialog from '@/components/ItemCompleteDialog/index.vue'
import type { SgjItem } from '@/types/api/business/item'

/**
 * 计划页（0.0.3 FR-003-4）：未完成 WANT/DOING 的全类型汇总。
 * 排序口径在服务端（start_date 升序、无日期置后），分组口径在前端（utils/plan.ts，
 * 设计 结论 78）。状态流转全部复用既有端点：开始/退回/改日期走 PUT /app/item/{id}，
 * 完成走统一完成弹窗（ItemCompleteDialog）。
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
const pageSize = 20
const hasMore = computed(() => list.value.length < total.value)

/** 分组基准日：每次取数时定格为“用户眼里的今天”，避免跨零点后分组漂移 */
const today = ref(localDateKey(new Date()))

const completeOpen = ref(false)
const completeItem = ref<SgjItem | null>(null)

const groups = computed(() => groupPlanItems(list.value, today.value))
const wantCount = computed(() => list.value.filter(i => i.status === 'WANT').length)
const doingCount = computed(() => list.value.filter(i => i.status === 'DOING').length)

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

/** 状态标签取字典文案（影视/书籍/地点三套字典按类型路由），不写死中文 */
function statusLabel(item: SgjItem): string {
  const dicts: Record<string, any> = {
    MOVIE: sgj_movie_status.value,
    TV: sgj_movie_status.value,
    BOOK: sgj_book_status.value,
    PLACE: sgj_place_status.value
  }
  const dict = dicts[item.itemType || ''] || []
  return dict.find((d: any) => d.value === item.status)?.label || item.status || ''
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

/** 点卡片本体跳到对应模块的详情（沿用“列表页 + ?itemId= 自动开详情”的最小方案） */
function openDetail(item: SgjItem): void {
  if (!item.itemId) return
  const path = item.itemType === 'BOOK' ? '/book' : item.itemType === 'PLACE' ? '/travel' : '/movie'
  getFrontItem(item.itemId).then(() => {
    router.push({ path, query: { itemId: String(item.itemId) } })
  }).catch(() => {})
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.plan-page {
  max-width: 1080px;
  margin: 0 auto;
  padding: 8px 24px 40px;
  font-family: var(--sgj-font-sans);
}

.page-banner {
  position: relative;
  border-radius: 20px;
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  padding: 30px 36px;
  margin-bottom: 22px;
  overflow: hidden;
  box-shadow: var(--sgj-shadow-sm);

  .banner-blob,
  .banner-blob2 {
    position: absolute;
    border-radius: 50%;
    background: var(--sgj-primary-soft);
    opacity: 0.6;
  }

  .banner-blob { width: 220px; height: 220px; right: -60px; top: -120px; }
  .banner-blob2 { width: 140px; height: 140px; right: 160px; bottom: -80px; background: var(--sgj-moss-soft); }

  .banner-text {
    position: relative;
    z-index: 1;

    h1 {
      margin: 0;
      font-family: var(--sgj-font-serif);
      font-size: 26px;
      color: var(--sgj-text);
    }

    p {
      margin: 8px 0 0;
      font-size: 13.5px;
      color: var(--sgj-text-2);
    }
  }
}

.plan-body { min-height: 240px; }

.empty-actions { display: flex; gap: 10px; justify-content: center; }

.load-error {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: center;
  padding: 32px 0;
  color: var(--sgj-text-3);
}

.group-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 26px 0 12px;

  .group-title {
    margin: 0;
    font-family: var(--sgj-font-serif);
    font-size: 18px;
    color: var(--sgj-text);
  }

  .group-title.group-overdue { color: var(--sgj-primary); }

  .group-count { font-size: 12.5px; color: var(--sgj-text-4); }

  .group-hint { font-size: 12.5px; color: var(--sgj-text-3); }
}

.plan-list { display: flex; flex-direction: column; gap: 10px; }

.plan-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: 14px;
  padding: 12px 16px;
  cursor: pointer;
  transition: box-shadow 0.16s, transform 0.16s;

  &:hover {
    box-shadow: var(--sgj-shadow-hover);
    transform: translateY(-1px);
  }

  .plan-glyph {
    flex: none;
    width: 40px;
    height: 40px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: var(--sgj-font-serif);
    font-size: 17px;
    background: var(--sgj-primary-soft);
    color: var(--sgj-primary);

    &.glyph-BOOK { background: var(--sgj-moss-soft); color: var(--sgj-moss); }
    &.glyph-PLACE { background: var(--sgj-amber-soft); color: var(--sgj-amber); }
  }

  .plan-main { flex: 1; min-width: 0; }

  .plan-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--sgj-text);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .plan-meta {
    margin-top: 4px;
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 12.5px;
    color: var(--sgj-text-3);

    .status-tag {
      padding: 1px 8px;
      border-radius: 999px;
      background: var(--sgj-bg-deep);
      color: var(--sgj-text-2);

      &.st-DOING { background: var(--sgj-primary-soft); color: var(--sgj-primary); }
      &.st-DONE { background: var(--sgj-moss-soft); color: var(--sgj-moss); }
    }

    .date-label {
      &.overdue { color: var(--sgj-primary); font-weight: 600; }
      &.empty { color: var(--sgj-text-4); }
    }
  }

  .plan-actions {
    display: flex;
    align-items: center;
    gap: 4px;
    flex: none;

    :deep(.el-button + .el-button) { margin-left: 0; }
  }
}

.load-more-wrap {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

.grid-end {
  text-align: center;
  color: var(--sgj-text-4);
  font-size: 12.5px;
  margin-top: 24px;
}

@media (max-width: 768px) {
  .plan-page { padding: 4px 14px 24px; }

  .page-banner { padding: 20px 20px;

    .banner-text h1 { font-size: 21px; }
  }

  .plan-card {
    flex-wrap: wrap;

    .plan-actions {
      width: 100%;
      justify-content: flex-end;
      padding-top: 6px;
      border-top: 1px dashed var(--sgj-border-card);
    }
  }
}
</style>
