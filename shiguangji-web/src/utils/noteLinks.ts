import type { NoteLink, NoteLinkReason } from '@/types/api/business/note'

/**
 * 笔记互链的前端渲染工具（0.0.5 FR-005-1/3）。
 *
 * 服务端是「哪个 [[...]] 算链接、指向哪篇」的唯一裁决者：这里只按详情接口下发的
 * links[].raw 在已渲染的 DOM 文本节点上做字面量替换——服务端没下发的 token 保持原文，
 * 因此两端不存在会漂移的第二套解析器。
 */

/** 降级原因的提示文案（title 属性与 aria-label 共用） */
const BROKEN_REASON_TEXT: Record<NoteLinkReason, string> = {
  NOT_FOUND: '笔记未找到',
  AMBIGUOUS: '标题重名，无法确定目标'
}

/** 祖先为这些节点的文本不参与替换：代码、既有链接、表单控件 */
const SKIP_ANCESTOR = 'code, pre, a, textarea, [contenteditable]'

export function noteLinkBrokenText(reason?: NoteLinkReason): string {
  return reason ? BROKEN_REASON_TEXT[reason] ?? '笔记未找到' : '笔记未找到'
}

/**
 * 标题能否作为互链目标：含 [ ] 或换行的标题无法用 [[...]] 语法表达
 * （插入选择器据此置灰候选）。
 */
export function isLinkableTitle(title?: string | null): boolean {
  if (!title) return false
  return !title.includes('[') && !title.includes(']') && !title.includes('\n')
}

/**
 * 把 links 的 raw 替换为站内链接 / 降级标记。幂等（替换后文本里已无 raw），
 * 在关键词高亮之前调用——高亮会把文本节点切进 mark，切完再找 raw 就找不全。
 */
export function linkifyNoteTokens(root: HTMLElement | null | undefined, links?: NoteLink[] | null): void {
  if (!root || !links?.length) return
  // 长 raw 先替换，避免短串吃掉长串的前缀
  const sorted = [...links].filter(link => link.raw).sort((a, b) => b.raw.length - a.raw.length)
  for (const link of sorted) {
    replaceAllOccurrences(root, link)
  }
}

function replaceAllOccurrences(root: HTMLElement, link: NoteLink): void {
  const nodes = collectTextNodes(root, link.raw)
  for (const node of nodes) {
    replaceInTextNode(node, link)
  }
}

/** 先收集再改写：遍历过程中替换节点会使 TreeWalker 失效 */
function collectTextNodes(root: HTMLElement, raw: string): Text[] {
  const targets: Text[] = []
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
    acceptNode: (node: Text) => {
      const value = node.nodeValue || ''
      if (!value.includes(raw)) return NodeFilter.FILTER_REJECT
      const parent = node.parentElement
      if (!parent || parent.closest(SKIP_ANCESTOR)) return NodeFilter.FILTER_REJECT
      return NodeFilter.FILTER_ACCEPT
    }
  })
  while (walker.nextNode()) targets.push(walker.currentNode as Text)
  return targets
}

function replaceInTextNode(node: Text, link: NoteLink): void {
  const text = node.nodeValue || ''
  const raw = link.raw
  const fragment = document.createDocumentFragment()
  let pos = 0
  let index = text.indexOf(raw)
  while (index >= 0) {
    if (index > pos) fragment.appendChild(document.createTextNode(text.slice(pos, index)))
    fragment.appendChild(buildNoteLinkNode(link))
    pos = index + raw.length
    index = text.indexOf(raw, pos)
  }
  if (pos < text.length) fragment.appendChild(document.createTextNode(text.slice(pos)))
  node.parentNode?.replaceChild(fragment, node)
}

/** 成链 → <a>（事件委托接管跳转）；降级 → 不可点的 <span>，保留标题文字与原因提示 */
function buildNoteLinkNode(link: NoteLink): HTMLElement {
  if (link.noteId != null) {
    const anchor = document.createElement('a')
    anchor.className = 'note-link'
    anchor.href = `/note/detail?noteId=${link.noteId}`
    anchor.dataset.noteId = String(link.noteId)
    anchor.textContent = link.title
    anchor.title = `笔记：${link.title}`
    return anchor
  }
  const span = document.createElement('span')
  span.className = 'note-link is-broken'
  span.textContent = link.title
  const hint = noteLinkBrokenText(link.reason)
  span.title = hint
  span.setAttribute('aria-label', `${link.title}（${hint}）`)
  return span
}
