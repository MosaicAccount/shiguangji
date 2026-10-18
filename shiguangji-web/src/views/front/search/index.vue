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

    <!-- 状态区：未输入 / 加载失败；无结果在分组区统一交代 -->
    <div v-if="!searched && !loading && !loadError" class="search-tip">
      试试「三体」「时间」「番茄」——条目和笔记会分组返回，空组自动隐藏。
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
                <span v-else class="s-glyph" aria-hidden="true">{{ row.glyph }}</span>
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

function coverFailed(key: string): boolean {
  return coverErrorKeys.value.has(key)
}

function onCoverError(key: string): void {
  coverErrorKeys.value.add(key)
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
.search-page {
  max-width: 860px;
  margin: 0 auto;
}

.search-hero {
  position: relative;
  overflow: hidden;
  padding: 28px 32px 26px;
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: var(--sgj-radius-xl);

  &::after {
    content: '';
    position: absolute;
    right: -90px;
    top: -130px;
    width: 320px;
    height: 320px;
    border-radius: 50%;
    background: var(--sgj-moss-soft);
    opacity: 0.8;
  }
}

.search-kicker {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.2em;
  color: var(--sgj-moss);
}

.search-title {
  margin: 8px 0 6px;
  font-family: var(--sgj-font-serif);
  font-size: 30px;
  line-height: 1.3;
  color: var(--sgj-text);
}

.search-desc {
  position: relative;
  z-index: 1;
  margin: 0;
  font-size: 13px;
  color: var(--sgj-text-3);
}

.search-box {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 10px;
  max-width: 660px;
  margin-top: 18px;

  .search-glyph {
    color: var(--sgj-text-4);
  }
}

.search-tip {
  margin-top: 18px;
  padding: 14px 18px;
  border: 1px dashed var(--sgj-border);
  border-radius: var(--sgj-radius-md);
  font-size: 13px;
  color: var(--sgj-text-3);
}

.search-error {
  margin-top: 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border: 1px solid var(--sgj-border);
  border-radius: var(--sgj-radius-md);
  font-size: 13px;
  color: var(--sgj-danger);
}

.search-body {
  margin-top: 18px;
  min-height: 120px;
}

.search-meta {
  margin: 0 2px 12px;
  font-size: 13px;
  color: var(--sgj-text-3);
}

.s-group {
  margin-bottom: 16px;
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: var(--sgj-radius-lg);
  overflow: hidden;
}

.s-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--sgj-border-card);

  h2 {
    margin: 0;
    font-family: var(--sgj-font-serif);
    font-size: 17px;
    color: var(--sgj-text);
  }

  .s-count {
    font-size: 11.5px;
    color: var(--sgj-text-4);
  }
}

.s-row {
  display: flex;
  align-items: center;
  gap: 13px;
  width: 100%;
  padding: 13px 20px;
  text-align: left;
  background: transparent;
  border: 0;
  border-top: 1px solid var(--sgj-border-card);
  cursor: pointer;
  transition: background 0.14s ease;

  &:hover,
  &:focus-visible {
    background: var(--sgj-bg);
  }
}

.s-cover {
  width: 44px;
  height: 56px;
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(155deg, #6d7f78, #3c4a45);
  color: #f4f6f5;

  &.is-note {
    height: 44px;
    border-radius: 11px;
    background: var(--sgj-primary-soft);
    color: var(--sgj-primary);
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .s-glyph {
    font-family: var(--sgj-font-serif);
    font-size: 18px;
    font-weight: 700;
  }
}

.s-main {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 3px;
}

.s-title {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--sgj-text);
  line-height: 1.4;

  .s-type {
    margin-left: 8px;
    padding: 1px 6px;
    border: 1px solid var(--sgj-border);
    border-radius: 4px;
    font-size: 10.5px;
    font-weight: 400;
    color: var(--sgj-text-3);
    vertical-align: 1px;
  }
}

.s-excerpt {
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--sgj-text-3);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.s-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 11.5px;
  color: var(--sgj-text-4);

  .s-rating {
    color: var(--sgj-amber);
  }
}

.s-arrow {
  flex-shrink: 0;
  color: var(--sgj-text-4);
  font-size: 14px;
}

mark {
  background: var(--sgj-amber-soft);
  color: var(--sgj-amber);
  border-radius: 3px;
  padding: 0 2px;
  font-weight: 600;
}

@media (max-width: 768px) {
  .search-hero {
    padding: 20px 18px;
    border-radius: var(--sgj-radius-lg);
  }

  .search-title {
    font-size: 24px;
  }

  .s-head {
    padding: 12px 14px;
  }

  .s-row {
    padding: 12px 14px;
    gap: 10px;
  }

  .s-cover {
    width: 38px;
    height: 48px;
    border-radius: 7px;
  }
}
</style>
