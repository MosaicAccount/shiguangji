<template>
   <div class="app-container profile-page">
      <el-row :gutter="24">
         <el-col :span="7" :xs="24">
            <div class="profile-card identity-card">
               <div class="identity-banner"></div>
               <div class="identity-avatar">
                  <userAvatar />
               </div>
               <div class="identity-name">{{ state.user.nickName || state.user.userName }}</div>
               <div class="identity-meta">
                  <span class="identity-account">账号 {{ state.user.userName || '—' }}</span>
                  <el-tag v-if="state.roleGroup" size="small" effect="light" round>{{ state.roleGroup }}</el-tag>
               </div>
               <ul class="identity-list">
                  <li class="identity-item">
                     <span class="identity-ic ic-terra"><svg-icon icon-class="user" /></span>
                     <span class="identity-label">用户名称</span>
                     <span class="identity-value">{{ state.user.userName || '—' }}</span>
                  </li>
                  <li class="identity-item">
                     <span class="identity-ic ic-moss"><svg-icon icon-class="phone" /></span>
                     <span class="identity-label">手机号码</span>
                     <span class="identity-value" :class="{ empty: !state.user.phonenumber }">{{ state.user.phonenumber || '未绑定' }}</span>
                  </li>
                  <li class="identity-item">
                     <span class="identity-ic ic-amber"><svg-icon icon-class="email" /></span>
                     <span class="identity-label">用户邮箱</span>
                     <span class="identity-value" :class="{ empty: !state.user.email }">{{ state.user.email || '未绑定' }}</span>
                  </li>
                  <li class="identity-item">
                     <span class="identity-ic ic-line"><svg-icon icon-class="peoples" /></span>
                     <span class="identity-label">所属角色</span>
                     <span class="identity-value">{{ state.roleGroup || '—' }}</span>
                  </li>
                  <li class="identity-item">
                     <span class="identity-ic ic-terra"><svg-icon icon-class="date" /></span>
                     <span class="identity-label">创建日期</span>
                     <span class="identity-value">{{ state.user.createTime || '—' }}</span>
                  </li>
               </ul>
            </div>
         </el-col>
         <el-col :span="17" :xs="24">
            <div class="profile-card panel-card">
               <el-tabs v-model="selectedTab" class="profile-tabs">
                  <el-tab-pane label="基本资料" name="userinfo">
                     <userInfo :user="state.user" />
                  </el-tab-pane>
                  <el-tab-pane label="修改密码" name="resetPwd">
                     <resetPwd />
                  </el-tab-pane>
               </el-tabs>
            </div>
         </el-col>
      </el-row>
   </div>
</template>

<script setup lang="ts" name="Profile">
import userAvatar from "./userAvatar.vue"
import userInfo from "./userInfo.vue"
import resetPwd from "./resetPwd.vue"
import { getUserProfile } from "@/api/system/user"
import type { SysUser } from '@/types/api/system/user'

const route = useRoute()
const selectedTab = ref<string>("userinfo")

interface UserProfileState {
  user: SysUser
  roleGroup: string
}

const state = reactive<UserProfileState>({
  user: {} as SysUser,
  roleGroup: ''
})

function getUser() {
  getUserProfile().then(response => {
    state.user = response.data
    state.roleGroup = response.roleGroup
  })
}

onMounted(() => {
  const activeTab = route.params && route.params.activeTab
  if (activeTab) {
    selectedTab.value = activeTab as string
  }
  getUser()
})
</script>

<style lang="scss" scoped>
.profile-page {
  font-family: var(--sgj-font-sans);
  padding-top: 8px;
}

.profile-card {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--sgj-radius-lg, 16px);
  box-shadow: var(--sgj-shadow-card);
}

/* ===== 左侧识别卡 ===== */
.identity-card {
  padding-bottom: 8px;
  overflow: hidden;
}

.identity-banner {
  height: 88px;
  background: linear-gradient(120deg, var(--sgj-primary-soft) 0%, var(--sgj-moss-soft) 100%);
}

.identity-avatar {
  display: flex;
  justify-content: center;
  margin-top: -44px;
}

.identity-name {
  margin-top: 14px;
  text-align: center;
  font-family: var(--sgj-font-serif);
  font-size: 22px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1.4;
  padding: 0 24px;
  word-break: break-all;
}

.identity-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 8px;
  padding: 0 24px;

  .identity-account {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}

.identity-list {
  list-style: none;
  margin: 20px 0 0;
  padding: 8px 20px 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.identity-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  font-size: 14px;

  & + .identity-item {
    border-top: 1px dashed var(--el-border-color-lighter);
  }

  .identity-ic {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: 10px;
    font-size: 14px;
    flex-shrink: 0;

    &.ic-terra { background: var(--sgj-primary-soft); color: var(--sgj-primary-dark); }
    &.ic-moss { background: var(--sgj-moss-soft); color: var(--sgj-moss); }
    &.ic-amber { background: var(--sgj-amber-soft); color: var(--sgj-amber); }
    &.ic-line { background: var(--sgj-cover); color: var(--sgj-text-2); }
  }

  .identity-label {
    color: var(--el-text-color-secondary);
    flex-shrink: 0;
  }

  .identity-value {
    margin-left: auto;
    color: var(--el-text-color-primary);
    text-align: right;
    word-break: break-all;

    &.empty {
      color: var(--el-text-color-placeholder);
    }
  }
}

/* ===== 右侧面板 ===== */
.panel-card {
  padding: 8px 24px 24px;
}

.profile-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 24px;
  }

  :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
    background-color: var(--el-border-color-lighter);
  }

  :deep(.el-tabs__item) {
    font-size: 15px;
    color: var(--el-text-color-secondary);

    &.is-active {
      font-weight: 600;
    }
  }
}

/* 移动端：左右卡片堆叠间距 */
@media (max-width: 767px) {
  .profile-page .el-col + .el-col {
    margin-top: 16px;
  }
}
</style>
