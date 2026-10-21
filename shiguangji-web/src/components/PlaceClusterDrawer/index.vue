<template>
  <el-drawer v-model="visible" size="440px" :with-header="false" append-to-body class="hd-app hd-drawer-wrap">
    <article v-if="places.length" class="cluster-sheet hd-paper hd-grain">
      <span class="hd-tape" aria-hidden="true"></span>
      <button class="sheet-close" type="button" aria-label="关闭" @click="visible = false">✕</button>

      <div class="head-row">
        <span class="hd-st done">去过</span>
        <span class="kind">同一片</span>
      </div>
      <h3>{{ city }} · {{ places.length }} 处足迹</h3>
      <p class="sub">跨度约 {{ spanKm }} km · 按到访先后排列</p>

      <!-- 小路线示意：编号针 + 虚线行程 -->
      <svg class="strip" viewBox="0 0 336 78" role="img" aria-label="这一片的小路线示意">
        <path v-if="places.length > 1" class="strip-route" :d="stripD" />
        <g v-for="(p, i) in places" :key="'s' + i" :transform="`translate(${stripX(i)},${stripY(i)})`">
          <circle r="12" />
          <text y="4.5" text-anchor="middle">{{ noOf(p, i) }}</text>
          <text class="lb" :y="30" text-anchor="middle">{{ p.title }}</text>
        </g>
      </svg>

      <!-- 地点列表：无照片显示手绘默认封面 -->
      <div class="rows">
        <button v-for="(p, i) in places" :key="'r' + i" type="button" class="row" @click="emit('select', p)">
          <img v-if="p.cover" :src="photoUrl(p.cover)" :alt="p.title" loading="lazy" />
          <span v-else class="hd-fallback place sm-naked">
            <span class="fc-band">地点</span>
            <span class="fc-body"><span class="fc-title">{{ p.title }}</span></span>
            <span class="fc-motif"></span>
          </span>
          <span class="txt">
            <span class="t">{{ p.title }}</span>
            <span class="m">{{ [p.city, p.country].filter(Boolean).join(' · ') || '—' }} · {{ dateText(p) }} · {{ p.photoCount || 0 }} 张照片</span>
          </span>
          <span class="arr" aria-hidden="true">→</span>
        </button>
      </div>
    </article>
  </el-drawer>
</template>

<script setup lang="ts" name="PlaceClusterDrawer">
import { photoUrl } from '@/utils/sgj'
import type { TravelPoint } from '@/types/api/front/travel'

/**
 * 「这一片」纸卡（0.0.6 终版）：手绘足迹图上点城市叠针后弹出——
 * 簇内地点按到访先后排列（照片 + 日期 + 张数，无照片走手绘默认封面），
 * 顶部一条编号小路线示意与跨度 km；点条目交给上层开详情抽屉。
 */
const props = defineProps<{
  modelValue: boolean
  /** 簇内地点（调用方排好序或组件内按日期排） */
  places: TravelPoint[]
  /** 簇所在城市（标题用） */
  city?: string
  /** 全年到访序号（与地图编号一致；缺省用簇内序） */
  orderMap?: Record<number, number>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'select', point: TravelPoint): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

const ordered = computed(() => props.places.slice().sort((a, b) =>
  String(a.finishDate || '').localeCompare(String(b.finishDate || ''))
))

const city = computed(() => props.city || ordered.value[0]?.city || '这一片')

/** 跨度 km：经度按纬度余弦折算 */
const spanKm = computed(() => {
  const pts = ordered.value.filter(p => p.latitude != null && p.longitude != null)
  if (pts.length < 2) return 1
  const lngs = pts.map(p => p.longitude as number)
  const lats = pts.map(p => p.latitude as number)
  const mid = lats.reduce((s, v) => s + v, 0) / lats.length
  const kmW = (Math.max(...lngs) - Math.min(...lngs)) * 111 * Math.cos((mid * Math.PI) / 180)
  const kmH = (Math.max(...lats) - Math.min(...lats)) * 111
  return Math.max(1, Math.round(Math.max(kmW, kmH)))
})

