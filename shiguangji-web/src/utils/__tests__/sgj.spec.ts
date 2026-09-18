import { describe, it, expect } from 'vitest'
import { splitByKeyword } from '../sgj'

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
