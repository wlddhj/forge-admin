<template>
  <el-dialog v-model="visible" title="打印合同" width="440px" append-to-body destroy-on-close>
    <el-form label-width="90px">
      <el-form-item label="纸张大小">
        <el-radio-group v-model="paperSize">
          <el-radio-button label="A4" value="A4" />
          <el-radio-button label="A5" value="A5" />
          <el-radio-button label="B3" value="B3" />
          <el-radio-button label="Letter" value="LETTER" />
          <el-radio-button label="Legal" value="LEGAL" />
          <el-radio-button label="自定义" value="CUSTOM" />
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="paperSize === 'CUSTOM'" label="宽×高 mm">
        <el-input-number v-model="customWidthMm" :min="50" :max="600" :precision="1" controls-position="right" style="width: 120px" />
        <span style="margin: 0 6px">×</span>
        <el-input-number v-model="customHeightMm" :min="50" :max="900" :precision="1" controls-position="right" style="width: 120px" />
      </el-form-item>
      <el-alert type="info" :closable="false" show-icon
        title="按所选纸张 1:1 重新渲染 PDF 后唤起打印；已定稿合同盖「定稿」水印，草稿盖「草稿」水印" />
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="printing" @click="confirm">渲染并打印</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { contractApi } from '@/api/contract/contract'
import type { PaperSizeCode } from '@/types/contract'

defineOptions({ name: 'ContractPrintDialog' })

const visible = defineModel<boolean>({ default: false })
const props = defineProps<{ contractId: string }>()

const paperSize = ref<PaperSizeCode>('A4')
const customWidthMm = ref(210)
const customHeightMm = ref(297)
const printing = ref(false)

const confirm = async () => {
  if (paperSize.value === 'CUSTOM' && (!customWidthMm.value || !customHeightMm.value)) {
    ElMessage.warning('自定义纸张须填写宽高')
    return
  }
  printing.value = true
  try {
    const blob = await contractApi.printPdf(props.contractId, {
      paperSize: paperSize.value,
      customWidthMm: paperSize.value === 'CUSTOM' ? customWidthMm.value : undefined,
      customHeightMm: paperSize.value === 'CUSTOM' ? customHeightMm.value : undefined
    })
    printBlob(blob)
    visible.value = false
  } finally {
    printing.value = false
  }
}

/**
 * blob PDF 打印：隐藏 iframe 挂 body 顶层（打印范式），自动唤起失败兜底新窗口
 */
const printBlob = (blob: Blob) => {
  const url = URL.createObjectURL(blob)
  const iframe = document.createElement('iframe')
  iframe.style.position = 'fixed'
  iframe.style.right = '0'
  iframe.style.bottom = '0'
  iframe.style.width = '0'
  iframe.style.height = '0'
  iframe.style.border = '0'
  iframe.src = url
  iframe.onload = () => {
    try {
      iframe.contentWindow?.focus()
      iframe.contentWindow?.print()
    } catch {
      window.open(url, '_blank')
    }
  }
  document.body.appendChild(iframe)
  // 清理：打印对话框关闭后回收（给足渲染时间）
  setTimeout(() => {
    URL.revokeObjectURL(url)
    iframe.remove()
  }, 60_000)
}
</script>
