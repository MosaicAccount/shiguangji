<template>
  <div class="note-edit-page">
    <div class="edit-card" v-loading="initializing">
      <!-- 顶栏：返回 + 标题 + 保存 -->
      <div class="edit-header">
        <el-button class="back-btn" circle :icon="ArrowLeft" @click="goBack" />
        <h1 class="edit-title">{{ form.noteId ? '编辑笔记' : '写笔记' }}</h1>
        <div class="header-actions">
          <el-button @click="goBack">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
        </div>
      </div>

      <!-- 草稿状态条：笔记被删 > 已恢复草稿（可关闭，关闭后显示同步态） > 同步三态 -->
      <div class="sync-bar" :class="{ 'is-restored': restored && !noteDeleted }" role="status">
        <template v-if="noteDeleted">
          <span class="sync-deleted">这篇笔记已被删除，草稿无法继续保存</span>
          <el-button link type="primary" size="small" @click="backToList">回列表页</el-button>
        </template>
        <template v-else-if="restored">
          <span class="restore-dot" aria-hidden="true"></span>
          <span>已恢复未保存的草稿</span>
          <el-button link type="primary" size="small" @click="discardDraft">放弃草稿</el-button>
          <el-button link size="small" @click="restored = false">关闭</el-button>
        </template>
        <template v-else>
          <span class="sync-state" :class="'is-' + syncState">{{ syncText }}</span>
          <el-button v-if="syncState === 'failed'" link type="primary" size="small" @click="pushDraft">重试</el-button>
        </template>
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
          <!-- 超限在编辑器内提前拦住：不拦的话每次自动保存都被服务端拒，状态条会永远停在「仅本地待同步」 -->
          <p v-if="contentTooLong" class="limit-warn">
            正文已 {{ (form.content || '').length }} 字，超过上限 {{ CONTENT_MAX_LENGTH }} 字，无法保存草稿与笔记
          </p>
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
import { getNoteDraft, getNoteDraftByNoteId, getBlankNoteDraft, saveNoteDraft, delNoteDraft } from '@/api/front/noteDraft'
import type { SgjNote } from '@/types/api/business/note'
import type { SgjNoteDraft, NoteDraftPayload } from '@/types/api/front/noteDraft'
import { getToken } from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import { parseServerTime } from '@/utils/sgj'
import {
  editorKeyOf,
  readNoteBuffer,
  writeNoteBuffer,
  clearNoteBuffer,
  bufferMatchesIdentity,
  decideRestore,
  restoreFormData,
  applyPushResult,
  type NoteDraftBuffer
} from '@/utils/noteDraftBuffer'

const { proxy } = getCurrentInstance() as { proxy: any }
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 与后端 SgjNoteServiceImpl.CONTENT_MAX_LENGTH 保持一致 */
const CONTENT_MAX_LENGTH = 100000
/** 服务端同步周期；本地缓冲在停手 1s 后写入 */
const SYNC_INTERVAL_MS = 15000
const LOCAL_DEBOUNCE_MS = 1000

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

/** 同步状态条三态 */
const syncState = ref<'synced' | 'pending' | 'failed'>('pending')
const syncText = computed(() => {
  if (syncState.value === 'synced') return '已同步到草稿箱'
  if (syncState.value === 'failed') return '同步失败（可重试）'
  return '仅本地待同步'
})
/** 已恢复未保存的草稿（优先显示，可关闭；关闭 ≠ 删草稿） */
const restored = ref(false)
/**
 * 进编辑器时先不画表单：先拿笔记原文、再拉草稿，两段 await 之间画出去的话，
 * 用户会看到内容「先变成笔记原文、又变成草稿」。内容与「已恢复未保存的草稿」提示条只该一起出现一次
 */
const initializing = ref(false)
/** 笔记已被删除：草稿接口的存活校验拒绝写入，反复重试没有意义 */
const noteDeleted = ref(false)
const contentTooLong = computed(() => (form.content || '').length > CONTENT_MAX_LENGTH)

/** 当前草稿ID（首次同步成功后由服务端返回） */
let draftId: number | undefined
/** 最近一次推送成功时服务端返回的 updateTime（epoch） */
let baseUpdateTime = 0
/** 本地递增计数器（不是时钟）：判断推送响应回来时内容有没有再变过 */
let seq = 0
/** 有服务端从没见过的内容 */
let dirty = false
/** 本地缓冲在飞：避免同一时刻并发两次 PUT */
let pushing = false
/** 程序化改写表单（恢复 / 放弃 / 回滚）时不当作「用户改动」 */
let muteChange = false
/** 保存成功后跳转不得再弹离开确认 */
let published = false
/** 打开时的服务器快照（离开确认的判据）：编辑态是笔记原文，新增态是空表单 + 预填条目 */
let baseline = ''

