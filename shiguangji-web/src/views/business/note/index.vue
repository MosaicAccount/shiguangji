<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <!-- ：keyword 同时检索标题与正文（MySQL FULLTEXT），label 由「标题」改为「关键词」 -->
      <el-form-item label="关键词" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          placeholder="搜索标题或正文"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="关联条目" prop="itemId">
        <item-select v-model="queryParams.itemId" style="width: 220px" />
      </el-form-item>
      <!-- ：公开筛选（后端 SgjNoteMapper 已支持 isPublic 精确查询） -->
      <!-- 标签筛选：选项来自标签管理（NOTE 模块），FIND_IN_SET 精确匹配 -->
      <el-form-item label="标签" prop="tags">
        <tag-select
          v-model="queryParams.tags"
          module="NOTE"
          :multiple="false"
          placeholder="选择标签"
          style="width: 200px"
          @update:model-value="handleQuery"
        />
      </el-form-item>
      <el-form-item label="公开" prop="isPublic">
        <el-select v-model="queryParams.isPublic" placeholder="公开状态" clearable style="width: 200px">
          <el-option label="公开" value="1" />
          <el-option label="私密" value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['sgj:note:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Upload" @click="pickImportFile" v-hasPermi="['sgj:note:add']">导入 md</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['sgj:note:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['sgj:note:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-dropdown v-hasPermi="['sgj:note:list']" @command="handleExport">
          <el-button type="warning" plain icon="Download" :loading="exportLoading">
            导出<el-icon class="el-icon--right"><arrow-down /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="json">导出 JSON</el-dropdown-item>
              <el-dropdown-item command="csv">导出 CSV</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <input ref="importInputRef" type="file" accept=".md" hidden @change="onImportFileChange" />

    <el-table v-loading="loading" :data="noteList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="笔记ID" align="center" prop="noteId" width="80" />
      <el-table-column label="标题" align="center" prop="title" :show-overflow-tooltip="true" min-width="180" />
      <!-- ：内容摘要列（Markdown 转纯文本截断 40 字） -->
      <el-table-column label="内容摘要" align="left" min-width="220">
        <template #default="scope">
          <span class="note-summary">{{ noteSummary(scope.row.content) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="关联条目" align="center" min-width="140" :show-overflow-tooltip="true">
        <template #default="scope">
          <span>{{ scope.row.itemName || (scope.row.itemId != null ? '#' + scope.row.itemId : '独立笔记') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="标签" align="center" prop="tags" :show-overflow-tooltip="true" min-width="140" />
      <el-table-column label="公开" align="center" prop="isPublic" width="90">
        <template #default="scope">
          <!-- 快捷切换公开状态，失败自动回滚 -->
          <el-switch
            v-model="scope.row.isPublic"
            active-value="1"
            inactive-value="0"
            @change="handlePublicChange(scope.row)"
            v-hasPermi="['sgj:note:edit']"
          />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="260" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['sgj:note:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['sgj:note:remove']">删除</el-button>
          <el-button link type="primary" icon="Download" @click="handleExportMarkdown(scope.row)" title="导出 Markdown（带 noteId，改完可以再导回来）">导出 Markdown</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog
      :title="title"
      v-model="open"
      width="1000px"
      append-to-body
      :close-on-click-modal="false"
      :before-close="beforeClose"
    >
      <!-- 草稿兜底（#57）：与前台编辑页同一套判定，这行说明当前这份内容到底存在哪里 -->
      <div class="draft-bar" :class="'is-' + syncState">{{ syncText }}</div>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="笔记标题" prop="title">
              <el-input v-model="form.title" placeholder="请输入笔记标题" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联条目" prop="itemId">
              <!-- 按名称选择关联条目，可取消关联 -->
              <item-select v-model="form.itemId" :key="form.noteId || 'new'" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="公开状态">
              <!-- 公开/私密：默认私密，公开后访客可见 -->
              <el-radio-group v-model="form.isPublic">
                <el-radio-button value="0">私密</el-radio-button>
                <el-radio-button value="1">公开</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标签" prop="tags">
              <!-- 标签来自后台标签管理（NOTE 模块），禁止自由输入 -->
              <tag-select v-model="form.tags" module="NOTE" placeholder="选择标签（可选）" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="内容" prop="content">
              <markdown-editor v-model="form.content" height="420px" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Note">
import MarkdownEditor from '@/components/MarkdownEditor/index.vue'
import TagSelect from '@/components/TagSelect/index.vue'
import ItemSelect from '@/components/front/ItemSelect.vue'
import { listNote, getNote, addNote, updateNote, delNote } from '@/api/business/note'
import { noteExportUrl } from '@/api/front/note'
import { getBlankNoteDraft, getNoteDraftByNoteId, saveNoteDraft } from '@/api/front/noteDraft'
import {
  applyPushResult,
  bufferMatchesIdentity,
  clearNoteBuffer,
  decideRestore,
  editorKeyOf,
  readNoteBuffer,
  restoreFormData,
  writeNoteBuffer,
  type NoteDraftBuffer
} from '@/utils/noteDraftBuffer'
import { NOTE_CONTENT_MAX_LENGTH } from '@/utils/note'
import { NOTE_TAG_MAX_COUNT, parseNoteMarkdown, type NoteImportPrefill } from '@/utils/noteImport'
import { findDraftConflict, findRoundTripTarget } from '@/utils/noteImportGuards'
import { getToken } from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import { fetchAllRows, downloadJson, downloadCsv, exportDateTag } from '@/utils/exportData'
import type { SgjNote } from '@/types/api/business/note'
import type { SgjNoteDraft } from '@/types/api/front/noteDraft'
import { parseServerTime, parseTime } from '@/utils/sgj'
import { ElMessageBox } from 'element-plus'

const { proxy } = getCurrentInstance() as { proxy: any }
const userStore = useUserStore()

const noteList = ref<SgjNote[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>('')

const data = reactive({
  form: {} as SgjNote,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    itemId: undefined,
    tags: undefined,
    isPublic: undefined
  },
  rules: {
    title: [{ required: true, message: '笔记标题不能为空', trigger: 'blur' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** ：Markdown 转纯文本摘要（与前台 ItemNotes.noteSummary 同逻辑），截断 40 字 */
function noteSummary(content?: string): string {
  if (!content) return '-'
  const plain = content
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[#>*`~\-_|]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return plain.length > 40 ? plain.slice(0, 40) + '…' : (plain || '-')
}

// ------------------------------------------------------------------
// 草稿兜底（issue #57）：后台弹窗接上既有的 sgj_note_draft
//
// 与前台编辑页 edit.vue **共用同一个槽位**：草稿行按 (create_by, note_id) 归属，本地缓冲按
// (username, 'note:{id}' | 'new') 归属。同一篇笔记的未保存内容不该有两个地方，所以后台与前台
// 同时开着同一篇时是后写覆盖（结论 24），与前台跨设备时的行为一致。
//
// ponytail: 「监听表单 → 本地缓冲 → 定时推送 → 三态」这套接线在 edit.vue 已有一份。这里按同样的
// 顺序再写一遍，而不是抽 composable——edit.vue 此刻正被导入/导出分支改着，现在抽会把两条分支拧在一起。
// 等 feat/note-import 合入后，两处接线可以收成一份 useNoteDraftSync。
// ------------------------------------------------------------------

/** 与前台 edit.vue 一致：本地缓冲的 key 由它算出来，两边不一致就会读写到两个槽位 */
const username = computed(() => userStore.name || getToken() || '')

/** 服务端同步周期；本地缓冲在停手 1s 后写入（与前台同一组数值） */
const SYNC_INTERVAL_MS = 15000
const LOCAL_DEBOUNCE_MS = 1000

const draftId = ref<number | undefined>(undefined)
const syncState = ref<'synced' | 'pending' | 'failed'>('pending')
/** 打开时恢复出了未保存的草稿（提示优先于同步态） */
const restored = ref(false)
/** 笔记已被删除：存活校验拒了写入，再试没有意义 */
const noteDeleted = ref(false)
const contentTooLong = computed(() => (form.value.content || '').length > NOTE_CONTENT_MAX_LENGTH)

/** 状态条：四态一句话（恢复提示优先，其次是两种「别试了」） */
const syncText = computed(() => {
  if (restored.value) return '已恢复未保存的草稿'
  if (noteDeleted.value) return '这篇笔记已被删除，草稿不再保存'
  if (contentTooLong.value) return `正文超过 ${NOTE_CONTENT_MAX_LENGTH} 字，草稿保存不了`
  if (syncState.value === 'synced') return '已同步到草稿箱'
  if (syncState.value === 'failed') return '同步失败（可重试）'
  return '仅本地待同步'
})

/** 本地递增计数器（不是时钟）：判断推送响应回来时内容有没有再变过 */
let seq = 0
/** 有服务端从没见过的内容 */
let dirty = false
/** 推送在飞：避免同一时刻并发两次 PUT */
let pushing = false
/** 最近一次推送成功时服务端返回的 updateTime（epoch 毫秒） */
let baseUpdateTime = 0
/** 打开时的快照（离开确认的判据） */
let baseline = ''
/** 程序化改写表单时不当作「用户改动」 */
let muteChange = false
let localTimer: number | undefined
let syncTimer: number | undefined

/**
 * 表单快照。
 *
 * `remark` 不在里面：草稿模型（`NoteDraftPayload`）没有备注字段，把它算进改动会弹出一个
 * 「已保存到草稿箱」的假承诺。只改备注就关闭，行为与做这个 issue 之前一致（不拦、不保）。
 */
function snapshot(): string {
  return JSON.stringify({
    title: form.value.title || '',
    content: form.value.content || '',
    itemId: form.value.itemId ?? null,
    tags: form.value.tags || '',
    isPublic: form.value.isPublic || '0'
  })
}

function hasUnsavedChanges(): boolean {
  return snapshot() !== baseline
}

/** 程序化改写表单：不触发「用户改动」 */
function applyForm(data: Partial<SgjNote>): void {
  muteChange = true
  Object.assign(form.value, data)
  nextTick(() => { muteChange = false })
}

function currentBuffer(): NoteDraftBuffer {
  return {
    draftId: draftId.value,
    noteId: form.value.noteId,
    itemId: form.value.itemId,
    title: form.value.title,
    content: form.value.content,
    tags: form.value.tags,
    isPublic: form.value.isPublic,
    baseUpdateTime,
    dirty
  }
}

function writeLocalBuffer(): void {
  writeNoteBuffer(username.value, editorKeyOf(form.value.noteId), currentBuffer())
}

/** 停手 1s 写本地：写本地是瞬间完成的，断网 / 页面被强杀都不会丢 */
function scheduleLocalWrite(): void {
  window.clearTimeout(localTimer)
  localTimer = window.setTimeout(writeLocalBuffer, LOCAL_DEBOUNCE_MS)
}

/**
 * 打开弹窗后准备草稿：与前台编辑页走同一条判定链（`utils/noteDraftBuffer`），
 * 所以「哪一份更新」两边理解一致——只用服务端时间 + dirty，不存客户端 savedAt
 *
 * @param entryNoteId 编辑态传笔记ID；新增态不传（对应空白草稿槽位 note_id = 0）
 */
async function prepareDraft(entryNoteId?: number): Promise<void> {
  noteDeleted.value = false
  restored.value = false
  syncState.value = 'pending'
  seq = 0
  dirty = false
  pushing = false
  // 快照要在套草稿**之前**取：套完再取的话两份相等，「恢复了草稿」永远看不出来
  baseline = snapshot()

  const rawLocal = readNoteBuffer(username.value, editorKeyOf(entryNoteId))
  const local = bufferMatchesIdentity(rawLocal, { noteId: entryNoteId }) ? rawLocal : null

  let serverDraft: SgjNoteDraft | null = null
  try {
    // 新增态取的是「空白草稿」（每人一份）：前台也在写那一份，不先取回来就会彼此静默顶掉
    serverDraft = entryNoteId
      ? ((await getNoteDraftByNoteId(entryNoteId)).data || null)
      : ((await getBlankNoteDraft()).data || null)
  } catch {
    // 草稿已被删（另一台设备清理 / 已发布）时当作服务端没有
    serverDraft = null
  }
  const serverUpdateTime = parseServerTime(serverDraft?.updateTime)

  const source = decideRestore(local, serverUpdateTime)
  const draftForm = restoreFormData(source, local, serverDraft, entryNoteId)
  if (draftForm) applyForm(draftForm)

  if (source === 'local' && local) {
    draftId.value = local.draftId ?? serverDraft?.draftId
    baseUpdateTime = local.baseUpdateTime
    dirty = local.dirty
    // 本地有服务端从没见过的改动：立即补推一次，不等下一个 15s
    if (local.dirty) pushDraft()
    else syncState.value = 'synced'
  } else if (source === 'server' && serverDraft) {
    draftId.value = serverDraft.draftId
    baseUpdateTime = serverUpdateTime
    dirty = false
    syncState.value = 'synced'
  } else {
    draftId.value = undefined
    baseUpdateTime = 0
  }

  restored.value = source !== 'none' && snapshot() !== baseline
}

/**
 * 推服务端（静默）。upsert 的收敛、存活校验、唯一键冲突都在服务端（结论 40 / 草稿表唯一键），
 * 这里失败只改状态条，内容由本地缓冲兜住
 */
function pushDraft(): void {
  if (pushing || noteDeleted.value || contentTooLong.value) return
  // 空表单不建草稿：否则点一次「新增」就留下一份空草稿挂在草稿箱里
  if (!draftId.value && !form.value.title && !form.value.content) return

  const pushedSeq = seq
  pushing = true
  saveNoteDraft({
    draftId: draftId.value,
    noteId: form.value.noteId,
    itemId: form.value.itemId,
    title: form.value.title,
    content: form.value.content,
    tags: form.value.tags,
    isPublic: form.value.isPublic || '0'
  }, true).then(response => {
    const saved = response.data
    draftId.value = saved?.draftId ?? draftId.value
    const buffer = applyPushResult(currentBuffer(), pushedSeq, seq, draftId.value, parseServerTime(saved?.updateTime))
    baseUpdateTime = buffer.baseUpdateTime
    dirty = buffer.dirty
    writeNoteBuffer(username.value, editorKeyOf(form.value.noteId), buffer)
    syncState.value = dirty ? 'pending' : 'synced'
  }).catch((error: any) => {
    const message = String(error?.message || error || '')
    if (message.includes('已被删除')) {
      noteDeleted.value = true
      restored.value = false
      proxy.$modal.msgError('这篇笔记已被删除，草稿不再保存')
    } else {
      syncState.value = 'failed'
    }
  }).finally(() => {
    pushing = false
  })
}

/**
 * 未保存离开确认。文案由「草稿是不是真的存在」决定——空表单不建草稿，
 * 此时说「已保存到草稿箱」与实际不符
 */
async function confirmLeave(): Promise<boolean> {
  if (!hasUnsavedChanges()) return true
  const message = draftId.value
    ? '内容已保存到草稿箱，下次可以接着改。确定关闭吗？'
    : (form.value.title || form.value.content)
      ? '内容还没同步到草稿箱（已存在本机浏览器）。确定关闭吗？'
      : '标题与正文都为空，不会生成草稿；只有条目 / 标签 / 公开状态的改动不会保留。确定关闭吗？'
  try {
    await proxy.$modal.confirm(message)
    return true
  } catch {
    return false
  }
}

/** 关闭前的尽力而为：先同步写本地，再推一次（未送达由下次打开时补推兜底） */
async function flushDraft(): Promise<void> {
  writeLocalBuffer()
  pushDraft()
}

/** 切走 / 关闭标签页：先同步写本地，再尽力推一次 */
function onHide(): void {
  if (document.visibilityState === 'hidden') {
    writeLocalBuffer()
    pushDraft()
  }
}

function startSyncTimers(): void {
  stopSyncTimers()
  syncTimer = window.setInterval(() => { if (dirty) pushDraft() }, SYNC_INTERVAL_MS)
  document.addEventListener('visibilitychange', onHide)
  window.addEventListener('pagehide', onHide)
}

function stopSyncTimers(): void {
  window.clearInterval(syncTimer)
  window.clearTimeout(localTimer)
  document.removeEventListener('visibilitychange', onHide)
  window.removeEventListener('pagehide', onHide)
}

watch(
  () => [form.value.title, form.value.content, form.value.itemId, form.value.tags, form.value.isPublic],
  () => {
    // !open 也要挡：关闭弹窗时 reset() 会改表单，那时不该算改动
    if (muteChange || !open.value) return
    seq += 1
    dirty = true
    restored.value = false
    if (syncState.value === 'synced') syncState.value = 'pending'
    scheduleLocalWrite()
  }
)

watch(open, (opened) => {
  if (opened) startSyncTimers()
  else stopSyncTimers()
})

onBeforeUnmount(stopSyncTimers)

/** 查询列表 */
function getList() {
  loading.value = true
  listNote(queryParams.value).then(response => {
    noteList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 取消按钮 */
async function cancel() {
  if (!(await confirmLeave())) return
  await flushDraft()
  open.value = false
  reset()
}

/**
 * 右上角 ✕ / ESC 关闭：el-dialog 的 before-close 只在这些路径上触发，「取消」按钮不走这里，
 * 所以两条路径各自都要拦一道（文案与判据同一个）
 */
async function beforeClose(done: () => void) {
  if (!(await confirmLeave())) return
  await flushDraft()
  done()
  reset()
}

/** 表单重置（程序化写表单，不算用户改动） */
function reset() {
  muteChange = true
  form.value = {
    noteId: undefined,
    itemId: undefined,
    title: undefined,
    content: undefined,
    tags: undefined,
    remark: undefined,
    isPublic: '0'
  }
  proxy.resetForm('formRef')
  nextTick(() => { muteChange = false })
}

/** 搜索按钮操作 */
function handleQuery() {
  // ngram_token_size=2，单字切不出 token 搜不到；不足 2 字提示后不发起检索
  const keyword = (queryParams.value.keyword || '').trim()
  if (keyword && keyword.length < 2) {
    proxy.$modal.msgWarning('关键词请至少输入 2 个字')
    return
  }
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.value.itemId = undefined
  queryParams.value.isPublic = undefined
  queryParams.value.tags = undefined
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection: SgjNote[]) {
  ids.value = selection.map(item => item.noteId!)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/**
 * 打开编辑弹窗并准备好草稿。新增态与编辑态共用这一条路——导入也走它，
 * 否则预填会盖在半个表单上（编辑态还得先读到原文，才能保住关联条目 / 公开状态 / 备注）
 */
async function openEditor(noteId?: number): Promise<void> {
  reset()
  if (noteId) {
    const response = await getNote(noteId)
    // 用 applyForm 而不是直接换对象：否则那个 watch 会把「打开一篇笔记」当成用户改动
    applyForm(response.data || {})
    // 公开状态归一化：仅接受 '0'/'1'，空值按私密处理（存量数据兜底）
    form.value.isPublic = form.value.isPublic === '1' ? '1' : '0'
    open.value = true
    title.value = '修改笔记'
    await prepareDraft(noteId)
    return
  }
  open.value = true
  title.value = '新增笔记'
  await prepareDraft()
}

/** 新增按钮操作 */
function handleAdd() {
  openEditor()
}

/** 修改按钮操作 */
function handleUpdate(row?: SgjNote) {
  openEditor(row?.noteId || ids.value[0])
}

/** 导入 md 的文件选择器（真实的 input 藏在模板里，按钮只是它的代理） */
const importInputRef = ref<HTMLInputElement>()

function pickImportFile(): void {
  importInputRef.value?.click()
}

/**
 * 导入一个 md：浏览器本地读 → 解析 → 打开编辑弹窗预填（不直接入库）。
 *
 * 解析用的是前台同一份函数（`utils/noteImport`），两道前置检查也是同一份
 * （`utils/noteImportGuards`：目标槽位的草稿冲突、front-matter 里的 noteId 往返识别）——
 * 两条入口问的是同一个问题，就必须给同一个答案。
 */
async function onImportFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 先清空：不然连着导入同一个文件不会再触发 change
  input.value = ''
  if (!file) return

  const result = parseNoteMarkdown(await file.text(), file.name)
  if (!result.ok) {
    if (result.code === 'not-md') {
      proxy.$modal.msgError('只支持导入 .md 文件')
    } else {
      proxy.$modal.msgError(`这篇共 ${result.length} 字，超过上限 ${NOTE_CONTENT_MAX_LENGTH} 字，无法导入`)
    }
    return
  }

  // 往返识别：命中的是**自己的**笔记才问（别人的一律静默新建，不写他人数据）
  const own = await findRoundTripTarget(result.prefill.noteId, username.value)
  let targetNoteId: number | undefined
  if (own?.noteId) {
    try {
      await ElMessageBox.confirm(
        `这份文件里带着《${own.title || '无标题'}》的标记，而且那篇笔记是你自己的。要更新它，还是新建一篇？`,
        '这份文件来自一篇已有的笔记',
        { confirmButtonText: '更新那篇', cancelButtonText: '新建一篇', type: 'info' }
      )
      targetNoteId = own.noteId
    } catch {
      targetNoteId = undefined
    }
  }

  // 冲突检查：要落到哪个槽位就查哪个槽位
  const conflict = await findDraftConflict(username.value, targetNoteId)
  if (conflict) {
    try {
      await ElMessageBox.confirm(
        `${targetNoteId ? '那篇笔记里' : '编辑页里'}有一份没写完的草稿（约 ${conflict.chars} 字），导入会覆盖它。`,
        '导入会覆盖草稿',
        { confirmButtonText: '继续导入', cancelButtonText: '先去看看', type: 'warning' }
      )
    } catch {
      // 「先去看看」：取消这次导入，把人送到对应的地方（更新态打开那篇，它会静默恢复自己的草稿）
      if (targetNoteId) openEditor(targetNoteId)
      return
    }
  }

  if (result.droppedTagCount > 0) {
    proxy.$modal.msgWarning(`标签最多带 ${NOTE_TAG_MAX_COUNT} 个，已忽略 ${result.droppedTagCount} 个`)
  }
  // 与前台同一条提示：图片文件不在 md 里，本版也还没做搬运（#56）
  if (result.localImageCount > 0) {
    proxy.$modal.msgWarning(`正文里有 ${result.localImageCount} 处图片引用；本版不搬图片，导入后那些图会失效`)
  }

  await openEditor(targetNoteId)
  title.value = '导入笔记'
  applyImportPrefill(result.prefill)
}

/**
 * 把导入的内容套到已打开的弹窗上（设计 结论 41 的三条约束，与前台跳编辑页时同一套）：
 * ① 不能走静默路径——要置 dirty，否则本地缓冲不写、15s 定时器也不推（它只推 dirty 的）；
 * ② baseline 在预填**之后**重取，否则关弹窗时会多弹一次确认；
 * ③ 立即推一次草稿，让「内容已经在草稿箱里」当场成立，而不是等 15s 或靠关窗那一哆嗦。
 */
function applyImportPrefill(prefill: NoteImportPrefill): void {
  applyForm({ title: prefill.title, content: prefill.content, tags: prefill.tags })
  baseline = snapshot()
  restored.value = false
  dirty = true
  seq += 1
  writeLocalBuffer()
  pushDraft()
}

/** 切换公开状态：列表快捷开关，失败回滚 */
function handlePublicChange(row: SgjNote) {
  updateNote({ ...row }).then(() => {
    proxy.$modal.msgSuccess(row.isPublic === '1' ? '已设为公开' : '已设为私密')
  }).catch(() => {
    row.isPublic = row.isPublic === '1' ? '0' : '1'
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs['formRef'].validate((valid: boolean) => {
    if (!valid) return
    if (contentTooLong.value) {
      proxy.$modal.msgError(`正文长度不能超过 ${NOTE_CONTENT_MAX_LENGTH} 个字符`)
      return
    }
    // 带 draftId 保存：服务端在同一事务里删掉草稿；不带也会按 note_id 兜一刀（结论 15 / §2.8）
    const payload: SgjNote = { ...form.value, draftId: draftId.value }
    const done = () => {
      clearNoteBuffer(username.value, editorKeyOf(form.value.noteId))
      draftId.value = undefined
      dirty = false
      open.value = false
      getList()
    }
    if (form.value.noteId != undefined) {
      updateNote(payload).then(() => {
        proxy.$modal.msgSuccess('修改成功')
        done()
      })
    } else {
      addNote(payload).then(() => {
        proxy.$modal.msgSuccess('新增成功')
        done()
      })
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row?: SgjNote) {
  const noteIds = row?.noteId || ids.value
  proxy.$modal.confirm('是否确认删除选中的笔记？').then(function() {
    return delNote(noteIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

/**
 * 导出为 Markdown 文件（每行都能导出，没有灰按钮）。
 *
 * 调的是前台那个导出接口（`/app/note/{noteId}/export`）：它的归属校验是「本人或管理员」，
 * 所以管理员在这里照样导得出任何一篇，而输出与前台详情页逐字节相同——一个接口才保证这一点（设计 结论 39）
 */
function handleExportMarkdown(row: SgjNote) {
  if (!row.noteId) return
  proxy.$download.file(noteExportUrl(row.noteId))
}

/** 导出进行中（数据导出） */
const exportLoading = ref<boolean>(false)

/** 笔记 CSV 导出列（JSON 导出为完整对象数组；CSV 取以下常用字段） */
const noteExportColumns = [
  { label: '笔记ID', key: 'noteId' },
  { label: '关联条目', key: 'itemName' },
  { label: '标题', key: 'title' },
  { label: '标签', key: 'tags' },
  { label: '内容', key: 'content' },
  { label: '备注', key: 'remark' },
  { label: '创建者', key: 'createBy' },
  { label: '创建时间', key: 'createTime' },
  { label: '更新时间', key: 'updateTime' }
]

/** 导出按钮操作（command: json/csv；循环分页拉取当前筛选下全量数据，pageSize 取后端上限） */
function handleExport(command: string) {
  proxy.$modal.confirm('是否确认导出当前筛选下的全部笔记数据？').then(async () => {
    exportLoading.value = true
    try {
      const rows = await fetchAllRows<SgjNote>((pageNum, pageSize) =>
        listNote({ ...queryParams.value, pageNum, pageSize })
      )
      if (!rows.length) {
        proxy.$modal.msgWarning('没有可导出的数据')
        return
      }
      const tag = exportDateTag()
      if (command === 'csv') {
        downloadCsv(`sgj_note_${tag}.csv`, noteExportColumns, rows as unknown as Record<string, any>[])
      } else {
        downloadJson(`sgj_note_${tag}.json`, rows)
      }
      proxy.$modal.msgSuccess('导出成功，共 ' + rows.length + ' 条')
    } catch (e) {
      // 失败提示由 request 拦截器统一弹出，这里仅记录便于排查
      console.error('导出失败', e)
    } finally {
      exportLoading.value = false
    }
  }).catch(() => {})
}

getList()
</script>

<style scoped>
/* 草稿状态条（#57）：一行小字，紧贴表单上方 */
.draft-bar {
  margin: -4px 0 14px;
  font-size: 12px;
  color: #909399;
}

.draft-bar.is-pending {
  color: #e6a23c;
}

.draft-bar.is-failed {
  color: #f56c6c;
}

/* ：内容摘要列（单行省略） */
.note-summary {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #606266;
}
</style>
