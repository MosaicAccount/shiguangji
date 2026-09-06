<template>
  <div class="sidebar-theme-wrapper theme-dark sidebar-container" :class="{ 'has-logo': showLogo }">
    <logo v-if="showLogo" :collapse="isCollapse" />
    <el-scrollbar wrap-class="scrollbar-wrapper" class="sidebar-scroll">
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :background-color="getMenuBackground"
        :text-color="getMenuTextColor"
        :unique-opened="true"
        :active-text-color="menuActiveTextColor"
        :collapse-transition="false"
        mode="vertical"
        class="theme-dark"
      >
        <sidebar-item
          v-for="(route, index) in sidebarRouters"
          :key="route.path + index"
          :item="route"
          :base-path="route.path"
        />
      </el-menu>
    </el-scrollbar>
    <!-- 底部坞：全局工具 + 账号（全站唯一账号入口，顶部栏不再重复） -->
    <div class="sidebar-dock" :class="{ 'is-mini': isCollapse }">
      <template v-if="!isCollapse">
        <router-link to="/home" class="dock-front" title="返回前台">
          <el-icon :size="14"><home-filled /></el-icon>
          <span>返回前台</span>
        </router-link>
        <div class="dock-tools">
          <el-tooltip content="收起菜单" effect="dark" placement="top">
            <div class="dock-btn" @click="toggleSideBar">
              <el-icon><fold /></el-icon>
            </div>
          </el-tooltip>
          <el-tooltip content="菜单搜索" effect="dark" placement="top">
            <div class="dock-btn">
              <header-search />
            </div>
          </el-tooltip>
          <el-tooltip content="布局大小" effect="dark" placement="top">
            <div class="dock-btn">
              <size-select />
            </div>
          </el-tooltip>
          <el-tooltip content="主题模式" effect="dark" placement="top">
            <div class="dock-btn" @click="toggleTheme">
              <el-icon>
                <sunny v-if="settingsStore.isDark" />
                <moon v-else />
              </el-icon>
            </div>
          </el-tooltip>
          <el-tooltip content="全屏" effect="dark" placement="top">
            <div class="dock-btn">
              <screenfull />
            </div>
          </el-tooltip>
          <el-tooltip content="消息通知" effect="dark" placement="top">
            <div class="dock-btn">
              <header-notice />
            </div>
          </el-tooltip>
        </div>
      </template>
      <template v-else>
        <div class="dock-stack">
          <el-tooltip content="展开菜单" effect="dark" placement="right">
            <div class="dock-btn" @click="toggleSideBar">
              <el-icon><expand /></el-icon>
            </div>
          </el-tooltip>
          <el-tooltip content="返回前台" effect="dark" placement="right">
            <router-link to="/home" class="dock-btn">
              <el-icon :size="16"><home-filled /></el-icon>
            </router-link>
          </el-tooltip>
          <el-tooltip content="主题模式" effect="dark" placement="right">
            <div class="dock-btn" @click="toggleTheme">
              <el-icon>
                <sunny v-if="settingsStore.isDark" />
                <moon v-else />
              </el-icon>
            </div>
          </el-tooltip>
          <el-tooltip content="全屏" effect="dark" placement="right">
            <div class="dock-btn">
              <screenfull />
            </div>
          </el-tooltip>
          <el-tooltip content="消息通知" effect="dark" placement="right">
            <div class="dock-btn">
              <header-notice />
            </div>
          </el-tooltip>
        </div>
      </template>
      <!-- 用户卡：点击弹出账号菜单 -->
      <el-dropdown trigger="click" placement="top-start" popper-class="sgj-account-popper" @command="handleCommand">
        <div v-if="!isCollapse" class="sidebar-userbar">
          <span class="userbar-avatar">{{ avatarChar }}</span>
          <div class="userbar-text">
            <span class="userbar-name">{{ userStore.nickName || '管理员' }}</span>
            <span class="userbar-role">super · 全部权限</span>
          </div>
        </div>
        <div v-else class="sidebar-userbar-mini">
          <span class="userbar-avatar">{{ avatarChar }}</span>
        </div>
        <template #dropdown>
          <div class="account-menu-head">
            <span class="account-menu-avatar">{{ avatarChar }}</span>
            <div class="account-menu-meta">
              <span class="account-menu-name">{{ userStore.nickName || '管理员' }}</span>
              <span class="account-menu-role">super · 全部权限</span>
            </div>
          </div>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><user /></el-icon>个人中心
            </el-dropdown-item>
            <el-dropdown-item command="lockScreen">
              <el-icon><lock /></el-icon>锁定屏幕
            </el-dropdown-item>
            <el-dropdown-item command="setLayout" v-if="settingsStore.showSettings">
              <el-icon><setting /></el-icon>布局设置
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><switch-button /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import { Fold, Expand, Sunny, Moon, User, Lock, Setting, SwitchButton, HomeFilled } from '@element-plus/icons-vue'
import Logo from './Logo.vue'
import SidebarItem from './SidebarItem.vue'
import HeaderSearch from '@/components/HeaderSearch/index.vue'
import SizeSelect from '@/components/SizeSelect/index.vue'
import Screenfull from '@/components/Screenfull/index.vue'
import HeaderNotice from '../HeaderNotice/index.vue'
import variables from '@/assets/styles/variables.module.scss'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'
import useUserStore from '@/store/modules/user'
import useLockStore from '@/store/modules/lock'
import { animateThemeToggle } from '@/utils/theme'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const settingsStore = useSettingsStore()
const permissionStore = usePermissionStore()
const userStore = useUserStore()
const lockStore = useLockStore()

