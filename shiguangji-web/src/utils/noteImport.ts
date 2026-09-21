/**
 * 笔记文件导入（issue #40）：读文件 → 解析 → 跳编辑页预填
 *
 * 全程在浏览器本地完成，**不上传**：md 是纯文本，`File.text()` 直接读得到；原件也不留档
 * （设计结论 37 已废弃「保存时上传原件」）。所以这个模块不碰网络、不碰路由、不碰 DOM，
 * 只做纯逻辑，便于单测。
 *
 * 规则来自需求 §3.3 FR-002-8：
 * - 标题：front-matter 的 `title` → 正文首个 H1 → 文件名去扩展名；
 * - front-matter 整块剥掉，但**不能把正文吃掉**（边界 2）；
 * - 标签归一化后原样带上（表内表外一视同仁，词表只管「选」与「筛」），最多 20 个（边界 10）；
 * - 超过 `NOTE_CONTENT_MAX_LENGTH` 在导入时就拦下（边界 6）。
 */
import { load as parseYaml } from 'js-yaml'
import { NOTE_CONTENT_MAX_LENGTH } from '@/utils/note'

/** 标签个数上限：Obsidian 一篇挂几十个标签并不稀奇，但 `sgj_note.tags` 只有 500 字符 */
export const NOTE_TAG_MAX_COUNT = 20
/** `sgj_note.tags` 的列宽（varchar(500)），超了会被截断或报错，这里先按字面量截 */
const TAGS_VALUE_MAX_LENGTH = 500

/** 解析出来、要预填进编辑页的内容 */
export interface NoteImportPrefill {
  title: string
  content: string
  tags: string
}

/** 解析失败的原因码（中文文案由调用方给，这里不拼提示，方便单测断言） */
export type NoteImportErrorCode = 'not-md' | 'too-long'

export type NoteImportResult =
  | { ok: true; prefill: NoteImportPrefill; droppedTagCount: number }
  | { ok: false; code: NoteImportErrorCode; length?: number }

interface FrontMatterSplit {
  /** front-matter 里解析出的键（不是 front-matter 时为空对象） */
  data: Record<string, unknown>
  /** 剥掉 front-matter 之后的正文 */
  body: string
}

/**
 * 剥掉 YAML front-matter。判据分两层，缺一层就会吃正文：
 *
 * 1. **结构**：文件第一行恰好是 `---`，且后面找得到闭合的 `---`。只有开头没有结尾时不当 front-matter
 *    ——残缺的 `---` 是正文的一部分。注意表格的分隔行 `|---|` 不满足 `trim() === '---'`，不会被误判成闭合行；
 * 2. **语义**：块内 YAML 必须解析成**映射**（`title:` / `tags:` 这种）。`---\n一段话\n---` 解析出的是标量，
 *    那其实是两条水平分隔线夹了一段正文，按结构判据会把它整块吞掉（FR-002-8 边界 2 明确要求别吃正文）。
 *    空块与解析失败的块都按 front-matter 处理：前者是空表头，后者的 `key:` 形状已足够说明意图，
 *    而且把 `title:` 这些行留在正文里还会进 ngram 索引变成垃圾 token。
 *
 * 行尾统一成 `\n`：Obsidian 在 Windows 上产出的 CRLF 会让上面所有比较落空。
 */
export function stripFrontMatter(text: string): FrontMatterSplit {
  const normalized = text.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n')
  const lines = normalized.split('\n')
  if ((lines[0] ?? '').trim() !== '---') return { data: {}, body: normalized }

  let end = -1
  for (let i = 1; i < lines.length; i++) {
    if (lines[i].trim() === '---') {
      end = i
      break
    }
  }
  if (end < 0) return { data: {}, body: normalized }

  const raw = lines.slice(1, end).join('\n')
  const body = lines.slice(end + 1).join('\n')
  if (!raw.trim()) return { data: {}, body }

  let parsed: unknown
  try {
    parsed = parseYaml(raw)
  } catch {
    // front-matter 里 YAML 写坏了（少个引号之类）：仍然剥掉，键当作没有。
    // 不能因为表头写坏就让整篇导入失败
    return { data: {}, body }
  }
  if (parsed === null || parsed === undefined) return { data: {}, body }
  if (typeof parsed !== 'object' || Array.isArray(parsed)) {
    // 不是映射 → 那两块 `---` 只是水平分隔线，正文原样保留
    return { data: {}, body: normalized }
  }
  return { data: parsed as Record<string, unknown>, body }
}

