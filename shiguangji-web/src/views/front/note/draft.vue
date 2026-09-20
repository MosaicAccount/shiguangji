<template>
  <div class="note-draft-page">
    <router-link class="backlink" to="/note">← 返回笔记列表</router-link>
    <div class="head">
      <h1>草稿箱</h1>
      <span class="sub">所有没保存完的改动，每条一份</span>
    </div>

    <ul class="dlist" :aria-busy="loading">
      <template v-if="loading">
        <li v-for="i in 3" :key="i" class="draft sk" aria-hidden="true">
          <div class="sk-line w1"></div>
          <div class="sk-line w2"></div>
        </li>
      </template>

      <li v-else-if="loadError" class="draft-state">
        <div class="state-box">
          <div class="state-title">草稿没能加载出来</div>
          <div class="state-desc">网络不稳或者服务没响应。重新加载一次，也可以先去写别的。</div>
          <button class="btn line" @click="loadData">重新加载</button>
        </div>
      </li>

      <li v-else-if="!list.length" class="draft-state">
        <div class="state-box">
          <div class="state-icon" aria-hidden="true">🗂</div>
          <div class="state-title">草稿箱是空的</div>
          <div class="state-desc">在编辑器里写一半离开，草稿就会出现在这里。</div>
          <router-link class="state-link" to="/note/edit">去写一篇 →</router-link>
        </div>
      </li>

      <template v-else>
        <li v-for="(draft, index) in list" :key="draft.draftId" class="draft">
          <div class="d-main">
            <p class="d-title" :class="{ clamp: !draft.title }">{{ draft.title || draft.excerpt || '无标题草稿' }}</p>
            <p class="d-sub">
              <span class="d-item"><template v-if="draft.noteId">✏️ 编辑 · </template>{{ draft.itemId ? '🔗 ' + (draft.itemName || '#' + draft.itemId) : '📝 独立笔记' }}</span>
              <span class="d-time">{{ relTime(draft.updateTime) }}</span>
            </p>
          </div>
          <div class="d-acts">
            <button
              :ref="el => setActionRef(el, index)"
              class="btn sm"
              :aria-label="`继续写：${draftLabel(draft)}`"
              @click="continueDraft(draft)"
            >继续写</button>
            <button
              class="btn sm danger"
              :aria-label="`删除草稿：${draftLabel(draft)}`"
              @click="handleDelete(draft, index)"
            >删除</button>
          </div>
        </li>
      </template>
    </ul>
    <!-- 删除结果播报：列表变化对读屏用户不可见 -->
    <p class="sr" role="status" aria-live="polite">{{ announce }}</p>
  </div>
</template>

<script setup lang="ts" name="FrontNoteDraft">
import { getToken } from '@/utils/auth'
import { listNoteDrafts, delNoteDraft } from '@/api/front/noteDraft'
import { parseServerTime } from '@/utils/sgj'
import { formatTime } from '@/utils/index'
import type { SgjNoteDraft } from '@/types/api/front/noteDraft'

const { proxy } = getCurrentInstance() as { proxy: any }
const router = useRouter()

const list = ref<SgjNoteDraft[]>([])
const loading = ref(false)
const loadError = ref(false)
const announce = ref('')
/** 「继续写」按钮引用：删掉一行后把焦点交给下一行的主操作，键盘用户不会丢失位置 */
const actionRefs = ref<Array<HTMLButtonElement | null>>([])

function setActionRef(el: any, index: number): void {
  actionRefs.value[index] = (el as HTMLButtonElement) || null
}

function draftLabel(draft: SgjNoteDraft): string {
  const name = draft.title || draft.excerpt || '无标题草稿'
  return name.length > 20 ? name.slice(0, 20) + '…' : name
}

/** 相对时间：后端给的是 yyyy-MM-dd HH:mm:ss，必须先转 epoch（直接喂 formatTime 会得到 NaN月NaN日） */
function relTime(time?: string): string {
  const ms = parseServerTime(time)
  return ms > 0 ? formatTime(ms) : ''
}

function loadData(): void {
  // 未登录不发请求（本页由路由守卫跳登录页，这里是兜底，避免 401 弹窗）
  if (!getToken()) return
  loading.value = true
  loadError.value = false
  listNoteDrafts().then(response => {
    list.value = response.data || []
  }).catch(() => {
    loadError.value = true
  }).finally(() => {
    loading.value = false
  })
}

function continueDraft(draft: SgjNoteDraft): void {
  // 一律按 draftId 继续写（草稿自己带着 noteId，编辑态会在表单里回填那篇笔记）。
  // 编辑页把这种情况当作「打开这份草稿」而不是「从笔记里恢复」：不弹恢复提示条（#32）
  if (!draft.draftId) return
  router.push({ path: '/note/edit', query: { draftId: String(draft.draftId) } })
}

