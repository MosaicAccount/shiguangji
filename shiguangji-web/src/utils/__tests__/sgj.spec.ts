import { describe, it, expect } from 'vitest'
import { splitByKeyword, applyKeywordHighlights, clearKeywordHighlights } from '../sgj'

/** issue #31：摘要关键词切片。字面量切分（元字符安全）、大小写不敏感、多词合并区间 */
describe('splitByKeyword', () => {
  it('无关键词时整段原样返回', () => {
    expect(splitByKeyword('纯文本摘要', undefined)).toEqual([{ text: '纯文本摘要', hit: false }])
    expect(splitByKeyword('纯文本摘要', '   ')).toEqual([{ text: '纯文本摘要', hit: false }])
  })

  it('空文本返回空数组', () => {
    expect(splitByKeyword('', '关键词')).toEqual([])
    expect(splitByKeyword(undefined, '关键词')).toEqual([])
  })

  it('单个词多处命中切分正确', () => {
    const segs = splitByKeyword('草稿写一半，草稿箱里还有草稿', '草稿')
    expect(segs).toEqual([
      { text: '草稿', hit: true },
      { text: '写一半，', hit: false },
      { text: '草稿', hit: true },
      { text: '箱里还有', hit: false },
      { text: '草稿', hit: true }
    ])
  })

  it('大小写不敏感，与 MySQL 全文匹配行为一致', () => {
    const segs = splitByKeyword('MySQL 与 Mysql 与 mysql', 'mysql')
    expect(segs.filter(s => s.hit).map(s => s.text)).toEqual(['MySQL', 'Mysql', 'mysql'])
  })

  it('多词检索任一词命中即标亮，相邻区间合并', () => {
    const segs = splitByKeyword('正文包含草稿与摘要两个词', '草稿 摘要')
    expect(segs).toEqual([
      { text: '正文包含', hit: false },
      { text: '草稿', hit: true },
      { text: '与', hit: false },
      { text: '摘要', hit: true },
      { text: '两个词', hit: false }
    ])
  })

  it('元字符关键词按字面量匹配，不报错', () => {
    expect(splitByKeyword('调用 f(**args) 与 a[0] 的写法', '(**args')).toEqual([
      { text: '调用 f', hit: false },
      { text: '(**args', hit: true },
      { text: ') 与 a[0] 的写法', hit: false }
    ])
    expect(splitByKeyword('数组 a[0] 取值', 'a[0]')).toEqual([
      { text: '数组 ', hit: false },
      { text: 'a[0]', hit: true },
      { text: ' 取值', hit: false }
    ])
  })

  it('未命中时整段原样返回', () => {
    expect(splitByKeyword('毫无关系的内容', '草稿')).toEqual([{ text: '毫无关系的内容', hit: false }])
  })
})

/** issue #31 详情页承接：DOM 命中高亮与清除（jsdom） */
describe('applyKeywordHighlights / clearKeywordHighlights', () => {
  function buildBody(html: string): HTMLElement {
    const root = document.createElement('div')
    root.innerHTML = html
    document.body.appendChild(root)
    return root
  }

  it('命中片段包裹为 mark.kw-hit，大小写不敏感', () => {
    const root = buildBody('<p>MySQL 全文索引很好用，mysql 是个好东西。</p>')
    const marks = applyKeywordHighlights(root, 'mysql')
    expect(marks).toHaveLength(2)
    expect(marks[0].textContent).toBe('MySQL')
    expect(marks[1].textContent).toBe('mysql')
    expect(root.querySelectorAll('mark.kw-hit')).toHaveLength(2)
    root.remove()
  })

  it('多词任一命中即标记且重叠合并', () => {
    const root = buildBody('<p>草稿与摘要两个词</p>')
    const marks = applyKeywordHighlights(root, '草稿 摘要')
    expect(marks.map(m => m.textContent)).toEqual(['草稿', '摘要'])
    root.remove()
  })

  it('跨元素边界的词不标记（与浏览器原生查找一致）', () => {
    const root = buildBody('<p>全文</p><p>索引</p>')
    expect(applyKeywordHighlights(root, '全文索引')).toHaveLength(0)
    root.remove()
  })

  it('clear 后恢复原文并可重新套用', () => {
    const root = buildBody('<p>草稿内容</p>')
    expect(applyKeywordHighlights(root, '草稿')).toHaveLength(1)
    clearKeywordHighlights(root)
    expect(root.querySelectorAll('mark.kw-hit')).toHaveLength(0)
    expect(root.querySelector('p')?.textContent).toBe('草稿内容')
    expect(applyKeywordHighlights(root, '草稿')).toHaveLength(1)
    root.remove()
  })

  it('空关键词与空容器安全', () => {
    const root = buildBody('<p>正文</p>')
    expect(applyKeywordHighlights(root, '')).toHaveLength(0)
    expect(applyKeywordHighlights(null, '草稿')).toHaveLength(0)
    expect(clearKeywordHighlights(null)).toBeUndefined()
    root.remove()
  })
})
