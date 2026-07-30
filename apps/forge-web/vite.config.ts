/// <reference types="vitest" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      // main.ts 已全量引入 element-plus/dist/index.css，
      // 再注入按需样式会让 Vite 首次访问页面时发现新依赖并强制整页刷新
      resolvers: [ElementPlusResolver({ importStyle: false })],
      dts: 'src/auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: false })],
      dts: 'src/components.d.ts'
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        api: 'modern'
      }
    }
  },
  server: {
    port: 3003,
    proxy: {
      '/admin-api': {
        target: 'http://localhost:8181',
        changeOrigin: true
      },
      '/app-api': {
        target: 'http://localhost:8181',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8181',
        changeOrigin: true,
        ws: true
      },
      // goView 大屏独立前端（开发 iframe 跨域代理）
      '/screen-app': {
        target: 'http://localhost:8001',
        changeOrigin: true
      }
    }
  },
  test: {
    environment: 'happy-dom',
    globals: true,
    include: ['src/**/*.{test,spec}.{ts,tsx}']
  }
})
