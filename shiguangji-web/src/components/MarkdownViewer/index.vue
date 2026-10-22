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
/* 0.0.6 长文排版（D-46）：纸面阅读节奏——行距更松，层级靠字号与手绘线，
   颜色只用 --hd-* 令牌，暗色随主题自动翻转；代码高亮 token 改成暖色系。 */
.markdown-body {
  font-size: 16.5px;
  line-height: 2.02;
  color: var(--hd-ink);
  word-break: break-word;

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4),
  :deep(h5),
  :deep(h6) {
    font-weight: 400;
    line-height: 1.45;
    color: var(--hd-ink);
    letter-spacing: .5px;
  }

  :deep(h1) {
    font-size: 30px;
    margin: 1.6em 0 .5em;
  }

  :deep(h2) {
    position: relative;
    font-size: 24px;
    margin: 1.9em 0 .5em;
    padding-bottom: 8px;

    &::after {
      content: "";
      position: absolute;
      left: -2px;
      bottom: 0;
      width: 118px;
      height: 0;
      border-top: 3px solid var(--hd-red);
      border-radius: 50%;
      opacity: .7;
    }
  }

  :deep(h3) {
    font-size: 19.5px;
    margin: 1.6em 0 .4em;
  }

  :deep(h4),
  :deep(h5),
  :deep(h6) { font-size: 17px; margin: 1.4em 0 .3em }

  :deep(p) { margin: .95em 0 }

  :deep(a) {
    color: var(--hd-red);
    text-decoration: underline;
    text-decoration-style: wavy;
    text-decoration-thickness: 1.3px;
    text-underline-offset: 4px;

    &:hover { text-decoration-style: solid }
  }

  /* 互链（[[标题]] 渲染后的 a.note-link）：设计图口径——陶红波浪线 + 尾部 ↗ */
  :deep(a.note-link) {
    color: var(--hd-red);
    text-decoration: underline;
    text-decoration-style: wavy;
    text-decoration-thickness: 1.4px;
    text-underline-offset: 4px;
    cursor: pointer;
  }
  :deep(a.note-link::after) {
    content: " ↗";
    font-size: .8em;
    text-decoration: none;
  }
  :deep(a.note-link:hover) { text-decoration-style: solid }
  /* 降级互链（未找到 / 重名）：灰虚线 + 问号，保留标题与原因提示 */
  :deep(.note-link.is-broken) {
    color: var(--hd-ink-faint);
    text-decoration: underline;
    text-decoration-style: dotted;
    text-decoration-thickness: 1.2px;
    text-underline-offset: 4px;
    cursor: help;
  }
  :deep(.note-link.is-broken::after) {
    content: " ?";
    font-size: .8em;
    text-decoration: none;
  }
  /* 列表标记也用陶红（设计图 li::marker） */
  :deep(li)::marker { color: var(--hd-red) }

  /* 图片 = 贴上去的照片：白边 + 轻微旋转 + 纸色阴影 */
  :deep(img) {
    display: block;
    max-width: 100%;
    margin: 18px auto;
    padding: 7px;
    background: var(--hd-paper);
    border: 1.6px solid var(--hd-line-soft);
    border-radius: 2px;
    box-shadow: var(--hd-shadow);
    transform: rotate(-.4deg);
  }

  :deep(.md-img-missing) {
    display: inline-flex;
    align-items: center;
    padding: 2px 9px;
    margin: 4px 0;
    font-size: 13px;
    color: var(--hd-ink-faint);
    cursor: help;
    background: var(--hd-paper-2);
    border: 1.4px dashed var(--hd-line-soft);
    border-radius: 5px;
  }

  :deep(ul),
  :deep(ol) {
    margin: .8em 0;
    padding-left: 1.4em;

    li { margin: .3em 0 }
  }

  /* 引用 = 红色侧笔 */
  :deep(blockquote) {
    margin: 1.2em 0;
    padding: 2px 0 2px 18px;
    border-left: 3px solid var(--hd-red);
    background: none;
    border-radius: 0;
    color: var(--hd-ink-soft);
    font-size: 16px;

    p { margin: .3em 0 }
  }

  :deep(hr) {
    margin: 2em 0;
    border: 0;
    border-top: 2px dashed var(--hd-line-soft);
  }

  :deep(code) {
    font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
    background: var(--hd-paper-2);
    border: 1.2px dashed var(--hd-line-soft);
    border-radius: 4px;
    padding: 1px 6px;
    font-size: 14.5px;
    color: var(--hd-red);
  }

  /* 代码块 = 方格纸 */
  :deep(pre) {
    margin: 1.2em 0;
    padding: 16px 18px;
    overflow-x: auto;
    line-height: 1.65;
    background-color: var(--hd-paper-3);
    background-image:
      linear-gradient(rgba(122, 112, 102, .10) 1px, transparent 1px),
      linear-gradient(90deg, rgba(122, 112, 102, .10) 1px, transparent 1px);
    background-size: 18px 18px;
    border: 1.8px solid var(--hd-line);
    border-radius: 4px 2px 5px 3px / 3px 5px 2px 4px;
    box-shadow: var(--hd-shadow-sm);

    code {
      background: none;
      border: 0;
      padding: 0;
      font-size: 14px;
      color: var(--hd-ink);
    }
  }

  :deep(table) {
    width: 100%;
    margin: 1.4em 0;
    border-collapse: collapse;
    font-size: 15px;

    th,
    td {
      padding: 7px 10px;
      border: 1.5px dashed var(--hd-line-soft);
      text-align: left;
    }

    th {
      font-weight: 400;
      color: var(--hd-ink-soft);
      border-bottom: 2px solid var(--hd-line);
    }
  }

  /* highlight.js 暖色 token：纸面上仍能分清关键字 / 字符串 / 注释 */
  :deep(.hljs-keyword),
  :deep(.hljs-selector-tag),
  :deep(.hljs-built_in) { color: var(--hd-red) }
  :deep(.hljs-string),
  :deep(.hljs-attr) { color: var(--hd-amber-ink) }
  :deep(.hljs-comment),
  :deep(.hljs-quote) { color: var(--hd-ink-faint); font-style: italic }
  :deep(.hljs-number),
  :deep(.hljs-literal) { color: var(--hd-ochre) }
  :deep(.hljs-title),
  :deep(.hljs-function) { color: var(--hd-ink) }
}
</style>