const username = computed(() => userStore.name || getToken() || '')
const entryNoteId = route.query.noteId ? Number(route.query.noteId) : undefined
const entryDraftId = route.query.draftId ? Number(route.query.draftId) : undefined
/** 本地缓冲的身份：新笔记固定是 new，不区分用户的话换人登录会看到上一个人的内容 */
const editorKey = editorKeyOf(entryNoteId, entryDraftId)

const timers: number[] = []
let localTimer: number | undefined

/** 未登录不可编辑（访客只读），直接回列表页 */
if (!getToken()) {
  proxy.$modal.msgError('请先登录后再写笔记')
  router.replace('/note')
} else {
  initEditor()
}

/**
 * 进编辑器：先取服务端两份快照（笔记原文 + 未保存的草稿），再与本地缓冲比对决定用哪份。
 * 恢复规则见 utils/noteDraftBuffer.decideRestore（只用服务端时间，不存客户端 savedAt）
 */
async function initEditor(): Promise<void> {
  initializing.value = true
  if (entryNoteId && !Number.isFinite(entryNoteId)) {
    initializing.value = false
    router.replace('/note')
    return
  }
  let note: SgjNote | undefined
  if (entryNoteId) {
    try {
      note = (await getFrontNote(entryNoteId)).data
    } catch {
      // 加载失败（含已删除）与原来的行为一致：提示后回列表页
    }
    if (!note) {
      initializing.value = false
      proxy.$modal.msgError('笔记不存在或已删除')
      router.replace('/note')
      return
    }
    applyForm(note)
    form.isPublic = note.isPublic === '1' ? '1' : '0'
  } else if (route.query.itemId && Number(route.query.itemId)) {
    applyForm({ itemId: Number(route.query.itemId) })
  }
  // 打开时的服务器快照：恢复草稿后「与它不同」即算有未保存改动（取消时才拦得住）
  baseline = snapshot()

  const local = readLocalBuffer()
  const serverDraft = await loadServerDraft(local)
  const serverUpdateTime = parseServerTime(serverDraft?.updateTime)

  const source = decideRestore(local, serverUpdateTime)
  // 来源那份内容一律套回表单：编辑已有笔记时本地那份同样是用户改过的最新内容，
  // 只给新增态套的话会出现「再进来还是笔记原文、也没有提示条」（见 restoreFormData 注释）
  const restoredForm = restoreFormData(source, local, serverDraft, form.noteId ?? entryNoteId)
  if (restoredForm) {
    applyForm(restoredForm)
  }

  if (source === 'local' && local) {
    draftId = local.draftId ?? serverDraft?.draftId
    baseUpdateTime = local.baseUpdateTime
    dirty = local.dirty
    // 本地有服务端从没见过的改动：立即补推一次，不等下一个 15s
    if (local.dirty) {
      pushDraft()
    } else {
      syncState.value = 'synced'
    }
  } else if (source === 'server' && serverDraft) {
    draftId = serverDraft.draftId
    baseUpdateTime = serverUpdateTime
    dirty = false
    syncState.value = 'synced'
  } else {
    draftId = entryDraftId
    syncState.value = 'pending'
  }

  // 从草稿箱「继续写」进来的（地址栏带着 draftId）：用户点开的就是这份草稿，不是「从笔记里恢复出来的」——
  // 以它本身为基准：不弹「已恢复未保存的草稿」，也不会因为「和打开时的快照不同」而误拦离开
  if (entryDraftId) {
    baseline = snapshot()
  }

  // 恢复了草稿就提示——不提示的话用户会以为这些内容已经正式保存了；
  // 内容与打开时的快照一致时（比如刷新后本地与服务端等价、或是从草稿箱进来的）不提示，避免无意义的状态条
  restored.value = source !== 'none' && snapshot() !== baseline
  initializing.value = false
  startTimers()
}

/**
 * 取服务端草稿：编辑态按 noteId，草稿箱「继续写」按 draftId，
 * 全新编辑页则用本地缓冲里的 draftId 找回同一份（刷新后仍能比对服务端时间）
 */
async function loadServerDraft(local: NoteDraftBuffer | null): Promise<SgjNoteDraft | null> {
  try {
    if (entryNoteId) {
      return (await getNoteDraftByNoteId(entryNoteId)).data || null
    }
    const targetId = entryDraftId || local?.draftId
    if (!targetId) {
      // 本机没有 draftId（换设备 / 清过缓存）时按身份把那份空白草稿取回来：
      // 空白草稿每人只有一份，不先取回来就写，第二篇会把第一篇未写完的内容静默覆盖掉
      return (await getBlankNoteDraft()).data || null
    }
    return (await getNoteDraft(targetId)).data || null
  } catch {
    // 草稿已被删（另一台设备清理 / 发布过）时当作服务端没有草稿
    return null
  }
}

