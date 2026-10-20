<template>
  <div class="note-page page-notes">
    <section class="hd-banner anim">
      <span class="hd-tape"></span>
      <div>
        <div class="eyebrow">写下即记得</div>
        <h1>笔记</h1>
        <p class="desc">记录你的思考与学习笔记。</p>
      </div>
      <div v-if="isLogin" class="acts">
        <button class="hd-btn" type="button" @click="pickImportFile">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M12 17 L12 5 M7 9 L12 4 L17 9 M5 20 L19 20"/></svg>
          导入 md
        </button>
        <button class="hd-btn go" type="button" @click="openAdd">＋ 写笔记</button>
      </div>
    </section>

    <button v-if="draftCount > 0" class="draft-strip" type="button" @click="openDrafts">
      <svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M4 7 L20 7 L20 20 L4 20 Z"/><path d="M7 7 L7 4 L17 4 L17 7"/><path d="M9 12 L15 12 M9 16 L13 16"/></svg>
      <span class="txt">草稿箱里有 <b>{{ draftCount }}</b> 份没写完的笔记</span>
      <span class="arr" aria-hidden="true">→</span>
    </button>

    <div class="tools">
      <div class="hd-search hd-input-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 Q18 18 21 21"/></svg>
        <input
          v-model="searchKeyword"
          class="hd-input"
          type="search"
          placeholder="搜索标题与正文"
          aria-label="搜索标题与正文"
          @keyup.enter="loadData"
        />
      </div>
      <div class="hd-summary">
        <span class="item">共 <b>{{ total }}</b> 篇</span>
        <span class="sep" aria-hidden="true">·</span>
        <span class="item">已展示 <b>{{ list.length }}</b></span>
        <span v-if="list.length" class="recent">最近 <b>{{ list[0]?.title }}</b></span>
      </div>
    </div>

    <input ref="importInputRef" type="file" accept=".md" hidden @change="onImportFileChange" />
    <note-image-carry-dialog ref="carryRef" />

    <tag-pills v-model="searchTag" module="NOTE" class="tag-row" @update:model-value="loadData" />

    <div v-if="filterItemId" class="filter-tip">
      <span>正在查看条目「{{ filterItemName || '#' + filterItemId }}」的关联笔记</span>
      <button class="hd-btn sm" type="button" @click="clearFilter">查看全部笔记</button>
    </div>

    <div v-loading="loading" class="hd-grid note-grid">
      <div v-if="!loading && !list.length && !loadError" class="hd-empty">
        <p>还没有笔记 —— 看见什么，随手记一句。</p>
        <div class="acts">
          <button v-if="isLogin" class="hd-btn go" type="button" @click="openAdd">＋ 写笔记</button>
        </div>
      </div>
      <div v-if="loadError" class="load-error">
        <span>加载失败，请稍后重试</span>
        <button class="hd-btn sm" type="button" @click="loadData">重试</button>
      </div>

      <article v-for="note in list" :key="note.noteId" class="hd-card note-card" @click="openDetail(note)">
        <div class="note-sheet hd-grain hd-rough">
          <span class="hd-tape" aria-hidden="true"></span>
          <span v-if="note.hitTotal" class="nt-hit">{{ hitCountText(note.hitTotal) }}</span>
          <h3 class="nt-title">
            <template v-for="(seg, si) in titleSegments(note)" :key="si">
              <mark v-if="seg.hit">{{ seg.text }}</mark>
              <template v-else>{{ seg.text }}</template>
            </template>
            <span v-if="isLogin && note.isPublic === '1'" class="hd-stamp">公开</span>
          </h3>
          <p v-if="note.excerpt" class="nt-excerpt">
            <template v-for="(seg, si) in excerptSegments(note)" :key="si">
              <mark v-if="seg.hit">{{ seg.text }}</mark>
              <template v-else>{{ seg.text }}</template>
            </template>
          </p>
          <div class="nt-meta">
            <span v-if="note.itemId" class="item">条目：{{ note.itemName || '#' + note.itemId }}</span>
            <span v-else class="item">独立笔记</span>
            <span v-if="note.tags" class="hd-tag plain">{{ note.tags }}</span>
            <span class="time">{{ formatTime(note.updateTime || note.createTime) }}</span>
          </div>
          <div v-if="isLogin" class="nt-acts" @click.stop>
            <button class="hd-btn sm sub" type="button" title="删除" @click="handleDelete(note)">删除</button>
          </div>
        </div>
      </article>
    </div>

    <div v-if="hasMore" class="load-more">
      <button class="hd-btn" type="button" :disabled="loadingMore" @click="loadMore">{{ loadingMore ? '加载中…' : '加载更多 ↓' }}</button>
      <span class="hd-tiny hd-muted">已展示 {{ list.length }} / 共 {{ total }} 篇</span>
    </div>
    <div v-else-if="list.length" class="grid-end">—— 已到底部 · 共 {{ total }} 篇 ——</div>
  </div>
</template>

