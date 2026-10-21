<template>
  <div class="search-page">
    <!-- 报头：与原型（mockups/link-0.0.5）同语言；输入即 URL（?keyword= 可直链/刷新/回退） -->
    <div class="search-hero">
      <div class="search-kicker">SEARCH / 全站检索</div>
      <h1 class="search-title">找一段记忆</h1>
      <p class="search-desc">条目按标题匹配，笔记按标题与正文匹配；至少输入 2 个字。</p>
      <div class="search-box">
        <el-input
          ref="inputRef"
          v-model="keyword"
          placeholder="搜电影、书、地点或笔记"
          clearable
          size="large"
          @keyup.enter="search(true)"
          @clear="search(true)"
        >
          <template #prefix><svg-icon icon-class="search" class="search-glyph" /></template>
        </el-input>
        <el-button type="primary" round size="large" :loading="loading" @click="search(true)">搜索</el-button>
      </div>
    </div>

    <!-- 状态区：可点建议词（未搜索或无结果时显示，帮用户起步/换词）；失败可重试 -->
    <div v-if="(!searched || !total) && !loading && !loadError" class="search-suggest">
      <span>试试：</span>
      <button v-for="word in SUGGESTIONS" :key="word" type="button" class="suggest-word" @click="applySuggestion(word)">
        {{ word }}
      </button>
      <span class="suggest-note">至少 2 个字；空组自动隐藏</span>
    </div>
    <div v-if="loadError" class="search-error">
      <span>搜索失败，请稍后重试</span>
      <el-button size="small" round @click="search()">重试</el-button>
    </div>

    <div v-loading="loading" class="search-body">
      <template v-if="searched && !loadError">
        <div v-if="total" class="search-meta">“{{ applied }}” · 共 {{ total }} 条结果</div>
        <el-empty v-else description="没有找到，换个关键词试试（至少 2 个字）" />

        <section v-for="group in groupViews" :key="group.key" class="s-group">
          <header class="s-head">
            <h2>{{ group.label }}</h2>
            <span class="s-count">
              共 {{ group.total }} 条<template v-if="group.total > group.rows.length">，仅展示前 {{ group.rows.length }} 条</template>
            </span>
          </header>
          <div class="s-rows">
            <button v-for="row in group.rows" :key="row.key" type="button" class="s-row" @click="openRow(row)">
              <!-- 封面位：条目用封面（失败回退类型衬线字），笔记用「记」字块 -->
              <span class="s-cover" :class="{ 'is-note': row.isNote }">
                <img
                  v-if="row.coverUrl && !coverFailed(row.key)"
                  :src="row.coverUrl"
                  :alt="row.title"
                  loading="lazy"
                  @error="onCoverError(row.key)"
                />
                <span v-else-if="row.isNote" class="s-glyph" aria-hidden="true">{{ row.glyph }}</span>
                <span v-else class="s-fb hd-fallback xs" :class="typeClass(row.itemType)" aria-hidden="true">
                  <span class="fc-motif"></span>
                </span>
              </span>
              <span class="s-main">
                <span class="s-title">
                  <template v-for="(seg, si) in row.titleSegs" :key="si">
                    <mark v-if="seg.hit">{{ seg.text }}</mark>
                    <template v-else>{{ seg.text }}</template>
                  </template>
                  <span v-if="row.typeText" class="s-type">{{ row.typeText }}</span>
                </span>
                <span v-if="row.excerptSegs" class="s-excerpt">
                  <template v-for="(seg, si) in row.excerptSegs" :key="si">
                    <mark v-if="seg.hit">{{ seg.text }}</mark>
                    <template v-else>{{ seg.text }}</template>
                  </template>
                </span>
                <span class="s-meta">
                  <span v-for="meta in row.metas" :key="meta" :class="{ 's-rating': meta.startsWith('★') }">{{ meta }}</span>
                </span>
              </span>
              <span class="s-arrow" aria-hidden="true">→</span>
            </button>
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts" name="FrontSearch">
import { searchFront } from '@/api/front/search'
import { SEARCH_GROUP_LABELS, type SearchGroup, type SearchRow } from '@/types/api/front/search'
import { splitByKeyword, photoUrl } from '@/utils/sgj'

