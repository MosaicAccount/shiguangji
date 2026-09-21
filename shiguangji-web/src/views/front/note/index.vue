<template>
  <div class="note-page">
    <div class="page-banner anim">
      <div class="banner-blob" aria-hidden="true"></div>
      <div class="banner-blob2" aria-hidden="true"></div>
      <div class="banner-text">
        <h1>笔记</h1>
        <p>记录你的思考与学习笔记。</p>
      </div>
      <el-button v-if="isLogin" type="primary" class="banner-add" @click="openAdd">＋ 写笔记</el-button>
    </div>

    <!-- 草稿箱入口：仅登录且有草稿时显示；数量取草稿列表长度（接口只回 excerpt，不拉正文） -->
    <button v-if="draftCount > 0" class="draft-entry" @click="openDrafts">
      <span class="draft-entry-icon" aria-hidden="true">🗂</span>
      <span class="draft-entry-text">草稿箱里有 {{ draftCount }} 份没写完的笔记</span>
      <span class="draft-entry-arrow" aria-hidden="true">→</span>
    </button>

    <div class="filter-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索标题与正文"
        clearable
        class="search-input"
        @keyup.enter="loadData"
        @clear="loadData"
      >
        <template #prefix><span class="search-icon">⌕</span></template>
        <template #append>
          <el-button @click="loadData">搜索</el-button>
        </template>
      </el-input>
      <!-- 导入 md：读文件与解析全在浏览器本地完成（不上传），解析完跳编辑页预填 -->
      <el-button v-if="isLogin" class="import-btn" @click="pickImportFile">导入 md</el-button>
    </div>
    <input ref="importInputRef" type="file" accept=".md" hidden @change="onImportFileChange" />

    <!-- 标签筛选：豆瓣式标签行（NOTE 模块），点击选中、再点取消 -->
    <tag-pills v-model="searchTag" module="NOTE" @update:model-value="loadData" />

    <div v-if="filterItemId" class="filter-tip">
      <span>正在查看条目「{{ filterItemName || '#' + filterItemId }}」的关联笔记</span>
      <el-button link type="primary" size="small" @click="clearFilter">查看全部笔记</el-button>
    </div>

    <div ref="listRef" v-loading="loading" class="note-list">
      <el-empty v-if="!loading && !list.length && !loadError" description="暂无笔记" />
      <div v-if="loadError" class="load-error">
        <span>加载失败，请稍后重试</span>
        <el-button size="small" round @click="loadData">重试</el-button>
      </div>
      <div v-for="note in list" :key="note.noteId" class="note-card" @click="openDetail(note)">
        <div class="note-header">
          <div class="note-title">
            <!-- 关键词高亮：切片段循环渲染（只命中标题时标题本身高亮），不拼 HTML、不走 v-html -->
            <template v-for="(seg, si) in titleSegments(note)" :key="si">
              <mark v-if="seg.hit">{{ seg.text }}</mark>
              <template v-else>{{ seg.text }}</template>
            </template>
            <!-- 公开标识仅登录态展示 -->
            <el-tag v-if="isLogin && note.isPublic === '1'" size="small" type="success" class="public-tag">公开</el-tag>
          </div>
          <!-- 删除角标与标题首行对齐；编辑入口统一在详情页 -->
          <el-button
            v-if="isLogin"
            class="note-delete"
            circle
            size="small"
            :icon="Delete"
            title="删除"
            @click.stop="handleDelete(note)"
          />
        </div>
        <!-- 纯文本摘要常显（后端生成）；渐隐仅在内容真的溢出时出现 -->
        <div
          v-if="note.excerpt"
          class="note-preview"
          :class="{ clip: clippedNoteIds.has(note.noteId!) }"
          :data-note-id="note.noteId"
        >
          <p class="note-excerpt">
            <template v-for="(seg, si) in excerptSegments(note)" :key="si">
              <mark v-if="seg.hit">{{ seg.text }}</mark>
              <template v-else>{{ seg.text }}</template>
            </template>
          </p>
        </div>
        <!-- 多于 1 处出现时提示；大数字不可行动，统一弱化为「多次」 -->
        <span v-if="note.hitTotal && note.hitTotal > 1" class="hit-count">{{ hitCountText(note.hitTotal) }}</span>
        <div class="note-meta">
          <!--  图标区分：🔗 关联笔记 / 📝 独立笔记 -->
          <span v-if="note.itemId" class="note-link">🔗 {{ note.itemName || '#' + note.itemId }}</span>
          <span v-else class="note-link">📝 独立笔记</span>
          <span v-if="note.tags" class="note-tags">{{ note.tags }}</span>
          <span class="note-time">{{ formatTime(note.updateTime || note.createTime) }}</span>
        </div>
      </div>
    </div>

    <div v-if="hasMore" class="load-more-wrap">
      <el-button :loading="loadingMore" round @click="loadMore">加载更多</el-button>
    </div>
  </div>
