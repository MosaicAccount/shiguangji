<template>
  <el-dialog
    v-model="visible"
    class="carry-dialog"
    title="搬运笔记里的图片"
    width="520px"
    align-center
    append-to-body
    :close-on-click-modal="false"
    @close="resolveSkip"
  >
    <!-- 默认态：指出图片在哪 -->
    <template v-if="stage === 'pick'">
      <p class="dtxt">
        正文里有 <b>{{ refs.length }}</b> 处本地图片引用（去重后 <b>{{ uniqueCount }}</b> 处不同路径）。
        这些文件不在 md 里，需要你指出它们在哪。
      </p>
      <p class="desc">
        选笔记所在的文件夹最准（相对路径能对上）；也可以直接多选图片文件，按文件名匹配。找不到的会保留原引用。
      </p>
      <p v-if="externalCount > 0" class="desc">
        另有 <b>{{ externalCount }}</b> 处是外链图片（https://…），不参与搬运；若显示不出来，可用编辑器的「插图」手动替换。
      </p>
    </template>

    <!-- 上传中 -->
    <template v-else-if="stage === 'uploading'">
      <el-progress :percentage="progressPercent" :stroke-width="8" :show-text="false" />
      <p class="pmeta">
        <span>正在上传 <b>{{ doneCount }} / {{ totalToUpload }}</b>…</span>
        <span>已完成 {{ successCount }} 张，失败 {{ failCount }} 张</span>
      </p>
    </template>

    <!-- 结果态（含全部未命中） -->
    <template v-else>
      <p v-if="matchedRefCount > 0" class="okline">已搬运 <b>{{ matchedRefCount }}</b> 张图片，正文引用已改写。</p>
      <p v-else class="warnline">
        <svg class="warn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <path d="M12 3 2 20h20L12 3Z" /><path d="M12 9v5" /><path d="M12 17.2v.2" />
        </svg>
        没有匹配到任何图片（已保留原引用）。
      </p>
      <p v-if="failedRefCount > 0" class="warnline">
        <svg class="warn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <path d="M12 3 2 20h20L12 3Z" /><path d="M12 9v5" /><path d="M12 17.2v.2" />
        </svg>
        {{ failedRefCount }} 张上传失败，保留了原引用。
      </p>
      <template v-if="unmatchedRefs.length > 0">
        <p class="warnline">
          <svg class="warn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M12 3 2 20h20L12 3Z" /><path d="M12 9v5" /><path d="M12 17.2v.2" />
          </svg>
          {{ unmatchedRefs.length }} 处没找到对应文件，保留了原引用：
        </p>
        <ul class="misses">
          <li v-for="ref in unmatchedRefs.slice(0, 5)" :key="ref.index">{{ ref.raw }}</li>
        </ul>
        <p v-if="unmatchedRefs.length > 5" class="morecount">等 {{ unmatchedRefs.length }} 处</p>
      </template>
      <p v-if="ignoredOversize > 0" class="ignored">已忽略 {{ ignoredOversize }} 个超过 10MB 的文件。</p>
      <p v-if="ignoredOvercount > 0" class="ignored">一次最多搬运 {{ NOTE_IMAGE_MAX_COUNT }} 张，超出的已忽略。</p>
      <p v-if="rewrite && rewrite.tooLong" class="warnline">
        <svg class="warn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <path d="M12 3 2 20h20L12 3Z" /><path d="M12 9v5" /><path d="M12 17.2v.2" />
        </svg>
        搬运后正文 {{ rewrite.length }} 字，超过上限 {{ NOTE_CONTENT_MAX_LENGTH }} 字——已照常填入，保存前请先精简。
      </p>
    </template>

    <template #footer>
      <div class="carry-footer">
        <div v-if="stage !== 'uploading' && !(stage === 'result' && matchedRefCount > 0)" class="btn-row">
          <el-button type="primary" @click="folderInput?.click()">选择图片文件夹</el-button>
          <el-button @click="fileInput?.click()">选择图片文件</el-button>
        </div>
        <div v-else-if="stage === 'uploading'" class="btn-row">
          <el-button type="primary" loading disabled>正在上传…</el-button>
        </div>
        <div v-else class="btn-row">
          <el-button type="primary" @click="finish()">完成，去编辑</el-button>
        </div>
        <el-button text @click="skip()">{{ stage === 'pick' ? '跳过，只导入文字' : '放弃搬运，只导入文字' }}</el-button>
      </div>
    </template>

    <!-- 两个真实的文件选择器：目录（webkitdirectory）拿得到相对路径，多选只有文件名 -->
    <input ref="folderInput" type="file" webkitdirectory hidden multiple @change="onFilesChange" />
    <input ref="fileInput" type="file" hidden multiple :accept="acceptTypes" @change="onFilesChange" />
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadNoteImage } from '@/api/common/upload'
import { NOTE_CONTENT_MAX_LENGTH } from '@/utils/note'
import {
  NOTE_IMAGE_EXTENSIONS,
  NOTE_IMAGE_MAX_COUNT,
  NOTE_IMAGE_MAX_SIZE,
  mdDirOf,
  resolveImageFiles,
  rewriteNoteImageRefs,
  uniqueTargetCount,
  type NoteImageRef,
  type ImageCandidate,
  type RewriteResult
} from '@/utils/noteImages'

