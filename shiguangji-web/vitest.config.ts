import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import autoImport from 'unplugin-auto-import/vite'
import path from 'path'

export default defineConfig({
  plugins: [
    vue(),
    // SFC 内 Vue API 依赖自动导入（与 vite.config.ts 保持一致）
    autoImport({ imports: ['vue'], dts: false })
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
      VITE_AMAP_SECURITY_CODE: 'test-amap-security-code'
    }
  }
})
