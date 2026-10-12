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

describe('MarkdownViewer 站内图片前缀（0.0.2 FR-002-9）', () => {
  const base = import.meta.env.VITE_APP_BASE_API

  const srcOf = (html: string) => /<img[^>]*src="([^"]+)"/.exec(html)?.[1]

  it('站内相对路径补上 API 前缀（正文里存 /profile，展示时要带 baseURL）', () => {
    const html = renderHtml('![图](/profile/upload/2026/10/10/abc.png)')
    expect(srcOf(html)).toBe(`${base}/profile/upload/2026/10/10/abc.png`)
  })

  it('外链与 data: 原样保留；blob: 被消毒的 URI 白名单挡掉（存量行为）', () => {
    expect(srcOf(renderHtml('![a](https://example.com/a.png)'))).toBe('https://example.com/a.png')
    expect(srcOf(renderHtml('![b](data:image/png;base64,AAAA)'))).toBe('data:image/png;base64,AAAA')
    // blob: 不在 DOMPurify 的 URI 白名单里，整条 src 被移除——正文里存 blob: 本来也没有意义
    expect(srcOf(renderHtml('![c](blob:http://localhost/xyz)'))).toBeUndefined()
  })

  it('钩子只注册一次：连续挂载两次，第二次渲染仍只有一个前缀', () => {
    // 若把注册挪进组件 setup（每次挂载都 addHook），第二次挂载后的渲染会被加两次前缀
    renderHtml('![图](/profile/upload/2026/10/10/abc.png)')
    const second = srcOf(renderHtml('![图](/profile/upload/2026/10/10/abc.png)'))
    expect(second).toBe(`${base}/profile/upload/2026/10/10/abc.png`)
    expect(second).not.toContain(`${base}${base}`)
  })
})