/** open() 的返回：null = 跳过（正文不改写，已上传的图交给孤儿清理，见设计 结论 60） */
export interface CarryOutcome {
  content: string
  rewrite: RewriteResult
}

const emit = defineEmits<{
  (e: 'applied', outcome: CarryOutcome): void
}>()

const visible = ref(false)
const stage = ref<'pick' | 'uploading' | 'result'>('pick')
const refs = ref<NoteImageRef[]>([])
const externalCount = ref(0)
const mdFileName = ref('')
const content = ref('')

const uniqueCount = computed(() => uniqueTargetCount(refs.value))
const acceptTypes = computed(() => NOTE_IMAGE_EXTENSIONS.map(ext => `.${ext}`).join(','))

const totalToUpload = ref(0)
const doneCount = ref(0)
const successCount = ref(0)
const failCount = ref(0)
const matchedRefCount = ref(0)
const failedRefCount = ref(0)
const unmatchedRefs = ref<NoteImageRef[]>([])
const ignoredOversize = ref(0)
const ignoredOvercount = ref(0)
const rewrite = ref<RewriteResult | null>(null)

const folderInput = ref<HTMLInputElement>()
const fileInput = ref<HTMLInputElement>()

let settle: ((outcome: CarryOutcome | null) => void) | null = null

const progressPercent = computed(() => (totalToUpload.value === 0 ? 0 : Math.round((doneCount.value / totalToUpload.value) * 100)))

/**
 * 打开弹窗并等待结果（调用方线性 await，导入流程不用被拆到回调里）。
 *
 * @returns 搬运结果；跳过 / 关闭弹窗 / 全部失败时返回 null，正文保持原样
 */
function open(sourceContent: string, imageRefs: NoteImageRef[], external: number, fileName: string): Promise<CarryOutcome | null> {
  content.value = sourceContent
  refs.value = imageRefs
  externalCount.value = external
  mdFileName.value = fileName
  stage.value = 'pick'
  resetResult()
  visible.value = true
  return new Promise(resolvePromise => {
    settle = resolvePromise
  })
}

function resetResult(): void {
  totalToUpload.value = 0
  doneCount.value = 0
  successCount.value = 0
  failCount.value = 0
  matchedRefCount.value = 0
  failedRefCount.value = 0
  unmatchedRefs.value = []
  ignoredOversize.value = 0
  ignoredOvercount.value = 0
  rewrite.value = null
}