</template>

<script setup lang="ts" name="FrontNote">
import { getToken } from '@/utils/auth'
import { Delete } from '@element-plus/icons-vue'
import TagPills from '@/components/TagPills/index.vue'
import { listFrontNote, delFrontNote, getFrontNote } from '@/api/front/note'
import { listNoteDrafts, getNoteDraftByNoteId } from '@/api/front/noteDraft'
import { getFrontItem } from '@/api/front/item'
import { splitByKeyword } from '@/utils/sgj'
import { ElMessageBox } from 'element-plus'
import useUserStore from '@/store/modules/user'
import { NOTE_CONTENT_MAX_LENGTH } from '@/utils/note'
import { NOTE_TAG_MAX_COUNT, parseNoteMarkdown, setPendingImport } from '@/utils/noteImport'
import { editorKeyOf, readNoteBuffer } from '@/utils/noteDraftBuffer'
import type { SgjNote } from '@/types/api/business/note'

const { proxy } = getCurrentInstance() as { proxy: any }
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 是否登录（访客只读，登录后可管理） */
const isLogin = computed(() => !!getToken())
/** 与编辑页 edit.vue 的口径保持一致：本地缓冲的 key 由它算出来，两边不一致就会查错槽位 */
const username = computed(() => userStore.name || getToken() || '')

