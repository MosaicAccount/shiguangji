<template>
  <div class="markdown-viewer markdown-body" v-html="html"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps<{
  content?: string | null
}>()

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
.markdown-body {
  font-size: 14px;
  line-height: 1.75;
  color: #3f3f46;
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
    color: #27272a;
  }

  :deep(h1) {
    font-size: 22px;
    padding-bottom: 8px;
    border-bottom: 1px solid #e8e8e3;
  }

  :deep(h2) {
    font-size: 18px;
    padding-bottom: 6px;
    border-bottom: 1px solid #e8e8e3;
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
    color: #1677ff;
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
    border-left: 4px solid #d9a066;
    background: #faf6f0;
    border-radius: 0 8px 8px 0;
    color: #6b5b4e;

    p {
      margin: 4px 0;
    }
  }

  :deep(code) {
    font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
    background: #f2f2ee;
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
      border: 1px solid #e4e4df;
      padding: 8px 12px;
      text-align: left;
    }

    th {
      background: #faf6f0;
      font-weight: 600;
      color: var(--sgj-text);
    }

    tr:nth-child(even) td {
      background: #fcfaf7;
    }
  }

  :deep(hr) {
    border: none;
    border-top: 1px solid #e8e8e3;
    margin: 16px 0;
  }

  :deep(input[type="checkbox"]) {
    margin-right: 6px;
  }

  :deep(del) {
    color: #a0a0a0;
  }
}
</style>
