import { describe, it, expect } from 'vitest'
import { isLinkableTitle, linkifyNoteTokens, noteLinkBrokenText } from '../noteLinks'
import type { NoteLink } from '@/types/api/business/note'

/** 0.0.5：互链 DOM 替换。服务端只下发 raw + 落点，前端做字面量替换（用例对应 link-testcases.md §C） */
describe('linkifyNoteTokens', () => {
  function mount(html: string): HTMLElement {
    const root = document.createElement('div')
    root.className = 'markdown-viewer'
    root.innerHTML = html
    document.body.appendChild(root)
    return root
  }

  const resolved: NoteLink = { raw: '[[番茄工作法]]', title: '番茄工作法', noteId: 102 }
  const notFound: NoteLink = { raw: '[[深度工作]]', title: '深度工作', reason: 'NOT_FOUND' }
  const ambiguous: NoteLink = { raw: '[[周记]]', title: '周记', reason: 'AMBIGUOUS' }

  it('成链替换为 a.note-link，href 指向目标详情', () => {
    const root = mount('<p>参考了 [[番茄工作法]] 一文</p>')
    linkifyNoteTokens(root, [resolved])
    const anchor = root.querySelector('a.note-link')
    expect(anchor).not.toBeNull()
    expect(anchor!.textContent).toBe('番茄工作法')
    expect(anchor!.getAttribute('href')).toBe('/note/detail?noteId=102')
    expect(root.textContent).toContain('参考了')
    expect(root.textContent).toContain('一文')
  })

  it('未找到 / 重名替换为不可点的降级 span，提示原因', () => {
    const root = mount('<p>见 [[深度工作]] 与 [[周记]]</p>')
    linkifyNoteTokens(root, [notFound, ambiguous])
    const spans = root.querySelectorAll('span.note-link.is-broken')
    expect(spans).toHaveLength(2)
    expect(spans[0].getAttribute('title')).toBe('笔记未找到')
    expect(spans[1].getAttribute('title')).toBe('标题重名，无法确定目标')
    expect(root.querySelector('a')).toBeNull()
  })

  it('代码块 / 行内代码 / 既有链接里的 raw 原样保留', () => {
    const root = mount(
      '<p>普通 [[番茄工作法]]</p><pre><code>const a = "[[番茄工作法]]"</code></pre>' +
      '<p>行内 <code>[[番茄工作法]]</code> 与 <a href="/x">既有链接 [[番茄工作法]]</a></p>'
    )
    linkifyNoteTokens(root, [resolved])
    expect(root.querySelectorAll('a.note-link')).toHaveLength(1)
    expect(root.querySelector('pre')!.textContent).toContain('[[番茄工作法]]')
    expect(root.querySelector('code')!.textContent).toContain('[[番茄工作法]]')
    expect(root.querySelector('a[href="/x"]')!.textContent).toContain('[[番茄工作法]]')
  })

  it('服务端未下发的 token 保持原文（不追加降级样式）', () => {
    const root = mount('<p>转义写法 \\[\\[不存在\\]\\] 与 [[未下发]]</p>')
    linkifyNoteTokens(root, [resolved])
    expect(root.textContent).toContain('[[未下发]]')
    expect(root.querySelector('.is-broken')).toBeNull()
  })

  it('同一 raw 多次出现全部替换；幂等（重复调用不产生嵌套）', () => {
    const root = mount('<p>[[番茄工作法]] 和 [[番茄工作法]]</p>')
    linkifyNoteTokens(root, [resolved])
    linkifyNoteTokens(root, [resolved])
    expect(root.querySelectorAll('a.note-link')).toHaveLength(2)
    expect(root.textContent).not.toContain('[[')
  })

  it('括号内空白变体按各自的 raw 替换，显示用去空白标题', () => {
    const spaced: NoteLink = { raw: '[[ 番茄工作法 ]]', title: '番茄工作法', noteId: 102 }
    const root = mount('<p>见 [[番茄工作法]] 与 [[ 番茄工作法 ]]</p>')
    linkifyNoteTokens(root, [resolved, spaced])
    expect(root.querySelectorAll('a.note-link')).toHaveLength(2)
    expect(root.textContent).not.toContain('[[')
  })
})

describe('isLinkableTitle', () => {
  it('含 [ ] 或换线的标题不可互链', () => {
    expect(isLinkableTitle('番茄工作法')).toBe(true)
    expect(isLinkableTitle('读书笔记 [草稿]')).toBe(false)
    expect(isLinkableTitle('a]b')).toBe(false)
    expect(isLinkableTitle('跨\n行')).toBe(false)
    expect(isLinkableTitle('')).toBe(false)
    expect(isLinkableTitle(undefined)).toBe(false)
  })
})

describe('noteLinkBrokenText', () => {
  it('两种降级原因各有文案，未知原因回退未找到', () => {
    expect(noteLinkBrokenText('NOT_FOUND')).toBe('笔记未找到')
    expect(noteLinkBrokenText('AMBIGUOUS')).toBe('标题重名，无法确定目标')
    expect(noteLinkBrokenText(undefined)).toBe('笔记未找到')
  })
})
