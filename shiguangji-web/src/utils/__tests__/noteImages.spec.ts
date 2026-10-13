import { describe, it, expect } from 'vitest'
import {
  collectImageRefs,
  mdDirOf,
  resolveImageFiles,
  rewriteNoteImageRefs,
  uniqueTargetCount,
  imageMarkdown,
  insertAtCursorPosition,
  type ImageCandidate,
  type NoteImageRef
} from '../noteImages'
import { NOTE_CONTENT_MAX_LENGTH } from '@/utils/note'

/** 造一个候选文件（name 必填；relPath 模拟 webkitdirectory 的相对路径，多选时为空） */
function cand(name: string, relPath = ''): ImageCandidate {
  return { file: new File(['x'], name), name: name.split('/').pop() || name, relPath }
}

/** 收集本地引用（快捷断言用） */
function locals(content: string): NoteImageRef[] {
  return collectImageRefs(content).local
}

describe('collectImageRefs：收集', () => {
  it('标准 markdown 的四种写法都取得出目标（T-01）', () => {
    const refs = locals('# t\n\n![a](./a.png)\n\n![b](attachments/b.png)\n\n![c](c.png "标题")\n\n![d](<d e.png>)')
    expect(refs.map(r => r.target)).toEqual(['a.png', 'attachments/b.png', 'c.png', 'd e.png'])
    expect(refs.every(r => r.kind === 'markdown')).toBe(true)
    // alt 原样保留，改写后的替代文本用它
    expect(refs.map(r => r.alt)).toEqual(['a', 'b', 'c', 'd'])
  })

  it('wikilink 的尺寸与块引用后缀被剥掉（T-02）', () => {
    const refs = locals('![[a.png]]\n\n![[attachments/b.png|200]]\n\n![[c.png#^block]]')
    expect(refs.map(r => r.target)).toEqual(['a.png', 'attachments/b.png', 'c.png'])
    expect(refs.every(r => r.kind === 'wikilink')).toBe(true)
    // wikilink 没有 alt，用目标文件名兜底
    expect(refs[0].alt).toBe('a.png')
  })

  it('围栏代码块与行内代码里的引用不收集（T-03）', () => {
    const fenced = '```\n![a](./a.png)\n![[b.png]]\n```\n\n正文 ![c](./c.png)'
    expect(locals(fenced).map(r => r.target)).toEqual(['c.png'])

    const tilde = '~~~\n![a](./a.png)\n~~~\n\n正文'
    expect(locals(tilde)).toHaveLength(0)

    const inline = '行内 `![a](./a.png)` 与 ![b](./b.png)'
    expect(locals(inline).map(r => r.target)).toEqual(['b.png'])
  })

  it('围栏未闭合：后续行仍按代码处理且不报错（T-07）', () => {
    expect(locals('```\n![a](./a.png)\n后面所有行都在围栏里')).toHaveLength(0)
  })

  it('外链 / data: / 协议相对 / 已搬过的 /profile 都不算本地引用（T-04）', () => {
    expect(locals('![a](https://e.com/a.png)\n![b](//cdn/b.png)\n![c](data:image/png;base64,AA)\n![d](/profile/upload/2026/10/10/x.png)')).toHaveLength(0)
  })

  it('外链计数：http(s) 与 // 计，data: 与 /profile 不计（T-54）', () => {
    const refs = collectImageRefs('![a](https://a/1.png)\n![b](//cdn/2.png)\n![c](data:image/png;base64,AA)\n![d](/profile/upload/x.png)\n![e](./e.png)')
    expect(refs.externalCount).toBe(2)
    expect(refs.local.map(r => r.target)).toEqual(['e.png'])
  })

  it('%xx 编码的目标先解码再匹配（T-05）', () => {
    expect(locals('![a](./%E5%9B%BE%20a.png)').map(r => r.target)).toEqual(['图 a.png'])
  })

  it('残缺的 %xx 不解码也不报错，按原样当目标', () => {
    expect(locals('![a](./a%E.png)')[0].target).toBe('./a%E.png'.slice(2))
  })

  it('同一文件两处引用各算一条（计数 2），去重后目标文件数 1（T-06）', () => {
    const refs = locals('![a](./a.png)\n\n又 ![b](a.png)')
    expect(refs).toHaveLength(2)
    expect(uniqueTargetCount(refs)).toBe(1)
  })

  it('raw 与 index 指向正文原文（掩码不动索引的前提）', () => {
    const content = '前 `code` 后 ![a](./a.png)'
    const ref = locals(content)[0]
    expect(content.slice(ref.index, ref.index + ref.raw.length)).toBe('![a](./a.png)')
  })
})