function handleDelete(draft: SgjNoteDraft, index: number): void {
  if (!draft.draftId) return
  proxy.$modal.confirm(`删除草稿「${draftLabel(draft)}」？删除后无法恢复，草稿不进回收站。`).then(() => {
    return delNoteDraft(draft.draftId!)
  }).then(() => {
    list.value = list.value.filter(item => item.draftId !== draft.draftId)
    announce.value = `已删除草稿「${draftLabel(draft)}」`
    // 触发删除的那一行已经不在了，把焦点交给下一行的「继续写」；列表删空时交给空态入口
    nextTick(() => {
      const next = actionRefs.value[Math.min(index, list.value.length - 1)]
      if (next) next.focus()
      else (document.querySelector('.state-link') as HTMLElement | null)?.focus()
    })
  }).catch(() => {})
}

loadData()
</script>

<style scoped lang="scss">
.note-draft-page {
  color: var(--sgj-text);
  /* 移动端底部固定的标签栏（60px）不得盖住内容 */
  padding-bottom: 76px;
  padding-bottom: calc(76px + env(safe-area-inset-bottom));
}

.backlink {
  display: inline-block;
  margin-bottom: 14px;
  font-size: 13px;
  color: var(--sgj-text-2);
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}

.head {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 6px;

  h1 {
    margin: 0;
    font-family: var(--sgj-font-serif);
    font-weight: 700;
    font-size: 26px;
  }

  .sub {
    color: var(--sgj-text-2);
    font-size: 13px;
    padding-bottom: 3px;
  }
}

.dlist {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.draft {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
  background: var(--sgj-bg-card);
  border: 1px solid var(--sgj-border-card);
  border-radius: 16px;
  box-shadow: var(--sgj-shadow-sm);
  transition: box-shadow 0.16s;

  &:hover {
    box-shadow: var(--sgj-shadow-hover);
  }
}

.d-main {
  flex: 1;
  min-width: 0;
}

.d-title {
  margin: 0;
  font-family: var(--sgj-font-serif);
  font-size: 16px;
  font-weight: 500;
  line-height: 24px;
  word-break: break-word;

  /* 无标题草稿展示正文首行，最多两行，超出裁掉 */
  &.clamp {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    color: var(--sgj-text-2);
  }
}

.d-sub {
  margin: 6px 0 0;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--sgj-text-2);

  .d-item {
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .d-time {
    font-variant-numeric: tabular-nums;
  }
}

.d-acts {
  flex-shrink: 0;
  display: flex;
  gap: 8px;
}

.btn {
  border: 1px solid transparent;
  cursor: pointer;
  border-radius: 999px;
  height: 34px;
  padding: 0 14px;
  font-size: 12.5px;
  background: var(--sgj-primary);
  color: #fff;

  &:hover {
    background: var(--sgj-primary-dark);
  }

  &:focus-visible {
    outline: 2px solid var(--sgj-primary);
    outline-offset: 2px;
  }

  &.line,
  &.danger {
    background: transparent;
    color: var(--sgj-text-2);
    border-color: var(--sgj-border);
  }

  &.danger {
    color: var(--sgj-danger);
  }

  &.line:hover {
    background: var(--sgj-bg-deep);
    color: var(--sgj-text);
  }

  &.danger:hover {
    background: var(--sgj-bg-deep);
    border-color: var(--sgj-danger);
  }
}

html.dark .btn:not(.line):not(.danger) {
  color: #171b1a;
}

.draft-state {
  list-style: none;
}

.state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: min(48vh, 400px);
  padding: 32px 16px;
  text-align: center;
  color: var(--sgj-text-2);

  .state-icon {
    font-size: 30px;
    opacity: 0.55;
  }

  .state-title {
    color: var(--sgj-text);
    font-family: var(--sgj-font-serif);
    font-size: 16px;
  }

  .state-desc {
    font-size: 13px;
  }

  .state-link {
    color: var(--sgj-primary);
    font-size: 13px;
  }
}

/* 加载骨架：保持列表节奏，不闪一屏空白 */
.sk {
  height: 78px;
  display: block;
  padding: 18px;

  .sk-line {
    height: 12px;
    border-radius: 6px;
    background: var(--sgj-bg-deep);

    &.w1 {
      width: 42%;
    }

    &.w2 {
      width: 26%;
      margin-top: 12px;
    }
  }
}

.sr {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 768px) {
  .head h1 {
    font-size: 22px;
  }

  .draft {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .d-acts {
    width: 100%;

    /* 移动端触控目标补到 44px，主操作占满剩余宽度 */
    .btn {
      height: 44px;
      flex: 1;
    }
  }
}
</style>
