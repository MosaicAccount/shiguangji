<template>
  <div class="app-container workbench" v-loading="loading">
    <div class="wb-greet">
      <div class="wb-greet-title">{{ greeting }}</div>
      <div class="wb-date">{{ today }}</div>
    </div>

    <div class="wb-stats">
      <div class="wb-stat">
        <span class="wb-stat-icon wb-stat-icon-terra">影</span>
        <div class="wb-stat-main">
          <span class="wb-stat-number">{{ mediaTotal }}</span>
          <span class="wb-stat-label">影视 · 本月 +{{ mediaMonth }}</span>
          <span class="wb-stat-sub">电影与电视剧合计</span>
        </div>
      </div>
      <div class="wb-stat">
        <span class="wb-stat-icon wb-stat-icon-moss">书</span>
        <div class="wb-stat-main">
          <span class="wb-stat-number">{{ total('BOOK') }}</span>
          <span class="wb-stat-label">书籍 · 本月 +{{ monthAdd('book') }}</span>
          <span class="wb-stat-sub">读过的书</span>
        </div>
      </div>
      <div class="wb-stat">
        <span class="wb-stat-icon wb-stat-icon-amber">地</span>
        <div class="wb-stat-main">
          <span class="wb-stat-number">{{ total('PLACE') }}</span>
          <span class="wb-stat-label">地点 · 本月 +{{ monthAdd('place') }}</span>
          <span class="wb-stat-sub">到过的地方</span>
        </div>
      </div>
      <div class="wb-stat">
        <span class="wb-stat-icon wb-stat-icon-line">记</span>
        <div class="wb-stat-main">
          <span class="wb-stat-number">{{ stats?.noteTotal || 0 }}</span>
          <span class="wb-stat-label">笔记 · 本月 +{{ stats?.noteMonth || 0 }}</span>
          <span class="wb-stat-sub">写下的思考</span>
        </div>
      </div>
    </div>

    <div class="wb-main">
      <!-- 最近时光表 -->
      <section class="wb-recent">
        <div class="wb-sec-head">
          <span class="wb-sec-title">最近时光</span>
        </div>
        <div class="wb-table">
          <div v-if="!stats?.recentItems || stats.recentItems.length === 0" class="empty-tip">暂无记录</div>
          <div v-for="(item, idx) in stats?.recentItems || []" :key="item.itemId" class="wb-row" :class="{ odd: idx % 2 === 1 }">
            <span class="wb-row-ic" :style="rowIconStyle(idx)">{{ rowGlyph(item.itemType) }}</span>
            <div class="wb-row-main">
              <span class="wb-row-title">{{ item.title }}</span>
              <span class="wb-row-type">{{ typeName(item.itemType) }}</span>
            </div>
            <span class="wb-row-rating">—</span>
            <span class="wb-row-time">{{ parseTime(item.createTime, '{y}-{m}-{d}') }}</span>
            <span class="wb-row-status" :class="item.status === 'DONE' ? 'done' : 'wish'">
              {{ item.status === 'DONE' ? '已看' : '想看' }}
            </span>
          </div>
        </div>
        <router-link to="/business/item" class="wb-more">查看全部 →</router-link>
      </section>

      <!-- 快捷入口 -->
      <aside class="wb-quick">
        <div class="wb-sec-head">
          <span class="wb-sec-title">快捷入口</span>
        </div>
        <div class="wb-quick-grid">
          <router-link v-for="q in quickLinks" :key="q.to" :to="q.to" class="wb-qk">
            <span class="wb-qk-icon" :style="{ background: q.bg, color: q.fg }">{{ q.glyph }}</span>
            <span class="wb-qk-title">{{ q.title }}</span>
            <span class="wb-qk-sub">{{ q.sub }}</span>
          </router-link>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts" name="Index">
import { getDashboardStatistics } from '@/api/business/dashboard'
import type { DashboardStatistics } from '@/types/api/business/dashboard'
import { parseTime } from '@/utils/sgj'

