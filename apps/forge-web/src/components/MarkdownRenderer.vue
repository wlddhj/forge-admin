<template>
  <div class="markdown-content" v-html="renderedContent"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked, Tokens } from 'marked'
import hljs from 'highlight.js'

// 自定义渲染器 - 处理代码高亮
const renderer = {
  code(token: Tokens.Code): string {
    const lang = token.lang || ''
    const code = token.text
    let highlighted: string

    if (lang && hljs.getLanguage(lang)) {
      try {
        highlighted = hljs.highlight(code, { language: lang }).value
      } catch (e) {
        highlighted = code
      }
    } else {
      highlighted = hljs.highlightAuto(code).value
    }

    return `<pre><code class="hljs language-${lang}">${highlighted}</code></pre>`
  }
}

// 配置 marked
marked.use({
  renderer,
  breaks: true,    // 单换行转为 <br>
  gfm: true        // GitHub风格Markdown
})

const props = defineProps<{
  content: string
}>()

const renderedContent = computed(() => {
  if (!props.content) return ''
  try {
    // 处理可能存在的 \n 字符串转义问题
    const processedContent = props.content.replace(/\\n/g, '\n')
    return marked.parse(processedContent) as string
  } catch (e) {
    return props.content
  }
})
</script>

<style lang="scss">
.markdown-content {
  line-height: 1.6;
  word-break: break-word;

  // 标题样式
  h1, h2, h3, h4, h5, h6 {
    margin: 0.6em 0 0.3em;
    font-weight: 600;
    line-height: 1.25;
  }

  h1 { font-size: 1.4em; }
  h2 { font-size: 1.2em; }
  h3 { font-size: 1.05em; }

  // 代码块样式
  pre {
    margin: 0.5em 0;
    padding: 12px 16px;
    background: #282c34;
    border-radius: 6px;
    overflow-x: auto;

    code {
      font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
      font-size: 0.9em;
      color: #abb2bf;
      background: transparent;
      padding: 0;
    }
  }

  // 行内代码样式
  code:not(pre code) {
    padding: 0.2em 0.4em;
    margin: 0;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 0.85em;
    background: rgba(0, 0, 0, 0.06);
    border-radius: 3px;
    color: #e83e8c;
  }

  // 列表样式
  ul, ol {
    margin: 0.5em 0;
    padding-left: 1.5em;

    li {
      margin: 0.2em 0;
    }
  }

  // 引用样式
  blockquote {
    margin: 0.4em 0;
    padding: 0.4em 0.8em;
    border-left: 3px solid #dfe2e5;
    background: #f8f8f8;
    color: #666;

    p:last-child {
      margin-bottom: 0;
    }
  }

  // 链接样式
  a {
    color: #409eff;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  // 表格样式
  table {
    width: 100%;
    margin: 0.4em 0;
    border-collapse: collapse;

    th, td {
      padding: 0.4em 0.8em;
      border: 1px solid #e4e7ed;
    }

    th {
      background: #f5f7fa;
      font-weight: 600;
    }
  }

  // 分割线
  hr {
    margin: 0.6em 0;
    border: none;
    border-top: 1px solid #e4e7ed;
  }

  // 图片
  img {
    max-width: 100%;
    height: auto;
    display: block;
    margin: 0.5em auto;
  }

  // 段落 - 最小间距
  p {
    margin: 0 0 0.4em 0;
  }

  // 强调
  strong {
    font-weight: 600;
  }

  em {
    font-style: italic;
  }
}
</style>