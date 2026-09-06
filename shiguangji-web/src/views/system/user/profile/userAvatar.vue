<template>
  <div class="user-info-head" @click="editCropper()">
    <img :src="options.img" title="点击上传头像" class="img-circle profile-avatar" />
    <div class="avatar-mask">
      <el-icon><Camera /></el-icon>
      <span>更换头像</span>
    </div>
    <el-dialog :title="title" v-model="open" width="800px" append-to-body @opened="modalOpened" @close="closeDialog">
      <el-row :gutter="24">
        <el-col :xs="24" :md="12">
          <div class="crop-pane-title">裁剪</div>
          <div class="crop-pane">
            <vue-cropper
              ref="cropper"
              :img="options.img"
              :info="true"
              :autoCrop="options.autoCrop"
              :autoCropWidth="options.autoCropWidth"
              :autoCropHeight="options.autoCropHeight"
              :fixedBox="options.fixedBox"
              :outputType="options.outputType"
              @realTime="realTime"
              v-if="visible"
            />
          </div>
        </el-col>
        <el-col :xs="24" :md="12">
          <div class="crop-pane-title">预览</div>
          <div class="crop-pane crop-preview-pane">
            <div class="crop-preview-circle">
              <img :src="options.previews.url" :style="options.previews.img" />
            </div>
          </div>
        </el-col>
      </el-row>
      <div class="crop-toolbar">
        <div class="crop-toolbar-left">
          <el-upload
            action="#"
            :http-request="requestUpload"
            :show-file-list="false"
            :before-upload="beforeUpload"
          >
            <el-button>
              选择图片
              <el-icon class="el-icon--right"><Upload /></el-icon>
            </el-button>
          </el-upload>
          <el-button icon="Plus" title="放大" @click="changeScale(1)"></el-button>
          <el-button icon="Minus" title="缩小" @click="changeScale(-1)"></el-button>
          <el-button icon="RefreshLeft" title="左旋" @click="rotateLeft()"></el-button>
          <el-button icon="RefreshRight" title="右旋" @click="rotateRight()"></el-button>
        </div>
        <el-button type="primary" @click="uploadImg()">提 交</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import "vue-cropper/dist/index.css"
import { VueCropper } from "vue-cropper"
import { uploadAvatar } from "@/api/system/user"
import useUserStore from "@/store/modules/user"

const userStore = useUserStore()
const { proxy } = getCurrentInstance() as { proxy: any }

const open = ref<boolean>(false)
const visible = ref<boolean>(false)
const title = ref<string>("修改头像")

//图片裁剪数据
const options = reactive<any>({
  img: userStore.avatar,     // 裁剪图片的地址
  autoCrop: true,            // 是否默认生成截图框
  autoCropWidth: 200,        // 默认生成截图框宽度
  autoCropHeight: 200,       // 默认生成截图框高度
  fixedBox: true,            // 固定截图框大小 不允许改变
  outputType: "png",         // 默认生成截图为PNG格式
  filename: 'avatar',        // 文件名称
  previews: {}               //预览数据
})

/** 编辑头像 */
function editCropper() {
  open.value = true
}

/** 打开弹出层结束时的回调 */
function modalOpened() {
  visible.value = true
}

/** 覆盖默认上传行为 */
function requestUpload() {}

/** 向左旋转 */
function rotateLeft() {
  proxy.$refs.cropper.rotateLeft()
}

/** 向右旋转 */
function rotateRight() {
  proxy.$refs.cropper.rotateRight()
}

/** 图片缩放 */
function changeScale(num: number) {
  num = num || 1
  proxy.$refs.cropper.changeScale(num)
}

/** 上传预处理 */
function beforeUpload(file: File) {
  if (file.type.indexOf("image/") == -1) {
    proxy.$modal.msgError("文件格式错误，请上传图片类型,如：JPG，PNG后缀的文件。")
  } else {
    const reader = new FileReader()
    reader.readAsDataURL(file)
    reader.onload = () => {
      options.img = reader.result
      options.filename = file.name
    }
  }
}

/** 上传图片 */
function uploadImg() {
  proxy.$refs.cropper.getCropBlob((data: Blob) => {
    const formData = new FormData()
    formData.append("avatarfile", data, options.filename)
    uploadAvatar(formData).then(response => {
      open.value = false
      options.img = import.meta.env.VITE_APP_BASE_API + response.imgUrl
      userStore.avatar = options.img
      proxy.$modal.msgSuccess("修改成功")
      visible.value = false
    })
  })
}

/** 实时预览 */
function realTime(data: any) {
  options.previews = data
}

/** 关闭窗口 */
function closeDialog() {
  options.img = userStore.avatar
  options.visible = false
}
</script>

<style lang='scss' scoped>
.user-info-head {
  position: relative;
  display: inline-block;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  cursor: pointer;
}

.profile-avatar {
  display: block;
  width: 96px;
  height: 96px;
  object-fit: cover;
  box-shadow: 0 0 0 4px var(--el-bg-color-overlay), var(--sgj-shadow-card);
  transition: box-shadow var(--el-transition-duration), transform var(--el-transition-duration);
}

.user-info-head:hover .profile-avatar {
  transform: scale(1.03);
}

.avatar-mask {
  position: absolute;
  inset: 0;
  pointer-events: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border-radius: 50%;
  background: rgba(23, 27, 26, 0.45);
  color: #fff;
  font-size: 12px;
  line-height: 1;
  opacity: 0;
  transition: opacity var(--el-transition-duration);

  .el-icon {
    font-size: 18px;
  }
}

.user-info-head:hover .avatar-mask {
  opacity: 1;
}

/* ===== 裁剪弹窗 ===== */
.crop-pane-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.crop-pane {
  height: 320px;
  border-radius: var(--sgj-radius-md, 10px);
  overflow: hidden;
  background: var(--sgj-bg-deep, #E5E9E7);
}

.crop-preview-pane {
  display: flex;
  align-items: center;
  justify-content: center;
}

.crop-preview-circle {
  width: 200px;
  height: 200px;
  border-radius: 50%;
  overflow: hidden;
  box-shadow: 0 0 0 6px var(--el-bg-color-overlay), var(--sgj-shadow-card);

  img {
    display: block;
  }
}

.crop-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 20px;
  flex-wrap: wrap;

  .crop-toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;

    :deep(.el-upload) {
      margin-right: 0;
    }
  }
}

/* 移动端：裁剪/预览纵向堆叠时间距 */
@media (max-width: 767px) {
  .crop-pane {
    margin-bottom: 16px;
  }
}
</style>
