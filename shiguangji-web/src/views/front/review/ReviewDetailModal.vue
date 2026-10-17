<template>
  <div v-if="open" class="hd-layer" @click.self="$emit('close')">
    <!-- 点详情：手绘卡片弹性展开（点「在地图上定位」后让位给地图视图） -->
    <div v-if="mode === 'place' && place && !locating" class="hd-card">
      <button class="x" type="button" aria-label="关闭" @click="$emit('close')">×</button>
      <div class="hd-photo">
        <span class="tf"></span>
        <img v-if="place.coverUrl" :src="place.coverUrl" :alt="place.title || ''" />
        <span v-else class="g">{{ (place.title || '').slice(0, 1) }}</span>
      </div>
      <h3>{{ place.title }}</h3>
      <p class="sub">
        {{ year }} 年 {{ monthOf(place) }}<template v-if="regionOf(place)"> · {{ regionOf(place) }}</template>
        <template v-if="place.rating != null"> · ★ {{ place.rating }}</template>
      </p>
      <p v-if="place.comment" class="quote">{{ place.comment }}</p>

      <!-- 照片：条目图集（点击新窗口看大图） -->
      <div v-if="photos.length" class="hd-shots">
        <span class="sh">
          <svg class="shicon" viewBox="0 0 20 20" aria-hidden="true">
            <path d="M3 6.5h3l1.4-2h5.2L14 6.5h3v9H3z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
            <circle cx="10" cy="11" r="2.6" fill="none" stroke="currentColor" stroke-width="1.6"/>
          </svg>
          照片 <b>{{ photos.length }}</b>
        </span>
        <a v-for="(u, i) in photos" :key="u + i" class="shot" :href="photoUrl(u)" target="_blank" rel="noopener">
          <img :src="photoUrl(u)" :alt="`${place.title} 照片 ${i + 1}`" loading="lazy" />
        </a>
      </div>

      <!-- 关联笔记：一行一条，左侧笔记图标 + 标题 + 日期 -->
      <div v-if="notes.length" class="hd-rnotes">
        <div class="sh">
          <svg class="shicon" viewBox="0 0 20 20" aria-hidden="true">
            <path d="M5 2.5h6l4 4v11H5z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
            <path d="M11 2.5v4h4" fill="none" stroke="currentColor" stroke-width="1.6"/>
            <path d="M7.6 11h4.8M7.6 14h3.2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          关联笔记 <b>{{ notes.length }}</b>
        </div>
        <button v-for="n in notes" :key="n.noteId" type="button" class="rn" @click="openNote(n)">
          <svg class="rnicon" viewBox="0 0 20 20" aria-hidden="true">
            <path d="M5 2.5h6l4 4v11H5z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
            <path d="M11 2.5v4h4" fill="none" stroke="currentColor" stroke-width="1.6"/>
          </svg>
          <span class="rt">{{ n.title }}</span>
          <span class="rd">{{ n.createTime }}</span>
          <span class="ra" aria-hidden="true">›</span>
        </button>
      </div>

      <div v-if="tags.length" class="rowm">
        <span v-for="t in tags" :key="t" class="hd-chip">{{ t }}</span>
      </div>
      <div class="acts">
        <button v-if="hasCoord(place)" class="hdbtn go" type="button" @click="locate(place)">在地图上定位 →</button>
        <button class="hdbtn" type="button" @click="viewRecord">看这条记录 →</button>
      </div>
    </div>

    <!-- 条目详情（影视 / 书籍）：与地点卡同一套手绘卡片，不用抽屉 -->
    <div v-else-if="mode === 'media' && media" class="hd-card">
      <button class="x" type="button" aria-label="关闭" @click="$emit('close')">×</button>
      <div class="hd-photo">
        <span class="tf"></span>
        <img v-if="media.coverUrl" :src="photoUrl(media.coverUrl)" :alt="media.title || ''" />
        <span v-else class="g">{{ mediaGlyph }}</span>
      </div>
      <h3>{{ media.title }}</h3>
      <p class="sub">{{ mediaMeta }}</p>
      <p v-if="media.comment" class="quote">{{ media.comment }}</p>
      <div v-if="mediaTags.length" class="rowm">
        <span v-for="t in mediaTags" :key="t" class="hd-chip">{{ t }}</span>
      </div>
      <div v-if="mediaNotes.length" class="hd-rnotes">
        <div class="sh">
          <svg class="shicon" viewBox="0 0 20 20" aria-hidden="true">
            <path d="M5 2.5h6l4 4v11H5z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
            <path d="M11 2.5v4h4" fill="none" stroke="currentColor" stroke-width="1.6"/>
            <path d="M7.6 11h4.8M7.6 14h3.2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          关联笔记 <b>{{ mediaNotes.length }}</b>
        </div>
        <button v-for="n in mediaNotes" :key="n.noteId" type="button" class="rn" @click="openNote(n)">
          <svg class="rnicon" viewBox="0 0 20 20" aria-hidden="true">
            <path d="M5 2.5h6l4 4v11H5z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
            <path d="M11 2.5v4h4" fill="none" stroke="currentColor" stroke-width="1.6"/>
          </svg>
          <span class="rt">{{ n.title }}</span>
          <span class="rd">{{ (n.createTime || '').slice(0, 10) }}</span>
          <span class="ra" aria-hidden="true">›</span>
        </button>
      </div>
      <div class="acts">
        <button class="hdbtn go" type="button" @click="openMediaList">去列表看全部 →</button>
      </div>
    </div>

    <!-- 在地图上定位：单独的地图视图，打开即聚焦该点；点地图上的点看详情 -->
    <div v-else-if="locating" class="hd-card">
      <button class="x" type="button" aria-label="关闭" @click="$emit('close')">×</button>
      <h3>{{ locating.title }} · 在地图上</h3>
      <p class="sub">{{ year }} 年 · {{ regionOf(locating) }} · 点图钉看这条记录</p>
      <div class="hd-provmap">
        <TravelMap :visited="travelPoints" :wish="[]" :height="260" @select="onMapSelect"
                   @ready="onMapReady" ref="travelRef" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { listFrontNote } from '@/api/front/note'
