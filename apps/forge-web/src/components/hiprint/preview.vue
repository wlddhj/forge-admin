<template>
  <el-dialog v-model="show" title="预览" width="80%" top="5vh" append-to-body>
    <div class="preview-body">
      <div class="preview-container" ref="containerRef"></div>
    </div>
    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, nextTick } from 'vue'

defineOptions({ name: 'StartPreview' })

const show = ref(false)
const containerRef = ref(null)

const close = () => {
  show.value = false
}

const showModal = async (...html) => {
  console.log('打印预览', html)
  show.value = true
  await nextTick()
  setTimeout(() => {
    const $ = window.jQuery || window.$
    if (!containerRef.value) return
    if ($) {
      const $container = $(containerRef.value)
      $container.empty()
      $container.html(html)
    } else {
      containerRef.value.innerHTML = ''
      const htmlStr = html.map(h => (typeof h === 'string' ? h : h?.outerHTML || h?.toString?.() || '')).join('')
      containerRef.value.insertAdjacentHTML('beforeend', htmlStr)
    }
  }, 100)
}

defineExpose({ showModal, close })
</script>

<style>
.preview-container .hiprint-printTemplate {
  background: #fff;
  border-bottom: 10px solid #ccc;
}
.preview-container .hiprint-printTemplate .hiprint-printPanel:not(:last-of-type) {
  border-bottom: 5px solid #ccc;
}
</style>

<style scoped>
.preview-body {
  background: #ccc;
  padding: 14px 0;
  display: flex;
  justify-content: center;
  max-height: 70vh;
  overflow: auto;
}
</style>
