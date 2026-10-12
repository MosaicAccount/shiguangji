/**
 * 带图笔记的图片搬运（0.0.2 FR-002-9，issue #56）：收集 / 匹配 / 改写三个纯函数。
 *
 * 本模块刻意不含网络与 DOM（与 {@link './noteImport'} 同一约定），全部行为可以在单测里钉死；
 * 上传与界面在 NoteImageCarryDialog / MarkdownEditor 里。设计见 docs/design/note-images-design.md §3.2。
 */
import { NOTE_CONTENT_MAX_LENGTH } from '@/utils/note'

/** 单次搬运的文件数上限（超出部分忽略并提示，见设计 §3.2 上限） */
export const NOTE_IMAGE_MAX_COUNT = 50

/** 单张体积上限（与 spring.servlet.multipart.max-file-size 对齐） */
export const NOTE_IMAGE_MAX_SIZE = 10 * 1024 * 1024

/** 收的图片扩展名（与后端 MimeTypeUtils.NOTE_IMAGE_EXTENSION 一致，改一侧必须同步另一侧） */
export const NOTE_IMAGE_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp']

/** 外链（含协议相对 //）；data: 内嵌既不算本地也不算外链——它随正文显示，不会失效 */
const EXTERNAL_PATTERN = /^(https?:)?\/\//i
const DATA_PATTERN = /^data:/i
/** 已经搬过的站内路径：不再算本地引用，这是「导出再导入」能无损往返的前提（结论 47 / 62） */
const PROFILE_PATTERN = /^\/profile\//i

const WIKILINK_PATTERN = /!\[\[([^\]\n]+)\]\]/g
const MARKDOWN_IMAGE_PATTERN = /!\[([^\]\n]*)\]\(([^)\n]*)\)/g

/** 正文里的一条本地图片引用 */
export interface NoteImageRef {
  /** 原始引用文本（逐条回显与按位置替换都靠它） */
  raw: string
  /** 引用指向的目标（已去掉 title / |alias / #heading / <> 包裹，并做了 %xx 解码） */
  target: string
  /** 语法形态：标准 markdown 或 Obsidian 短引用（后者站点不渲染，搬运时必须转成 markdown） */
  kind: 'markdown' | 'wikilink'
  /** raw 在正文中的起始位置（改写按它替换） */
  index: number
  /** 改写后的替代文本用它做 alt（wikilink 用目标文件名兜底） */
  alt: string
}

/** 一次收集的结果：本地引用逐条列出，外链只计数（提示语用，见结论 62） */
export interface ImageRefCollection {
  local: NoteImageRef[]
  externalCount: number
}

/** 用户选出来的候选图片文件 */
export interface ImageCandidate {
  file: File
  /** File.name；目录选择时就是 basename */
  name: string
  /** File.webkitRelativePath（多选文件时为空串） */
  relPath: string
}

/** 一条引用与它匹配到的候选文件 */
export interface NoteImageMatch {
  ref: NoteImageRef
  candidate: ImageCandidate
}

/** 改写结果：超长信号由调用方提示且照常预填（结论 59），不静默回退 */
export interface RewriteResult {
  content: string
  length: number
  tooLong: boolean
}

/**
 * 收集正文里的图片引用（收集范围与排除项见设计 结论 50 / 62）
 *
 * 围栏代码块与行内代码里的引用不收集——那是示例代码，改写它会毁掉示例。
 * 实现是逐行扫描 + 行内代码「等长空格掩码」：掩码不动索引，改写才能按位置替换。
 */
