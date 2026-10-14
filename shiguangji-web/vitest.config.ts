import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import createAutoImport from './vite/plugins/auto-import'

export default defineConfig({
  plugins: [
    vue(),
    // SFC 自动导入与应用侧共用同一份配置（vite/plugins/auto-import.ts）。
    // 原先此处自行写 imports: ['vue']，漏了 vue-router 与 useDict/selectDictLabel，
    // 于是「挂载用到 useRouter 的页面」的用例会以 useRouter is not defined 失败。
    createAutoImport()
  ],
  resolve: {
    alias: {
      '~': path.resolve(__dirname, './'),
      '@': path.resolve(__dirname, './src')
    }
  },
  test: {
    environment: 'jsdom',
    // 高德 Key 测试值：loadAMap 断言用，不发真实请求
    env: {
      VITE_AMAP_KEY: 'test-amap-key',
      VITE_AMAP_SECURITY_CODE: 'test-amap-security-code',
      // 站内图片前缀断言用（photoUrl / MarkdownViewer）：测试态没有 .env.development，
      // 不给值会让「补前缀」的用例变成恒等式，测不出东西
      VITE_APP_BASE_API: '/dev-api'
    }
  }
})
