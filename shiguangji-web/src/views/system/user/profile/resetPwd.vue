<template>
  <div class="pwd-form-wrap">
    <div class="pwd-tip">密码长度 6-20 位，建议混合字母、数字与特殊字符以提升安全性。</div>
    <el-form ref="pwdRef" :model="user" :rules="rules" label-position="top">
      <el-form-item label="旧密码" prop="oldPassword">
        <el-input v-model="user.oldPassword" placeholder="请输入旧密码" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword" :rules="infoPwdValidator">
        <el-input v-model="user.newPassword" placeholder="请输入新密码" type="password" show-password />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="user.confirmPassword" placeholder="请再次输入新密码" type="password" show-password />
      </el-form-item>
      <el-form-item class="form-actions">
        <el-button type="primary" :loading="loading" @click="submit">保存修改</el-button>
        <el-button :disabled="loading" @click="close">关闭</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { usePasswordRule } from "@/utils/passwordRule"
import { updateUserPwd } from "@/api/system/user"

const { proxy } = getCurrentInstance() as { proxy: any }
const { infoPwdValidator } = usePasswordRule()

const loading = ref<boolean>(false)

interface UserProfilePwd {
  // 旧密码
  oldPassword?: string
  // 新密码
  newPassword?: string
  // 确认密码
  confirmPassword?: string
}

const user = reactive<UserProfilePwd>({
  oldPassword: undefined,
  newPassword: undefined,
  confirmPassword: undefined
})

const equalToPassword = (rule: any, value: string, callback: (error?: Error) => void): void => {
  if (user.newPassword !== value) {
    callback(new Error("两次输入的密码不一致"))
  } else {
    callback()
  }
}

const rules = {
  oldPassword: [{ required: true, message: "旧密码不能为空", trigger: "blur" }],
  confirmPassword: [{ required: true, message: "确认密码不能为空", trigger: "blur" }, { required: true, validator: equalToPassword, trigger: "blur" }]
}

/** 提交按钮 */
function submit() {
  proxy.$refs.pwdRef.validate((valid: boolean) => {
    if (valid) {
      loading.value = true
      updateUserPwd(user.oldPassword!, user.newPassword!).then(() => {
        proxy.$modal.msgSuccess("修改成功")
      }).finally(() => {
        loading.value = false
      })
    }
  })
}

/** 关闭按钮 */
function close() {
  proxy.$tab.closePage()
}
</script>

<style lang="scss" scoped>
.pwd-form-wrap {
  max-width: 480px;
  padding: 4px 0 0;
}

.pwd-tip {
  margin-bottom: 20px;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--sgj-text-2, var(--el-text-color-secondary));
  background: var(--sgj-moss-soft);
  border-radius: var(--sgj-radius-md, 10px);
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--el-text-color-regular);
  padding-bottom: 6px;
}

.form-actions {
  margin-top: 28px;
  margin-bottom: 0;

  :deep(.el-button--primary) {
    min-width: 104px;
  }
}
</style>