const emits = defineEmits(['setLayout'])

const sidebarRouters = computed(() => permissionStore.sidebarRouters)
const showLogo = computed(() => settingsStore.sidebarLogo)
const theme = computed(() => settingsStore.theme)
/** 折叠态弹出子菜单（teleport 到 body，不吃侧栏内覆盖样式）的激活文字色：暗色下用提亮陶土，深底上可读 */
const menuActiveTextColor = computed(() => (settingsStore.isDark ? '#C27A6D' : settingsStore.theme))
const isCollapse = computed(() => !appStore.sidebar.opened)

// 获取菜单背景色
const getMenuBackground = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-bg)'
  }
  return variables.menuBg
})

// 获取菜单文字颜色
const getMenuTextColor = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-text)'
  }
  return variables.menuText
})

const activeMenu = computed(() => {
  const { meta, path } = route
  if (meta.activeMenu) {
    return meta.activeMenu
  }
  return path
})

/** 用户栏首字 */
const avatarChar = computed(() => {
  const name = userStore.nickName || ''
  return (name.trim()[0] || '管').slice(0, 1)
})

function toggleSideBar(): void {
  appStore.toggleSideBar()
}

function toggleTheme(event?: MouseEvent): Promise<void> {
  return animateThemeToggle(() => settingsStore.toggleTheme(), event)
}

function handleCommand(command: string): void {
  switch (command) {
    case 'profile':
      goProfile()
      break
    case 'lockScreen':
      lockScreen()
      break
    case 'setLayout':
      emits('setLayout')
      break
    case 'logout':
      logout()
      break
    default:
      break
  }
}

/** 用户卡：个人中心入口 */
function goProfile(): void {
  router.push('/user/profile')
}

function lockScreen(): void {
  lockStore.lockScreen(route.fullPath)
  router.push('/lock')
}

function logout(): void {
  ElMessageBox.confirm('确定注销并退出系统吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logOut().then(() => {
      // 退出登录后直接返回前台首页（博客式公开首页，访客可浏览），而非登录页
      location.href = '/home'
    }).catch(() => {})
  }).catch(() => { })
}
</script>

