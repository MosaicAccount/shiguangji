<template>
  <div ref="rootRef" class="markdown-viewer markdown-body" v-html="html"></div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/common'
import { markedHighlight } from 'marked-highlight'
import { photoUrl } from '@/utils/sgj'
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

// 站内相对路径（/profile/...）必须补上 API 前缀才取得到图：dev 的 vite 代理只有 /dev-api 与
// /v3/api-docs，直接请求 /profile/** 会落回前端源（404）。钩子挂在 DOMPurify 单例上，
// 守卫理由同上面的 marked：dev HMR 会重复执行本模块，重复 addHook 会把前缀加两次
// （/dev-api/dev-api/...）。外链与 data:/blob: 由 photoUrl 原样放行。
const purifySingleton = DOMPurify as typeof DOMPurify & { __sgjImagePrefixHook?: boolean }
if (!purifySingleton.__sgjImagePrefixHook) {
  purifySingleton.__sgjImagePrefixHook = true
  DOMPurify.addHook('afterSanitizeAttributes', node => {
    if (!(node instanceof Element) || node.tagName !== 'IMG') return
    const src = node.getAttribute('src')
    if (src) node.setAttribute('src', photoUrl(src))
  })
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

// ------------------------------------------------------------------
// 加载失败的图片（0.0.2 FR-002-9 的后续补强）：裂图换成可辨认的标记
// ------------------------------------------------------------------

const rootRef = ref<HTMLElement>()

/**
 * 取不到的图现在只显示浏览器默认的裂图图标，用户看不出是「文件没了」还是「被对方拒了」
 * （防盗链外链就是后者，见设计 结论 62）。这里把它换成一个浅色小标记，
 * 原地址留在 title 里——鼠标一悬停就知道是哪条链接失效，也方便手工替换。
 *
 * 只处理本组件内的图片；标记用 DOM 直接替换，v-html 下次重渲染会重建（失败了会再标记一次）。
 */
function onImageError(event: Event): void {
  const target = event.target as Element | null
  // 用 tagName 判定而不是 instanceof HTMLImageElement：jsdom / 跨 realm（iframe）下 instanceof 会失手
  if (target && target.tagName === 'IMG') {
    markImageMissing(target as HTMLImageElement)
  }
}

onMounted(() => {
  // error 事件不冒泡，必须用捕获阶段接（模板里的 @error.capture 实测不生效，显式挂更确定）
  rootRef.value?.addEventListener('error', onImageError, true)
  // 挂载时补扫一遍：命中缓存里已失败的图片不会再触发 error
  rootRef.value?.querySelectorAll('img').forEach(img => {
    if (img.complete && img.naturalWidth === 0) markImageMissing(img)
  })
})

onBeforeUnmount(() => {
  rootRef.value?.removeEventListener('error', onImageError, true)
})

function markImageMissing(img: HTMLImageElement): void {
  // 已经被替换过（没有父节点）就跳过；不用 isConnected —— 组件可能挂在游离容器里（单测即如此）
  if (!img.parentNode) return
  const marker = document.createElement('span')
  marker.className = 'md-img-missing'
  marker.title = img.getAttribute('src') || ''
  marker.textContent = '图片已失效'
  img.replaceWith(marker)
}
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

  /* 加载失败的图片标记（DOM 里现造的节点没有 scoped 属性，必须走 :deep） */
  :deep(.md-img-missing) {
    display: inline-flex;
    align-items: center;
    padding: 2px 9px;
    margin: 4px 0;
    font-size: 12px;
    color: var(--sgj-text-4);
    cursor: help;
    background: var(--sgj-bg-deep);
    border: 1px dashed var(--sgj-border-card);
    border-radius: 5px;
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