const stats = ref<DashboardStatistics | null>(null)
const loading = ref<boolean>(false)

const typeNames: Record<string, string> = {
  MOVIE: '电影',
  TV: '电视剧',
  BOOK: '书籍',
  PLACE: '地点',
  NOTE: '笔记'
}

const typeName = (type?: string) => typeNames[type || ''] || type || '-'

const quickLinks = [
  { to: '/sgj/media/movie', glyph: '影', title: '电影管理', sub: '影视条目', bg: 'var(--sgj-primary-soft)', fg: 'var(--sgj-primary-dark)' },
  { to: '/sgj/media/tv', glyph: '剧', title: '电视剧管理', sub: '影视条目', bg: 'var(--sgj-primary-soft)', fg: 'var(--sgj-primary-dark)' },
  { to: '/sgj/book', glyph: '书', title: '书籍管理', sub: '书籍条目', bg: 'var(--sgj-moss-soft)', fg: 'var(--sgj-moss)' },
  { to: '/sgj/place', glyph: '地', title: '地点管理', sub: '地点足迹', bg: 'var(--sgj-amber-soft)', fg: 'var(--sgj-amber)' },
  { to: '/sgj/note', glyph: '记', title: '笔记管理', sub: '记录思考', bg: 'var(--sgj-cover)', fg: 'var(--sgj-text-2)' },
  { to: '/sgj/recycle', glyph: '↺', title: '回收站', sub: '恢复 / 彻底删除', bg: 'var(--sgj-moss-soft)', fg: 'var(--sgj-moss)' }
]

const rowGlyph = (type?: string): string => {
  switch (type) {
    case 'MOVIE': return '影'
    case 'TV': return '剧'
    case 'BOOK': return '书'
    case 'PLACE': return '地'
    default: return '记'
  }
}