describe('resolveImageFiles：匹配', () => {
  const refOf = (target: string): NoteImageRef => ({ raw: `![x](${target})`, target, kind: 'markdown', index: 0, alt: 'x' })

  it('md 同目录的精确相对路径优先（T-08）', () => {
    const candidates = [cand('a.png', 'vault/attachments/a.png'), cand('a.png', 'vault/dup/a.png')]
    const outcome = resolveImageFiles([refOf('attachments/a.png')], candidates, 'vault')
    expect(outcome.matched[0].candidate.relPath).toBe('vault/attachments/a.png')
    expect(outcome.unresolved).toHaveLength(0)
  })

  it('md 不在候选树里（mdDir 为空）时退到相对所选根（T-09）', () => {
    const candidates = [cand('a.png', 'attachments/a.png')]
    const outcome = resolveImageFiles([refOf('attachments/a.png')], candidates, '')
    expect(outcome.matched[0].candidate.relPath).toBe('attachments/a.png')
  })

  it('文件名唯一匹配兜底，且大小写不敏感（T-10）', () => {
    const outcome = resolveImageFiles([refOf('a.png')], [cand('A.PNG')], 'vault')
    expect(outcome.matched[0].candidate.name).toBe('A.PNG')
  })

  it('文件名命中多个不猜，按未命中处理（T-11）', () => {
    const outcome = resolveImageFiles([refOf('a.png')], [cand('a.png', 'x/'), cand('a.png', 'y/')], 'vault')
    expect(outcome.matched).toHaveLength(0)
    expect(outcome.unresolved).toHaveLength(1)
  })

  it('没有候选时全部未命中且不报错（T-12）；带 ./ 前缀的目标能对上', () => {
    expect(resolveImageFiles([refOf('a.png')], [], 'vault').unresolved).toHaveLength(1)
    expect(resolveImageFiles([refOf('./a.png')], [cand('a.png')], 'vault').matched).toHaveLength(1)
  })

  /**
   * 回归（#56 实测踩到）：md 自己不是图片，弹窗若先把非图片文件滤掉再定位 md，
   * mdDir 就成了空串，规则①静默失效——`attachments/a.png` 只能退到文件名匹配，
   * 而它与 `dup/a.png` 同名 → 被误判成未命中、图不搬。
   * 这里同时钉住两件事：mdDirOf 从完整树里取得到目录；拿到目录后歧义由精确路径解开。
   */
  it('md 被排除出图片候选后仍能靠 mdDirOf 走精确相对匹配（T-57 回归）', () => {
    const selectedTree = [
      { name: '观影笔记.md', relPath: 'vault/观影笔记.md' },
      { name: 'a.png', relPath: 'vault/attachments/a.png' },
      { name: 'a.png', relPath: 'vault/dup/a.png' },
      { name: 'b.png', relPath: 'vault/attachments/b.png' }
    ]
    const mdDir = mdDirOf(selectedTree, '观影笔记.md')
    expect(mdDir).toBe('vault')

    // 候选表只留图片（md 不在其中）——这是弹窗的真实形态
    const candidates = selectedTree.filter(f => f.name.endsWith('.png')).map(f => cand(f.name, f.relPath))
    const outcome = resolveImageFiles([refOf('attachments/a.png'), refOf('a.png')], candidates, mdDir)

    expect(outcome.matched.map(m => m.candidate.relPath)).toEqual(['vault/attachments/a.png'])
    expect(outcome.unresolved.map(r => r.target)).toEqual(['a.png'])
  })

  it('mdDirOf：md 不在所选树里时返回空串（退回规则②③），文件名大小写不敏感', () => {
    expect(mdDirOf([{ name: 'a.png', relPath: 'vault/a.png' }], '观影笔记.md')).toBe('')
    expect(mdDirOf([{ name: 'Note.MD', relPath: 'vault/sub/Note.MD' }], 'note.md')).toBe('vault/sub')
  })

  /**
   * 回归（#56 实测踩到）：拿不到 md 目录时（平铺多选 / md 不在所选树），
   * 带目录的引用必须靠「路径后缀唯一匹配」对上，不能只靠文件名——否则同名文件会让它静默不搬。
   */
  it('拿不到 md 目录时，带目录的引用靠路径后缀唯一匹配命中（T-59 回归）', () => {
    const candidates = [cand('a.png', 'vault/attachments/a.png'), cand('a.png', 'vault/dup/a.png')]

    // 平铺多选：relPath 全为空 → 后缀规则也无从下手，带目录的引用只能判未命中（安全方向）
    const flat = [cand('a.png'), cand('a.png')]
    expect(resolveImageFiles([refOf('attachments/a.png')], flat, '').unresolved).toHaveLength(1)

    // 选了文件夹但 md 不在树里（mdDir 为空）：后缀唯一 → 命中
    const outcome = resolveImageFiles([refOf('attachments/a.png')], candidates, '')
    expect(outcome.matched.map(m => m.candidate.relPath)).toEqual(['vault/attachments/a.png'])

    // 同名的 attachments/a.png 分处两个父目录 → 后缀也歧义，不猜
    const twoDirs = [cand('a.png', 'noteA/attachments/a.png'), cand('a.png', 'noteB/attachments/a.png')]
    expect(resolveImageFiles([refOf('attachments/a.png')], twoDirs, '').unresolved).toHaveLength(1)
  })
})