const list = ref<SgjNote[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const loadError = ref(false)
const searchKeyword = ref('')
/** 已生效检索词：搜索成功返回后才更新；高亮跟随它而非输入框实时值，避免结果集与高亮错位 */
const appliedKeyword = ref('')
/** 标签筛选（TagPills 点击选中、再点取消后触发 loadData） */
const searchTag = ref('')
/** 分页 */
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const hasMore = computed(() => list.value.length < total.value)
/** B-03：按关联条目过滤展示（从条目编辑弹窗"查看关联笔记"跳转而来） */
const filterItemId = ref<number | undefined>(undefined)
/** 过滤条目的名称（展示用，取不到时回退 #id） */
const filterItemName = ref('')
/** 草稿份数（入口条用；未登录不发请求，失败也不提示） */
const draftCount = ref(0)

/**
 * 拉草稿份数：匿名访客不发（本页免登录，拉了会因失效 token 弹「登录状态已过期」）；
 * 请求带静默标记，失败只当作没有草稿，不弹任何提示
 */
function loadDraftCount(): void {
  if (!isLogin.value) {
    draftCount.value = 0
    return
  }
  listNoteDrafts(true).then(response => {
    draftCount.value = response.data?.length || 0
  }).catch(() => {
    draftCount.value = 0
  })
}

function openDrafts(): void {
  router.push('/note/draft')
}

/** 导入的文件选择器（真实的 input 藏在模板里，按钮只是它的代理） */
const importInputRef = ref<HTMLInputElement>()

function pickImportFile(): void {
  importInputRef.value?.click()
}

/**
 * 导入前置检查：**这次要落地的那个槽位**里是不是已经存着还没保存的内容（有就说明再导入会把它顶掉）。
 *
 * 两个槽位共用一套判据（设计 结论 42 只写了空白草稿那个，但同一失效模式在编辑态同样成立）：
 * - `entryNoteId` 缺省：新增态编辑页 = 空白草稿槽位（`note_id = 0`）；
 * - `entryNoteId` 给了：那篇笔记自己的编辑态草稿槽位。
 *
 * 两个来源都要看，缺一个就会漏：本地缓冲（停手 1s 就写，比服务端 15s 推得早）与服务端草稿
 * （换设备 / 清过缓存时本地没有，只有它）。判据取宽——只要有内容就当冲突：多问一次只是多一次点击，
 * 漏问一次就是用户的半篇笔记无声消失。
 */
async function unfinishedDraft(entryNoteId?: number): Promise<{ chars: number } | null> {
  const buffer = readNoteBuffer(username.value, editorKeyOf(entryNoteId))
  const local = `${buffer?.title || ''}${buffer?.content || ''}`.trim()
  if (local) return { chars: local.length }
  try {
    // 草稿箱列表不下发正文，用 title / excerpt 判断就够了（纯代码块的正文也会回退成原文开头）；
    // 按 noteId 取单条时是带正文的，两边都用 title + 正文类字段
    const draft = entryNoteId
      ? (await getNoteDraftByNoteId(entryNoteId)).data
      : ((await listNoteDrafts(true)).data || []).find(item => !item.noteId)
    const server = `${draft?.title || ''}${draft?.content || draft?.excerpt || ''}`.trim()
    if (server) return { chars: server.length }
  } catch {
    // 这一步只是「多问一句」，查不到就当作没有，不能因为它的失败阻断导入
  }
  return null
}

/**
 * 往返识别（设计 结论 34 / 39）：文件里带着 `noteId`，且它确实是**自己**的笔记时，问更新还是新建。
 *
 * 「是不是自己的」用返回的 create_by 严格比对，而不是「能不能看到」——否则管理员导入一份别人的导出文件
 * 也会被问「更新」，而按需求那种情况必须静默新建（不写他人数据）。
 * 不是自己的 / 已删除 / 查不到，一律返回 undefined（调用方按新建处理），不报错也不多弹一个框。
 *
 * @returns 要更新的笔记ID；按新建处理时返回 undefined
 */
async function askRoundTripTarget(noteId?: number): Promise<number | undefined> {
  if (!noteId) return undefined
  let own: SgjNote | null = null
  try {
    const note = (await getFrontNote(noteId)).data
    own = note && note.createBy === username.value ? note : null
  } catch {
    // 查不到（已删除）或无权访问：都按新建处理
    return undefined
  }
  if (!own?.noteId) return undefined
  try {
    await ElMessageBox.confirm(
      `这份文件里带着《${own.title || '无标题'}》的标记，而且那篇笔记是你自己的。要更新它，还是新建一篇？`,
      '这份文件来自一篇已有的笔记',
      { confirmButtonText: '更新那篇', cancelButtonText: '新建一篇', type: 'info' }
    )
    return own.noteId
  } catch {
    return undefined
  }
}

/**
 * 导入一个 md：浏览器本地读 → 解析 → 跳编辑页预填。
 *
 * 全程不上传（md 是纯文本，`File.text()` 直接读得到），原件也不留档（设计结论 37）。
 * 之所以跳编辑页而不是直接入库：标签 / 关联条目 / 公开状态能在保存前设，解析出错也不会在库里留垃圾笔记。
 */
async function onImportFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 先清空：不然连着导入同一个文件不会再触发 change
  input.value = ''
  if (!file) return

  // ponytail: 没有文件体积上限，先整体读入再按字数拦。几 GB 的 md 会把标签页卡住，真遇到再加 size 预检
  const result = parseNoteMarkdown(await file.text(), file.name)
  if (!result.ok) {
    if (result.code === 'not-md') {
      proxy.$modal.msgError('只支持导入 .md 文件')
    } else {
      proxy.$modal.msgError(`这篇共 ${result.length} 字，超过上限 ${NOTE_CONTENT_MAX_LENGTH} 字，无法导入`)
    }
    return
  }

  // 往返识别放在最前面：若是「更新已有笔记」，接下来要看的是那篇笔记自己的草稿，而不是空白草稿
  const targetNoteId = await askRoundTripTarget(result.prefill.noteId)

  const conflict = await unfinishedDraft(targetNoteId)
  if (conflict) {
    try {
      await ElMessageBox.confirm(
        `${targetNoteId ? '那篇笔记里' : '编辑页里'}有一份没写完的草稿（约 ${conflict.chars} 字），导入会覆盖它。`,
        '导入会覆盖草稿',
        { confirmButtonText: '继续导入', cancelButtonText: '先去看看', type: 'warning' }
      )
    } catch {
      // 「先去看看」：更新分支送去那篇笔记的编辑页（静默恢复未保存的草稿），新建分支送去草稿箱。
      // 两种都是取消这次导入
      router.push(targetNoteId ? { path: '/note/edit', query: { noteId: String(targetNoteId) } } : '/note/draft')
      return
    }
  }

  if (result.droppedTagCount > 0) {
    proxy.$modal.msgWarning(`标签最多带 ${NOTE_TAG_MAX_COUNT} 个，已忽略 ${result.droppedTagCount} 个`)
  }
  // 图片文件不在 md 里，本版也还没做搬运（#56）：不静默，明说这一版会失效
  if (result.localImageCount > 0) {
    proxy.$modal.msgWarning(`正文里有 ${result.localImageCount} 处图片引用；本版不搬图片，导入后那些图会失效`)
  }
  setPendingImport(result.prefill)
  // 带 noteId 进去 = 更新那篇（编辑页会把它当编辑态），不带 = 新建一篇
  router.push(targetNoteId ? { path: '/note/edit', query: { noteId: String(targetNoteId) } } : '/note/edit')
}