/**
 * front-matter 的 `tags` 归一化。三种写法都要认：
 * 逗号串 `tags: a, b`、内联数组 `tags: [a, b]`、块数组 `tags:` + `- a`
 * （js-yaml 已把后两种变成数组，这里只负责清洗与去重）
 */
export function normalizeTags(value: unknown): string[] {
  let raw: unknown[]
  if (Array.isArray(value)) {
    raw = value
  } else if (value === null || value === undefined || typeof value === 'object') {
    raw = []
  } else {
    // 标量：`tags: a, b` 是逗号串，`tags: 2024` 会被 YAML 解析成数字
    raw = String(value).split(',')
  }

  const seen = new Set<string>()
  for (const item of raw) {
    if (item === null || item === undefined || typeof item === 'object') continue
    // Obsidian 的标签常带前导 `#`，那只是标记，不是名字的一部分
    const name = String(item).trim().replace(/^#+/, '').trim()
    if (name) seen.add(name)
  }
  return [...seen]
}

/** 按 `sgj_note.tags` 的列宽硬截断：装不下就停，不把一个标签切成两半 */
function joinTagsWithinLimit(tags: string[]): string {
  let out = ''
  for (const tag of tags) {
    const next = out ? `${out},${tag}` : tag
    if (next.length > TAGS_VALUE_MAX_LENGTH) break
    out = next
  }
  return out
}

/** 正文首个 H1 的文本（顺便去掉 ATX 标题结尾那串装饰性 `#`） */
function firstH1(body: string): string {
  const matched = /^#[ \t]+(.+?)[ \t]*#*[ \t]*$/m.exec(body)
  return matched ? matched[1].trim() : ''
}

/** 把 front-matter 的某个标量键读成字符串（`title: 2024` 会被 YAML 解析成数字） */
function scalarText(value: unknown): string {
  if (value === null || value === undefined || typeof value === 'object') return ''
  return String(value).trim()
}

/**
 * 解析一个 md 文件的内容。
 *
 * @param text     文件全文（调用方用 `File.text()` 读出来）
 * @param fileName 文件名，用于扩展名校验与标题兜底
 */
export function parseNoteMarkdown(text: string, fileName: string): NoteImportResult {
  if (!/\.md$/i.test(fileName)) return { ok: false, code: 'not-md' }

  const { data, body } = stripFrontMatter(text)
  const content = body.trim()
  if (content.length > NOTE_CONTENT_MAX_LENGTH) {
    return { ok: false, code: 'too-long', length: content.length }
  }

  const title = scalarText(data.title) || firstH1(content) || fileName.replace(/\.md$/i, '').trim()
  const allTags = normalizeTags(data.tags)
  const kept = allTags.slice(0, NOTE_TAG_MAX_COUNT)

  return {
    ok: true,
    prefill: { title, content, tags: joinTagsWithinLimit(kept) },
    droppedTagCount: allTags.length - kept.length
  }
}

/**
 * 一次性预填交接：列表页解析完放在这里，编辑页进来取走。
 *
 * 正文可能有 10 万字，塞不进 URL，所以用模块级变量交接。**取走即清空**，
 * 否则下次进编辑页会把上一次导入的内容又套一遍。
 * 刷新页面会丢掉它——此时内容有两条后路：编辑页一进去就推一次服务端草稿、并同步写本地缓冲。
 */
let pendingPrefill: NoteImportPrefill | null = null

export function setPendingImport(prefill: NoteImportPrefill): void {
  pendingPrefill = prefill
}

/** 只返回一次（编辑页初始化时调用） */
export function takePendingImport(): NoteImportPrefill | null {
  const prefill = pendingPrefill
  pendingPrefill = null
  return prefill
}
