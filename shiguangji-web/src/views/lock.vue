<template>
  <div class="lock-screen">
    <!-- 冷雾胶片背景：品牌色晕 + 胶片颗粒 -->
    <div class="film-halo halo-terracotta" aria-hidden="true"></div>
    <div class="film-halo halo-sage" aria-hidden="true"></div>
    <div class="film-grain" aria-hidden="true"></div>

    <!-- 片孔装饰条 -->
    <div class="film-strip" aria-hidden="true">
      <span v-for="i in 10" :key="i" class="perf" />
    </div>

    <!-- 品牌 -->
    <header class="lock-brand">
      <div class="brand-logo">拾光记</div>
      <div class="brand-en">S H I G U A N G J I&ensp;·&ensp;C O N S O L E</div>
    </header>

    <!-- 时钟 -->
    <div class="lock-clock">
      <div class="clock-time">{{ currentTime }}<span class="clock-seconds">{{ currentSeconds }}</span></div>
      <div class="clock-date">{{ currentDate }}</div>
    </div>

    <!-- 锁屏卡片 -->
    <div class="lock-card">
      <div class="avatar-wrap">
        <img :src="userStore.avatar" class="lock-avatar" @error="onAvatarError" />
        <div class="lock-badge">
          <svg viewBox="0 0 24 24" width="12" height="12" aria-hidden="true">
            <rect x="5" y="10.5" width="14" height="10" rx="2.5" fill="currentColor" />
            <path d="M8 10.5V8a4 4 0 1 1 8 0v2.5" fill="none" stroke="currentColor" stroke-width="2.4" />
          </svg>
        </div>
      </div>
      <div class="lock-username">{{ userStore.nickName }}</div>
      <div class="lock-hint">系统已锁定，请输入密码解锁</div>

      <div class="input-wrap" :class="{ shake: isShaking }">
        <input
          ref="passwordInput"
          v-model="password"
          type="password"
          placeholder="请输入登录密码"
          class="lock-input"
          @keydown.enter="handleUnlock"
          autocomplete="off"
        />
      </div>

      <button class="unlock-btn" :disabled="loading" @click="handleUnlock">
        <span v-if="!loading">解 锁</span>
        <span v-else>解 锁 中...</span>
      </button>

      <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

      <div class="lock-footer">
        <a href="javascript:;" @click="goLogin">退出重新登录</a>
      </div>
    </div>

    <footer class="lock-copyright">© 2026 SHIGUANGJI</footer>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import useLockStore from '@/store/modules/lock'
import { unlockScreen } from '@/api/login'
import defAva from '@/assets/images/profile.jpg'

const router = useRouter()
const userStore = useUserStore()
const lockStore = useLockStore()

const password = ref<string>('')
const loading = ref<boolean>(false)
const errorMsg = ref<string>('')
const isShaking = ref<boolean>(false)
const currentTime = ref<string>('')
const currentSeconds = ref<string>('')
const currentDate = ref<string>('')
const passwordInput = ref<HTMLInputElement | null>(null)

let timer: any = null

const onAvatarError = (e: Event) => {
  (e.target as HTMLImageElement).src = defAva
}