/** 本地缓冲：身份（用户 + draftId + noteId）对不上的一律不采用，否则会把 A 草稿的内容带进 B */
function readLocalBuffer(): NoteDraftBuffer | null {
  const buffer = readNoteBuffer(username.value, editorKey)
  return bufferMatchesIdentity(buffer, { noteId: entryNoteId, draftId: entryDraftId }) ? buffer : null
}

/** 表单快照（离开确认的判据：与打开时的服务器快照比，不能与草稿比——草稿总是最新的，那样写等于永不拦截） */
function snapshot(): string {
  return JSON.stringify({
    title: form.title || '',
    content: form.content || '',
    itemId: form.itemId ?? null,
    tags: form.tags || '',
    isPublic: form.isPublic || '0'
  })
}

function hasUnsavedChanges(): boolean {
  return !published && snapshot() !== baseline
}

/** 程序化改写表单：不触发「用户改动」 */
function applyForm(data: Partial<SgjNote>): void {
  muteChange = true
  Object.assign(form, data)
  nextTick(() => {
    muteChange = false
  })
}

watch(
  () => [form.title, form.content, form.itemId, form.tags, form.isPublic],
  () => {
    if (muteChange) return
    seq += 1
    dirty = true
    if (syncState.value === 'synced') syncState.value = 'pending'
    scheduleLocalWrite()
  }
)

/** 停手 1s 写本地：写本地是瞬间完成的，断网 / 页面被强杀都不会丢 */
function scheduleLocalWrite(): void {
  window.clearTimeout(localTimer)
  localTimer = window.setTimeout(writeLocalBuffer, LOCAL_DEBOUNCE_MS)
}

function currentBuffer(): NoteDraftBuffer {
  return {
    draftId,
    noteId: form.noteId ?? entryNoteId,
    itemId: form.itemId,
    title: form.title,
    content: form.content,
    tags: form.tags,
    isPublic: form.isPublic,
    baseUpdateTime,
    dirty
  }
}

function writeLocalBuffer(): void {
  writeNoteBuffer(username.value, editorKey, currentBuffer())
}

/**
 * 推服务端（后台静默）：每次带 draftId（有则更新）、noteId（编辑态）。
 * 失败不弹提示，只把状态条转「仅本地待同步」/「同步失败」；内容由本地缓冲兜底
 */
function pushDraft(): void {
  if (pushing || noteDeleted.value) return
  // 空表单不建草稿（否则进一次编辑页就多一份空草稿，20 份很快被占满）；
  // 已经有草稿行时照样推，让服务端跟着清空
  if (!draftId && !form.title && !form.content) return
  if (contentTooLong.value) return
  const payload: NoteDraftPayload = {
    draftId,
    noteId: form.noteId ?? entryNoteId,
    itemId: form.itemId,
    title: form.title,
    content: form.content,
    tags: form.tags,
    isPublic: form.isPublic || '0'
  }
  const pushedSeq = seq
  pushing = true
  saveNoteDraft(payload, true).then(response => {
    const saved = response.data
    draftId = saved?.draftId ?? draftId
    const serverTime = parseServerTime(saved?.updateTime)
    // 推送在飞时用户又打了字：不能把这段内容当作已同步
    const buffer = applyPushResult(currentBuffer(), pushedSeq, seq, draftId, serverTime)
    baseUpdateTime = buffer.baseUpdateTime
    dirty = buffer.dirty
    writeNoteBuffer(username.value, editorKey, buffer)
    syncState.value = dirty ? 'pending' : 'synced'
  }).catch((error: any) => {
    const message = String(error?.message || error || '')
    if (message.includes('已被删除')) {
      noteDeleted.value = true
      restored.value = false
    } else {
      syncState.value = 'failed'
    }
  }).finally(() => {
    pushing = false
  })
}

function startTimers(): void {
  timers.push(window.setInterval(() => {
    if (dirty) pushDraft()
  }, SYNC_INTERVAL_MS))
  document.addEventListener('visibilitychange', onHide)
  window.addEventListener('pagehide', onHide)
  window.addEventListener('online', pushDraft)
}

/** 切走 / 关闭页面：先同步写本地，再尽力推一次（未送达由下次进编辑器补推兜底） */
function onHide(): void {
  if (document.visibilityState === 'hidden') {
    writeLocalBuffer()
    pushDraft()
  }
}

onBeforeUnmount(() => {
  timers.forEach(id => window.clearInterval(id))
  window.clearTimeout(localTimer)
  document.removeEventListener('visibilitychange', onHide)
  window.removeEventListener('pagehide', onHide)
  window.removeEventListener('online', pushDraft)
})

