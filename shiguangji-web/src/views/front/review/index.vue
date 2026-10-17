<template>
  <div class="plan-page hd-page">
    <!-- 手绘抖线滤镜：与 handdrawn.css 同名同参数 -->
    <svg width="0" height="0" style="position:absolute" aria-hidden="true">
      <filter id="rough">
        <feTurbulence type="fractalNoise" baseFrequency="0.02 0.05" numOctaves="2" seed="7" result="n" />
        <feDisplacementMap in="SourceGraphic" in2="n" scale="3.2" xChannelSelector="R" yChannelSelector="G" />
      </filter>
    </svg>

    <!-- ===== 加载中：手绘骨架 ===== -->
    <div v-if="loading">
      <div class="hdsk-hero">
        <span class="hdsk" style="width:150px;height:60px"></span>
        <span style="display:grid;gap:10px">
          <span class="hdsk hdsk-line" style="width:58%"></span>
          <span class="hdsk hdsk-line" style="width:38%;height:11px"></span>
        </span>
      </div>
      <div class="hdsk-stats">
        <div v-for="i in 4" :key="i" class="hdsk hdsk-stat">
          <span class="hdsk" style="width:34px;height:34px"></span>
          <span class="hdsk hdsk-line" style="width:52px;height:30px;margin-top:10px"></span>
          <span class="hdsk hdsk-line" style="width:96px;height:11px;margin-top:8px"></span>
        </div>
      </div>
      <div class="hdsk-sheet">
        <span class="hdsk" style="width:150px;height:22px"></span>
        <span class="hdsk" style="width:100%;height:200px;margin-top:16px;border-radius:14px"></span>
      </div>
      <div class="hdsk-sheet">
        <span class="hdsk" style="width:120px;height:22px"></span>
        <div class="hd-cols" style="margin-top:16px">
          <div v-for="n in 2" :key="n" class="hdsk" style="padding:16px 18px;border-radius:14px">
            <span class="hdsk" style="width:90px;height:15px"></span>
            <div v-for="r in 3" :key="r" class="hdsk-row">
              <span class="hdsk" style="width:34px;height:34px;border-radius:9px"></span>
              <span style="flex:1"><span class="hdsk hdsk-line" :style="{ width: 60 + r * 4 + '%' }"></span><span class="hdsk hdsk-line" style="width:36%;height:10px;margin-top:7px"></span></span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 失败：一张纸上的便条 + 重试 ===== -->
    <div v-else-if="error" class="sheet hd-sec">
      <div class="paper">
        <span class="tape"></span>
        <div class="lines"><span class="frame"></span></div>
        <div class="hd-empty">
          <h3>这一年没能翻开</h3>
          <p>{{ error }}</p>
          <div class="acts"><button class="hdbtn go" type="button" @click="load()">再试一次</button></div>
        </div>
      </div>
    </div>

    <template v-else-if="data">
      <!-- ===== 报头 ===== -->
      <div class="hd-hero">
        <div class="hd-eyebrow">YEAR IN REVIEW</div>
        <div class="hd-hero-main">
          <button class="hd-round" type="button" aria-label="上一年" @click="step(-1)">‹</button>
          <span class="hd-year">{{ year }}</span>
          <button class="hd-round" type="button" aria-label="下一年" :disabled="atMaxYear" @click="step(1)">›</button>
          <p class="hd-lead">这一年，你记录了 <b>{{ totalMemories }}</b> 件值得记住的小事。</p>
        </div>
        <p class="hd-caliber">条目按完成日期归年 · 笔记按写下时间归年</p>
        <div class="hd-chips" v-if="data.years.length">
          <button v-for="y in data.years" :key="y" type="button" class="hd-chip" :class="{ doing: y === year }" @click="switchYear(y)">{{ y }}</button>
          <span class="cap">有记录的年份</span>
        </div>
      </div>

      <!-- ===== 主体：默认 / 空态 ===== -->
      <div v-if="isEmpty">
        <div class="sheet hd-sec">
          <div class="paper">
            <span class="tape"></span>
            <div class="lines"><span class="frame"></span></div>
            <div class="hd-empty">
              <svg viewBox="0 0 150 96" fill="none" stroke="var(--hd-ink-soft)" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M44 14 Q75 10 106 14 Q110 45 106 80 Q75 84 44 80 Q40 46 44 14 Z"/>
                <path d="M57 33 Q75 30 93 33"/><path d="M57 45 Q75 42 93 45"/><path d="M57 57 Q70 54 79 57"/>
                <path d="M116 26 Q128 18 137 24 Q135 34 125 33"/>
              </svg>
              <h3>{{ year }} 年还没有记录</h3>
              <p>去影单、书单或足迹添几笔，明年这个时候就有东西可看了；也可以往右翻回有记录的年份。</p>
              <div class="acts">
                <button class="hdbtn go" type="button" @click="$router.push('/movie')">去影单看看</button>
                <button class="hdbtn" type="button" @click="$router.push('/book')">去书单看看</button>
              </div>
              <p class="hd-note" style="margin-top:20px">换年份用上面的 ‹ ›，胶囊只列有记录的年份。</p>
            </div>
          </div>
        </div>
      </div>

      <div v-else>
        <!-- 统计卡 -->
        <div class="hd-stats">
          <div class="hd-stat hdk-movie">
            <span class="hdg"><svg viewBox="0 0 34 34"><ellipse cx="17" cy="17" rx="14.5" ry="13.5"/></svg><span>影</span></span>
            <span class="num">{{ data.summary.movie + data.summary.tv }}</span>
            <span class="lbl">看过 · 电影 {{ data.summary.movie }} · 剧集 {{ data.summary.tv }}</span>
          </div>
          <div class="hd-stat hdk-book">
            <span class="hdg"><svg viewBox="0 0 34 34"><ellipse cx="17" cy="17" rx="14.5" ry="13.5"/></svg><span>书</span></span>
            <span class="num">{{ data.summary.book }}</span>
            <span class="lbl">读过 · 书籍</span>
          </div>
          <div class="hd-stat hdk-place">
            <span class="hdg"><svg viewBox="0 0 34 34"><ellipse cx="17" cy="17" rx="14.5" ry="13.5"/></svg><span>地</span></span>
            <span class="num">{{ data.summary.place }}</span>
            <span class="lbl">去过 · 地点</span>
          </div>
          <div class="hd-stat hdk-note">
            <span class="hdg"><svg viewBox="0 0 34 34"><ellipse cx="17" cy="17" rx="14.5" ry="13.5"/></svg><span>文</span></span>
            <span class="num">{{ data.summary.note }}</span>
            <span class="lbl">写过 · 笔记</span>
          </div>
        </div>

        <!-- 足迹 -->
        <ReviewMap :places="data.places" @open-place="openPlace" @open-group="openGroup" />

        <!-- 评分榜 -->
        <section class="hd-sec">
          <div class="g-head">
            <h2 class="g-title">评分最高<svg viewBox="0 0 100 9" preserveAspectRatio="none"><path d="M2 5 Q12 1 24 4 T48 4 T74 3 T98 5" stroke="currentColor" stroke-width="2.4" fill="none" stroke-linecap="round" opacity=".85"/></svg></h2>
            <span class="g-count">{{ ratedTotal }} 条里 {{ ratedCount }} 条有评分</span>
          </div>
          <div class="hd-cols">
            <div class="sheet alt">
              <div class="paper">
                <span class="tape"></span>
                <div class="lines"><span class="frame"></span></div>
                <div class="g-head g-movie"><h2 class="g-title">影视<svg viewBox="0 0 100 9" preserveAspectRatio="none"><path d="M2 5 Q12 1 24 4 T48 4 T74 3 T98 5" stroke="currentColor" stroke-width="2.4" fill="none" stroke-linecap="round" opacity=".85"/></svg></h2></div>
                <div class="rows g-movie">
                  <div v-if="!data.topRated.movie.length" class="hd-note">这一年还没有打过分的影视。</div>
                  <div v-for="(it, i) in data.topRated.movie" :key="it.itemId" class="row clickable"
                       role="button" tabindex="0" @click="openItem(it)" @keydown.enter="openItem(it)">
                    <span class="glyph"><svg class="ring" viewBox="0 0 34 34"><ellipse cx="17" cy="17" rx="14.5" ry="13.5" fill="none"/></svg><span>{{ it.itemType === 'TV' ? '剧' : '影' }}</span></span>
                    <div class="main"><div class="t">{{ it.title }}</div><div class="m"><span>{{ topMeta(it) }}</span></div></div>
                    <span class="hd-score">{{ it.rating }}</span>
                  </div>
                </div>
              </div>
            </div>
            <div class="sheet">
              <div class="paper">
                <span class="tape"></span>
                <div class="lines"><span class="frame"></span></div>
                <div class="g-head g-book"><h2 class="g-title">书籍<svg viewBox="0 0 100 9" preserveAspectRatio="none"><path d="M2 5 Q12 1 24 4 T48 4 T74 3 T98 5" stroke="currentColor" stroke-width="2.4" fill="none" stroke-linecap="round" opacity=".85"/></svg></h2></div>
                <div class="rows g-book">
                  <div v-if="!data.topRated.book.length" class="hd-note">这一年还没有打过分的书。</div>
                  <div v-for="it in data.topRated.book" :key="it.itemId" class="row clickable"
                       role="button" tabindex="0" @click="openItem(it)" @keydown.enter="openItem(it)">
                    <span class="glyph"><svg class="ring" viewBox="0 0 34 34"><ellipse cx="17" cy="17" rx="14.5" ry="13.5" fill="none"/></svg><span>书</span></span>
                    <div class="main"><div class="t">{{ it.title }}</div><div class="m"><span>{{ topMeta(it) }}</span></div></div>
                    <span class="hd-score">{{ it.rating }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- 标签 -->
        <section class="hd-sec">
          <div class="g-head">
            <h2 class="g-title">都是些什么<svg viewBox="0 0 100 9" preserveAspectRatio="none"><path d="M2 5 Q12 1 24 4 T48 4 T74 3 T98 5" stroke="currentColor" stroke-width="2.4" fill="none" stroke-linecap="round" opacity=".85"/></svg></h2>
            <span class="g-count">Top {{ topTags.length }} / {{ data.tags.length }} 个标签</span>
          </div>
          <div class="sheet">
            <div class="paper">
              <span class="tape"></span>
              <div class="lines"><span class="frame"></span></div>
              <p class="hd-note" style="margin: 2px 0 10px">统计影视 / 书籍 / 地点的条目标签，笔记不参与。</p>
              <div class="hd-tags">
                <div v-for="t in topTags" :key="t.name" class="hd-tag">
                  <span class="tn">{{ t.name }}</span>
                  <span class="tb" :style="{ width: barWidth(t.count) + '%' }"></span>
                  <span class="tc">{{ t.count }}</span>
                </div>
              </div>
              <p v-if="restTagCount" class="hd-note" style="margin-top:6px">其余 {{ restTagCount }} 个标签未进前十。</p>
            </div>
          </div>
        </section>
      </div>
    </template>

    <!-- 弹层：点详情 / 组地图（高德按需加载） -->
    <ReviewDetailModal :mode="modalMode" :place="modalPlace" :group="modalGroup" :media="modalMedia" :year="year"
                       @close="closeModal" @open-place="openPlace" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getReviewYear } from '@/api/front/review'