function rowIconStyle(index: number): Record<string, string> {
  const bg = ['var(--sgj-cover)', 'var(--sgj-primary-soft)', 'var(--sgj-moss-soft)', 'var(--sgj-amber-soft)']
  const fg = ['var(--sgj-text-2)', 'var(--sgj-primary-dark)', 'var(--sgj-moss)', 'var(--sgj-amber)']
  return { background: bg[index % 4], color: fg[index % 4] }
}

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const today = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 · 星期${week}`
})

const mediaTotal = computed(() => {
  const movie = stats.value?.total?.['MOVIE'] || 0
  const tv = stats.value?.total?.['TV'] || 0
  return movie + tv
})

const mediaMonth = computed(() => {
  const trend = stats.value?.trend || []
  const last = trend[trend.length - 1]
  return last ? (last.movie || 0) + (last.tv || 0) : 0
})

function total(type: string): number {
  return stats.value?.total?.[type] || 0
}

function monthAdd(type: keyof DashboardStatistics['trend'][number]): number {
  const trend = stats.value?.trend || []
  const last = trend[trend.length - 1]
  return last ? (last[type] as number) || 0 : 0
}

function getStats(): void {
  loading.value = true
  getDashboardStatistics().then(response => {
    stats.value = response.data || null
  }).finally(() => {
    loading.value = false
  })
}

getStats()
</script>

<style scoped lang="scss">
.workbench {
  font-family: var(--sgj-font-sans);
}

.wb-greet {
  margin-bottom: 24px;

  .wb-greet-title {
    font-family: var(--sgj-font-serif);
    font-weight: 700;
    font-size: 26px;
    color: var(--el-text-color-primary);
  }

  .wb-date {
    font-size: 13px;
    color: var(--el-text-color-secondary);
    margin-top: 6px;
  }
}

.wb-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 32px;
}

.wb-stat {
  display: flex;
  gap: 16px;
  padding: 18px 20px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 16px;

  .wb-stat-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 12px;
    font-family: var(--sgj-font-serif);
    font-weight: 700;
    font-size: 16px;
    flex-shrink: 0;
  }

  .wb-stat-icon-terra { background: var(--sgj-primary-soft); color: var(--sgj-primary-dark); }
  .wb-stat-icon-moss { background: var(--sgj-moss-soft); color: var(--sgj-moss); }
  .wb-stat-icon-amber { background: var(--sgj-amber-soft); color: var(--sgj-amber); }
  .wb-stat-icon-line { background: var(--sgj-cover); color: var(--sgj-text-2); }

  .wb-stat-main {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .wb-stat-number {
    font-family: var(--sgj-font-serif);
    font-weight: 700;
    font-size: 28px;
    line-height: 1.3;
    color: var(--el-text-color-primary);
  }

  .wb-stat-label {
    font-size: 14px;
    font-weight: 500;
    color: var(--el-text-color-regular);
    margin-top: 4px;
  }

  .wb-stat-sub {
    font-size: 11px;
    color: var(--el-text-color-secondary);
    margin-top: 2px;
  }
}

.wb-main {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 24px;
  align-items: start;
}

.wb-sec-head {
  margin-bottom: 16px;

  .wb-sec-title {
    font-family: var(--sgj-font-serif);
    font-size: 16px;
    font-weight: 700;
    color: var(--el-text-color-primary);
  }
}

.wb-recent {
  .wb-table {
    border-radius: 16px;
    overflow: hidden;
  }

  .wb-row {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 12px 16px;
    background: var(--el-bg-color-overlay);
    border-radius: 12px;
    margin-bottom: 8px;

    &.odd {
      background: var(--el-bg-color);
      border: 1px solid var(--el-border-color-light);
    }

    .wb-row-ic {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      border-radius: 9px;
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 13px;
      flex-shrink: 0;
    }

    .wb-row-main {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    .wb-row-title {
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 14px;
      color: var(--el-text-color-primary);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .wb-row-type {
      font-size: 11px;
      color: var(--el-text-color-secondary);
      margin-top: 2px;
    }

    .wb-row-rating {
      font-family: var(--sgj-font-serif);
      font-size: 13px;
      color: var(--sgj-amber);
      width: 60px;
      text-align: right;
      flex-shrink: 0;
    }

    .wb-row-time {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      width: 80px;
      flex-shrink: 0;
    }

    .wb-row-status {
      flex-shrink: 0;
      padding: 0 12px;
      height: 24px;
      display: inline-flex;
      align-items: center;
      border-radius: 12px;
      font-size: 11px;
      font-weight: 500;

      &.done {
        background: var(--sgj-moss-soft);
        color: var(--sgj-moss);
      }

      &.wish {
        background: var(--sgj-amber-soft);
        color: var(--sgj-amber);
      }
    }
  }

  .wb-more {
    display: inline-block;
    margin-top: 14px;
    font-size: 12px;
    color: var(--el-color-primary);
    text-decoration: none;
  }
}

.wb-quick {
  .wb-quick-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
  }

  .wb-qk {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 16px;
    background: var(--el-bg-color-overlay);
    border: 1px solid var(--el-border-color-light);
    border-radius: 16px;
    text-decoration: none;
    transition: box-shadow 0.16s, transform 0.16s;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 24px rgba(23, 27, 26, 0.1);
    }

    .wb-qk-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      border-radius: 10px;
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 15px;
    }

    .wb-qk-title {
      font-weight: 500;
      font-size: 14px;
      color: var(--el-text-color-primary);
    }

    .wb-qk-sub {
      font-size: 11px;
      color: var(--el-text-color-secondary);
    }
  }
}

.empty-tip {
  color: var(--el-text-color-placeholder);
  text-align: center;
  padding: 30px 0;
  font-size: 13px;
  background: var(--el-bg-color-overlay);
  border-radius: 16px;
  border: 1px solid var(--el-border-color-light);
}

@media (max-width: 1200px) {
  .wb-stats {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 900px) {
  .wb-main {
    grid-template-columns: 1fr;
  }
}
</style>