<script setup lang="ts" name="FrontNote">
import { getToken } from '@/utils/auth'
import { Delete } from '@element-plus/icons-vue'
import TagPills from '@/components/TagPills/index.vue'
import { listFrontNote, delFrontNote } from '@/api/front/note'
import { countNoteDrafts } from '@/api/front/noteDraft'
import { getFrontItem } from '@/api/front/item'
import { splitByKeyword } from '@/utils/sgj'
import { ElMessageBox } from 'element-plus'
import useUserStore from '@/store/modules/user'
import { NOTE_CONTENT_MAX_LENGTH } from '@/utils/note'
import { NOTE_TAG_MAX_COUNT, parseNoteMarkdown, setPendingImport } from '@/utils/noteImport'
import NoteImageCarryDialog from '@/components/NoteImageCarryDialog/index.vue'
import { findDraftConflict, findRoundTripTarget } from '@/utils/noteImportGuards'
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
 * 请求带静默标记，失败只当作没有草稿，不弹任何提示。
 * 只订阅份数接口，不为这一行文案把草稿箱列表（含每份草稿正文）拉下来
 */
function loadDraftCount(): void {
  if (!isLogin.value) {
    draftCount.value = 0
    return
  }
  countNoteDrafts(true).then(response => {
    draftCount.value = Number(response.data) || 0
  }).catch(() => {
    draftCount.value = 0
  })
}

function openDrafts(): void {
  router.push('/note/draft')
}

/** 导入的文件选择器（真实的 input 藏在模板里，按钮只是它的代理） */
const importInputRef = ref<HTMLInputElement>()

/** 图片搬运弹窗（#56）：open() 返回 Promise，导入流程得以保持线性 */
const carryRef = ref<InstanceType<typeof NoteImageCarryDialog>>()

function pickImportFile(): void {
  importInputRef.value?.click()
}

/**
 * 往返识别（设计 结论 34 / 39）：文件里带着 `noteId`，且它确实是**自己**的笔记时，问更新还是新建。
 *
 * 命中判定（含「管理员导入别人的文件也要静默新建」那条）在 `utils/noteImportGuards`——
 * 后台导入入口问的是同一个问题，答案必须一致。这里只管问法与跳转。
 *
 * @returns 要更新的笔记ID；按新建处理时返回 undefined
 */
