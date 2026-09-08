<template>
  <el-dialog title="打印预览" v-model="show" width="80%" top="5vh">
    <div class="preview-body" style="max-height: 70vh; overflow: auto">
      <div class="preview-container"></div>
    </div>
    <template #footer>
      <slot></slot>
      <el-button type="info" @click="webPrint">打印</el-button>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script>
import { getCurrentInstance, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { newHiprintPrintTemplate } from '@/utils/hiprint/template-helper'
import { hiprint } from 'vue-plugin-hiprint'
import { PrintTemplateApi } from '@/api/system/print-template'
import { cloneDeep, set, transform } from 'lodash-es'
import { isArray, isObject } from 'min-dash'

export default {
  name: 'print-view',
  emits: ['success'],
  setup(props, { emit }) {
    const templateCode = ref('')
    const TEMPLATE_KEY = getCurrentInstance().type.name
    const show = ref(false)
    let printDataOri = {}
    const printData = ref({})
    let hiprintTemplate
    const templateRef = ref({})

    const close = () => {
      show.value = false
    }

    const showModal = (...html) => {
      show.value = true
      setTimeout(() => {
        const container = document.querySelector('.preview-container')
        if (container) {
          container.innerHTML = ''
          container.insertAdjacentHTML('beforeend', html.join(''))
        }
      }, 200)
    }

    async function loadTemplateData() {
      const data = await PrintTemplateApi.getByCode(templateCode.value)
      if (data && data.contents) {
        templateRef.value = JSON.parse(data.contents)
      }
    }

    const prePrint = async (code, data) => {
      templateCode.value = code
      printDataOri = cloneDeep(data)
      printData.value = printDataOri
      await loadTemplateData()
      showPrint()
    }

    const showPrint = () => {
      hiprintTemplate = newHiprintPrintTemplate(TEMPLATE_KEY, {
        template: templateRef.value
      })
      const html = hiprintTemplate.getHtml(printData.value)
      showModal(html)
    }

    const webPrint = () => {
      const options = { leftOffset: 0, topOffset: 0 }
      const ext = {
        callback: () => {
          emit('success', templateCode.value)
        }
      }
      hiprintTemplate.print(printData.value, options, ext)
    }

    return {
      show,
      close,
      showModal,
      prePrint,
      webPrint
    }
  }
}
</script>

<style>
.preview-container .hiprint-printTemplate {
  background: #fff;
  border-bottom: 10px solid #ccc;
  color: #000000;
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
}
</style>
