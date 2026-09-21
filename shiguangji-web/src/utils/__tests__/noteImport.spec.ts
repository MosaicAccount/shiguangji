import { describe, it, expect } from 'vitest'
import {
  NOTE_TAG_MAX_COUNT,
  normalizeTags,
  parseNoteMarkdown,
  setPendingImport,
  stripFrontMatter,
  takePendingImport
} from '../noteImport'
import { NOTE_CONTENT_MAX_LENGTH } from '@/utils/note'

/** 断言解析成功并取出预填内容，失败信息带上原因码 */
function prefillOf(text: string, fileName = 'note.md') {
  const result = parseNoteMarkdown(text, fileName)
  expect(result.ok, `期望解析成功，实际失败于 ${result.ok ? '' : result.code}`).toBe(true)
  if (!result.ok) throw new Error(result.code)
  return result
}

describe('stripFrontMatter', () => {
  it('剥掉 front-matter 并解析出键', () => {
    const { data, body } = stripFrontMatter('---\ntitle: 表头标题\ntags: [a]\n---\n正文第一行\n')
    expect(data.title).toBe('表头标题')
    expect(body).toBe('正文第一行\n')
  })

  it('没有 front-matter 时正文一字不动', () => {
    const text = '# 只有正文\n\n段落。\n'
    expect(stripFrontMatter(text)).toEqual({ data: {}, body: text })
  })

  it('正文开头只是水平分隔线时不吃正文（块的 YAML 不是映射）', () => {
    // 结构上满足「首行 --- + 有闭合 ---」，但 YAML 解析出的是标量：
    // 那其实是两条分隔线夹了一段正文，整块吞掉就丢内容了
    const text = '---\n这只是正文里的一句普通文字\n---\n后面还有正文\n'
    const { data, body } = stripFrontMatter(text)
    expect(data).toEqual({})
    expect(body).toBe(text)
  })

  it('残缺的 ---（只有开头没有闭合）不当 front-matter', () => {
    const text = '---\n标题行\n后面没有闭合\n'
    expect(stripFrontMatter(text)).toEqual({ data: {}, body: text })
  })

  it('表格的分隔行不被当成闭合行', () => {
    const text = '---\n| a | b |\n|---|---|\n'
    expect(stripFrontMatter(text)).toEqual({ data: {}, body: text })
  })

  it('空 front-matter 块整块剥掉', () => {
    expect(stripFrontMatter('---\n---\n正文\n').body).toBe('正文\n')
  })

  it('front-matter 里 YAML 写坏了也剥掉，且不抛错', () => {
    const { data, body } = stripFrontMatter('---\ntitle: "未闭合\n---\n正文\n')
    expect(data).toEqual({})
    expect(body).toBe('正文\n')
  })

  it('认 CRLF 与 BOM（Obsidian 在 Windows 上的产物）', () => {
    const { data, body } = stripFrontMatter('\uFEFF---\r\ntitle: 表头\r\n---\r\n# 正文标题\r\n')
    expect(data.title).toBe('表头')
    expect(body).toBe('# 正文标题\n')
  })
})

