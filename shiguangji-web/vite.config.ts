import { defineConfig, loadEnv, searchForWorkspaceRoot } from 'vite'
import path from 'path'
import createVitePlugins from './vite/plugins'

export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd())
  const proxyTarget = env.VITE_DEV_PROXY_TARGET || 'http://localhost:18080'
  const devPort = Number(env.VITE_DEV_PORT || 5173)

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
        // worktree（`.worktrees/<topic>/`）里的 node_modules 通常是从主检出符号链接过来的，
        // 而默认的 allow 只有 worktree 根，于是依赖文件会以 /@fs/<主检出>/… 的绝对路径被拒成 403
        // （典型症状：字体全 404/403、控制台一片红）。允许仓库根即可。
        allow: [searchForWorkspaceRoot(process.cwd()), path.resolve(__dirname, '..')]
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