// 关闭 / 刷新标签页：文案由浏览器决定（不可自定义），移动端 iOS Safari 基本不触发
function onBeforeUnload(event: BeforeUnloadEvent): void {
  if (!hasUnsavedChanges()) return
  writeLocalBuffer()
  event.preventDefault()
  event.returnValue = ''
}

window.addEventListener('beforeunload', onBeforeUnload)
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))

/** 返回列表页：离开前拦一道（未保存改动确认），确认后草稿保留 */
async function goBack(): Promise<void> {
  const leave = await confirmLeave()
  if (!leave) return
  router.replace('/note')
}

function backToList(): void {
  published = true
  router.replace('/note')
}

/**
 * 未保存离开确认：应用内文案须说明内容已保存到草稿箱，
 * 但这句话由「草稿是否真的存在」决定——空表单不建草稿，此时说「已保存到草稿箱」与实际不符
 */
async function confirmLeave(): Promise<boolean> {
  if (!hasUnsavedChanges()) return true
  const message = draftId
    ? '内容已保存到草稿箱，可从草稿箱继续写。确定离开吗？'
    : (form.title || form.content)
      ? '内容还没同步到草稿箱（已存在本机浏览器）。确定离开吗？'
      : '标题与正文都为空，不会生成草稿；只有条目 / 标签 / 公开状态的改动不会保留。确定离开吗？'
  try {
    await proxy.$modal.confirm(message)
    writeLocalBuffer()
    pushDraft()
    return true
  } catch {
    return false
  }
}

onBeforeRouteLeave(async () => confirmLeave())

/**
 * 放弃草稿：删服务端草稿 + 清本地缓冲 + 表单退回打开时的服务器快照。
 * 只删草稿不退表单的话，用户以为内容还在，点取消时也还会被拦
 */
async function discardDraft(): Promise<void> {
  try {
    if (draftId) {
      await delNoteDraft(draftId)
    }
  } catch {
    // 草稿已被删（另一台设备）时忽略：本地那份照样要清掉
  }
  clearNoteBuffer(username.value, editorKey)
  draftId = undefined
  baseUpdateTime = 0
  dirty = false
  // seq 前进一格：在飞的推送响应回来时不得把「刚放弃的内容」标记成已同步
  seq += 1
  restored.value = false
  noteDeleted.value = false
  syncState.value = 'pending'
  rollbackToBaseline()
}

/** 表单退回打开时的快照（新增态即空表单 + 预填条目；编辑态即笔记原文） */
function rollbackToBaseline(): void {
  const snap = JSON.parse(baseline) as Record<string, any>
  applyForm({
    title: snap.title || undefined,
    content: snap.content || undefined,
    itemId: snap.itemId ?? undefined,
    tags: snap.tags || undefined,
    isPublic: snap.isPublic || '0'
  })
}

/** 保存：新增或修改，成功后清掉本地缓冲并回列表页 */
function submitForm(): void {
  proxy.$refs['editFormRef'].validate((valid: boolean) => {
    if (!valid) return
    if (contentTooLong.value) {
      proxy.$modal.msgError(`正文长度不能超过 ${CONTENT_MAX_LENGTH} 个字符`)
      return
    }
    saving.value = true
    const payload = { ...form, draftId }
    const request = form.noteId ? updateFrontNote(payload) : addFrontNote(payload)
    request.then(() => {
      // 保存成功后本地那份也要清掉：留着下次进编辑器会把刚保存好的正文盖回去
      clearNoteBuffer(username.value, editorKey)
      published = true
      proxy.$modal.msgSuccess(form.noteId ? '修改成功' : '新增成功')
      router.replace('/note')
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

/* 草稿状态条：三态 + 恢复提示（恢复提示优先，可关闭） */
.sync-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-height: 24px;
  margin-bottom: 12px;
  padding: 6px 12px;
  border-radius: 10px;
  background: var(--sgj-bg);
  border: 1px solid var(--sgj-border-card);
  font-size: 12.5px;
  color: var(--sgj-text-2);

  .sync-state.is-synced {
    color: var(--sgj-moss);
  }

  // 恢复草稿：与「同步三态」区分开，别让用户以为是已保存状态；这是「这些内容还没正式保存」的唯一提示
  &.is-restored {
    background: var(--sgj-primary-soft);
    border-color: transparent;

    .restore-dot {
      width: 7px;
      height: 7px;
      flex-shrink: 0;
      border-radius: 50%;
      background: var(--sgj-primary);
    }
  }

  .sync-state.is-failed,
  .sync-deleted {
    color: var(--sgj-danger);
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

  .limit-warn {
    margin: 8px 0 0;
    font-size: 12.5px;
    color: var(--sgj-danger);
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
