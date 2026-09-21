import { defineConfig, loadEnv, searchForWorkspaceRoot } from 'vite'
import fs from 'fs'
import path from 'path'
import createVitePlugins from './vite/plugins'

export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd())
  const proxyTarget = env.VITE_DEV_PROXY_TARGET || 'http://localhost:18080'
  const devPort = Number(env.VITE_DEV_PORT || 5173)

  /**
   * node_modules 的真实位置。
   *
   * worktree（`.worktrees/<topic>/`）里的 node_modules 常是从主检出符号链接过来的，
   * 此时依赖文件会以 `/@fs/<主检出>/node_modules/…` 的真实路径被请求，而 Vite 默认的
   * `server.fs.allow` 只含当前根，于是字体这类资源全部 403（控制台一片红，页面缺字体）。
   * 相对路径行不通：仓库根在 worktree 里是上三层、在主检出里只是上一层——所以直接解析真实路径。
   */
  const nodeModulesRealPath = (() => {
    try {
      return fs.realpathSync(path.resolve(__dirname, 'node_modules'))
    } catch {
      // 还没装依赖时不需要它
      return path.resolve(__dirname, 'node_modules')
    }
  })()

  return {
    base: '/',
    plugins: createVitePlugins(env, command === 'build'),
    resolve: {
      // https://cn.vitejs.dev/config/#resolve-alias
      alias: {
        // 设置路径
        '~': path.resolve(__dirname, './'),
        // 设置别名
        '@': path.resolve(__dirname, './src')
      },
      // https://cn.vitejs.dev/config/#resolve-extensions
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },
    // 打包配置
    build: {
      // https://vite.dev/config/build-options.html
      sourcemap: command === 'build' ? false : 'inline',
      outDir: 'dist',
      assetsDir: 'assets',
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          chunkFileNames: 'static/js/[name]-[hash].js',
          entryFileNames: 'static/js/[name]-[hash].js',
          assetFileNames: 'static/[ext]/[name]-[hash].[ext]'
        }
      }
    },
    // vite 相关配置
    server: {
      port: devPort,
      host: true,
      open: false,
      fs: {
        allow: [searchForWorkspaceRoot(process.cwd()), nodeModulesRealPath]
      },
      proxy: {
        // https://cn.vitejs.dev/config/#server-proxy
        '/dev-api': {
          target: proxyTarget,
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/dev-api/, '')
        },
         // springdoc proxy
         '^/v3/api-docs/(.*)': {
          target: proxyTarget,
          changeOrigin: true,
        }
      }
    },
    css: {
      postcss: {
        plugins: [
          {
            postcssPlugin: 'internal:charset-removal',
            AtRule: {
              charset: (atRule: any) => {
                if (atRule.name === 'charset') {
                  atRule.remove()
                }
              }
            }
          }
        ]
      }
    }
  }
})