export function collectImageRefs(content: string): ImageRefCollection {
  const local: NoteImageRef[] = []
  let externalCount = 0
  let fence: { marker: string } | null = null
  let offset = 0

  for (const line of content.split('\n')) {
    const fenceMark = /^ {0,3}(`{3,}|~{3,})/.exec(line)
    if (fence) {
      // 围栏内：只有同标记且不短于开栏长度的那一行才关栏（CommonMark 口径的简化版）
      if (fenceMark && fenceMark[1][0] === fence.marker[0] && fenceMark[1].length >= fence.marker.length) {
        fence = null
      }
    } else if (fenceMark) {
      fence = { marker: fenceMark[1] }
    } else {
      collectInLine(maskInlineCode(line), line, offset, local, refs => (externalCount += refs))
    }
    offset += line.length + 1
  }
  return { local, externalCount }
}

/** 在一行里找两种语法的引用；masked 用于匹配（代码段被掩掉），原文用于取值（长度一致，索引不变） */
function collectInLine(
  masked: string,
  original: string,
  offset: number,
  local: NoteImageRef[],
  countExternal: (n: number) => void
): void {
  for (const pattern of [MARKDOWN_IMAGE_PATTERN, WIKILINK_PATTERN]) {
    pattern.lastIndex = 0
    let hit: RegExpExecArray | null
    while ((hit = pattern.exec(masked)) !== null) {
      const isMarkdown = pattern === MARKDOWN_IMAGE_PATTERN
      const inner = isMarkdown ? parseMarkdownDestination(hit[2]) : parseWikilinkTarget(hit[1])
      const target = decodeTarget(inner.replace(/^\.\//, ''))
      if (!target) continue

      if (EXTERNAL_PATTERN.test(target)) {
        countExternal(1)
      } else if (!DATA_PATTERN.test(target) && !PROFILE_PATTERN.test(target)) {
        const index = offset + hit.index
        local.push({
          raw: original.slice(hit.index, hit.index + hit[0].length),
          target,
          kind: isMarkdown ? 'markdown' : 'wikilink',
          index,
          alt: isMarkdown ? hit[1] : basename(target)
        })
      }
    }
  }
}

/** 行内代码等长掩码：`` `code` `` 变成空格，索引保持不变 */
function maskInlineCode(line: string): string {
  return line.replace(/(`+)([\s\S]*?)\1/g, match => ' '.repeat(match.length))
}

/** `![[a.png|200]]` / `![[a.png#^block]]` → `a.png`（尺寸与块引用跟搬运无关） */
function parseWikilinkTarget(inner: string): string {
  return inner.split('|')[0].split('#')[0].trim()
}

/** `path "title"` / `<a b.png>` → `path`（带空格的路径按 markdown 规范必须包在 <> 里） */
function parseMarkdownDestination(inner: string): string {
  let s = inner.trim()
  if (s.startsWith('<')) {
    const close = s.indexOf('>')
    s = close > 0 ? s.slice(1, close) : s.slice(1)
  } else {
    s = s.split(/\s+/)[0] || ''
  }
  return s
}

/** `%E5%9B%BE` 解码后再匹配；残缺的编码按原样比对（catch 后原样返回） */
function decodeTarget(target: string): string {
  if (!target.includes('%')) return target
  try {
    return decodeURIComponent(target)
  } catch {
    return target
  }
}

function basename(path: string): string {
  const seg = path.split('/').filter(Boolean).pop() || path
  return seg
}

// ------------------------------------------------------------------
// 匹配
// ------------------------------------------------------------------

export interface ResolveOutcome {
  matched: NoteImageMatch[]
  unresolved: NoteImageRef[]
}

/**
 * 给每条引用找它指向的候选文件，四级优先、歧义不猜（设计 结论 50）：
 * ① md 同目录的精确相对路径（md 在候选树里按文件名定位，拿到它的目录）
 * ② 相对所选根目录的路径 ③ 文件名唯一匹配（大小写不敏感，兜住 Obsidian 短引用与平铺多选）
 * ④ 文件名命中多个 → 未命中。命中 0 个候选的引用原样保留（结论 51）。
 */
