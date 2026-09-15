<template>
  <el-drawer v-model="visible" size="440px" :with-header="false" class="media-drawer" append-to-body>
    <div v-if="item" class="drawer-inner">
      <!-- Hero：封面铺底，标题与 meta 叠底（与足迹页详情抽屉同风格） -->
      <div class="md-hero" :class="{ 'is-empty': !heroImage }">
        <img v-if="heroImage" class="md-hero-img" :src="heroImage" :alt="item.title" @error="onHeroError" />
        <span v-else class="md-hero-glyph" aria-hidden="true">{{ glyph }}</span>
        <div class="md-hero-mask" aria-hidden="true"></div>
        <span class="md-status" :class="item.status">{{ item.status === 'DONE' ? doneLabel : wantLabel }}</span>
        <button class="md-close" aria-label="关闭详情" @click="visible = false">✕</button>
        <div class="md-hero-bottom">
          <h2>{{ item.title }}</h2>
          <div class="md-meta-line">
            <span v-if="item.rating">⭐ {{ item.rating }}</span>
            <span v-for="meta in heroMeta" :key="meta">{{ meta }}</span>
          </div>
        </div>
      </div>

      <div class="md-body">
        <!-- 页面差异内容：chips / 信息行 / 短评等（使用本组件提供的 md-* 类名） -->
        <slot />

        <div v-if="memoryLabel && item.comment" class="md-memory">
          <div class="md-memory-label">{{ memoryLabel }}</div>
          <p>{{ item.comment }}</p>
        </div>

        <item-notes :item-id="item.itemId" />

        <div v-if="canOperate" class="md-actions">
          <el-button type="primary" round @click="emit('edit')">编辑</el-button>
          <el-button type="danger" plain round @click="emit('delete')">删除</el-button>
        </div>
      </div>
    </div>
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
  /** 状态文案（DONE/WANT），如 看过/想看、已读/想读 */
  doneLabel: string
  wantLabel: string
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
.media-drawer {
  .el-drawer__body {
    padding: 0;
  }
}
.drawer-inner {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  text-align: left;
}
.md-hero {
  position: relative;
  height: 216px;
  flex: none;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
  background: linear-gradient(160deg, var(--sgj-primary-soft), var(--sgj-cover));

  &.is-empty .md-hero-glyph {
    font-family: var(--sgj-font-serif);
    font-size: 96px;
    font-weight: 700;
    color: var(--sgj-primary-dark);
    opacity: 0.28;
  }
}
.md-hero-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.md-hero-glyph {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.md-hero-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(23, 27, 26, 0.08) 0%, rgba(23, 27, 26, 0) 35%, rgba(23, 27, 26, 0.66) 100%);
}
.md-status {
  position: absolute;
  top: 14px;
  left: 14px;
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  backdrop-filter: blur(4px);

  &.DONE {
    background: rgba(93, 111, 102, 0.92);
  }

  &.WANT {
    background: rgba(192, 138, 62, 0.92);
  }
}
.md-close {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 0;
  background: var(--sgj-bg-card);
  color: var(--sgj-text);
  font-size: 14px;
  cursor: pointer;

  &:hover {
    background: #fff;
  }
}
.md-hero-bottom {
  position: relative;
  z-index: 1;
  width: 100%;
  padding: 0 18px 14px;

  h2 {
    margin: 0;
    font: 700 24px/1.3 var(--sgj-font-serif);
    color: #fff;
    text-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .md-meta-line {
    margin-top: 6px;
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
    font-size: 12.5px;
    color: rgba(255, 255, 255, 0.94);
  }
}
.md-body {
  padding: 16px 18px 26px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
}
.md-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;

  .md-chip {
    padding: 3px 12px;
    border-radius: 999px;
    background: var(--sgj-primary-soft);
    color: var(--sgj-primary-dark);
    font-size: 12px;
  }
}
.md-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .md-row {
    display: flex;
    gap: 8px;
    font-size: 13px;
    color: var(--sgj-text-2);
    line-height: 1.7;

    .md-ico {
      flex: none;
    }

    .md-label {
      flex: none;
      color: var(--sgj-text);
      font-weight: 600;
    }
  }
}
.md-memory {
  padding: 13px 16px 15px;
  background: var(--sgj-bg-card);
  border-radius: 4px 16px 16px 16px;
  border-left: 3px solid var(--sgj-primary);

  .md-memory-label {
    font-size: 10.5px;
    letter-spacing: 2px;
    color: var(--sgj-text-4);
    margin-bottom: 6px;
  }

  p {
    margin: 0;
    font-family: var(--sgj-font-serif);
    font-size: 13.5px;
    line-height: 1.8;
    color: var(--sgj-text);
    white-space: pre-wrap;
  }
}
.md-actions {
  display: flex;
  gap: 10px;
  padding-top: 4px;
}
</style>