<style lang="scss" scoped>
.sidebar-container {
  display: flex;
  flex-direction: column;
  background-color: v-bind(getMenuBackground);

  /* 覆盖全局 #app .sidebar-container .el-scrollbar 高度，改为弹性中段（底部留坞区） */
  :deep(.el-scrollbar) {
    height: auto !important;
    flex: 1 1 0;
    min-height: 0;
  }

  .el-menu {
    border: none;
    height: 100%;
    width: 100% !important;
    padding-bottom: 12px;

    .el-menu-item, .el-sub-menu__title {
      &:hover {
        background-color: var(--menu-hover, rgba(0, 0, 0, 0.06)) !important;
      }
    }

    .el-menu-item {
      color: v-bind(getMenuTextColor);

      &.is-active {
        color: var(--menu-active-text, #ffffff);
        background-color: var(--menu-active-bg, #A85F52) !important;
      }
    }

    .el-sub-menu__title {
      color: v-bind(getMenuTextColor);
    }
  }

  /* ---- 底部坞 ---- */
  .sidebar-dock {
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 12px 12px 14px;
    border-top: 1px solid rgba(255, 255, 255, 0.08);

    /* 折叠态（54px）：收紧左右留白，容纳 40px 头像钮 */
    &.is-mini {
      padding: 10px 5px 12px;
      gap: 8px;
    }
  }

  .dock-front {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    height: 32px;
    border: 1px solid rgba(255, 255, 255, 0.22);
    border-radius: 16px;
    font-size: 13px;
    color: #e7ece9;
    text-decoration: none;
    white-space: nowrap;
    transition: color 0.2s, border-color 0.2s, background 0.2s;

    &:hover {
      color: #fff;
      border-color: var(--el-color-primary);
      background: rgba(168, 95, 82, 0.2);
    }
  }

  .dock-tools {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 4px;
  }

  .dock-stack {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
  }

  .dock-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    font-size: 15px;
    color: rgba(231, 236, 233, 0.78);
    cursor: pointer;
    transition: background 0.2s, color 0.2s;

    &:hover {
      background: rgba(255, 255, 255, 0.09);
      color: #fff;
    }
  }

  /* 用户卡（设计稿：深色面板 · 圆角 12）：点击弹出账号菜单 */
  .sidebar-userbar {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px;
    background: #333a37;
    border-radius: 12px;
    cursor: pointer;
    overflow: hidden;
    transition: box-shadow 0.2s;

    &:hover {
      box-shadow: 0 0 0 1px var(--el-color-primary);
    }
  }

  .sidebar-userbar-mini {
    width: fit-content;
    margin: 0 auto;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 2px;
    border-radius: 50%;
    cursor: pointer;
    transition: box-shadow 0.2s;

    &:hover {
      box-shadow: 0 0 0 2px var(--el-color-primary);
    }
  }

  .userbar-avatar {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: var(--el-color-primary);
    color: #fff;
    font-family: var(--sgj-font-serif);
    font-weight: 700;
    font-size: 12px;
    flex-shrink: 0;
  }

  .userbar-text {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .userbar-name {
    font-size: 13px;
    font-weight: 500;
    color: #e7ece9;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .userbar-role {
    font-size: 10px;
    color: #aeb8b3;
    margin-top: 2px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>

<style lang="scss">
/* 坞区图标统一 16px：el-icon 与 svg-icon 默认字号不一致，会导致大小混杂 */
#app .sidebar-container .sidebar-dock .dock-btn .el-icon,
#app .sidebar-container .sidebar-dock .dock-btn svg {
  width: 16px !important;
  height: 16px !important;
  font-size: 16px !important;
}

/* 图标颜色统一继承按钮色：el-dropdown 等子组件会自带 #606266 文字色 */
#app .sidebar-container .sidebar-dock .dock-btn .el-dropdown,
#app .sidebar-container .sidebar-dock .dock-btn .el-icon,
#app .sidebar-container .sidebar-dock .dock-btn svg {
  color: inherit !important;
}

/* 用户卡撑满坞区宽度：el-dropdown 默认 inline-flex 会把卡片缩成内容宽 */
#app .sidebar-container .sidebar-dock .el-dropdown {
  display: flex;
  width: 100%;
}

/* 胶囊按钮恢复 flex 布局：压过全局 #app .sidebar-container a 的 inline-block */
#app .sidebar-container .sidebar-dock a.dock-front,
#app .sidebar-container .sidebar-dock a.dock-btn {
  display: flex;
}

/* 折叠态的链接按钮保持圆形定宽 */
#app .sidebar-container .sidebar-dock a.dock-btn {
  width: 28px;
  flex-shrink: 0;
}

