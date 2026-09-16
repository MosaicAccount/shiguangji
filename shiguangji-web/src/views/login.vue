<template>
  <div class="login">
    <!-- 左：品牌面板（冷炭灰 studio） -->
    <aside class="login-brand">
      <div class="brand-top">
        <div class="film-strip" aria-hidden="true">
          <span v-for="i in 10" :key="i" class="perf" />
        </div>
        <div class="brand-logo">拾光记</div>
        <div class="brand-logo-en">S H I G U A N G J I  ·  C O N S O L E</div>
      </div>
      <div class="brand-slogan">
        <div class="brand-h">把日子，过成照片</div>
        <div class="brand-sub">管理后台 · 内容 / 用户 / 数据，一处安放。</div>
      </div>
      <div class="brand-stats">
        <div class="brand-stat">
          <span class="brand-num">1,286</span>
          <span class="brand-label">条时光记录</span>
        </div>
        <div class="brand-stat">
          <span class="brand-num">12</span>
          <span class="brand-label">个活跃用户</span>
        </div>
      </div>
      <div class="brand-footer">© 2026 SHIGUANGJI</div>
    </aside>

    <!-- 右：登录表单 -->
    <main class="login-main">
      <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
        <div class="mobile-brand" aria-hidden="true">
          <div class="film-strip">
            <span v-for="i in 10" :key="i" class="perf" />
          </div>
          <div class="mobile-logo">拾光记</div>
          <div class="mobile-logo-en">S H I G U A N G J I · C O N S O L E</div>
        </div>
        <div class="form-title">欢迎回来</div>
        <div class="form-sub">登录拾光记管理台</div>
        <div class="form-label">账号</div>
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            type="text"
            size="large"
            auto-complete="off"
            placeholder="请输入账号"
          />
        </el-form-item>
        <div class="form-label">密码</div>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            size="large"
            auto-complete="off"
            placeholder="请输入密码"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item prop="code" v-if="captchaEnabled">
          <el-input
            v-model="loginForm.code"
            class="code-input"
            size="large"
            auto-complete="off"
            placeholder="验证码"
            @keyup.enter="handleLogin"
          />
          <div class="login-code">
            <img :src="codeUrl" @click="getCode" class="login-code-img" alt="验证码" />
          </div>
        </el-form-item>
        <div class="form-row">
          <el-checkbox v-model="loginForm.rememberMe">记住我</el-checkbox>
          <span v-if="register" class="form-register">
            <router-link class="link-type" :to="'/register'">注册账号</router-link>
          </span>
          <span class="form-forgot">忘记密码？</span>
        </div>
        <el-form-item style="width:100%; margin-bottom: 12px;">
          <el-button
            :loading="loading"
            size="large"
            type="primary"
            class="login-btn"
            @click.prevent="handleLogin"
          >
            <span v-if="!loading">登 录</span>
            <span v-else>登 录 中...</span>
          </el-button>
        </el-form-item>
      </el-form>
    </main>
  </div>
</template>

<script setup lang="ts">
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from "@/utils/jsencrypt"
import useUserStore from '@/store/modules/user'
import type { CaptchaInfoResult } from '@/types/api/login'
import type { LoginForm } from '@/types/api/login'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance() as { proxy: any }

const loginForm = ref<LoginForm>({
  username: "",
  password: "",
  rememberMe: false,
  code: "",
  uuid: ""
})

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}

const codeUrl = ref("")
const loading = ref(false)
// 验证码开关
const captchaEnabled = ref(true)
// 注册开关
const register = ref(false)
const redirect = ref<string | undefined>(undefined)

watch(route, (newRoute: any) => {
    redirect.value = (newRoute.query && newRoute.query.redirect) as string | undefined
}, { immediate: true })