import { photoUrl } from '@/utils/sgj'
import TravelMap from '@/components/TravelMap/index.vue'
import type { SgjItem } from '@/types/api/business/item'
import type { ReviewNoteRef, ReviewPlace } from '@/types/api/front/review'

const props = defineProps<{
  mode: 'place' | 'media' | null
  place: ReviewPlace | null
  media: SgjItem | null
  year: number
}>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'openPlace', place: ReviewPlace): void }>()
const router = useRouter()

const open = computed(() => props.mode !== null)

/* ===== 条目详情（影视 / 书籍）：手绘卡片 ===== */
const MEDIA_GLYPH: Record<string, string> = { MOVIE: '影', TV: '剧', BOOK: '书', PLACE: '地' }
const mediaGlyph = computed(() => MEDIA_GLYPH[props.media?.itemType || ''] || '拾')
const mediaTags = computed(() =>
  (props.media?.tags || '').split(',').map((t) => t.trim()).filter(Boolean)
)
const mediaMeta = computed(() => {
  const m = props.media
  if (!m) return ''
  const month = m.finishDate ? `${Number(String(m.finishDate).slice(5, 7))} 月` : ''
  const type = m.itemType === 'BOOK' ? '书籍' : m.itemType === 'TV' ? '剧集' : '电影'
  const verb = m.itemType === 'BOOK' ? '读完' : m.itemType === 'TV' ? '看完' : '看过'
  const parts = [type, month ? `${month}${verb}` : verb]
  if (m.genre) parts.unshift(m.genre)
  const line = parts.join(' · ')
  return m.rating != null ? `${props.year} 年 · ${line} · ★ ${m.rating}` : `${props.year} 年 · ${line}`
})

const mediaNotes = ref<ReviewNoteRef[]>([])
function loadMediaNotes(itemId?: number) {
  mediaNotes.value = []
  if (!itemId) return
  listFrontNote({ itemId, pageNum: 1, pageSize: 50 })
    .then((res) => {
      mediaNotes.value = (res.data || []).map((n) => ({
        noteId: n.noteId,
        title: n.title,
        createTime: n.createTime
      }))
    })
    .catch(() => {})
}

function openMediaList() {
  const t = props.media?.itemType
  router.push(t === 'BOOK' ? '/book' : '/movie')
}

watch(
  () => props.media?.itemId,
  (id) => {
    if (props.mode === 'media') loadMediaNotes(id)
  },
  { immediate: true }
)