const { proxy } = getCurrentInstance() as { proxy: any }
const route = useRoute()
const router = useRouter()
const inputRef = ref<{ focus?: () => void }>()

const keyword = ref('')
const applied = ref('')
const groups = ref<SearchGroup[]>([])
const loading = ref(false)
const loadError = ref(false)
const searched = ref(false)
const coverErrorKeys = ref(new Set<string>())

const total = computed(() => groups.value.reduce((sum, group) => sum + group.total, 0))

/** 建议词（原型同款）：点击即搜索——开发库真实存在的词，各覆盖一类命中 */
const SUGGESTIONS = ['三体', '时间', '番茄']

/** 类型徽标文案（影单组内电影/电视剧混排，靠它区分） */
const TYPE_TEXT: Record<string, string> = { MOVIE: '电影', TV: '电视剧', BOOK: '书籍', PLACE: '地点' }
const STATUS_TEXT: Record<string, Record<string, string>> = {
  MOVIE: { WANT: '想看', DOING: '在看', DONE: '看过' },
  TV: { WANT: '想看', DOING: '在看', DONE: '看过' },
  BOOK: { WANT: '想读', DOING: '在读', DONE: '已读' },
  PLACE: { WANT: '想去', DOING: '在做', DONE: '去过' }
}

/**
 * 行视图模型：把「条目 | 笔记」的联合在脚本里拍平，模板不做类型收窄
 * （SgjNote 字段全可选，结构上 SgjItem 可赋给它，模板里的 is 判型收窄会塌成 never）。
 */
interface RowView {
  key: string
  isNote: boolean
  noteId?: number
  itemType?: string
  itemId?: number
  title: string
  titleSegs: { text: string; hit: boolean }[]
  excerptSegs?: { text: string; hit: boolean }[]
  coverUrl?: string
  glyph: string
  typeText: string
  metas: string[]
}

const groupViews = computed(() =>
  groups.value.map(group => ({
    key: group.key,
    label: SEARCH_GROUP_LABELS[group.key] || group.key,
    total: group.total,
    rows: group.rows.map(row => buildRowView(row))
  }))
)

function buildRowView(row: SearchRow): RowView {
  const note = row as { noteId?: number; excerpt?: string; hitTotal?: number; updateTime?: string; createTime?: string }
  if (note.noteId != null) {
    const time = String(note.updateTime || note.createTime || '').slice(0, 10)
    return {
      key: `note-${note.noteId}`,
      isNote: true,
      noteId: note.noteId,
      title: row.title || '',
      titleSegs: splitByKeyword(row.title, applied.value),
      excerptSegs: note.excerpt ? splitByKeyword(note.excerpt, applied.value) : undefined,
      glyph: '记',
      typeText: '',
      metas: [note.hitTotal ? `文中 ${note.hitTotal} 处` : '', time].filter(Boolean)
    }
  }
  const item = row as {
    itemId?: number; itemType?: string; coverUrl?: string; rating?: number; status?: string
    releaseYear?: number; startYear?: number; author?: string; city?: string
  }
  const metas = [
    item.rating ? `★ ${item.rating}` : '',
    STATUS_TEXT[item.itemType || '']?.[item.status || ''] || '',
    String(item.releaseYear || item.startYear || item.author || item.city || '')
  ].filter(Boolean)
  return {
    key: `item-${item.itemId}`,
    isNote: false,
    itemId: item.itemId,
    itemType: item.itemType,
    title: row.title || '',
    titleSegs: splitByKeyword(row.title, applied.value),
    coverUrl: item.coverUrl ? photoUrl(item.coverUrl) : undefined,
    glyph: item.itemType === 'BOOK' ? '书' : item.itemType === 'PLACE' ? '地' : item.itemType === 'TV' ? '剧' : '影',
    typeText: TYPE_TEXT[item.itemType || ''] || '',
    metas
  }
}