describe('normalizeTags：front-matter 的三种写法', () => {
  it('逗号串 / 内联数组 / 块数组都解析成同一个结果', () => {
    const comma = prefillOf('---\ntags: a, b\n---\n正文')
    const inline = prefillOf('---\ntags: [a, b]\n---\n正文')
    const block = prefillOf('---\ntags:\n  - a\n  - b\n---\n正文')
    expect(comma.prefill.tags).toBe('a,b')
    expect(inline.prefill.tags).toBe('a,b')
    expect(block.prefill.tags).toBe('a,b')
  })

  it('去掉前导 #、丢空值、去重', () => {
    expect(normalizeTags(['#读书', '读书', '  ', '', '#技术'])).toEqual(['读书', '技术'])
    expect(normalizeTags('  #读书 , 读书 ,#技术')).toEqual(['读书', '技术'])
  })

  it('标量（数字 / 布尔）也当标签读，不整条丢掉', () => {
    expect(normalizeTags(2024)).toEqual(['2024'])
    expect(prefillOf('---\ntags: 2024\n---\n正文').prefill.tags).toBe('2024')
  })

  it('对象与 null 不作为标签', () => {
    expect(normalizeTags({ a: 1 })).toEqual([])
    expect(normalizeTags(null)).toEqual([])
    expect(normalizeTags(undefined)).toEqual([])
  })

  it(`超过 ${NOTE_TAG_MAX_COUNT} 个只留前 ${NOTE_TAG_MAX_COUNT} 个，并给出被丢弃的数量`, () => {
    const tags = Array.from({ length: 25 }, (_, i) => `t${i}`)
    const result = prefillOf(`---\ntags: [${tags.join(', ')}]\n---\n正文`)
    expect(result.prefill.tags.split(',')).toHaveLength(NOTE_TAG_MAX_COUNT)
    expect(result.droppedTagCount).toBe(5)
  })

  it('按 sgj_note.tags 的 500 字符列宽硬截断，且不把一个标签切成两半', () => {
    const long = Array.from({ length: 30 }, () => 'a'.repeat(30))
    const result = prefillOf(`---\ntags: [${long.join(', ')}]\n---\n正文`)
    const tags = result.prefill.tags.split(',')
    expect(result.prefill.tags.length).toBeLessThanOrEqual(500)
    expect(tags.length).toBeLessThan(NOTE_TAG_MAX_COUNT)
    expect(tags.every(tag => tag === 'a'.repeat(30))).toBe(true)
  })
})

describe('parseNoteMarkdown：标题与正文', () => {
  it('标题按 front-matter → 首个 H1 → 文件名 的顺序取', () => {
    expect(prefillOf('# 正文标题\n\n段落\n', '文件名.md').prefill.title).toBe('正文标题')
    expect(prefillOf('---\ntitle: 表头标题\n---\n# 正文标题\n', '文件名.md').prefill.title).toBe('表头标题')
    expect(prefillOf('没有标题的正文\n', '我的笔记.md').prefill.title).toBe('我的笔记')
  })

  it('front-matter 的 title 是数字时也当标题读', () => {
    expect(prefillOf('---\ntitle: 2024\n---\n正文', 'x.md').prefill.title).toBe('2024')
  })

  it('H1 结尾的装饰性 # 不留在标题里，且不误取 H2', () => {
    expect(prefillOf('## 二级标题\n\n# 一级标题 #\n', 'x.md').prefill.title).toBe('一级标题')
  })

  it('front-matter 之后的首尾空白被去掉，正文其余部分不动', () => {
    const result = prefillOf('---\ntitle: t\n---\n\n\n保留   中间的   空格\n\n')
    expect(result.prefill.content).toBe('保留   中间的   空格')
  })

  it(`正文超过 ${NOTE_CONTENT_MAX_LENGTH} 字时在导入阶段就拦下，并回传实际字数`, () => {
    const tooLong = parseNoteMarkdown('x'.repeat(NOTE_CONTENT_MAX_LENGTH + 1), 'note.md')
    expect(tooLong).toEqual({ ok: false, code: 'too-long', length: NOTE_CONTENT_MAX_LENGTH + 1 })

    // 刚好到上限要通过
    const atLimit = parseNoteMarkdown('x'.repeat(NOTE_CONTENT_MAX_LENGTH), 'note.md')
    expect(atLimit.ok).toBe(true)
  })

  it('非 .md 文件直接拒绝', () => {
    expect(parseNoteMarkdown('# t\n', 'note.docx')).toEqual({ ok: false, code: 'not-md' })
    expect(parseNoteMarkdown('# t\n', 'note.txt')).toEqual({ ok: false, code: 'not-md' })
  })
})

describe('导入预填的交接', () => {
  it('取走即清空：第二次取到 null（避免下次进编辑页又套一遍）', () => {
    setPendingImport({ title: 't', content: 'c', tags: 'a' })
    expect(takePendingImport()).toEqual({ title: 't', content: 'c', tags: 'a' })
    expect(takePendingImport()).toBeNull()
  })
})