import type { ReviewPlace, ReviewYearData } from '@/types/api/front/review'
import { getFrontItem } from '@/api/front/item'
import type { SgjItem } from '@/types/api/business/item'
import ReviewMap from './ReviewMap.vue'
import ReviewDetailModal from './ReviewDetailModal.vue'
// 手绘皮肤唯一来源：与 /plan 共用同一份（全局 import，不能放进 scoped style）
import '../plan/handdrawn.css'

defineOptions({ name: 'FrontReview' })

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const data = ref<ReviewYearData | null>(null)
const year = ref(new Date().getFullYear())

const totalMemories = computed(() =>
  data.value ? data.value.summary.total + data.value.summary.note : 0
)
const isEmpty = computed(() =>
  data.value !== null && data.value.summary.total === 0 && data.value.summary.note === 0
)
const atMaxYear = computed(() =>
  !data.value || data.value.years.length === 0 || year.value >= Math.max(...data.value.years)
)
const ratedTotal = computed(() =>
  data.value ? data.value.summary.movie + data.value.summary.tv + data.value.summary.book : 0
)
const ratedCount = computed(() =>
  data.value ? data.value.topRated.ratedCount.movie + data.value.topRated.ratedCount.book : 0
)

/** 标签榜：Top 10，其余折叠成一行 */
const TOP_TAG_N = 10
const topTags = computed(() => (data.value ? data.value.tags.slice(0, TOP_TAG_N) : []))
const restTagCount = computed(() =>
  data.value ? Math.max(0, data.value.tags.length - TOP_TAG_N) : 0
)
function barWidth(count: number): number {
  const max = topTags.value[0]?.count ?? 1
  return Math.max(8, Math.round((count / max) * 100))
}