/** 条目类型 → hd-fallback 形制类（与各页卡片同一套） */
function typeClass(type?: string): string {
  switch (type) {
    case 'MOVIE': return 'movie'
    case 'TV': return 'tv'
    case 'BOOK': return 'book'
    case 'PLACE': return 'place'
    default: return 'note'
  }
}

function coverFailed(key: string): boolean {
  return coverErrorKeys.value.has(key)
}

function onCoverError(key: string): void {
  coverErrorKeys.value.add(key)
}

/** 点建议词 = 填入输入框并按用户提交走（同步 URL，可回退） */
function applySuggestion(word: string): void {
  keyword.value = word
  search(true)
}

/**
 * 提交搜索；fromUser 表示用户主动提交（同步 URL，直链/刷新/回退都能还原）。
 * 关键词不足 2 字不发请求（ngram 按 2 字切词，单字搜不到；条目检索也统一 2 字门槛）。
 */
function search(fromUser = false): void {
  const query = keyword.value.trim()
  if (query && query.length < 2) {
    proxy.$modal.msgWarning('关键词请至少输入 2 个字')
    return
  }
  if (fromUser) {
    router.replace({ path: '/search', query: query ? { keyword: query } : {} })
  }
  if (!query) {
    groups.value = []
    applied.value = ''
    searched.value = false
    loadError.value = false
    return
  }
  void doSearch(query)
}

function doSearch(query: string): Promise<void> {
  loading.value = true
  loadError.value = false
  return searchFront(query).then(response => {
    groups.value = response.data?.groups || []
    applied.value = query
    searched.value = true
  }).catch(() => {
    loadError.value = true
    searched.value = true
  }).finally(() => {
    loading.value = false
  })
}

/**
 * 跳转矩阵（需求 §3.3.5）：条目到所属模块页带 ?itemId= 深链（影/剧补 type 预选胶囊），
 * 笔记带 keyword 进详情复用既有命中高亮。
 */
function openRow(row: RowView): void {
  if (row.isNote && row.noteId) {
    router.push({
      path: '/note/detail',
      query: { noteId: String(row.noteId), keyword: applied.value || undefined }
    })
    return
  }
  if (row.itemType === 'MOVIE' || row.itemType === 'TV') {
    router.push({ path: '/movie', query: { type: row.itemType, itemId: String(row.itemId) } })
  } else if (row.itemType === 'BOOK') {
    router.push({ path: '/book', query: { itemId: String(row.itemId) } })
  } else {
    router.push({ path: '/travel', query: { itemId: String(row.itemId) } })
  }
}

onMounted(() => {
  const initial = String(route.query.keyword || '').trim()
  if (initial) {
    keyword.value = initial
    void doSearch(initial)
  } else {
    inputRef.value?.focus?.()
  }
})

// 浏览器前进/后退带不同关键词时跟随（URL 是唯一状态源）
watch(() => route.query.keyword, value => {
  const next = String(value || '').trim()
  if (next === keyword.value.trim()) return
  keyword.value = next
  if (next) {
    void doSearch(next)
  } else {
    groups.value = []
    applied.value = ''
    searched.value = false
  }
})
</script>

<style scoped lang="scss">
/* 0.0.6 全站搜索：报头纸卡 + 建议词 + 四组结果（.xs 封面 + 高亮） */
.search-page { max-width: 980px; margin: 0 auto }