export function resolveImageFiles(
  refs: NoteImageRef[],
  candidates: ImageCandidate[],
  mdFileName: string
): ResolveOutcome {
  const mdEntry = mdFileName ? candidates.find(c => c.name.toLowerCase() === mdFileName.toLowerCase()) : undefined
  const mdDir = mdEntry && mdEntry.relPath ? parentDir(mdEntry.relPath) : ''

  const matched: NoteImageMatch[] = []
  const unresolved: NoteImageRef[] = []
  for (const ref of refs) {
    const target = normalizePath(ref.target)
    const relative = !target.startsWith('/')
    const hit =
      (relative && mdDir && candidates.find(c => normKey(joinPath(mdDir, target)) === normKey(c.relPath))) ||
      (relative && candidates.find(c => normKey(target) === normKey(c.relPath))) ||
      findByUniqueBasename(candidates, target)
    if (hit) matched.push({ ref, candidate: hit })
    else unresolved.push(ref)
  }
  return { matched, unresolved }
}

function findByUniqueBasename(candidates: ImageCandidate[], target: string): ImageCandidate | undefined {
  const wanted = basename(target).toLowerCase()
  const hits = candidates.filter(c => (c.name || basename(c.relPath)).toLowerCase() === wanted)
  return hits.length === 1 ? hits[0] : undefined
}

/** 统一斜杠、压掉 `./` 与重复斜杠；不解析 `..`（候选树里不该有，出现即匹配不上，落回文件名规则） */
function normalizePath(path: string): string {
  return path.replace(/\\/g, '/').replace(/\/{2,}/g, '/').replace(/^\.\/+/, '')
}

function joinPath(dir: string, path: string): string {
  return `${dir.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`
}

function parentDir(path: string): string {
  const idx = path.lastIndexOf('/')
  return idx > 0 ? path.slice(0, idx) : ''
}

/** 路径比对统一小写（macOS / Windows 文件系统本就不敏感，Linux 上赌同名仅大小写不同的两张图极少见） */
function normKey(path: string): string {
  return normalizePath(path).toLowerCase()
}

// ------------------------------------------------------------------
// 改写
// ------------------------------------------------------------------

/**
 * 把命中的引用换成站内路径，未命中的原样保留（结论 51）；按位置从后往前替换，前面的索引不被破坏。
 * wikilink 一并转成标准 markdown 语法——站点不渲染 `![[…]]`，搬完不转等于没搬。
 */
export function rewriteNoteImageRefs(
  content: string,
  replacements: Array<{ ref: NoteImageRef; path: string }>
): RewriteResult {
  const ordered = [...replacements].sort((a, b) => b.ref.index - a.ref.index)
  let out = content
  for (const { ref, path } of ordered) {
    out = out.slice(0, ref.index) + `![${ref.alt}](${path})` + out.slice(ref.index + ref.raw.length)
  }
  return { content: out, length: out.length, tooLong: out.length > NOTE_CONTENT_MAX_LENGTH }
}

/** 引用去重后的目标文件数（弹窗默认态「去重后 M 个文件」的那个 M） */
export function uniqueTargetCount(refs: NoteImageRef[]): number {
  return new Set(refs.map(ref => normKey(ref.target))).size
}

/** 生成一条站内图片语法（编辑器「插图」入口共用，保证与搬运改写的产物同一形状） */
export function imageMarkdown(path: string, alt = ''): string {
  return `![${alt}](${path})`
}

/**
 * 在光标处插入文本：光标前若不是行首则补换行，插入物后若原文不是行首也补换行——
 * 图片语法独占一行才不会被前后的文字吞进同一段落
 */
export function insertAtCursorPosition(text: string, position: number, insert: string): { text: string; cursor: number } {
  const atLineStart = position <= 0 || text[position - 1] === '\n'
  const prefix = atLineStart ? '' : '\n'
  const atEnd = position >= text.length
  const nextIsLineStart = atEnd || text[position] === '\n'
  const suffix = nextIsLineStart ? '' : '\n'
  const next = text.slice(0, position) + prefix + insert + suffix + text.slice(position)
  return { text: next, cursor: position + prefix.length + insert.length }
}
