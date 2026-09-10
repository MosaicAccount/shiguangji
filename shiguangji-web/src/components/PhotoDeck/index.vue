<template>
  <div class="deck-wrap">
    <button class="deck" :aria-label="`打开相册，共 ${photos.length} 张照片`" @click="emit('open')">
      <span
        v-for="(url, i) in previewPhotos"
        :key="url + i"
        class="card"
        :style="{ '--a': angles[i] + 'deg', zIndex: i }"
        aria-hidden="true"
      >
        <span class="ph" :style="{ backgroundImage: `url('${url}')` }"></span>
        <span class="cap">NO.{{ i + 1 }}</span>
      </span>
      <span class="deck-count" aria-hidden="true">{{ photos.length }}</span>
    </button>
    <p class="deck-hint">一叠旅途的牌<br /><b>点击展开相册</b>，逐页翻看</p>
  </div>
</template>

<script setup lang="ts">
/** 相册扇形入口：最多展示 4 张半展开的"扑克牌"，点击打开相册 */
const props = defineProps<{
  /** 照片 URL（已按展示顺序排列） */
  photos: string[]
}>()

const emit = defineEmits<{
  (e: 'open'): void
}>()

const angles = [-16, -6, 5, 15]
const previewPhotos = computed(() => props.photos.slice(0, 4))
</script>

<style scoped lang="scss">
.deck-wrap {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  padding: 18px 4px 8px;
}

.deck {
  position: relative;
  width: 158px;
  height: 128px;
  flex: none;
  padding: 0;
  background: none;
  border: 0;
  cursor: pointer;

  &:hover {
    filter: drop-shadow(0 6px 10px rgba(23, 27, 26, 0.14));
  }
}

.card {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 88px;
  height: 116px;
  background: #fff;
  border-radius: 10px;
  padding: 5px 5px 20px;
  box-shadow: 0 3px 10px rgba(23, 27, 26, 0.22);
  transform: translateX(-50%) rotate(var(--a));
  transform-origin: 50% 118%;
  transition: transform 0.22s ease;
  display: block;
}

.deck:hover .card,
.deck:focus-visible .card {
  transform: translateX(-50%) translateY(-7px) rotate(calc(var(--a) * 1.4));
}

.ph {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 6px;
  background-size: cover;
  background-position: center;
}

.cap {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 4px;
  font: 600 10px/1 var(--sgj-font-serif);
  /* 拍立得白底固定墨色（不随主题翻转） */
  color: #5c6360;
  text-align: center;
}

.deck-count {
  position: absolute;
  top: -2px;
  right: 2px;
  z-index: 9;
  min-width: 30px;
  height: 30px;
  padding: 0 8px;
  background: var(--sgj-primary);
  color: #fff;
  border: 2.5px solid #fff;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  line-height: 25px;
  text-align: center;
  box-shadow: 0 3px 8px rgba(140, 74, 63, 0.4);
}

.deck-hint {
  font-size: 12px;
  color: var(--sgj-text-4);
  line-height: 1.6;
  padding-bottom: 10px;
  margin: 0;

  b {
    color: var(--sgj-primary);
    font-weight: 600;
  }
}
</style>