.search-hero {
  position: relative;
  padding: 30px 32px 28px;
  background-color: var(--hd-paper);
  border: 2.2px solid var(--hd-line);
  border-radius: 6px 3px 7px 4px / 4px 7px 3px 6px;
  box-shadow: var(--hd-shadow);
}
.search-hero::before {
  content: "";
  position: absolute;
  top: -10px;
  left: 46px;
  width: 88px;
  height: 22px;
  transform: rotate(-2.5deg);
  background: var(--hd-tape);
  pointer-events: none;
}
.search-kicker { font-size: 13px; letter-spacing: 4px; color: var(--hd-ink-faint) }
.search-title { margin-top: 6px; font-size: 40px; letter-spacing: 1.5px }
.search-desc { margin-top: 6px; font-size: 14.5px; color: var(--hd-ink-soft) }

.search-box { display: flex; gap: 10px; margin-top: 20px }
.search-box :deep(.el-input) { flex: 1 }
.search-box :deep(.el-input__wrapper) {
  background-color: var(--hd-paper-2);
  border: 1.8px solid var(--hd-line);
  border-radius: 6px 2px 7px 3px / 3px 7px 2px 6px;
  padding: 4px 12px;
}
.search-glyph { width: 17px; height: 17px; color: var(--hd-ink-faint) }

.search-suggest {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 18px;
  font-size: 13.5px;
  color: var(--hd-ink-soft);
}
.suggest-word {
  padding: 2px 12px;
  font-size: 13.5px;
  color: var(--hd-ink);
  background: var(--hd-paper);
  border: 1.5px solid var(--hd-line-soft);
  border-radius: 999px;
}
.suggest-word:hover { color: var(--hd-red); border-color: var(--hd-red) }
.suggest-note { color: var(--hd-ink-faint) }

.search-error {
  display: flex; align-items: center; gap: 12px; padding: 28px 0;
  font-size: 14px; color: var(--hd-red);
}

.search-meta { margin-top: 22px; font-size: 13.5px; color: var(--hd-ink-soft) }

.s-group { margin-top: 26px }
.s-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 2px dashed var(--hd-line-soft);
}
.s-head h2 { font-size: 21px; letter-spacing: 1px }
.s-count { font-size: 13px; color: var(--hd-ink-faint) }

.s-rows { display: flex; flex-direction: column }
.s-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 11px 4px;
  text-align: left;
  color: var(--hd-ink);
  background: none;
  border: 0;
  border-bottom: 1.5px dashed var(--hd-line-soft);
  cursor: pointer;
}
.s-row:hover { background: var(--hd-paper-2) }
.s-row:hover .s-title { color: var(--hd-red) }

.s-cover {
  position: relative;
  flex: none;
  width: 46px;
  height: 58px;
  overflow: hidden;
  border: 1.5px solid var(--hd-line-soft);
  border-radius: 3px 1px 4px 2px;
  background: var(--hd-paper-2);
  display: grid;
  place-items: center;
}
.s-cover img { width: 100%; height: 100%; object-fit: cover; display: block }
.s-cover.is-note { background: var(--hd-amber-soft) }
.s-glyph { font-size: 18px; color: var(--hd-ink-soft) }
.s-cover .s-fb { position: absolute; inset: 0 }

.s-main { flex: 1; min-width: 0 }
.s-title { display: block; font-size: 16.5px }
.s-type {
  margin-left: 8px;
  font-size: 13px;
  color: var(--hd-ink-soft);
  border: 1.4px solid var(--hd-line-soft);
  border-radius: 8px 2px 7px 2px / 2px 7px 2px 8px;
  padding: 0 7px;
}
.s-excerpt {
  display: -webkit-box;
  margin-top: 2px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--hd-ink-soft);
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.s-meta { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 2px; font-size: 13px; color: var(--hd-ink-faint) }
.s-rating { color: var(--hd-amber-ink) }
.s-arrow { flex: none; color: var(--hd-ink-faint); font-size: 17px }
.s-row:hover .s-arrow { color: var(--hd-red) }

mark { background: var(--hd-amber-soft); color: var(--hd-amber-ink); padding: 0 1px }

@media (max-width: 720px) {
  .search-hero { padding: 22px 18px }
  .search-title { font-size: 30px }
  .search-box { flex-direction: column }
}
</style>