async function onFilesChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  // 先清空：连着选同一个文件夹不会再触发 change
  input.value = ''
  if (files.length === 0) return

  // 只收图片、滤掉超限的（忽略并提示，不中断整次搬运）。
  // 注意：md 所在目录必须从**未过滤**的完整文件树里取（utils/noteImages 的 mdDirOf）——
  // 若在这里先把非图片文件滤掉，md 自己也被滤掉，匹配规则①会静默失效，
  // 引用只能退到「文件名唯一匹配」，同名歧义时就被误判成未命中（#56 实测踩到）
  const mdDir = mdDirOf(
    files.map(file => ({ name: file.name, relPath: (file as File & { webkitRelativePath?: string }).webkitRelativePath || '' })),
    mdFileName.value
  )
  ignoredOversize.value = 0
  ignoredOvercount.value = 0
  const candidates: ImageCandidate[] = []
  for (const file of files) {
    const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
    if (!NOTE_IMAGE_EXTENSIONS.includes(ext)) continue
    if (file.size > NOTE_IMAGE_MAX_SIZE) {
      ignoredOversize.value++
      continue
    }
    if (candidates.length >= NOTE_IMAGE_MAX_COUNT) {
      ignoredOvercount.value++
      continue
    }
    candidates.push({
      file,
      name: file.name,
      relPath: (file as File & { webkitRelativePath?: string }).webkitRelativePath || ''
    })
  }

  const { matched, unresolved } = resolveImageFiles(refs.value, candidates, mdDir)
  // 诊断留痕：定位「为什么某张没搬」时，mdDir 与候选数是第一现场（这类静默降级不看日志很难查）
  console.debug('[note-image-carry] 选择结果', {
    mdFileName: mdFileName.value,
    mdDir,
    选中文件: files.length,
    图片候选: candidates.length,
    命中: matched.length,
    未命中: unresolved.map(ref => ref.target)
  })
  if (matched.length === 0) {
    unmatchedRefs.value = unresolved
    stage.value = 'result'
    if (ignoredOversize.value > 0 || ignoredOvercount.value > 0) {
      ElMessage.warning(ignoredOversize.value > 0 ? '已忽略超过 10MB 的文件' : '一次最多搬运 50 张，超出的已忽略')
    }
    return
  }

  // 同一文件被多处引用只上传一次；它的上传失败会让所有指向它的引用保留原样
  stage.value = 'uploading'
  const uploads = dedupeByCandidate(matched)
  totalToUpload.value = uploads.length
  doneCount.value = 0
  successCount.value = 0
  failCount.value = 0

  const failedRefs: NoteImageRef[] = []
  const succeeded = new Map<ImageCandidate, string>()
  await runWithConcurrency(uploads, 3, async upload => {
    try {
      const res = await uploadNoteImage(upload.candidate.file)
      if (res.code === 200 && res.path) {
        succeeded.set(upload.candidate, res.path)
        successCount.value++
      } else {
        throw new Error(res.msg || '上传失败')
      }
    } catch {
      failedRefs.push(...upload.refs)
      failCount.value++
    } finally {
      doneCount.value++
    }
  })

  const replaced = matched.filter(m => succeeded.has(m.candidate)).map(m => ({ ref: m.ref, path: succeeded.get(m.candidate)! }))
  rewrite.value = rewriteNoteImageRefs(content.value, replaced)
  matchedRefCount.value = replaced.length
  failedRefCount.value = new Set(failedRefs).size
  const failedSet = new Set(failedRefs)
  unmatchedRefs.value = [...unresolved, ...matched.map(m => m.ref).filter(ref => failedSet.has(ref))]
  stage.value = 'result'
}

/** 按候选文件去重：一个候选带它服务的全部引用 */
function dedupeByCandidate(matched: { ref: NoteImageRef; candidate: ImageCandidate }[]): Array<{ candidate: ImageCandidate; refs: NoteImageRef[] }> {
  const byCandidate = new Map<ImageCandidate, NoteImageRef[]>()
  for (const m of matched) {
    byCandidate.set(m.candidate, [...(byCandidate.get(m.candidate) ?? []), m.ref])
  }
  return [...byCandidate.entries()].map(([candidate, list]) => ({ candidate, refs: list }))
}

/** 简单并发池：顺序起跑、最多 limit 个在飞（设计 §3.2：限并发 3） */
async function runWithConcurrency<T>(items: T[], limit: number, worker: (item: T) => Promise<void>): Promise<void> {
  let cursor = 0
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (cursor < items.length) {
      const item = items[cursor++]
      await worker(item)
    }
  })
  await Promise.all(runners)
}

