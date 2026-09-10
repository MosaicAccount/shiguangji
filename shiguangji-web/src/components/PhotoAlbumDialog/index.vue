<template>
  <el-dialog
    v-model="visible"
    :title="`${title} · 相册`"
    width="860px"
    append-to-body
    class="photo-album-dialog"
  >
    <div class="album-scroll">
      <div class="album-grid">
        <div v-for="(url, i) in visiblePhotos" :key="url + i" class="polaroid">
          <el-image
            :src="photoUrl(url)"
            :preview-src-list="previewList"
            :initial-index="i"
            fit="cover"
            lazy
            preview-teleported
            :hide-on-click-modal="false"
          />
          <span class="cap"><span class="no">{{ String(i + 1).padStart(2, '0') }}</span>{{ fileNameOf(url) }}</span>
          <button
            v-if="canManage"
            class="polaroid-del"
            type="button"
            :aria-label="`删除第${i + 1}张照片`"
            @click="removePhoto(i)"
          >✕</button>
        </div>
        <!-- 上传入口（仅登录用户）：走通用上传接口，成功后追加到相册末尾 -->
        <el-upload
          v-if="canManage"
          class="upload-wrap"
          :action="uploadUrl"
          :headers="uploadHeaders"
          :show-file-list="false"
          multiple
          accept="image/png,image/jpeg,image/webp,image/gif"
          :before-upload="beforeUpload"
          :on-success="onUploadSuccess"
          :on-error="onUploadError"
        >
          <div class="upload-tile" :class="{ busy: uploading > 0 }">
            <span class="plus" aria-hidden="true">＋</span>
            {{ uploading > 0 ? `正在上传 ${uploading} 张…` : '添加照片' }}
          </div>
        </el-upload>
      </div>
      <div v-if="!photos.length && !canManage" class="album-empty">
        <div class="big">🖼</div>
        还没有照片
      </div>
    </div>
    <template #footer>
      <div class="album-foot">
        <el-button v-if="hasMore" round type="primary" @click="shown += PAGE">加载更多照片</el-button>
        <div v-else-if="photos.length" class="end-note">— 已经到底啦 · 共 {{ photos.length }} 张 —</div>
        <div class="status-line" role="status">已显示 {{ visiblePhotos.length }} / {{ photos.length }} 张</div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import { photoUrl } from '@/utils/sgj'

/**
 * 照片相册弹窗：拍立得网格 + 客户端分页 + el-image 全屏预览。
 * 登录用户可在末尾虚线牌上传（/common/upload）或单张删除；
 * 排序沿用后台管理的拖拽能力，前台不重复实现。
 */
const props = defineProps<{
  /** 双向绑定弹窗可见性 */
  modelValue: boolean
  /** 照片地址（相对 /profile 路径或外链），逗号顺序即展示顺序 */
  photos: string[]
  /** 相册归属地点名（标题展示用） */
  title: string
  /** 是否可管理（登录用户） */
  canManage?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'update:photos', value: string[]): void
}>()

const PAGE = 8
/** 单页展示张数 */
const shown = ref(PAGE)
const uploading = ref(0)

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const visiblePhotos = computed(() => props.photos.slice(0, shown.value))
const hasMore = computed(() => shown.value < props.photos.length)
const previewList = computed(() => props.photos.map(url => photoUrl(url)))

const uploadUrl = import.meta.env.VITE_APP_BASE_API + '/common/upload'
const uploadHeaders = { Authorization: 'Bearer ' + getToken() }

watch(
  () => props.modelValue,
  value => {
    if (value) shown.value = Math.min(PAGE, Math.max(props.photos.length, 1))
  }
)

// 外部（如上传失败回读）更新照片后，收拢分页位置避免越界
watch(
  () => props.photos.length,
  len => {
    shown.value = Math.min(Math.max(shown.value, Math.min(PAGE, Math.max(len, 1))), Math.max(len, 1))
  }
)

function fileNameOf(url: string): string {
  const tail = url.split('/').pop() || url
  return tail.includes('.') ? tail.replace(/\.[a-z0-9]+$/i, '') : tail
}

