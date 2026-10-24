/* eslint-env node */
module.exports = {
  root: true,
  env: { browser: true, es2021: true, node: true },
  globals: {
    // Vue 3 <script setup> 编译器宏(eslint 不识别)
    defineProps: 'readonly', defineEmits: 'readonly', defineExpose: 'readonly',
    defineOptions: 'readonly', withDefaults: 'readonly', defineModel: 'readonly',
    // unplugin-auto-import 免导入的 Vue/浏览器 API
    computed: 'readonly', ref: 'readonly', reactive: 'readonly', watch: 'readonly',
    watchEffect: 'readonly', onMounted: 'readonly', onUnmounted: 'readonly',
    onBeforeUnmount: 'readonly', nextTick: 'readonly', provide: 'readonly', inject: 'readonly',
    toRef: 'readonly', toRefs: 'readonly', unref: 'readonly', shallowRef: 'readonly'
  },
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
    'plugin:@typescript-eslint/recommended'
  ],
  parser: 'vue-eslint-parser',
  parserOptions: {
    parser: '@typescript-eslint/parser',
    ecmaVersion: 'latest',
    sourceType: 'module'
  },
  rules: {
    // 与存量代码风格冲突的面降为 warn(不阻断门禁),保留可自动修复的核心集
    'vue/multi-word-component-names': 'off',
    'vue/max-attributes-per-line': 'off',
    'vue/singleline-html-element-content-newline': 'off',
    'vue/html-self-closing': 'off',
    'vue/html-indent': 'off',
    'vue/html-closing-bracket-newline': 'off',
    'vue/first-attribute-linebreak': 'off',
    'vue/attributes-order': 'warn',
    'vue/no-v-html': 'off',
    '@typescript-eslint/no-explicit-any': 'warn',
    '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    'no-unused-vars': 'off',
    '@typescript-eslint/no-empty-interface': 'off',
    '@typescript-eslint/ban-types': 'off',
    '@typescript-eslint/no-this-alias': 'off',
    '@typescript-eslint/no-non-null-assertion': 'off',
    // vue-eslint-parser 对模板引用的 AST 与核心规则 no-obj-calls 不兼容(已知崩溃),关停该低价值规则
    'no-obj-calls': 'off',
    'no-empty': ['error', { allowEmptyCatch: true }],
    'vue/no-unused-vars': ['error', { ignorePattern: '^_' }]
  },
  ignorePatterns: ['dist/', 'node_modules/', '*.d.ts', 'vite.config.ts']
}
