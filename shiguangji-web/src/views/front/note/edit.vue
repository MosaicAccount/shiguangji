<template>
  <div class="note-edit-page">
    <div class="edit-card">
      <!-- 顶栏：返回 + 标题 + 保存 -->
      <div class="edit-header">
        <el-button class="back-btn" circle :icon="ArrowLeft" @click="goBack" />
        <h1 class="edit-title">{{ form.noteId ? '编辑笔记' : '写笔记' }}</h1>
        <div class="header-actions">
          <el-button @click="goBack">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
        </div>
      </div>

      <el-form ref="editFormRef" :model="form" :rules="rules" label-position="top" class="edit-form">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入笔记标题" maxlength="200" class="title-input" />
        </el-form-item>
        <div class="meta-row">
          <el-form-item label="关联条目">
            <item-select v-model="form.itemId" :key="form.noteId || 'new'" />
          </el-form-item>
          <el-form-item label="标签">
            <!-- 标签来自后台标签管理（NOTE 模块），禁止自由输入 -->
            <tag-select v-model="form.tags" module="NOTE" placeholder="选择标签（可选）" />
          </el-form-item>
          <el-form-item label="公开状态">
            <!-- 公开/私密：默认私密，公开后访客可见 -->
            <el-radio-group v-model="form.isPublic">
              <el-radio-button value="0">私密</el-radio-button>
              <el-radio-button value="1">公开</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>
        <el-form-item label="内容" prop="content" class="content-item">
          <markdown-editor v-model="form.content" :height="editorHeight" placeholder="支持 Markdown 语法，如 **加粗**、# 标题、- 列表" />
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts" name="FrontNoteEdit">
import { ArrowLeft } from '@element-plus/icons-vue'
import MarkdownEditor from '@/components/MarkdownEditor/index.vue'
import ItemSelect from '@/components/front/ItemSelect.vue'
import TagSelect from '@/components/TagSelect/index.vue'
import { getFrontNote, addFrontNote, updateFrontNote } from '@/api/front/note'
import type { SgjNote } from '@/types/api/business/note'
import { getToken } from '@/utils/auth'

const { proxy } = getCurrentInstance() as { proxy: any }
const route = useRoute()
const router = useRouter()

const saving = ref(false)
const form = reactive<SgjNote>({
  noteId: undefined,
  itemId: undefined,
  title: undefined,
  content: undefined,
  tags: undefined,
  isPublic: '0'
})

const rules = {
  title: [{ required: true, message: '请输入笔记标题', trigger: 'blur' }]
}

/** 编辑器高度：视口高度扣除顶栏/表单/页脚的占位，保证分栏尽量占满屏幕 */
const editorHeight = 'calc(100vh - 360px)'

/** 未登录不可编辑（访客只读），直接回列表页 */
if (!getToken()) {
  proxy.$modal.msgError('请先登录后再写笔记')
  router.replace('/note')
} else {
  loadForm()
}

/** 编辑模式加载笔记；新增模式支持 ?itemId= 预填关联条目 */
function loadForm(): void {
  const rawNoteId = route.query.noteId
  if (rawNoteId) {
    const noteId = Number(rawNoteId)
    if (!noteId) {
      router.replace('/note')
      return
    }
    getFrontNote(noteId).then(response => {
      const note = response.data
      if (!note) {
        proxy.$modal.msgError('笔记不存在或已删除')
        router.replace('/note')
        return
      }
      Object.assign(form, note)
      // 公开状态归一化：仅接受 '0'/'1'，空值按私密处理（存量数据兜底）
      form.isPublic = note.isPublic === '1' ? '1' : '0'
    }).catch(() => {
      router.replace('/note')
    })
    return
  }
  const rawItemId = route.query.itemId
  const itemId = rawItemId ? Number(rawItemId) : NaN
  if (itemId) {
    form.itemId = itemId
  }
}

/** 返回列表页 */
function goBack(): void {
  router.replace('/note')
}

/** 保存：新增或修改，成功后回列表页 */
function submitForm(): void {
  proxy.$refs['editFormRef'].validate((valid: boolean) => {
    if (!valid) return
    saving.value = true
    const request = form.noteId ? updateFrontNote(form) : addFrontNote(form)
    request.then(() => {
      proxy.$modal.msgSuccess(form.noteId ? '修改成功' : '新增成功')
      goBack()
    }).catch(() => {}).finally(() => {
      saving.value = false
    })
  })
}
</script>

<style scoped lang="scss">
.note-edit-page {
  color: var(--sgj-text);
}

.edit-card {
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: 20px;
  padding: 24px 28px;
}

.edit-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;

  .back-btn {
    border-color: var(--sgj-border-card);
    background: var(--sgj-bg);
    color: var(--sgj-text-2);
  }

  .edit-title {
    flex: 1;
    margin: 0;
    font-family: var(--sgj-font-serif);
    font-size: 22px;
    font-weight: 700;
    color: var(--sgj-text);
  }
}

.edit-form {
  .title-input {
    max-width: 560px;
  }

  .meta-row {
    display: flex;
    flex-wrap: wrap;
    gap: 0 28px;

    .el-form-item {
      margin-right: 0;
    }
  }

  .content-item {
    margin-bottom: 0;

    :deep(.el-form-item__content) {
      display: block;
    }
  }
}

@media (max-width: 768px) {
  .edit-card {
    padding: 16px 14px;
    border-radius: 14px;
  }

  .edit-header {
    flex-wrap: wrap;

    .edit-title {
      font-size: 18px;
    }
  }
}
</style>