/** 组装列表查询参数；关键词不足 2 字时提示并中止（ngram_token_size=2，单字切不出 token 搜不到） */
function buildQuery() {
  const keyword = searchKeyword.value.trim()
  if (keyword && keyword.length < 2) {
    proxy.$modal.msgWarning('关键词请至少输入 2 个字')
    return null
  }
  return {
    keyword: keyword || undefined,
    tags: searchTag.value || undefined,
    itemId: filterItemId.value,
    pageNum: pageNum.value,
    pageSize: pageSize
  }
}

function loadData(): void {
  const query = buildQuery()
  if (!query) return
  loading.value = true
  loadError.value = false
  pageNum.value = 1
  listFrontNote(query).then(response => {
    list.value = response.data || []
    total.value = (response as any).total || 0
    // 结果落地后才更新高亮用的已生效检索词
    appliedKeyword.value = (query.keyword || '').trim()
  }).catch(() => {
    loadError.value = true
  }).finally(() => {
    loading.value = false
  })
}

/** 加载更多 */
function loadMore(): void {
  if (loadingMore.value || !hasMore.value) return
  pageNum.value += 1
  const query = buildQuery()
  if (!query) {
    pageNum.value -= 1
    return
  }
  loadingMore.value = true
  listFrontNote(query).then(response => {
    list.value = list.value.concat(response.data || [])
    total.value = (response as any).total || 0
  }).catch(() => {
    pageNum.value -= 1
  }).finally(() => {
    loadingMore.value = false
  })
}

/** 清除条目过滤，恢复全部笔记 */
function clearFilter(): void {
  filterItemId.value = undefined
  filterItemName.value = ''
  loadData()
}

/** 按条目过滤时回查条目名称用于展示 */
function fetchFilterItemName(itemId: number): void {
  getFrontItem(itemId).then(response => {
    filterItemName.value = response.data?.title || ''
  }).catch(() => {
    filterItemName.value = ''
  })
}

function formatTime(time?: string): string {
  if (!time) return ''
  return time.replace('T', ' ').slice(0, 16)
}

/** 摘要/标题按「已生效检索词」切片段（字面量切分，不含 HTML，模板循环渲染） */
function excerptSegments(note: SgjNote) {
  return splitByKeyword(note.excerpt, appliedKeyword.value)
}

function titleSegments(note: SgjNote) {
  return splitByKeyword(note.title, appliedKeyword.value)
}

/** 出现次数提示：统一「文中出现 N 次」；仅 1 次时不展示（无信息量） */
function hitCountText(hitTotal?: number): string {
  if (!hitTotal || hitTotal < 2) return ''
  return `文中出现 ${hitTotal} 次`
}