describe('rewriteNoteImageRefs：改写', () => {
  const content = '第一段\n\n![a](./a.png)\n\n![[b.png]]\n\n![c](./c.png)\n\n结尾'
  const refs = collectImageRefs(content).local

  it('只换命中的，未命中的一字不动，其余正文与顺序不变（T-13）', () => {
    const result = rewriteNoteImageRefs(content, [
      { ref: refs[0], path: '/profile/upload/2026/10/10/aaa.png' },
      { ref: refs[1], path: '/profile/upload/2026/10/10/bbb.png' }
    ])
    expect(result.content).toContain('![a](/profile/upload/2026/10/10/aaa.png)')
    // wikilink 一并转成标准 markdown——站点不渲染 ![[…]]，不转等于没搬
    expect(result.content).toContain('![b.png](/profile/upload/2026/10/10/bbb.png)')
    expect(result.content).toContain('![c](./c.png)')
    expect(result.content).toContain('结尾')
  })

  it('同一文件两处引用都指向同一个新路径（T-14 的改写侧）', () => {
    const dup = '![a](./a.png)\n\n![b](a.png)'
    const dupRefs = collectImageRefs(dup).local
    const result = rewriteNoteImageRefs(dup, dupRefs.map((ref, i) => ({ ref, path: `/p/${i}.png` })))
    expect(result.content).not.toContain('a.png)')
  })

  it('改写后超长给出信号（含实际字数），不静默截断（T-15）', () => {
    const nearLimit = 'x'.repeat(NOTE_CONTENT_MAX_LENGTH - 10) + '![a](./a.png)'
    const result = rewriteNoteImageRefs(nearLimit, [
      { ref: collectImageRefs(nearLimit).local[0], path: `/profile/upload/2026/10/10/${'f'.repeat(32)}.png` }
    ])
    expect(result.tooLong).toBe(true)
    expect(result.length).toBeGreaterThan(NOTE_CONTENT_MAX_LENGTH)
    expect(result.content.startsWith('x'.repeat(NOTE_CONTENT_MAX_LENGTH - 10))).toBe(true)
  })
})

describe('编辑器插图的两个小工具（提交 6 共用）', () => {
  it('imageMarkdown 生成与搬运改写同一形状的语法', () => {
    expect(imageMarkdown('/profile/upload/a.png')).toBe('![](/profile/upload/a.png)')
    expect(imageMarkdown('/p.png', '图')).toBe('![图](/p.png)')
  })

  it('insertAtCursorPosition 让图片语法独占一行', () => {
    // 行首插入不补前换行；后面有文字则补后换行
    expect(insertAtCursorPosition('ab', 0, '![x](/p.png)')).toEqual({ text: '![x](/p.png)\nab', cursor: 12 })
    // 行中插入前后都补换行
    expect(insertAtCursorPosition('ab', 1, '![x](/p.png)').text).toBe('a\n![x](/p.png)\nb')
    // 已在行尾且原文以换行结尾时不补后换行
    expect(insertAtCursorPosition('a\n', 2, '![x](/p.png)').text).toBe('a\n![x](/p.png)')
  })
})