/** 评分榜行内元信息：电影 · 5 月看过 / 剧集 · 9 月看完 / 书籍 · 4 月读完 */
function topMeta(it: { itemType?: string; finishDate?: string | null }): string {
  const at = monthOf(it.finishDate)
  if (it.itemType === 'TV') return `剧集 · ${at}看完`
  if (it.itemType === 'BOOK') return `书籍 · ${at}读完`
  return `电影 · ${at}看过`
}

function monthOf(finishDate?: string | null): string {
  const m = finishDate ? Number(finishDate.slice(5, 7)) : 0
  return m > 0 ? `${m} 月` : ''
}

/* ===== 弹层状态：mode 四态（null 关闭）；条目详情与地点详情同一套手绘弹窗 ===== */
const modalMode = ref<'place' | 'group' | 'media' | null>(null)
const modalPlace = ref<ReviewPlace | null>(null)
const modalGroup = ref<{ key: string; level: '省' | '大区'; places: ReviewPlace[] } | null>(null)
const modalMedia = ref<SgjItem | null>(null)

/** 评分榜点开条目：取详情后用手绘弹窗展示（不跳列表、不用抽屉） */
function openItem(it: { itemId?: number }) {
  if (!it.itemId) return
  getFrontItem(it.itemId)
    .then((res) => {
      modalMedia.value = (res.data as SgjItem) || null
      modalMode.value = 'media'
    })
    .catch(() => {})
}