/* ===== 地点卡：照片 / 关联笔记 / 标签 / 时间与地区 ===== */
const tags = computed(() =>
  (props.place?.tags || '').split(',').map((t) => t.trim()).filter(Boolean)
)
const photos = computed(() => props.place?.photos ?? [])
const notes = computed<ReviewNoteRef[]>(() => props.place?.notes ?? [])

function openNote(n: ReviewNoteRef) {
  if (n.noteId) router.push({ path: '/note/detail', query: { noteId: String(n.noteId) } })
}
function hasCoord(p?: ReviewPlace | null): boolean {
  return !!p && p.latitude != null && p.longitude != null
}
function monthOf(p: ReviewPlace): string {
  const m = p.finishDate ? Number(p.finishDate.slice(5, 7)) : 0
  return m > 0 ? `${m} 月` : ''
}
function regionOf(p: ReviewPlace): string {
  return p.province || p.country || ''
}

/** 「在地图上定位」：切成地图视图并聚焦该点 */
const locating = ref<ReviewPlace | null>(null)
function locate(p: ReviewPlace) {
  locating.value = p
}
function viewRecord() {
  router.push('/travel')
}
/** 地图实例（定位视图）：就绪后 focus 到该点 */
const travelRef = ref<{ focus: (itemId?: number) => boolean } | null>(null)

/** 地图上的点：定位视图只有该地点 */
const mapPlaces = computed(() => (locating.value ? [locating.value] : []))

const travelPoints = computed(() => {
  const places = mapPlaces.value
  return places
    .filter((p) => p.latitude != null && p.longitude != null)
    .map((p) => ({
      itemId: p.itemId,
      title: p.title || '',
      province: p.province || undefined,
      country: p.country || undefined,
      latitude: p.latitude as number,
      longitude: p.longitude as number,
      finishDate: p.finishDate || undefined,
      // 回顾的地图只用来定位：不喂照片，免得先弹「照片聚合牌」多一步
      photoCount: 0
    }))
})

/** 地图上的点被点中 → 打开该地点详情（先退出「定位」态，否则地点卡会被定位视图挡住） */
function onMapSelect(itemId: number) {
  const place = mapPlaces.value.find((p) => p.itemId === itemId)
  if (!place) return
  locating.value = null
  emit('openPlace', place)
}

/** 地图就绪后再定位（实例未建好时 focus 无效） */
function onMapReady() {
  const p = locating.value
  if (p) travelRef.value?.focus(p.itemId)
}
</script>

<style scoped>

/* 弹层盖满视口；自带 plan-page 令牌作用域外的兜底不需要——组件渲染在 .plan-page 内 */
.hd-layer { position: fixed; inset: 0; z-index: 2000; display: flex; align-items: center; justify-content: center;
  padding: 26px; background: rgba(59, 52, 44, .3) }
.hd-card { position: relative; width: 100%; max-width: 520px; max-height: 92%; overflow: auto; background: var(--hd-paper);
  border-radius: 14px 5px 16px 6px/6px 16px 5px 14px; padding: 18px 20px 20px;
  box-shadow: 5px 7px 0 var(--hd-line-soft); animation: hdspring .44s cubic-bezier(.2, 1.45, .4, 1) both }