/** 渐隐仅在摘要真的溢出时显示（渲染与窗口尺寸变化后测量） */
const clippedNoteIds = ref<Set<number>>(new Set())
const listRef = ref<HTMLElement | null>(null)

function measureClip(): void {
  const next = new Set<number>()
  listRef.value?.querySelectorAll<HTMLElement>('.note-preview').forEach(el => {
    if (el.scrollHeight > el.clientHeight + 2 && el.dataset.noteId) {
      next.add(Number(el.dataset.noteId))
    }
  })
  clippedNoteIds.value = next
}

watch(list, () => nextTick(measureClip))
onMounted(() => window.addEventListener('resize', measureClip))
onBeforeUnmount(() => window.removeEventListener('resize', measureClip))

/** 跳转独立编辑页（新增/编辑共用，验收：单独的 markdown 编辑页面） */
function openAdd(): void {
  router.push('/note/edit')
}/**
 * B-03/：处理路由参数
 * - /note?itemId=x&write=1：条目页"去写笔记"跳转，转独立编辑页并预填关联条目
 * - /note?itemId=x：条目编辑弹窗"查看关联笔记"跳转，列表按该条目过滤展示
 * - /note?noteId=x：旧版详情链接（首页最近笔记曾用），重定向到独立详情页
 */
function handleRouteQuery(): void {
  const rawNoteId = route.query.noteId
  if (rawNoteId) {
    const noteId = Number(rawNoteId)
    router.replace(noteId ? { path: '/note/detail', query: { noteId: String(noteId) } } : { path: '/note', query: {} })
    return
  }
  const raw = route.query.itemId
  if (!raw) return
  const itemId = Number(raw)
  if (!itemId) return
  if (route.query.write === '1') {
    // 写笔记语义：仅登录用户跳转独立编辑页，预填关联条目
    if (isLogin.value) {
      router.replace({ path: '/note/edit', query: { itemId: String(itemId) } })
      return
    }
  } else {
    // 查看语义：按条目过滤笔记列表
    filterItemId.value = itemId
    fetchFilterItemName(itemId)
    loadData()
  }
  router.replace({ path: '/note', query: {} })
}

watch(
  () => route.query.itemId,
  () => handleRouteQuery()
)

/** 跳转独立详情页（验收：笔记内容单独页面展示，不再用抽屉）；带已生效检索词供详情页命中高亮 */
function openDetail(note: SgjNote): void {
  if (!note.noteId) return
  router.push({
    path: '/note/detail',
    query: { noteId: String(note.noteId), keyword: appliedKeyword.value || undefined }
  })
}

function handleDelete(note: SgjNote): void {
  if (!note.noteId) return
  proxy.$modal.confirm('是否确认删除该笔记？').then(() => {
    return delFrontNote(note.noteId!)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    loadData()
  }).catch(() => {})
}

loadData()
handleRouteQuery()
loadDraftCount()
</script>

<style scoped lang="scss">
.note-page {
  color: var(--sgj-text);
}

.load-more-wrap {
  display: flex;
  justify-content: center;
  margin: 20px 0;
}

.load-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 0;
  color: var(--sgj-text-4);
}

.page-banner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 56px;
  height: 176px;
  margin-bottom: 24px;
  border-radius: 24px;
  background: #282e2c;
  color: #e7ece9;
  overflow: hidden;

  .banner-blob {
    position: absolute;
    right: -40px;
    top: -120px;
    width: 300px;
    height: 300px;
    border-radius: 50%;
    background: rgba(168, 95, 82, 0.35);
    pointer-events: none;
  }

  .banner-blob2 {
    position: absolute;
    right: 140px;
    bottom: -90px;
    width: 200px;
    height: 200px;
    border-radius: 50%;
    background: rgba(93, 111, 102, 0.4);
    pointer-events: none;
  }

  .banner-text {
    position: relative;
    z-index: 1;

    h1 {
      margin: 0;
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 36px;
      line-height: 1.3;
    }

    p {
      margin: 12px 0 0;
      font-size: 14px;
      color: #aeb8b3;
      letter-spacing: 0.5px;
    }
  }

  .banner-add {
    position: relative;
    z-index: 1;
    height: 46px;
    padding: 0 28px;
    border-radius: 23px;
    font-size: 13px;
    font-weight: 500;
    letter-spacing: 1px;
  }

}