/* 夜间模式：无论侧栏风格选什么，坞区一律深底浅图标，保证与日间模式区分 */
html.dark #app .sidebar-container .sidebar-dock {
  border-top-color: rgba(255, 255, 255, 0.08);

  .dock-front {
    border-color: rgba(255, 255, 255, 0.22);
    color: #e7ece9;

    &:hover {
      color: #fff;
      border-color: var(--el-color-primary);
      background: rgba(168, 95, 82, 0.2);
    }
  }

  .dock-btn {
    color: rgba(231, 236, 233, 0.78);

    &:hover {
      background: rgba(255, 255, 255, 0.09);
      color: #fff;
    }
  }
}

/* 移动端：顶栏已保留「返回前台」，坞区内不再重复 */
@media (max-width: 991px) {
  #app .sidebar-container .sidebar-dock .dock-front {
    display: none;
  }
}

html.dark .sidebar-container .sidebar-userbar {
  background: #1a201e;
}

/* 夜间模式用户卡（设计稿暗色板）：提亮陶土头像 + 深墨首字 */
html.dark #app .sidebar-container .sidebar-userbar .userbar-avatar,
html.dark #app .sidebar-container .sidebar-userbar-mini .userbar-avatar {
  background: #c27a6d;
  color: #171b1a;
}

html.dark #app .sidebar-container .sidebar-userbar .userbar-name {
  color: #eef2f0;
}

html.dark #app .sidebar-container .sidebar-userbar .userbar-role {
  color: #9ca8a2;
}

/* ---- 账号菜单弹层（el-dropdown teleport 到 body，需全局样式）----
   默认弹层贴内容宽，四个短词挤成窄条：加宽到与用户卡协调，
   顶部补账号信息头，条目改圆角胶囊 + 主题色悬停 */
.sgj-account-popper.el-popper {
  min-width: 224px;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.14);

  .el-dropdown-menu {
    padding: 6px;
  }

  .account-menu-head {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px 12px;
    margin-bottom: 6px;
    border-bottom: 1px solid var(--el-border-color-extra-light);
  }

  .account-menu-avatar {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background: var(--el-color-primary);
    color: #fff;
    font-family: var(--sgj-font-serif);
    font-weight: 700;
    font-size: 14px;
    flex-shrink: 0;
  }

  .account-menu-meta {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .account-menu-name {
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .account-menu-role {
    font-size: 11px;
    color: var(--el-text-color-secondary);
    margin-top: 2px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .el-dropdown-menu__item {
    gap: 10px;
    height: 34px;
    line-height: 34px;
    padding: 0 12px;
    margin: 0 0 2px;
    border-radius: 8px;
    font-size: 13px;

    &:last-child {
      margin-bottom: 0;
    }

    .el-icon {
      margin-right: 0;
      font-size: 15px;
      color: var(--el-text-color-secondary);
    }

    &:not(.is-disabled):hover,
    &:not(.is-disabled):focus {
      background: var(--el-color-primary-light-9);
      color: var(--el-color-primary);

      .el-icon {
        color: var(--el-color-primary);
      }
    }
  }

  /* 退出登录：分隔线通到弹层边缘，悬停用危险色提示不可逆操作 */
  .el-dropdown-menu__item--divided {
    position: relative;
    margin-top: 6px;
    border-top: none;

    &::before {
      content: '';
      position: absolute;
      top: -4px;
      left: -6px;
      right: -6px;
      height: 1px;
      background: var(--el-border-color-extra-light);
    }

    &:not(.is-disabled):hover,
    &:not(.is-disabled):focus {
      background: var(--el-color-danger-light-9) !important;
      color: var(--el-color-danger);

      .el-icon {
        color: var(--el-color-danger);
      }
    }
  }
}

/* 夜间模式：头像沿用坞区暗色板（提亮陶土底 + 深墨首字），弹层底色走 EP 暗色遮罩变量 */
html.dark .sgj-account-popper.el-popper {
  .account-menu-avatar {
    background: #c27a6d;
    color: #171b1a;
  }
}
</style>
