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
    environment: 'jsdom'
  }
})