function handleLogin(): void {
  proxy.$refs.loginRef.validate((valid: boolean) => {
    if (valid) {
      loading.value = true
      // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 })
        Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 })
        Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 })
      } else {
        // 否则移除
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove("rememberMe")
      }
      // 调用action的登录方法
      userStore.login(loginForm.value).then(() => {
        const query = route.query
        const otherQueryParams = Object.keys(query).reduce((acc: Record<string, any>, cur) => {
          if (cur !== "redirect") {
            acc[cur] = query[cur]
          }
          return acc
        }, {})
        router.push({ path: redirect.value || "/", query: otherQueryParams })
      }).catch(() => {
        loading.value = false
        // 重新获取验证码
        if (captchaEnabled.value) {
          getCode()
        }
      })
    }
  })
}

function getCode(): void {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      loginForm.value.uuid = res.uuid
    }
  })
}

function getCookie(): void {
  const username = Cookies.get("username")
  const password = Cookies.get("password")
  const rememberMe = Cookies.get("rememberMe")
  loginForm.value.username = username === undefined ? loginForm.value.username : username
  loginForm.value.password = password === undefined ? loginForm.value.password : (decrypt(password) || '')
  loginForm.value.rememberMe = rememberMe === undefined ? false : Boolean(rememberMe)
}

getCode()
getCookie()
</script>

<style lang='scss' scoped>
.login {
  display: flex;
  min-height: 100%;
  background: var(--el-bg-color);
}

/* ===== 左：品牌面板 ===== */
.login-brand {
  position: relative;
  width: 760px;
  min-width: 760px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 60px 90px;
  background: #282e2c;
  color: #e7ece9;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    right: -60px;
    top: 40px;
    width: 420px;
    height: 420px;
    border-radius: 50%;
    background: rgba(168, 95, 82, 0.3);
  }

  &::after {
    content: '';
    position: absolute;
    left: -80px;
    bottom: -60px;
    width: 360px;
    height: 360px;
    border-radius: 50%;
    background: rgba(93, 111, 102, 0.35);
  }

  .brand-top,
  .brand-slogan,
  .brand-stats,
  .brand-footer {
    position: relative;
    z-index: 1;
  }

  .film-strip {
    display: flex;
    gap: 14px;
    margin-bottom: 28px;

    .perf {
      width: 26px;
      height: 10px;
      border-radius: 5px;
      background: rgba(255, 255, 255, 0.12);
    }
  }

  .brand-logo {
    font-family: var(--sgj-font-serif);
    font-weight: 700;
    font-size: 34px;
    line-height: 1.3;
  }

  .brand-logo-en {
    font-size: 11px;
    letter-spacing: 3px;
    color: #aeb8b3;
    margin-top: 8px;
  }

  .brand-slogan {
    margin: 130px 0 0;

    .brand-h {
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 44px;
      line-height: 1.4;
    }

    .brand-sub {
      margin-top: 18px;
      font-size: 15px;
      color: #aeb8b3;
    }
  }

  .brand-stats {
    display: flex;
    gap: 130px;
    margin-top: 84px;

    .brand-stat {
      display: flex;
      flex-direction: column;

      .brand-num {
        font-family: var(--sgj-font-serif);
        font-weight: 700;
        font-size: 40px;
        line-height: 1.3;
        color: #efe0dc;
      }

      .brand-label {
        font-size: 13px;
        color: #aeb8b3;
        margin-top: 8px;
      }
    }
  }

  .brand-footer {
    margin-top: auto;
    font-size: 11px;
    color: rgba(255, 255, 255, 0.25);
    letter-spacing: 1px;
  }
}

html.dark .login-brand {
  background: #111514;

  .brand-stats .brand-num {
    color: #c27a6d;
  }
}