function finish(): void {
  if (!rewrite.value) return skip()
  const outcome: CarryOutcome = { content: rewrite.value.content, rewrite: rewrite.value }
  visible.value = false
  emit('applied', outcome)
  settle?.(outcome)
  settle = null
}

function skip(): void {
  visible.value = false
  settle?.(null)
  settle = null
}

/** 关闭弹窗（X / ESC / 点遮罩）等同跳过：不改写正文，已上传的图交给孤儿清理（结论 60） */
function resolveSkip(): void {
  settle?.(null)
  settle = null
}

defineExpose({ open })
</script>

<style lang="scss">
/**
 * 弹窗 chrome 由 Element Plus 渲染，append-to-body 之后不在本组件的 scoped 作用域里，
 * 所以这一段不加 scoped，统一挂 .carry-dialog 前缀。数值逐条对齐设计图
 * docs/design/mockups/note-images/carry-images.html（§ 弹窗），颜色一律走 --sgj-* 令牌，
 * 深浅主题各自定义，因而两套主题都自动成立。
 */
.carry-dialog {
  background: var(--sgj-bg-card);
  border-radius: var(--sgj-radius-lg);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18);
  overflow: hidden;

  .el-dialog__header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-right: 0;
    padding: 18px 20px 10px;
  }

  .el-dialog__title {
    font-family: var(--sgj-font-serif);
    font-size: 16px;
    font-weight: 700;
    color: var(--sgj-text);
  }

  .el-dialog__headerbtn {
    top: 16px;
    right: 16px;
    font-size: 16px;
    color: var(--sgj-text-4);

    &:hover .el-dialog__close {
      color: var(--sgj-text-2);
    }
  }

  .el-dialog__body {
    padding: 4px 20px 8px;
  }

  .el-dialog__footer {
    padding: 12px 20px 20px;
  }

  .el-button {
    font: inherit;
    padding: 8px 16px;
    font-size: 13px;
    border-radius: 6px;
  }

  .el-button--primary {
    color: #fff;
  }

  html.dark & .el-button--primary {
    color: #171b1a;
  }

  .el-button.is-text {
    padding: 4px 8px;
    font-size: 12.5px;
    color: var(--sgj-text-3);

    &:hover {
      color: var(--sgj-primary);
      background: transparent;
    }
  }

  /* 进度条：设计图是 8px 圆角、底 --sgj-bg-deep、进度 --sgj-primary（EP 真实类名是 el-progress-bar__*） */
  .el-progress {
    margin: 2px 0 10px;
  }

  .el-progress-bar__outer {
    height: 8px;
    border-radius: 999px;
    background: var(--sgj-bg-deep);
  }

  .el-progress-bar__inner {
    border-radius: 999px;
    background: var(--sgj-primary);
  }
}
</style>

<style scoped lang="scss">
.dtxt {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.75;
  color: var(--sgj-text-2);

  &:last-child {
    margin-bottom: 0;
  }
}

.desc {
  margin: 0 0 10px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--sgj-text-3);
}

.pmeta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin: 0;
  font-size: 12.5px;
  color: var(--sgj-text-3);

  b {
    color: var(--sgj-text-2);
  }
}

.okline {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--sgj-text);
}

.warnline {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 0 0 6px;
  font-size: 12.5px;
  color: var(--sgj-warning);
}

.warn-icon {
  flex-shrink: 0;
  width: 13px;
  height: 13px;
}

.misses {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 160px;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;

  li {
    align-self: flex-start;
    padding: 3px 9px;
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: 12px;
    color: var(--sgj-text-2);
    word-break: break-all;
    background: var(--sgj-bg-deep);
    border-radius: 5px;
  }
}

.morecount {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--sgj-text-4);
}

.ignored {
  margin: 10px 0 0;
  padding-top: 10px;
  font-size: 12.5px;
  color: var(--sgj-text-3);
  border-top: 1px dashed var(--sgj-border-card);
}

.carry-footer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
}

.btn-row {
  display: flex;
  gap: 10px;
  width: 100%;

  :deep(.el-button) {
    flex: 1;
  }
}
</style>
