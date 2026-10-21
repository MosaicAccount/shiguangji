<template>
  <el-drawer v-model="visible" size="480px" :with-header="false" append-to-body class="hd-app hd-drawer-wrap">
    <article v-if="item" class="detail-sheet hd-paper hd-grain">
      <span class="hd-tape" aria-hidden="true"></span>

      <!-- 封面区：真实封面压暗角叠标题；无图走 A 形制默认封面 -->
      <div class="sheet-hero">
        <template v-if="heroImage">
          <img class="hero-img" :src="heroImage" :alt="item.title" @error="onHeroError" />
          <span class="hero-mask" aria-hidden="true"></span>
          <span class="hd-st hero-st" :class="{ done: item.status === 'DONE', doing: item.status === 'DOING' }">{{ statusText }}</span>
          <div class="hero-bottom">
            <h2>{{ item.title }}</h2>
            <div class="hero-meta">
              <span v-if="item.rating" class="hero-stars" :aria-label="`评分 ${item.rating} 分，满分 10 分`">{{ '★'.repeat(Math.round(item.rating / 2)) }}</span>
              <span v-for="meta in heroMeta" :key="meta">{{ meta }}</span>
            </div>
          </div>
        </template>
        <template v-else>
          <span class="hd-fallback" :class="fallbackType">
            <span class="fc-band">{{ fallbackBand }}</span>
            <span class="fc-body">
              <span class="fc-title">{{ item.title }}</span>
              <span class="fc-meta">{{ (heroMeta || []).join(' · ') }}</span>
            </span>
            <span class="fc-motif"></span>
          </span>
        </template>
        <button class="sheet-close" type="button" aria-label="关闭详情" @click="visible = false">✕</button>
      </div>

      <div class="sheet-body">
        <div class="sheet-status">
          <span class="hd-st" :class="{ done: item.status === 'DONE', doing: item.status === 'DOING' }">{{ statusText }}</span>
          <span class="kind">{{ glyph }}</span>
        </div>
        <!-- 页面差异内容：chips / 信息行 / 短评等（使用本组件提供的 md-* 类名） -->
        <slot />

        <div v-if="memoryLabel && item.comment" class="sheet-memory">
          <div class="memory-label">{{ memoryLabel }}</div>
          <p>{{ item.comment }}</p>
        </div>

        <item-notes :item-id="item.itemId" />

        <div v-if="canOperate" class="sheet-actions">
          <button class="hd-btn go" type="button" @click="emit('edit')">编辑</button>
          <button class="hd-btn sub" type="button" @click="emit('delete')">删除</button>
        </div>
      </div>
    </article>
  </el-drawer>
</template>

<script setup lang="ts" name="MediaDetailDrawer">
import { photoUrl } from '@/utils/sgj'
import ItemNotes from '@/components/ItemNotes/index.vue'
import type { SgjItem } from '@/types/api/business/item'

/**
 * 影视/书籍前台详情抽屉（手账风，与足迹页详情抽屉同语言）：
 * 封面铺底 hero + 状态胶囊 + 左对齐信息卡；差异字段由页面经默认插槽与 props 传入
 */