function removePhoto(index: number): void {
  const next = props.photos.filter((_, i) => i !== index)
  emit('update:photos', next)
}

function beforeUpload(file: File): boolean {
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请上传图片文件')
    return false
  }
  if (file.name.includes(',')) {
    ElMessage.error('文件名不能包含英文逗号')
    return false
  }
  if (file.size / 1024 / 1024 >= 10) {
    ElMessage.error('图片大小不能超过 10MB')
    return false
  }
  uploading.value++
  return true
}

// /common/upload 返回 { code, fileName }，fileName 为 /profile 相对路径
function onUploadSuccess(res: { code?: number; fileName?: string; msg?: string }): void {
  uploading.value = Math.max(0, uploading.value - 1)
  if (res.code !== 200 || !res.fileName) {
    ElMessage.error(res.msg || '上传失败')
    return
  }
  emit('update:photos', [...props.photos, res.fileName!])
  shown.value += 1
}

function onUploadError(): void {
  uploading.value = Math.max(0, uploading.value - 1)
  ElMessage.error('上传失败，请稍后重试')
}
</script>

<style scoped lang="scss">
.album-scroll {
  max-height: 60vh;
  overflow-y: auto;
  padding: 4px 4px 8px;
}

.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(148px, 1fr));
  gap: 16px 14px;
}

.polaroid {
  position: relative;
  background: #fff;
  border-radius: 6px;
  padding: 7px 7px 26px;
  box-shadow: 0 2px 8px rgba(23, 27, 26, 0.14);
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:nth-child(odd) {
    transform: rotate(-1.1deg);
  }

  &:nth-child(even) {
    transform: rotate(1deg);
  }

  &:hover {
    transform: rotate(0) translateY(-4px);
    box-shadow: 0 10px 28px rgba(23, 27, 26, 0.16);
    z-index: 2;

    .polaroid-del {
      opacity: 1;
    }
  }

  :deep(.el-image) {
    display: block;
    width: 100%;
    aspect-ratio: 4 / 3.4;
    border-radius: 3px;
    background: var(--sgj-bg-card, #f0ece2);

    .el-image__inner {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  .cap {
    position: absolute;
    left: 9px;
    bottom: 6px;
    right: 26px;
    font: 600 11px/1.3 var(--sgj-font-serif);
    /* 拍立得白底固定墨色（不随主题翻转） */
    color: #5c6360;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;

    .no {
      color: #97a09b;
      font-weight: 400;
      margin-right: 5px;
    }
  }

  .polaroid-del {
    position: absolute;
    top: 4px;
    right: 4px;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    border: 0;
    background: rgba(40, 46, 44, 0.72);
    color: #fff;
    font-size: 12px;
    line-height: 1;
    cursor: pointer;
    opacity: 0;
    transition: opacity 0.16s ease;

    &:hover {
      background: var(--sgj-primary);
    }
  }
}

.upload-wrap {
  :deep(.el-upload) {
    width: 100%;
  }
}

.upload-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  aspect-ratio: 4 / 4.36;
  min-height: 140px;
  border: 2px dashed var(--sgj-border-card);
  border-radius: 6px;
  background: var(--sgj-bg-card);
  color: var(--sgj-text-3);
  font-size: 12px;
  transition: border-color 0.16s ease, color 0.16s ease, background 0.16s ease;

  .plus {
    font-size: 26px;
    line-height: 1;
    color: var(--sgj-primary);
  }

  &:hover {
    border-color: var(--sgj-primary);
    color: var(--sgj-primary);
    background: var(--sgj-primary-soft);
  }

  &.busy {
    pointer-events: none;
    opacity: 0.7;
  }
}

.album-empty {
  text-align: center;
  color: var(--sgj-text-4);
  padding: 30px 0;
  font-size: 13px;

  .big {
    font-size: 26px;
    margin-bottom: 6px;
  }
}

.album-foot {
  text-align: center;
}

.end-note,
.status-line {
  font-size: 12px;
  color: var(--sgj-text-4);
}

.status-line {
  margin-top: 7px;
}
</style>
