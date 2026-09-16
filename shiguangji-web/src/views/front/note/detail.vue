<template>
  <div class="note-detail-page">
    <div class="detail-card">
      <!-- 顶栏：返回 + 登录态操作 -->
      <div class="detail-header">
        <el-button class="back-btn" circle :icon="ArrowLeft" @click="goBack" />
        <div v-if="note && isLogin" class="header-actions">
          <el-button type="primary" round size="small" @click="openEdit">编辑</el-button>
          <el-button type="danger" round size="small" @click="handleDelete">删除</el-button>
        </div>
      </div>

      <div v-loading="loading" class="detail-main">
        <template v-if="note">
          <h1 class="detail-title">{{ note.title }}</h1>
          <div class="detail-meta">
            <!--  图标区分：🔗 关联笔记 / 📝 独立笔记 -->
            <span v-if="note.itemId" class="detail-link">🔗 {{ note.itemName || '#' + note.itemId }}</span>
            <span v-else class="detail-link">📝 独立笔记</span>
            <!-- 公开状态仅登录态展示 -->
            <el-tag v-if="isLogin" :type="note.isPublic === '1' ? 'success' : 'info'" size="small">
              {{ note.isPublic === '1' ? '公开' : '私密' }}
            </el-tag>
            <span class="detail-time">{{ formatTime(note.updateTime || note.createTime) }}</span>
          </div>
          <div v-if="tagList.length" class="detail-tags">
            <span v-for="tag in tagList" :key="tag" class="tag-pill">{{ tag }}</span>
          </div>
          <div class="detail-body">
            <markdown-viewer :content="note.content" />
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts" name="FrontNoteDetail">
import { ArrowLeft } from '@element-plus/icons-vue'
import MarkdownViewer from '@/components/MarkdownViewer/index.vue'
import { getFrontNote, delFrontNote } from '@/api/front/note'
import type { SgjNote } from '@/types/api/business/note'
import { getToken } from '@/utils/auth'

const { proxy } = getCurrentInstance() as { proxy: any }
const route = useRoute()
const router = useRouter()

/** 是否登录（访客只读，登录后可管理） */
const isLogin = computed(() => !!getToken())

const loading = ref(false)
const note = ref<SgjNote | null>(null)

/** 逗号拼接标签拆成胶囊展示 */
const tagList = computed(() =>
  (note.value?.tags || '').split(',').map(tag => tag.trim()).filter(Boolean)
)

loadNote()

/** 按 ?noteId= 加载笔记，缺参/不存在/加载失败均回列表页 */
function loadNote(): void {
  const noteId = Number(route.query.noteId)
  if (!noteId) {
    router.replace('/note')
    return
  }
  loading.value = true
  getFrontNote(noteId).then(response => {
    if (!response.data) {
      proxy.$modal.msgError('笔记不存在或已删除')
      router.replace('/note')
      return
    }
    note.value = response.data
  }).catch(() => {
    router.replace('/note')
  }).finally(() => {
    loading.value = false
  })
}

/** 返回列表页 */
function goBack(): void {
  router.replace('/note')
}

/** 跳转独立编辑页，保存后由编辑页回列表 */
function openEdit(): void {
  if (!note.value?.noteId) return
  router.push({ path: '/note/edit', query: { noteId: String(note.value.noteId) } })
}

function handleDelete(): void {
  const noteId = note.value?.noteId
  if (!noteId) return
  proxy.$modal.confirm('是否确认删除该笔记？').then(() => {
    return delFrontNote(noteId)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    goBack()
  }).catch(() => {})
}

function formatTime(time?: string): string {
  if (!time) return ''
  return time.replace('T', ' ').slice(0, 16)
}
</script>

<style scoped lang="scss">
.note-detail-page {
  color: var(--sgj-text);
}

.detail-card {
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: 20px;
  padding: 24px 32px;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;

  .back-btn {
    border-color: var(--sgj-border-card);
    background: var(--sgj-bg);
    color: var(--sgj-text-2);
  }

  .header-actions {
    display: flex;
    gap: 8px;
  }
}

.detail-title {
  margin: 0;
  font-family: var(--sgj-font-serif);
  font-weight: 700;
  font-size: 28px;
  line-height: 1.4;
  color: var(--sgj-text);
  word-break: break-word;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  font-size: 13px;
  color: var(--sgj-text-4);

  .detail-link {
    color: var(--sgj-primary-light);
  }
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;

  .tag-pill {
    padding: 2px 12px;
    border-radius: 999px;
    font-size: 12px;
    color: var(--sgj-amber);
    background: var(--sgj-bg);
    border: 1px solid var(--sgj-border);
  }
}

.detail-body {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--sgj-border);
  font-size: var(--sgj-font-base);
  line-height: 1.7;
  color: var(--sgj-text);
}

/* 移动端最小适配：收窄内边距、标题降级 */
@media (max-width: 768px) {
  .detail-card {
    padding: 16px 14px;
    border-radius: 14px;
  }

  .detail-title {
    font-size: 22px;
  }
}
</style>
