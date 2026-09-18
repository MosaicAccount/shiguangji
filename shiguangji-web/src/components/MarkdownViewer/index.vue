<template>
  <div class="markdown-viewer markdown-body" v-html="html"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/common'
import { markedHighlight } from 'marked-highlight'
import 'highlight.js/styles/atom-one-dark.css'

const props = defineProps<{
  content?: string | null
}>()

// 只高亮显式标注且 hljs 认识的语言，不自动猜语言；返回原文时 marked-highlight 会按未高亮的普通代码转义输出。
// 守卫标记挂在 marked 单例上：dev 下 HMR 会重复执行本模块，重复 use 会把高亮扩展叠加导致输出指数级膨胀
const markedSingleton = marked as typeof marked & { __sgjHighlightRegistered?: boolean }
if (!markedSingleton.__sgjHighlightRegistered) {
  markedSingleton.__sgjHighlightRegistered = true
  markedSingleton.use(
    markedHighlight({
      langPrefix: 'hljs language-',
      highlight(code, lang) {
        if (!lang || !hljs.getLanguage(lang)) return code
        try {
          return hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
        } catch {
          return code
        }
      }
    })
  )
}

marked.setOptions({
  gfm: true,
  breaks: true
})

const html = computed(() => {
  const source = props.content || ''
  if (!source.trim()) return ''
  const raw = marked.parse(source, { async: false }) as string
  return DOMPurify.sanitize(raw, {
    ADD_ATTR: ['target'],
    ADD_TAGS: ['img']
  })
})
</script>

<style scoped lang="scss">
/* 颜色全部走 --sgj-* 设计令牌，html.dark 下随主题自动翻转 */
.markdown-body {
  font-size: 14px;
  line-height: 1.75;
  color: var(--sgj-text-2);
  word-break: break-word;

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4),
  :deep(h5),
  :deep(h6) {
    margin: 18px 0 10px;
    font-weight: 600;
    line-height: 1.4;
    color: var(--sgj-text);
  }

  :deep(h1) {
    font-size: 22px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--sgj-border-card);
  }

  :deep(h2) {
    font-size: 18px;
    padding-bottom: 6px;
    border-bottom: 1px solid var(--sgj-border-card);
  }

  :deep(h3) {
    font-size: 16px;
  }

  :deep(h4),
  :deep(h5),
  :deep(h6) {
    font-size: 14px;
  }

  :deep(p) {
    margin: 8px 0;
  }

  :deep(a) {
    color: var(--sgj-link);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  :deep(img) {
    max-width: 100%;
    border-radius: 8px;
    display: block;
    margin: 10px auto;
  }

  :deep(ul),
  :deep(ol) {
    margin: 8px 0;
    padding-left: 24px;

    li {
      margin: 4px 0;
    }
  }

  :deep(blockquote) {
    margin: 10px 0;
    padding: 8px 14px;
    border-left: 4px solid var(--sgj-amber);
    background: var(--sgj-amber-soft);
    border-radius: 0 8px 8px 0;
    color: var(--sgj-text-2);

    p {
      margin: 4px 0;
    }
  }

  :deep(code) {
    font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
    background: var(--sgj-cover);
    border-radius: 4px;
    padding: 2px 5px;
    font-size: 13px;
    color: var(--sgj-primary);
  }

  :deep(pre) {
    background: #282c34;
    color: #e6e6e6;
    border-radius: 10px;
    padding: 14px 16px;
    overflow-x: auto;
    margin: 12px 0;
    line-height: 1.6;

    code {
      background: transparent;
      color: inherit;
      padding: 0;
      font-size: 13px;
    }
  }

  :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: 12px 0;
    font-size: 13px;

    th,
    td {
      border: 1px solid var(--sgj-border-card);
      padding: 8px 12px;
      text-align: left;
    }

    th {
      background: var(--sgj-amber-soft);
      font-weight: 600;
      color: var(--sgj-text);
    }

    tr:nth-child(even) td {
      background: var(--sgj-bg);
    }
  }

  :deep(hr) {
    border: none;
    border-top: 1px solid var(--sgj-border-card);
    margin: 16px 0;
  }

  :deep(input[type="checkbox"]) {
    margin-right: 6px;
  }

  :deep(del) {
    color: var(--sgj-text-4);
  }
}
</style>
