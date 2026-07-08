import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import { OUTPUT_DIR, brotliSize, chunkSizeWarningLimit, terserOptions, rollupOptions } from './build/constant'
import viteCompression from 'vite-plugin-compression'
import { viteMockServe } from 'vite-plugin-mock'
import monacoEditorPlugin from 'vite-plugin-monaco-editor'

function pathResolve(dir: string) {
  return resolve(process.cwd(), '.', dir)
}

export default defineConfig({
  base: '/',
  server: {
    port: 8001,
    open: false,
    proxy: {
      '/admin-api': {
        target: 'http://localhost:8181',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/admin-api/, '/api')
      }
    }
  },
  resolve: {
    alias: [
      { find: /\/#\//, replacement: pathResolve('types') },
      { find: '@', replacement: pathResolve('src') },
      { find: 'vue-i18n', replacement: 'vue-i18n/dist/vue-i18n.cjs.js' }
    ],
    dedupe: ['vue']
  },
  css: {
    preprocessorOptions: {
      scss: {
        javascriptEnabled: true,
        additionalData: `@import "src/styles/common/style.scss";`
      }
    }
  },
  define: {
    __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: 'true'
  },
  plugins: [
    vue({
      template: {
        compilerOptions: {
          isCustomElement: tag => tag.startsWith('iconify-icon')
        }
      }
    }),
    monacoEditorPlugin({
      languageWorkers: ['editorWorkerService', 'typescript', 'json', 'html']
    }),
    viteMockServe({
      mockPath: '/src/api/mock',
      localEnabled: true,
      prodEnabled: true,
      supportTs: true,
      watchFiles: true
    }),
    viteCompression({
      verbose: true,
      disable: false,
      threshold: 10240,
      algorithm: 'gzip',
      ext: '.gz'
    })
  ],
  build: {
    target: 'es2020',
    outDir: OUTPUT_DIR,
    rollupOptions: rollupOptions,
    reportCompressedSize: brotliSize,
    chunkSizeWarningLimit: chunkSizeWarningLimit
  }
})