function openPlace(places: ReviewPlace | ReviewPlace[]) {
  modalPlace.value = Array.isArray(places) ? places[0] ?? null : places
  modalMode.value = 'place'
}
function openGroup(group: { key: string; level: '省' | '大区'; places: ReviewPlace[] }) {
  modalGroup.value = group
  modalMode.value = 'group'
}
function closeModal() {
  modalMode.value = null
}

/* ===== 数据加载与年份切换 ===== */
function queryYear(): number | null {
  const raw = String(route.query.year ?? '')
  return /^\d{4}$/.test(raw) ? Number(raw) : null
}

async function load(target?: number) {
  const y = target ?? queryYear() ?? year.value
  loading.value = true
  error.value = ''
  try {
    const res = await getReviewYear(y)
    const d = res.data
    if (!d) throw new Error('回顾数据加载失败')
    data.value = d
    year.value = d.year
    // 无查询参数时默认落到最近有数据的年份（当前年没数据就翻回去）
    if (queryYear() === null && d.years.length > 0 && d.years[0] !== y) {
      await load(d.years[0])
      return
    }
    router.replace({ query: { ...route.query, year: String(year.value) } })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '回顾数据加载失败'
  } finally {
    loading.value = false
  }
}

function step(delta: number) {
  switchYear(year.value + delta)
}
function switchYear(y: number) {
  if (y === year.value || loading.value) return
  year.value = y
  load(y)
}

watch(
  () => route.query.year,
  (v) => {
    const y = /^\d{4}$/.test(String(v ?? '')) ? Number(v) : null
    if (y !== null && y !== year.value && !loading.value) {
      year.value = y
      load(y)
    }
  }
)

onMounted(() => load())
</script>

<style scoped>

.hd-page { max-width: 900px }
.hd-sec { margin-top: 30px }

