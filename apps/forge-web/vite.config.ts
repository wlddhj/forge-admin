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
  // 路由懒加载页面不在默认扫描入口内，Vite 运行期才发现新依赖会触发
  // re-optimize 并整页刷新；entries 让启动期扫描全部页面一次性预构建。
  // 按需注入的 element-plus 组件深层路径与 vxe 语言包无法被静态扫描穷尽，
  // 用 glob 全量预构建彻底杜绝运行期发现新依赖。
  optimizeDeps: {
    include: [
      'vue',
      'vue-router',
      'pinia',
      'axios',
      'dayjs',
      'nprogress',
      'vxe-table',
      'vxe-pc-ui',
      'vxe-pc-ui/lib/language/zh-CN',
      'xe-utils',
      '@wangeditor/editor',
      '@wangeditor/editor-for-vue',
      '@stomp/stompjs',
      'sockjs-client',
      '@element-plus/icons-vue',
      'element-plus/es/components/**'
    ],
    entries: ['index.html', 'src/views/**/*.vue']
  },
  test: {
    environment: 'happy-dom',
    globals: true,
    include: ['src/**/*.{test,spec}.{ts,tsx}']
  }
})