@keyframes hdspring { 0% { transform: translateY(24px) scale(.955); opacity: 0 } 62% { transform: translateY(-5px) scale(1.012) } 100% { transform: none; opacity: 1 } }
.hd-card::after { content: ''; position: absolute; inset: 0; border: 2.4px solid var(--hd-line); border-radius: inherit; filter: url(#rough); pointer-events: none }
.hd-card .x { position: absolute; right: 12px; top: 10px; width: 32px; height: 32px; border: 2px solid var(--hd-line);
  background: var(--hd-paper); border-radius: 50% 46% 52% 48%/48% 52% 46% 54%; cursor: pointer; font-size: 16px;
  line-height: 1; filter: url(#rough); z-index: 2 }

/* 照片：贴进手账（胶带 + 手绘裱框；无封面时衬线占位字） */
.hd-photo { position: relative; height: 168px; margin: 6px 0 14px; background: var(--hd-red-soft);
  border-radius: 8px 3px 9px 4px/4px 9px 3px 8px; display: flex; align-items: center; justify-content: center; overflow: hidden }
.hd-photo::after { content: ''; position: absolute; inset: 0; border: 2px solid var(--hd-line); border-radius: inherit; filter: url(#rough) }
.hd-photo img { width: 100%; height: 100%; object-fit: cover }
.hd-photo .g { font-size: 40px; color: var(--hd-red); opacity: .55 }
.hd-photo .tf { position: absolute; left: 50%; top: -11px; transform: translateX(-50%) rotate(-2deg); width: 86px; height: 22px;
  background: var(--hd-tape); border-radius: 2px; z-index: 1 }
.hd-card h3 { margin: 0 0 4px; font-size: 24px; font-weight: 400; letter-spacing: 1px }
.hd-card .sub { font-size: 14px; color: var(--hd-ink-soft); margin: 0 0 12px }
.hd-card .quote { margin: 0 0 14px; font-size: 16px; line-height: 1.7; background: var(--hd-amber-soft);
  border-radius: 10px 4px 11px 5px/5px 11px 4px 10px; padding: 10px 12px }
.hd-card .rowm { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 14px }
.hd-card .acts { display: flex; gap: 10px; flex-wrap: wrap }

/* 照片 / 关联笔记：同一种分组头（图标 + 计数），条目用卡片行 */
.hd-shots, .hd-rnotes { margin: 0 0 14px }
.hd-shots .sh, .hd-rnotes .sh { display: flex; align-items: center; gap: 6px; font-size: 13px;
  color: var(--hd-ink-faint); margin-bottom: 7px }
.hd-shots .sh b, .hd-rnotes .sh b { color: var(--hd-ink-soft); font-weight: 600 }
.shicon { width: 15px; height: 15px; flex: none }

.hd-shots { display: grid; grid-template-columns: repeat(auto-fill, 74px); gap: 8px }
.hd-shots .sh { grid-column: 1 / -1 }
.hd-shots .shot { display: block; width: 74px; height: 74px; border-radius: 6px 3px 7px 4px/4px 7px 3px 6px;
  overflow: hidden; border: 1.6px solid var(--hd-line-soft); background: var(--hd-paper-2);
  transition: transform .12s }
.hd-shots .shot:hover { transform: translateY(-2px) rotate(-.6deg) }
.hd-shots .shot img { width: 100%; height: 100%; object-fit: cover; display: block }

.hd-rnotes .rn { display: grid; grid-template-columns: 18px minmax(0, 1fr) auto 12px; align-items: center;
  gap: 9px; width: 100%; text-align: left; font: inherit; color: inherit; cursor: pointer;
  background: var(--hd-paper-2); border: 1.4px solid var(--hd-line-soft);
  border-radius: 9px 4px 10px 5px/5px 10px 4px 9px; padding: 8px 10px; margin-top: 6px;
  transition: transform .12s }
.hd-rnotes .rn:hover { transform: translateY(-1px); border-color: var(--hd-line) }
.rnicon { width: 18px; height: 18px; color: var(--hd-amber) }
.hd-rnotes .rt { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 15px }
.hd-rnotes .rn:hover .rt { text-decoration: underline }
.hd-rnotes .rd { flex: none; font-size: 12.5px; color: var(--hd-ink-faint); font-variant-numeric: tabular-nums }
.hd-rnotes .ra { color: var(--hd-ink-faint); font-size: 16px; line-height: 1 }

/* 组地图：高德瓦片区（按需加载），无坐标/失败时清单兜底 */
.hd-provmap { position: relative; margin: 8px 0 12px; border-radius: 10px; overflow: hidden }
.hd-provmap::after { content: ''; position: absolute; inset: 0; border: 2.4px solid var(--hd-line); border-radius: inherit; filter: url(#rough); pointer-events: none }

.hd-provlist .k { display: grid; grid-template-columns: 20px minmax(0, 1fr) 12px; gap: 8px; width: 100%;
  font: inherit; font-size: 14.5px; line-height: 1.85; text-align: left; color: inherit;
  background: none; border: 0; border-radius: 6px; padding: 2px 4px; cursor: pointer }
.hd-provlist .k:hover { background: var(--hd-amber-soft) }
.hd-provlist .k b { font-weight: 400; color: var(--hd-red) }
.hd-provlist .k i { font-style: normal; color: var(--hd-ink-faint) }

@media (max-width: 768px) {
  .hd-layer { align-items: flex-end; padding: 0 }
  .hd-card { max-width: none; max-height: 86vh; border-radius: 16px 16px 0 0/16px 16px 0 0 }
  .hd-photo { height: 140px }
}
</style>