html.dark .page-banner {
  background: var(--sgj-bg-deep);
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 20px;

  .search-input {
    width: 260px;
  }

  .import-btn {
    flex: none;
    margin-left: 10px;
  }
}

/* 草稿箱入口条：与卡片同风格，整条可点，键盘可达 */
.draft-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--sgj-bg);
  border: 1px solid var(--sgj-border);
  border-radius: 12px;
  font: inherit;
  font-size: 13px;
  color: var(--sgj-text-2);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s, background 0.16s;

  &:hover {
    border-color: var(--sgj-primary);
    background: var(--sgj-bg-card);
  }

  &:focus-visible {
    outline: 2px solid var(--sgj-primary);
    outline-offset: 2px;
  }

  .draft-entry-icon {
    font-size: 15px;
  }

  .draft-entry-text {
    flex: 1;
    min-width: 0;
  }

  .draft-entry-arrow {
    color: var(--sgj-primary);
  }
}

.filter-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--sgj-bg);
  border: 1px solid var(--sgj-border);
  border-radius: 12px;
  padding: 8px 14px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--sgj-primary);
}

.note-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 200px;
}

.note-card {
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: 16px;
  padding: 18px;
  box-shadow: var(--sgj-shadow-sm);
  cursor: pointer;
  transition: box-shadow 0.16s, transform 0.16s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--sgj-shadow-hover);
  }

  .note-header {
    display: flex;
    align-items: flex-start;
    gap: 10px;

    .note-title {
      flex: 1;
      min-width: 0;
      font-family: var(--sgj-font-serif);
      font-weight: 500;
      font-size: 16px;
      line-height: 24px;
      color: var(--sgj-text);
      word-break: break-word;

      mark {
        background: var(--sgj-amber-soft);
        color: var(--sgj-amber);
        border-radius: 3px;
        padding: 0 2px;
        font-weight: 600;
      }

      .public-tag {
        margin-left: 8px;
        font-weight: 400;
      }
    }

    /* 删除角标：与标题首行垂直居中对齐，不随长标题换行 */
    .note-delete {
      flex-shrink: 0;
      margin-top: 0;
      border-color: var(--sgj-border-card);
      background: var(--sgj-bg);
      color: var(--sgj-text-3);

      &:hover {
        border-color: var(--sgj-danger);
        background: var(--sgj-bg-card);
        color: var(--sgj-danger);
      }
    }
  }

  .note-preview {
    margin-top: 8px;
    max-height: 96px;
    overflow: hidden;
    position: relative;

    /* 底部渐隐仅在内容真的溢出时出现（measureClip 测量后加 clip 类） */
    &.clip::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      height: 32px;
      background: linear-gradient(transparent, var(--sgj-bg-card));
      pointer-events: none;
    }

    .note-excerpt {
      margin: 0;
      font-size: 13px;
      color: var(--sgj-text-3);
      line-height: 1.6;
      word-break: break-word;

      mark {
        background: var(--sgj-amber-soft);
        color: var(--sgj-amber);
        border-radius: 3px;
        padding: 0 2px;
        font-weight: 600;
      }
    }
  }

  .hit-count {
    display: inline-flex;
    align-items: center;
    margin-top: 6px;
    font-size: 12px;
    color: var(--sgj-amber);
    font-variant-numeric: tabular-nums;
  }

  .note-meta {
    margin-top: 10px;
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    font-size: 12px;
    color: var(--sgj-text-4);

    .note-tags {
      color: var(--sgj-amber);
    }
  }
}

/* 移动端最小适配：搜索框占满整行、过滤提示与卡片标题行可换行 */
@media (max-width: 768px) {
  .filter-bar {
    justify-content: flex-start;
    flex-wrap: wrap;

    .search-input {
      width: 100% !important;
    }

    .import-btn {
      width: 100%;
      margin: 10px 0 0;
    }
  }

  .filter-tip {
    flex-wrap: wrap;
    gap: 6px;
  }
}
</style>