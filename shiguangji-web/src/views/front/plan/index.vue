<template>
  <div class="plan-page">
    <div class="page-banner anim">
      <div class="banner-blob" aria-hidden="true"></div>
      <div class="banner-blob2" aria-hidden="true"></div>
      <div class="banner-text">
        <h1>心愿 · 想到就排上</h1>
        <p>影单 / 书单 / 足迹里<strong>还没做完</strong>的心愿，按你定的日子排在一起：定了日子的在前，没定日子的在最后一组。</p>
        <div class="banner-chips">
          <span class="bchip">共 {{ total }} 项</span>
          <span class="bchip bchip-doing">在做 {{ doingCount }}</span>
          <span class="bchip">在想 {{ wantCount }}</span>
        </div>
      </div>
    </div>

    <div v-loading="loading" class="plan-body">
      <el-empty
        v-if="!loading && !loadError && !list.length"
        description="还没有心愿 —— 去影单 / 书单挑一个，填上打算哪天看。"
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

      <!-- 每一组是一块有身份色的区域：标题说「这批事是什么」，说明说「该拿它做什么」 -->
      <section
        v-for="group in groups"
        :key="group.key"
        class="plan-group anim"
        :class="'g-' + group.key"
      >
        <div class="group-head">
          <h2 class="group-title">{{ group.title }}</h2>
          <span class="group-count">{{ group.items.length }} 项</span>
          <span class="group-hint">{{ group.hint }}</span>
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
                <!-- 无日期的卡片不标「未排期」：所在组名「还没定日子」已经说了，重复且词汇不一致 -->
              </div>
            </div>
            <div class="plan-actions" @click.stop>
              <template v-if="isLogin">
                <!-- 状态推进（主操作，实心）：想看 → 开始；在看 → 标记完成 -->
                <el-button
                  v-if="item.status === 'WANT' && item.itemType !== 'PLACE'"
                  type="primary"
                  size="small"
                  round
                  @click="startDoing(item)"
                >开始{{ item.itemType === 'BOOK' ? '读' : '看' }} →</el-button>
                <el-button
                  v-if="item.status !== 'DONE'"
                  :type="item.status === 'DOING' ? 'primary' : 'default'"
                  :plain="item.status === 'WANT'"
                  size="small"
                  round
                  @click="openComplete(item)"
                >标记{{ doneVerb(item.itemType) }}</el-button>

                <!-- 计划日期（次操作，描边按钮）：有日期叫改日期、没有叫定个日子 -->
                <el-popover :width="280" trigger="click">
                  <template #reference>
                    <el-button plain size="small" round>📅 {{ item.startDate ? '改日期' : '定个日子' }}</el-button>
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

                <!-- 退回（次操作）：只在在看的卡片上出现 -->
                <el-button v-if="item.status === 'DOING'" plain size="small" round @click="backToWant(item)">退回心愿</el-button>
              </template>
              <span v-else class="actions-readonly">仅浏览 · 登录后可操作</span>
            </div>
          </div>
        </div>
      </section>

      <div v-if="list.length" class="list-foot">
        <span class="list-count">已展示 {{ list.length }} / 共 {{ total }} 项</span>
        <el-button v-if="hasMore" :loading="loadingMore" round @click="loadMore">加载更多</el-button>
        <span v-else class="list-end">—— 已到底部 ——</span>
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

/** 点卡片本体跳到对应模块并自动开详情：带上 type/status 预选胶囊——
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
      line-height: 1.7;
      color: var(--sgj-text-2);
      max-width: 640px;
    }

    .banner-chips {
      margin-top: 12px;
      display: flex;
      gap: 8px;

      .bchip {
        font-size: 12px;
        padding: 2px 10px;
        border-radius: 999px;
        background: var(--sgj-bg-deep);
        color: var(--sgj-text-2);

        &.bchip-doing {
          background: var(--sgj-primary-soft);
          color: var(--sgj-primary);
          font-weight: 600;
        }
      }
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

/* 每一组是一块有身份色的区域：左侧色条 + 标题染色把「这组意味着什么」画出来，
   组名自解释、说明写「该拿它做什么」（结论 93） */
.plan-group {
  margin-top: 22px;
  padding-left: 14px;
  border-left: 3px solid var(--sgj-border);

  &.g-overdue {
    border-left-color: var(--sgj-primary);

    .group-title { color: var(--sgj-primary); }
  }

  &.g-thisWeek {
    border-left-color: var(--sgj-amber);

    .group-title { color: var(--sgj-amber); }
  }

  &.g-later {
    border-left-color: var(--sgj-moss);

    .group-title { color: var(--sgj-moss); }
  }

  &.g-unscheduled {
    border-left-color: var(--sgj-border);

    .group-title { color: var(--sgj-text-2); }
  }
}

.group-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 0 0 12px;

  .group-title {
    margin: 0;
    font-family: var(--sgj-font-serif);
    font-size: 18px;
    color: var(--sgj-text);
  }

  .group-count {
    font-size: 11.5px;
    padding: 1px 8px;
    border-radius: 999px;
    background: var(--sgj-bg-deep);
    color: var(--sgj-text-2);
  }

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
    gap: 8px;
    flex: none;

    /* 元素之间靠 gap 分隔：清掉 element-plus 的相邻按钮 margin，避免间距忽大忽小 */
    :deep(.el-button + .el-button) { margin-left: 0; }

    .actions-readonly {
      font-size: 12.5px;
      color: var(--sgj-text-4);
    }
  }
}

/* 列表底部：始终给出「已展示 X / 共 N」，有下一页时给加载更多（原先只在有下一页时才出现，
   少量数据一页装完就等于看不见分页） */
.list-foot {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;

  .list-count { font-size: 12.5px; color: var(--sgj-text-3); }

  .list-end { font-size: 12.5px; color: var(--sgj-text-4); }
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
      padding-top: 8px;
      border-top: 1px dashed var(--sgj-border-card);

      /* 移动端：三个按钮平分一行，点按目标更大 */
      :deep(.el-button) {
        flex: 1;
        margin-left: 0;
      }
    }
  }
}
</style>
