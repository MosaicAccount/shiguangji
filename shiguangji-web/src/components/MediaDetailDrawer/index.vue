<template>
  <el-drawer v-model="visible" size="480px" :with-header="false" append-to-body class="hd-app hd-drawer-wrap">
    <article v-if="item" class="detail-sheet hd-paper hd-grain">
      <span class="hd-tape" aria-hidden="true"></span>
      <button class="sheet-close" type="button" aria-label="关闭详情" @click="visible = false">✕</button>

      <div class="sheet-head">
        <span class="hd-st" :class="{ done: item.status === 'DONE', doing: item.status === 'DOING' }">{{ statusText }}</span>
        <span class="sheet-kind">{{ glyph }}</span>
      </div>

      <h2 class="sheet-title">{{ item.title }}</h2>

      <div class="sheet-meta">
        <span v-if="item.rating" class="sheet-stars" :aria-label="`评分 ${item.rating} 分，满分 10 分`">{{ '★'.repeat(Math.round(item.rating / 2)) }}</span>
        <span v-for="meta in heroMeta" :key="meta">{{ meta }}</span>
      </div>

      <figure v-if="heroImage" class="sheet-photo">
        <img :src="heroImage" :alt="item.title" @error="onHeroError" />
      </figure>

      <div class="sheet-body">
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
/* 0.0.6 详情抽屉：抽屉本体透明，内容是一张贴在右侧的纸（非 scoped：插槽内容来自页面） */
.detail-sheet {
  position: relative;
  margin: 14px 16px 16px;
  padding: 24px 24px 22px;
  border-radius: 6px 3px 7px 4px / 4px 7px 3px 6px;
  box-shadow: var(--hd-shadow-lift);
  max-height: calc(100vh - 30px);
  overflow-y: auto;
}

.detail-sheet > .hd-tape {
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

.sheet-close:hover { color: var(--hd-red); }

.sheet-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-right: 34px;
}

.sheet-kind {
  font-size: 13px;
  letter-spacing: 2px;
  color: var(--hd-ink-faint);
}

.sheet-title {
  margin-top: 8px;
  font-size: 28px;
  line-height: 1.35;
  letter-spacing: 1px;
  color: var(--hd-ink);
}

.sheet-meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
  font-size: 13.5px;
  color: var(--hd-ink-soft);
}

.sheet-stars {
  color: var(--hd-amber-ink);
  letter-spacing: 1px;
  font-size: 14px;
}

.sheet-photo {
  margin: 16px 0 4px;
  padding: 7px 7px 10px;
  background: var(--hd-paper);
  border: 1.6px solid var(--hd-line-soft);
  border-radius: 3px 2px 4px 2px / 2px 4px 2px 3px;
  box-shadow: var(--hd-shadow);
  transform: rotate(-.5deg);
}

.sheet-photo img {
  display: block;
  width: 100%;
  max-height: 300px;
  object-fit: cover;
  border-radius: 2px;
}

.sheet-body { margin-top: 16px; }

.sheet-memory {
  margin-top: 18px;
  padding: 12px 16px;
  background: var(--hd-paper-2);
  border-left: 3px solid var(--hd-red);
  border-radius: 0 3px 3px 0;
}

.memory-label {
  font-size: 13px;
  letter-spacing: 2px;
  color: var(--hd-ink-faint);
}

.sheet-memory p {
  margin-top: 6px;
  font-size: 15px;
  line-height: 1.9;
  color: var(--hd-ink-soft);
}

.sheet-actions {
  margin-top: 20px;
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

/* 页面插槽内容 */
.md-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.md-chip {
  font-size: 13px;
  padding: 2px 10px;
  color: var(--hd-ink-soft);
  border: 1.5px solid var(--hd-line-soft);
  border-radius: 9px 3px 8px 3px / 3px 8px 3px 9px;
}

.md-rows {
  margin-top: 12px;
  border-top: 1.5px dashed var(--hd-line-soft);
}

.md-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 9px 0;
  font-size: 14px;
  color: var(--hd-ink);
  border-bottom: 1.5px dashed var(--hd-line-soft);
}

.md-ico {
  flex: none;
  width: 18px;
  text-align: center;
  color: var(--hd-ink-faint);
}

.md-label {
  flex: none;
  width: 64px;
  color: var(--hd-ink-soft);
}
</style>