/* 报头：年份与说明句共用一条基线（说明句是年份的题注） */
.hd-hero { padding: 8px 4px 0 }
.hd-eyebrow { font-size: 12px; letter-spacing: 5px; color: var(--hd-ink-faint) }
.hd-hero-main { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; margin-top: 2px }
.hd-year { font-size: 76px; line-height: 1.05; letter-spacing: 1px }
.hd-round { width: 40px; height: 40px; flex: none; align-self: center; border: 2px solid var(--hd-line);
  background: var(--hd-paper); cursor: pointer; border-radius: 50% 46% 52% 48%/48% 52% 46% 54%;
  font-size: 19px; line-height: 1; display: inline-flex; align-items: center; justify-content: center; filter: url(#rough) }
.hd-round[disabled] { opacity: .4; cursor: not-allowed }
.hd-lead { font-size: 21px; margin: 0 0 0 16px }
.hd-lead b { color: var(--hd-red); font-size: 25px }
.hd-caliber { font-size: 13.5px; color: var(--hd-ink-soft); margin: 10px 0 0 }
.hd-chips { display: flex; gap: 9px; margin-top: 16px; flex-wrap: wrap; align-items: center }
.hd-chips .cap { font-size: 13px; color: var(--hd-ink-faint) }
.hd-chip { border: 0; background: none; cursor: pointer; font: inherit; color: inherit }

/* 统计卡（类型色 hdk- 前缀，避免与正文注记 .hd-note 撞名） */
.hd-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-top: 18px }
.hd-stat { background: var(--hd-paper); border-radius: 12px 4px 13px 5px/5px 13px 4px 12px;
  padding: 12px 14px 14px; box-shadow: 2px 3px 0 var(--hd-line-soft) }
.hdg { position: relative; width: 34px; height: 34px; display: flex; align-items: center; justify-content: center;
  font-size: 17px; flex: none }
.hdg svg { position: absolute; inset: 0; filter: url(#rough) }
.hdg svg ellipse { fill: none; stroke: currentColor; stroke-width: 1.9 }
.hd-stat .num { display: block; font-size: 34px; margin-top: 6px; line-height: 1.15 }
.hd-stat .lbl { display: block; font-size: 13.5px; color: var(--hd-ink-soft) }
.hdk-movie { color: var(--hd-red) } .hdk-book { color: var(--hd-moss) }
.hdk-place { color: var(--hd-amber) } .hdk-note { color: var(--hd-ink-soft) }

.hd-note { font-size: 13.5px; color: var(--hd-ink-soft); margin: 16px 0 0 }

/* 评分榜 / 标签 */
.hd-cols { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px }
.hd-cols .sheet { min-width: 0 }
.hd-cols .row .main { min-width: 0 }
.hd-cols .row .t { white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.hd-cols .row.clickable { cursor: pointer; border-radius: 8px }
.hd-cols .row.clickable:hover { background: var(--hd-amber-soft) }
.hd-cols .row.clickable:focus-visible { outline: 2px solid var(--hd-red); outline-offset: 2px }
.hd-cols .sheet { margin-top: 0 }
.hd-cols .paper { padding: 6px 16px 10px }
.hd-score { margin-left: auto; font-size: 20px; color: var(--hd-amber); flex: none }
/* 共享皮肤缺口：handdrawn.css 只给影视的 glyph 圈设了 stroke，书籍/地点的圈因此不可见；
   这里统一成类型色，0.0.7 应收敛回 handdrawn.css */
.hd-page .row .glyph .ring ellipse { stroke: currentColor }
.hd-tags { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 26px; position: relative; z-index: 1; padding: 4px 0 8px }
.hd-tag { display: grid; grid-template-columns: 68px 1fr 26px; align-items: center; gap: 10px }
.hd-tag .tn { font-size: 15px; text-align: right; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.hd-tag .tb { height: 12px; background: var(--hd-red-soft); border: 1.5px solid var(--hd-line-soft);
  border-radius: 8px 3px 9px 4px/4px 9px 3px 8px; filter: url(#rough) }
.hd-tag .tc { font-size: 14px; color: var(--hd-ink-soft); text-align: right }

/* 空态 */
.hd-empty { text-align: center; padding: 40px 10px 30px }
.hd-empty svg { width: 150px; height: 96px; margin-bottom: 4px }
.hd-empty h3 { margin: 0 0 10px; font-size: 22px; font-weight: 400 }
.hd-empty p { margin: 0 auto; font-size: 15px; color: var(--hd-ink-soft); max-width: 420px }
.hd-empty .acts { display: flex; gap: 14px; justify-content: center; margin-top: 22px; flex-wrap: wrap }

/* 手绘骨架 */
.hdsk { background: var(--hd-paper-2); border: 1.6px solid var(--hd-line-soft);
  border-radius: 10px 4px 12px 5px/5px 12px 4px 10px; filter: url(#rough); animation: hdsk 1.3s ease-in-out infinite; display: block }
@keyframes hdsk { 0%, 100% { opacity: .5 } 50% { opacity: 1 } }
.hdsk-line { height: 14px }
.hdsk-hero { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 8px 30px; align-items: center; margin-top: 10px }
.hdsk-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-top: 18px }
.hdsk-stat { padding: 12px 14px 14px }
.hdsk-sheet { margin-top: 30px }
.hdsk-row { display: flex; align-items: center; gap: 12px; padding: 9px 0 }

@media (max-width: 768px) {
  .hd-page { padding: 2px 12px 24px }
  .hd-year { font-size: 54px }
  .hd-lead { font-size: 18px; margin-left: 0; flex-basis: 100% }
  .hd-lead b { font-size: 21px }
  .hdsk-hero { grid-template-columns: 1fr; gap: 12px }
  .hd-stats, .hdsk-stats { grid-template-columns: repeat(2, 1fr); gap: 10px }
  .hd-cols { grid-template-columns: 1fr; gap: 14px }
  .hd-tags { grid-template-columns: 1fr }
}
</style>