function noOf(p: TravelPoint, i: number): number {
  return (p.itemId != null && props.orderMap?.[p.itemId]) || i + 1
}

function dateText(p: TravelPoint): string {
  const d = String(p.finishDate || '')
  return d.length >= 10 ? `${Number(d.slice(5, 7))}/${Number(d.slice(8, 10))}` : '—'
}

const stripX = (i: number) => 336 * ((i + 1) / (ordered.value.length + 1))
const stripY = (i: number) => 40 + (i % 2 ? -6 : 6)

const stripD = computed(() =>
  ordered.value.length > 1
    ? 'M' + ordered.value.map((_, i) => `${stripX(i)} ${stripY(i)}`).join(' L')
    : ''
)
</script>

<style scoped>
/* 「这一片」纸卡：抽屉本体透明，内容是一张贴在右侧的纸（与详情抽屉同一形制） */
.cluster-sheet {
  position: relative;
  margin: 14px 16px 16px;
  padding: 24px 24px 20px;
  border-radius: 6px 3px 7px 4px / 4px 7px 3px 6px;
  box-shadow: var(--hd-shadow-lift);
  max-height: calc(100vh - 30px);
  overflow-y: auto;
}

.cluster-sheet > .hd-tape {
  top: -10px;
  left: 50%;
  transform: translateX(-50%) rotate(-2deg);
  width: 92px;
}

.sheet-close {
  position: absolute;
  top: 10px;
  right: 12px;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--hd-ink-soft);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  border-radius: 50%;
}

.sheet-close:hover { color: var(--hd-red) }

.head-row { display: flex; align-items: center; gap: 10px; padding-right: 34px }
.kind { font-size: 13px; letter-spacing: 2px; color: var(--hd-ink-faint) }

.cluster-sheet h3 {
  margin-top: 8px;
  font-size: 26px;
  letter-spacing: 1px;
  color: var(--hd-ink);
}

.sub { margin: 6px 0 0; font-size: 13px; color: var(--hd-ink-faint) }

.strip {
  display: block;
  width: 100%;
  margin-top: 14px;
  border: 1.6px dashed var(--hd-line-soft);
  border-radius: 4px 2px 5px 3px / 3px 5px 2px 4px;
  background: var(--hd-paper-2);
}

.strip-route { fill: none; stroke: var(--hd-ochre); stroke-width: 2.4; stroke-dasharray: 8 7; stroke-linecap: round; opacity: .8 }

.strip circle { fill: var(--hd-paper); stroke: var(--hd-red); stroke-width: 2 }
.strip text { fill: var(--hd-red); font-size: 12.5px; font-family: var(--hd-hand) }
.strip .lb { fill: var(--hd-ink-soft); font-size: 12px }

.rows { margin-top: 10px }

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 2px;
  text-align: left;
  background: none;
  border: 0;
  border-bottom: 1.5px dashed var(--hd-line-soft);
  cursor: pointer;
  font-family: var(--hd-hand);
}

.row:last-child { border-bottom: 0 }
.row:hover .t { color: var(--hd-red) }

.row img {
  flex: none;
  width: 44px;
  height: 56px;
  object-fit: cover;
  border: 1.6px solid var(--hd-line);
  border-radius: 3px 1px 4px 2px;
}

/* 无照片默认封面：hd-fallback 的裸尺寸形态（不铺满父级） */
.row .sm-naked { position: relative; flex: none; width: 44px; height: 56px; padding: 0; border-radius: 3px 1px 4px 2px }
.row .sm-naked .fc-band { font-size: 11px; padding: 1px 7px; border-radius: 2px 0 3px 0 }
.row .sm-naked .fc-title { font-size: 12.5px; padding: 0 5px 4px; -webkit-line-clamp: 2; display: -webkit-box; -webkit-box-orient: vertical; overflow: hidden }

.txt { min-width: 0; display: flex; flex-direction: column; gap: 2px }
.t { font-size: 16px; color: var(--hd-ink) }
.m { font-size: 13px; color: var(--hd-ink-soft) }
.arr { margin-left: auto; color: var(--hd-ink-faint); font-size: 16px }
.row:hover .arr { color: var(--hd-red) }
</style>
