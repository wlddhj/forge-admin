<template>
  <el-dialog v-model="show" title="预览" width="80%" top="5vh" append-to-body>
    <div class="preview-body">
      <div ref="containerRef" class="preview-container"></div>
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
    if (!containerRef.value) return
    containerRef.value.innerHTML = ''
    // hiprint getHtml 返回 jQuery 集合(类数组)/字符串,纯 DOM 挂载
    const items = html == null ? [] : Array.from(html)
    for (const item of items) {
      const node = item?.nodeType ? item : item?.target?.[0]
      if (node?.nodeType) { containerRef.value.appendChild(node); continue }
      if (typeof item === 'string' && item) containerRef.value.insertAdjacentHTML('beforeend', item)
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