/* ===== 右：表单 ===== */
.login-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.login-form {
  width: 400px;
  max-width: 100%;

  .form-title {
    font-family: var(--sgj-font-serif);
    font-weight: 700;
    font-size: 32px;
    color: var(--el-text-color-primary);
  }

  .form-sub {
    font-size: 15px;
    color: var(--el-text-color-secondary);
    margin: 10px 0 36px;
  }

  .form-label {
    font-size: 13px;
    font-weight: 500;
    color: var(--el-text-color-regular);
    margin-bottom: 8px;
  }

  .el-form-item {
    margin-bottom: 22px;
  }

  /* el-input 内部结构需要 :deep 才能命中，否则只有 EP 默认样式生效 */
  :deep(.el-input__wrapper) {
    border-radius: 10px;
    padding: 6px 14px;
  }

  :deep(.el-input__inner) {
    height: 36px;
    font-size: 14px;
  }

  .code-input {
    flex: 1;
  }

  .login-code {
    flex-shrink: 0;
    margin-left: 12px;

    .login-code-img {
      display: block;
      width: 110px;
      height: 48px;
      border: 1px solid var(--el-border-color);
      border-radius: 10px;
      object-fit: cover;
      cursor: pointer;
    }
  }

  .form-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin: -6px 0 20px;

    .el-checkbox {
      color: var(--el-text-color-regular);
    }

    .form-register {
      font-size: 13px;

      .link-type {
        color: var(--el-color-primary);
      }
    }

    .form-forgot {
      font-size: 13px;
      color: var(--el-color-primary);
      cursor: pointer;

      &:hover {
        color: var(--el-color-primary-light-3);
      }
    }
  }

  .login-btn {
    width: 100%;
    height: 48px;
    border-radius: 24px;
    font-size: 15px;
    font-weight: 500;
    letter-spacing: 2px;
  }
}

/* 移动端品牌头：仅单栏布局显示，桌面由左侧品牌面板承担 */
.mobile-brand {
  display: none;
}

/* 单栏（≤1240px）时品牌面板隐藏，整页沿用它的冷炭灰胶片视觉 */
@media (max-width: 1240px) {
  .login-brand {
    display: none;
  }

  .login {
    background: #282e2c;
  }

  .login-main {
    position: relative;
    overflow: hidden;
    padding: 48px 32px;

    /* 与品牌面板同款的装饰圆 */
    &::before {
      content: '';
      position: absolute;
      right: -80px;
      top: -60px;
      width: 300px;
      height: 300px;
      border-radius: 50%;
      background: rgba(168, 95, 82, 0.22);
    }

    &::after {
      content: '';
      position: absolute;
      left: -90px;
      bottom: -80px;
      width: 260px;
      height: 260px;
      border-radius: 50%;
      background: rgba(93, 111, 102, 0.25);
    }

    .login-form {
      position: relative;
      z-index: 1;
    }
  }

  .mobile-brand {
    display: block;
    margin-bottom: 32px;

    .film-strip {
      display: flex;
      gap: 10px;
      margin-bottom: 20px;

      .perf {
        width: 22px;
        height: 8px;
        border-radius: 4px;
        background: rgba(255, 255, 255, 0.12);
      }
    }

    .mobile-logo {
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 30px;
      line-height: 1.3;
      color: #e7ece9;
    }

    .mobile-logo-en {
      margin-top: 6px;
      font-size: 10px;
      letter-spacing: 3px;
      color: #aeb8b3;
    }
  }

  /* 画布常暗，表单文字固定奶油色系，不随日夜主题翻转 */
  .login-form {
    .form-title {
      color: #e7ece9;
    }

    .form-sub {
      color: #aeb8b3;
    }

    .form-label {
      color: #c7d0cb;
    }

    :deep(.el-input__wrapper) {
      background-color: rgba(255, 255, 255, 0.06);
      box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.18) inset;
    }

    :deep(.el-input__wrapper.is-focus) {
      box-shadow: 0 0 0 1px var(--el-color-primary) inset;
    }

    :deep(.el-input__inner) {
      color: #e7ece9;
      caret-color: #e7ece9;
    }

    :deep(.el-input__inner::placeholder) {
      color: rgba(231, 236, 233, 0.38);
    }

    :deep(.el-checkbox__label) {
      color: #aeb8b3;
    }

    :deep(.el-checkbox__inner) {
      background-color: rgba(255, 255, 255, 0.06);
      border-color: rgba(255, 255, 255, 0.3);
    }
  }
}

@media (max-width: 480px) {
  .login-main {
    padding: 40px 24px;
  }

  .login-form {
    .form-title {
      font-size: 26px;
    }

    .form-sub {
      margin: 8px 0 28px;
    }
  }
}
</style>
