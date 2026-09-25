import { describe, it, expect } from 'vitest'
import {
  splitByKeyword,
  applyKeywordHighlights,
  clearKeywordHighlights,
  parseServerTime,
  parseTime,
  selectDictLabels,
  handleTree,
  tansParams
} from '../sgj'

/** issue #31：摘要关键词切片。字面量切分（元字符安全）、大小写不敏感、整串匹配（后端只支持一个关键词） */
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

  it('整串匹配：空格分隔的多个词不分别标亮', () => {
    expect(splitByKeyword('正文包含草稿与摘要两个词', '草稿 摘要')).toEqual([
      { text: '正文包含草稿与摘要两个词', hit: false }
    ])
    expect(splitByKeyword('搜索词写成「草稿 摘要」才算命中', '草稿 摘要')).toEqual([
      { text: '搜索词写成「', hit: false },
      { text: '草稿 摘要', hit: true },
      { text: '」才算命中', hit: false }
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

  it('整串匹配：空格分隔的多个词不分别标记', () => {
    const root = buildBody('<p>草稿与摘要两个词</p>')
    expect(applyKeywordHighlights(root, '草稿 摘要')).toHaveLength(0)
    expect(applyKeywordHighlights(root, '草稿与摘要')).toHaveLength(1)
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

/** issue #32 / #39：后端 yyyy-MM-dd HH:mm:ss（GMT+8）必须先转 epoch，直接喂 formatTime 会得到 NaN月NaN日 */
describe('parseServerTime', () => {
  it('解析后端时间串为 epoch（按 +08:00 解释，不受浏览器时区影响）', () => {
    expect(parseServerTime('2026-09-19 17:12:00')).toBe(Date.parse('2026-09-19T17:12:00+08:00'))
    expect(parseServerTime('2026-09-19 17:12:00')).not.toBeNaN()
  })

  it('容忍 ISO 的 T 分隔与已带时区偏移的串', () => {
    expect(parseServerTime('2026-09-19T17:12:00')).toBe(Date.parse('2026-09-19T17:12:00+08:00'))
    expect(parseServerTime('2026-09-19T17:12:00Z')).toBe(Date.parse('2026-09-19T17:12:00Z'))
  })

  it('兼容 epoch 数字与数字串（10 位按秒处理）', () => {
    expect(parseServerTime(1758273120000)).toBe(1758273120000)
    expect(parseServerTime('1758273120')).toBe(1758273120000)
  })

  it('空值与无法解析的输入返回 0（不抛错、不产生 NaN）', () => {
    expect(parseServerTime(undefined)).toBe(0)
    expect(parseServerTime(null)).toBe(0)
    expect(parseServerTime('')).toBe(0)
    expect(parseServerTime('not-a-date')).toBe(0)
  })
})
/**
 * 下面四组的对象都是 ruoyi 遗留工具，在给它们收窄类型（`Record<string, any>` → 精确类型）与
 * 把被当 `forEach` 用的 `.some` 换成 `forEach` 之前没有测试。这些用例钉住的是**改之前的行为**，
 * 收窄类型/换写法的提交必须让它们原样通过
 */
describe('parseTime', () => {
  it('按 pattern 格式化，两位数以内补零', () => {
    expect(parseTime(new Date(2026, 8, 21, 10, 23, 45), '{y}-{m}-{d} {h}:{i}:{s}')).toBe('2026-09-21 10:23:45')
    expect(parseTime(new Date(2026, 0, 5, 3, 4, 6), '{y}-{m}-{d} {h}:{i}:{s}')).toBe('2026-01-05 03:04:06')
  })

  it('{a} 输出星期几（0 = 周日）：2026-09-21 是周一，2026-09-20 是周日', () => {
    expect(parseTime(new Date(2026, 8, 21), '{a}')).toBe('一')
    expect(parseTime(new Date(2026, 8, 20), '{a}')).toBe('日')
  })

  it('不传 pattern 时用默认格式；空值返回 null', () => {
    expect(parseTime(new Date(2026, 8, 21, 10, 23, 45))).toBe('2026-09-21 10:23:45')
    expect(parseTime(undefined)).toBeNull()
    expect(parseTime('')).toBeNull()
  })
})

describe('selectDictLabels', () => {
  const DICTS = [{ value: '0', label: '男' }, { value: '1', label: '女' }]

  it('逗号串逐个翻译，末尾不留下分隔符', () => {
    expect(selectDictLabels(DICTS, '0,1')).toBe('男,女')
    expect(selectDictLabels(DICTS, '1,1')).toBe('女,女')
  })

  it('查不到的值原样保留', () => {
    expect(selectDictLabels(DICTS, '0,9')).toBe('男,9')
  })

  it('数组入参与自定义分隔符', () => {
    expect(selectDictLabels(DICTS, ['0', '1'])).toBe('男,女')
    expect(selectDictLabels(DICTS, '0|1', '|')).toBe('男|女')
  })

  it('空值返回空串', () => {
    expect(selectDictLabels(DICTS, undefined)).toBe('')
    expect(selectDictLabels(DICTS, '')).toBe('')
  })
})

describe('handleTree', () => {
  it('按 parentId 组装成树，根节点保持原顺序', () => {
    const rows = [
      { id: 1, parentId: 0, name: 'a' },
      { id: 2, parentId: 1, name: 'a-1' },
      { id: 3, parentId: 1, name: 'a-2' },
      { id: 4, parentId: 0, name: 'b' }
    ]
    const tree = handleTree(rows)
    expect(tree.map((node: any) => node.id)).toEqual([1, 4])
    expect(tree[0].children.map((node: any) => node.id)).toEqual([2, 3])
    // 第一轮会给每个节点都补上空 children 数组，所以叶子也是 [] 而不是 undefined
    expect(tree[1].children).toEqual([])
  })

  it('可自定义 id / parentId / children 的键名', () => {
    const rows = [{ key: 1, pid: 0 }, { key: 2, pid: 1 }]
    const tree = handleTree(rows, 'key', 'pid', 'nodes')
    expect(tree).toHaveLength(1)
    expect(tree[0].nodes.map((node: any) => node.key)).toEqual([2])
  })
})

describe('tansParams', () => {
  it('标量直接编码，末尾保留 &', () => {
    expect(tansParams({ a: 1, b: 'x y' })).toBe('a=1&b=x%20y&')
  })

  it('嵌套对象展开成 key[sub]=value（方括号也会被编码）', () => {
    expect(tansParams({ page: { num: 1, size: 10 } })).toBe('page%5Bnum%5D=1&page%5Bsize%5D=10&')
  })

  it('null / 空串 / undefined 跳过，0 要保留', () => {
    expect(tansParams({ a: null, b: '', c: undefined, d: 0 })).toBe('d=0&')
  })
})