const props = defineProps<{
  modelValue: boolean
  item: SgjItem | null
  /** 无封面时的衬线占位字（影/剧/书） */
  glyph: string
  /** 状态文案（DONE/WANT/DOING），如 看过/想看/在看、已读/想读/在读 */
  doneLabel: string
  wantLabel: string
  /** 进行中态文案（0.0.3 三态）；地点等无中间态的页面不传，回退 wantLabel */
  doingLabel?: string
  /** 标题下 meta 行（评分之外），如「1994 年上映」 */
  heroMeta?: string[]
  /** 短评卡标签（如 MY THOUGHTS · 我的短评），不传则不渲染短评卡 */
  memoryLabel?: string
  /** 是否展示编辑/删除操作 */
  canOperate?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'edit'): void
  (e: 'delete'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

/** 状态徽标文案：显式三态，缺 doingLabel 的页面回退 wantLabel（地点无中间态） */
/** 无封面兜底：与卡片网格的 A 影像封面同一形制（电影/电视剧/书籍） */
const fallbackType = computed(() => {
  switch (props.item?.itemType) {
    case 'MOVIE': return 'movie'
    case 'TV': return 'tv'
    case 'BOOK': return 'book'
    default: return 'note'
  }
})

const fallbackBand = computed(() => {
  switch (props.item?.itemType) {
    case 'MOVIE': return '电影'
    case 'TV': return '电视剧'
    case 'BOOK': return '书籍'
    default: return '记录'
  }
})

const statusText = computed(() => {
  if (props.item?.status === 'DONE') return props.doneLabel
  if (props.item?.status === 'DOING') return props.doingLabel || props.wantLabel
  return props.wantLabel
})

const heroFailed = ref(false)
watch(() => props.item?.itemId, () => { heroFailed.value = false })

/** 封面铺底图：相对路径补 API 前缀，加载失败回退衬线占位 */
const heroImage = computed(() => {
  if (heroFailed.value || !props.item?.coverUrl) return ''
  return photoUrl(props.item.coverUrl)
})

function onHeroError(): void {
  heroFailed.value = true
}
</script>

<!-- 抽屉传送至 body 且默认插槽内容需复用样式，故用非 scoped 并以 .media-drawer 作命名空间 -->
<style lang="scss">
/* 0.0.6 详情抽屉（影视/书籍）：与足迹地点抽屉同一张纸——纸底 + 胶带 + 抖线框 + 影；
   封面区 200px，有图压暗角叠标题，无图走 A 形制默认封面；滚动条隐藏。 */
.detail-sheet {
  position: relative;
  margin: 14px 16px 16px;
  max-height: calc(100vh - 30px);
  overflow-y: auto;
  overflow-x: hidden;
  background-color: var(--hd-paper);
  background-image: var(--hd-grain);
  border: 2.2px solid var(--hd-line);
  border-radius: 6px 3px 7px 4px / 4px 7px 3px 6px;
  box-shadow: var(--hd-shadow-lift);
  scrollbar-width: none;
}
.detail-sheet::-webkit-scrollbar { width: 0; height: 0; display: none }

.detail-sheet > .hd-tape {
  top: 6px;
  left: 50%;
  transform: translateX(-50%) rotate(-2deg);
  width: 92px;
  z-index: 4;
}

.sheet-hero {
  position: relative;
  height: 210px;
  overflow: hidden;
  background: linear-gradient(160deg, var(--hd-amber-soft), var(--hd-paper-3));
}
.hero-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.hero-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(30, 24, 18, .06) 0%, rgba(30, 24, 18, 0) 35%, rgba(30, 24, 18, .68) 100%);
}
.hero-st { position: absolute; top: 12px; left: 12px; z-index: 2 }
.hero-bottom {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2;
  padding: 0 18px 14px;
}
.hero-bottom h2 {
  margin: 0;
  font-size: 26px;
  line-height: 1.3;
  color: #FBF6EC;
  text-shadow: 0 2px 8px rgba(0, 0, 0, .35);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.hero-meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 13px;
  color: rgba(251, 246, 236, .94);
}
.hero-stars { color: #F2D9A8; letter-spacing: 1px }

.sheet-close {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 5;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1.6px solid var(--hd-line-soft);
  background: var(--hd-paper);
  color: var(--hd-ink-soft);
  font-size: 14px;
  cursor: pointer;
}
.sheet-close:hover { color: var(--hd-red); border-color: var(--hd-red) }

.sheet-body { padding: 14px 20px 20px }
.sheet-status { display: flex; align-items: center; gap: 10px; margin-bottom: 10px }
.sheet-status .kind { font-size: 13px; letter-spacing: 2px; color: var(--hd-ink-faint) }

.sheet-memory {
  margin-top: 16px;
  padding: 12px 16px 15px;
  background: var(--hd-paper-2);
  border-left: 3px solid var(--hd-red);
  border-radius: 0 3px 3px 0;
}
.memory-label { font-size: 12.5px; letter-spacing: 2px; color: var(--hd-ink-faint); margin-bottom: 6px }
.sheet-memory p { margin: 0; font-size: 15px; line-height: 1.9; color: var(--hd-ink-soft) }

.sheet-actions {
  margin-top: 20px;
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

/* 页面插槽内容（父级作用域，需非 scoped 才能命中） */
.md-chips { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px }
.md-chip {
  font-size: 13px;
  padding: 2px 11px;
  color: var(--hd-ink-soft);
  border: 1.5px solid var(--hd-line-soft);
  border-radius: 9px 3px 8px 3px / 3px 8px 3px 9px;
  font-family: var(--hd-hand);
}
.md-rows { margin-top: 6px; border-top: 1.5px dashed var(--hd-line-soft) }
.md-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 9px 0;
  font-size: 14px;
  color: var(--hd-ink);
  border-bottom: 1.5px dashed var(--hd-line-soft);
}
.md-ico { flex: none; width: 18px; text-align: center; color: var(--hd-ink-faint) }
.md-label { flex: none; width: 64px; color: var(--hd-ink-soft) }
</style>
