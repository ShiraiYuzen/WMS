import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * Vite 配置（WMS-v4）。
 *
 * 关键点：
 *  1. @ 别名指向 src
 *  2. /api 代理到 v4 后端 http://127.0.0.1:8080（真实数据库，无 mock）
 *  3. Element Plus 采用全量引入（见 main.js），省掉自动导入插件的版本适配成本。
 */
export default defineConfig(({ mode }) => ({
  plugins: [vue()],

  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },

  server: {
    port: 5173,
    open: false,
    host: '127.0.0.1',
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
        // 后端 context-path 为 /，接口本身就是 /api/**，无需 rewrite
      }
    }
  },

  build: {
    outDir: 'dist',
    sourcemap: mode !== 'production',
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        // 把体积较大的三方库拆包，避免单文件过大。
        // 注意：Vite 8 底层为 rolldown，manualChunks 必须是函数形式（对象形式已不支持）。
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('echarts') || id.includes('zrender')) return 'echarts'
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'element'
          if (id.includes('vue-router') || id.includes('pinia') || id.includes('/vue/') || id.includes('@vue')) {
            return 'vue'
          }
          return 'vendor'
        }
      }
    }
  },

  css: {
    preprocessorOptions: {
      scss: {
        api: 'modern-compiler',
        additionalData: `@use "@/styles/variables.scss" as *;`
      }
    }
  }
}))