import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import MarkdownViewer from '../index.vue'

const renderHtml = (content: string) => mount(MarkdownViewer, { props: { content } }).html()

describe('MarkdownViewer 代码语法高亮', () => {
  it('标注语言的代码块渲染出 hljs 高亮 span，class 未被消毒误伤', () => {
    const html = renderHtml('```java\npublic class A {\n  String s = "x";\n}\n```')
    expect(html).toContain('hljs language-java')
    expect(html).toContain('<span class="hljs-keyword">')
  })

  it('无语言标注的代码块原样转义显示、不高亮、不报错', () => {
    const html = renderHtml('```\nplain <b>text</b>\n```')
    expect(html).toContain('<pre><code>')
    expect(html).toContain('&lt;b&gt;text&lt;/b&gt;')
    expect(html).not.toContain('hljs-')
  })

  it('不认识的语言不高亮、不报错', () => {
    const html = renderHtml('```notalang\nconst x = 1;\n```')
    expect(html).toContain('hljs language-notalang')
    expect(html).not.toContain('<span class="hljs-')
  })

  it('危险内容仍被消毒过滤', () => {
    const html = renderHtml(
      '<script>alert(1)</script>\n\n<img src=x onerror="alert(1)">\n\n```js\nconst a = 1;\n```'
    )
    expect(html).not.toContain('<script')
    expect(html).not.toContain('onerror')
    expect(html).toContain('<span class="hljs-keyword">')
  })

  it('常规 Markdown 语法回归：标题、列表、表格、链接、图片、任务列表', () => {
    const html = renderHtml(
      [
        '# 标题',
        '',
        '- 项目',
        '',
        '| a | b |',
        '| --- | --- |',
        '| 1 | 2 |',
        '',
        '[链接](https://example.com)',
        '',
        '![图片](https://example.com/a.png)',
        '',
        '- [x] 已完成'
      ].join('\n')
    )
    expect(html).toContain('<h1')
    expect(html).toContain('<li>项目</li>')
    expect(html).toContain('<table>')
    expect(html).toContain('href="https://example.com"')
    expect(html).toContain('src="https://example.com/a.png"')
    expect(html).toContain('checked')
  })
})