async function askRoundTripTarget(noteId?: number): Promise<number | undefined> {
  const own = await findRoundTripTarget(noteId, username.value)
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

  const conflict = await findDraftConflict(username.value, targetNoteId)
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
  // 图片搬运（#56 / #68）：正文有本地图片引用**或**外链图片引用时出现；跳过或未命中的引用原样保留并提示。
  // 跳过时不弹「已搬运 N 张」这类计数，所以只报本地引用的处数（外链不转存也能显示，不告警）
  if (result.localImageRefs.length > 0 || result.externalImageRefs.length > 0) {
    const carried = await carryRef.value?.open(
      result.prefill.content,
      result.localImageRefs,
      result.externalImageRefs,
      file.name
    )
    if (carried) {
      result.prefill.content = carried.content
    } else if (result.localImageCount > 0) {
      proxy.$modal.msgWarning(`正文里有 ${result.localImageCount} 处图片引用未搬运，导入后那些图会显示不出来`)
    }
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

/** 出现次数提示：统一「文中出现 N 次」（1 次也显示；数字为后端统计的真·出现次数） */
function hitCountText(hitTotal?: number): string {
  if (!hitTotal) return ''
  return `文中出现 ${hitTotal} 次`
}

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
/* 0.0.6 笔记列表：样式取自设计图 notes.html（.page-notes 作用域） */

/* ===== 笔记列表 ===== */
.page-notes mark {
  color: inherit; padding: 0 1px;
  background: linear-gradient(transparent 62%, var(--hd-amber-soft) 62%, var(--hd-amber-soft) 92%, transparent 92%);
}
.page-notes .hd-banner { margin-top: 4px }
.page-notes .hd-banner .hd-tape { top: -11px; left: 44px; width: 96px; transform: rotate(-3deg) }
.page-notes .hd-banner .acts .hd-btn svg { width: 17px; height: 17px }

/* 草稿条：整宽暖色便签，与 banner 拉开一层 */
.page-notes .draft-strip {
  position: relative; display: flex; align-items: center; gap: 12px;
  margin-top: 24px; padding: 11px 18px;
  background-color: var(--hd-paper-3); background-image: var(--hd-grain);
  border-radius: 3px; box-shadow: var(--hd-shadow);
  color: var(--hd-ink); text-decoration: none; transform: rotate(-.35deg);
}
.page-notes .draft-strip .hd-tape {
  top: -9px; left: 34px; width: 70px; height: 20px; transform: rotate(-2.5deg);
}
.page-notes .draft-strip .ico { flex: none; width: 22px; height: 22px; color: var(--hd-amber-ink) }
.page-notes .draft-strip .txt { font-size: 15.5px; line-height: 1.6 }
.page-notes .draft-strip .txt b { font-weight: 400; color: var(--hd-red) }
.page-notes .draft-strip .arr { margin-left: auto; padding-left: 10px; color: var(--hd-red); font-size: 17px }

/* 工具条 */
.page-notes .tools { margin-top: 24px; display: flex; align-items: center; gap: 12px 16px; flex-wrap: wrap }
.page-notes .tools .hd-search { flex: 1 1 220px; max-width: 330px }
.page-notes .tools .hd-search svg {
  position: absolute; left: 11px; top: 50%; transform: translateY(-50%);
  width: 17px; height: 17px; color: var(--hd-ink-faint); pointer-events: none;
}
.page-notes .tools .hd-search .hd-input {
  padding: 4px 10px 4px 34px; font-size: 15px; line-height: 1.6;
}
.page-notes .tools .hd-chips { gap: 8px }

/* 卡片 */
.page-notes .hd-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 34px 26px; margin-top: 26px }
.page-notes .note-card { text-decoration: none; color: var(--hd-ink) }
.page-notes .note-sheet {
  position: relative; height: 100%; display: flex; flex-direction: column; gap: 7px;
  background-color: var(--hd-paper); padding: 20px 20px 15px;
  border-radius: 6px; box-shadow: var(--hd-shadow);
  transition: transform .12s, box-shadow .12s;
}
.page-notes .note-card:hover .note-sheet { transform: translateY(-2px); box-shadow: var(--hd-shadow-lift) }
.page-notes .note-sheet .hd-tape { top: -9px; width: 84px; height: 20px }
.page-notes .nt-hit {
  position: absolute; top: 16px; right: 17px;
  font-size: 13px; letter-spacing: .5px; color: var(--hd-ink-faint);
}
.page-notes .nt-title { font-size: 18.5px; line-height: 1.45; padding-right: 74px }
.page-notes .nt-title .hd-stamp { margin-left: 5px; vertical-align: 3px }
.page-notes .nt-excerpt {
  font-size: 14.5px; line-height: 1.75; color: var(--hd-ink-soft);
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.page-notes .nt-meta {
  margin-top: auto; padding-top: 10px;
  display: flex; align-items: center; gap: 5px 8px; flex-wrap: wrap;
  font-size: 13.5px; color: var(--hd-ink-soft);
}
.page-notes .nt-meta .item { display: inline-flex; align-items: center; gap: 5px; min-width: 0 }
.page-notes .nt-meta .item svg { flex: none; width: 14px; height: 14px; color: var(--hd-amber-ink) }
.page-notes .nt-meta .time { margin-left: auto; font-size: 13px; color: var(--hd-ink-faint); white-space: nowrap }

.page-notes .load-more { margin-top: 40px; display: flex; flex-direction: column; align-items: center; gap: 8px }

@media (max-width: 960px) {
  .page-notes .hd-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 30px 20px }
  .page-notes .nt-title { padding-right: 62px }
}
@media (max-width: 720px) {
  .page-notes .hd-banner { padding: 20px 18px }
  .page-notes .hd-banner h1 { font-size: 31px }
  .page-notes .draft-strip { margin-top: 24px; padding: 10px 14px; transform: none }
  .page-notes .draft-strip .txt { font-size: 14.5px }
  .page-notes .hd-summary .sep:nth-last-child(2) { display: none }
  .page-notes .tools { margin-top: 20px }
  .page-notes .tools .hd-search { flex-basis: 100%; max-width: none }
  .page-notes .hd-grid { grid-template-columns: minmax(0, 1fr); gap: 26px }
  .page-notes .nt-title { font-size: 18px }
  .page-notes .load-more { margin-top: 32px }
}

/* 产品补充：工具条、标签行、过滤提示、加载/空态、卡片删除按钮 */
.page-notes .tools .hd-summary { margin: 0; padding: 0; border: 0; background: none; box-shadow: none }
.page-notes .tag-row { margin-top: 16px }
.page-notes .filter-tip {
  display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
  margin-top: 16px; padding: 9px 16px; font-size: 13.5px; color: var(--hd-ink-soft);
  background: var(--hd-paper-2); border: 1.6px dashed var(--hd-line-soft);
  border-radius: 4px 2px 5px 3px / 3px 5px 2px 4px;
}
.page-notes .filter-tip .hd-btn { padding: 1px 9px; font-size: 13px }
.page-notes .note-sheet { cursor: pointer; position: relative }
.page-notes .nt-acts { position: absolute; right: 14px; bottom: 12px; opacity: 0; transition: opacity .15s }
.page-notes .note-card:hover .nt-acts, .page-notes .note-card:focus-within .nt-acts { opacity: 1 }
.page-notes .grid-end { margin-top: 34px; text-align: center; font-size: 13.5px; color: var(--hd-ink-soft) }
.page-notes .load-error { grid-column: 1 / -1; display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 48px 0; color: var(--hd-ink-soft) }
.page-notes .hd-empty { grid-column: 1 / -1 }

</style>