const startClock = () => {
  const update = () => {
    const now = new Date()
    const pad = (n: number) => String(n).padStart(2, '0')
    currentTime.value = `${pad(now.getHours())}:${pad(now.getMinutes())}`
    currentSeconds.value = pad(now.getSeconds())
    const days = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
    currentDate.value = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 · ${days[now.getDay()]}`
  }
  update()
  timer = setInterval(update, 1000)
}

const handleUnlock = async () => {
  if (!password.value) {
    showError('请输入密码')
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    await unlockScreen(password.value)
    const lockPath = lockStore.lockPath
    lockStore.unlockScreen()
    router.replace(lockPath)
  } catch (err: any) {
    const msg = err.message || err.toString()
    showError(msg)
    password.value = ''
    nextTick(() => passwordInput.value?.focus())
  } finally {
    loading.value = false
  }
}

const showError = (msg: string) => {
  errorMsg.value = msg
  isShaking.value = true
  setTimeout(() => { isShaking.value = false }, 600)
}

const goLogin = () => {
  lockStore.unlockScreen()
  userStore.logOut().then(() => {
    router.push('/login')
  }).catch(() => {})
}

onMounted(() => {
  startClock()
  nextTick(() => passwordInput.value?.focus())
})

onBeforeUnmount(() => {
  clearInterval(timer)
})
</script>

<style lang="scss" scoped>
/* 锁屏 = 登录品牌面板的延续：冷炭灰 studio，双主题仅背景深度不同（对齐 login.vue） */
.lock-screen {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #282e2c;
  color: #e7ece9;
  font-family: var(--sgj-font-sans);
  overflow: hidden;
}

html.dark .lock-screen {
  background: #111514;
}

/* ===== 背景装饰：品牌色晕（同登录页左板的双圆） ===== */
.film-halo {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.halo-terracotta {
  right: -120px;
  top: -80px;
  width: 560px;
  height: 560px;
  background: rgba(168, 95, 82, 0.22);
  animation: halo-drift 26s ease-in-out infinite alternate;
}

.halo-sage {
  left: -140px;
  bottom: -120px;
  width: 520px;
  height: 520px;
  background: rgba(93, 111, 102, 0.28);
  animation: halo-drift 32s ease-in-out infinite alternate-reverse;
}

@keyframes halo-drift {
  from { transform: translate(0, 0); }
  to   { transform: translate(-36px, 28px); }
}

/* 胶片颗粒 */
.film-grain {
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.05;
  background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/><feColorMatrix type='saturate' values='0'/></filter><rect width='100%25' height='100%25' filter='url(%23n)'/></svg>");
}

/* ===== 片孔装饰条（同登录页） ===== */
.film-strip {
  position: absolute;
  top: 44px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 14px;

  .perf {
    width: 22px;
    height: 8px;
    border-radius: 4px;
    background: rgba(255, 255, 255, 0.1);
  }
}

/* ===== 品牌 ===== */
.lock-brand {
  position: relative;
  text-align: center;
  animation: rise 0.6s ease both;

  .brand-logo {
    font-family: var(--sgj-font-serif);
    font-weight: 700;
    font-size: 24px;
    letter-spacing: 2px;
  }

  .brand-en {
    margin-top: 8px;
    font-size: 10px;
    letter-spacing: 3px;
    color: #aeb8b3;
  }
}

/* ===== 时钟 ===== */
.lock-clock {
  position: relative;
  text-align: center;
  margin: 44px 0 48px;
  animation: rise 0.6s ease 0.06s both;

  .clock-time {
    font-family: var(--sgj-font-serif);
    font-weight: 700;
    font-size: clamp(64px, 10vw, 96px);
    line-height: 1.1;
    color: #e7ece9;
    font-variant-numeric: tabular-nums;
  }

  .clock-seconds {
    margin-left: 10px;
    font-size: 0.36em;
    color: #aeb8b3;
  }

  .clock-date {
    margin-top: 10px;
    font-size: 14px;
    letter-spacing: 2px;
    color: #aeb8b3;
  }
}

/* ===== 锁屏卡片 ===== */
.lock-card {
  position: relative;
  width: 360px;
  max-width: calc(100vw - 48px);
  padding: 36px 40px 28px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--sgj-radius-xl, 20px);
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.35);
  animation: rise 0.6s ease 0.12s both;
}

.avatar-wrap {
  position: relative;
  margin-bottom: 16px;
}

.lock-avatar {
  display: block;
  width: 76px;
  height: 76px;
  border-radius: 50%;
  border: 3px solid rgba(168, 95, 82, 0.55);
  object-fit: cover;
}

.lock-badge {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #a85f52;
  color: #fff;
  border: 3px solid #2c322f;
}

.lock-username {
  font-family: var(--sgj-font-serif);
  font-weight: 700;
  font-size: 18px;
  letter-spacing: 1px;
  color: #e7ece9;
}

.lock-hint {
  margin: 6px 0 24px;
  font-size: var(--sgj-font-sm, 13px);
  color: #aeb8b3;
}

/* ===== 输入与按钮 ===== */
.input-wrap {
  width: 100%;
  border-radius: var(--sgj-radius-md, 10px);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  transition: border-color 0.25s, box-shadow 0.25s, background 0.25s;
}

.input-wrap:focus-within {
  border-color: #c27a6d;
  background: rgba(255, 255, 255, 0.08);
  box-shadow: 0 0 0 3px rgba(194, 122, 109, 0.15);
}

.input-wrap.shake {
  animation: shake 0.5s ease;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-8px); }
  40% { transform: translateX(8px); }
  60% { transform: translateX(-6px); }
  80% { transform: translateX(6px); }
}

.lock-input {
  width: 100%;
  box-sizing: border-box;
  background: transparent;
  border: none;
  outline: none;
  height: 44px;
  padding: 0 16px;
  color: #e7ece9;
  font-size: 14px;
}

.lock-input::placeholder {
  color: rgba(231, 236, 233, 0.35);
}

.unlock-btn {
  width: 100%;
  height: 44px;
  margin-top: 14px;
  border: none;
  border-radius: var(--sgj-radius-pill, 999px);
  background: #a85f52;
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 2px;
  cursor: pointer;
  transition: background 0.25s, transform 0.2s;
}

.unlock-btn:hover:not(:disabled) {
  background: #b56b5d;
}

.unlock-btn:active:not(:disabled) {
  background: #874a41;
  transform: scale(0.98);
}

.unlock-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error-msg {
  margin-top: 14px;
  font-size: var(--sgj-font-sm, 13px);
  color: #d67870;
  text-align: center;
  animation: rise 0.3s ease;
}

.lock-footer {
  margin-top: 22px;
}

.lock-footer a {
  font-size: var(--sgj-font-sm, 13px);
  color: #d9a9a0;
  text-decoration: none;
  transition: color 0.2s;
}

.lock-footer a:hover {
  color: #eac9c1;
}

/* ===== 页脚 ===== */
.lock-copyright {
  position: absolute;
  bottom: 28px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 11px;
  letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.25);
}

@keyframes rise {
  from { opacity: 0; transform: translateY(12px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* 窄屏收紧留白 */
@media (max-width: 480px) {
  .lock-clock {
    margin: 32px 0 36px;
  }

  .lock-card {
    padding: 30px 26px 24px;
  }
}

/* 弱化动效偏好 */
@media (prefers-reduced-motion: reduce) {
  .film-halo {
    animation: none;
  }

  .lock-brand,
  .lock-clock,
  .lock-card {
    animation: none;
  }
}
</style>
