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
      console.log('[print-view] showModal html:', html)
      show.value = true
      setTimeout(() => {
        const container = document.querySelector('.preview-container')
        if (!container) {
          console.error('[print-view] .preview-container not found in DOM')
          return
        }
        container.innerHTML = ''
        const $ = window.jQuery || window.$
        if ($) {
          const $container = $(container)
          $container.empty()
          $container.html(html)
        } else {
          const htmlStr = html.map(h => {
            if (typeof h === 'string') return h
            if (h?.outerHTML) return h.outerHTML
            if (h?.toString?.()) return h.toString()
            return ''
          }).join('')
          container.insertAdjacentHTML('beforeend', htmlStr)
        }
        console.log('[print-view] container innerHTML length:', container.innerHTML.length)
      }, 300)
    }

    async function loadTemplateData() {
      const data = await PrintTemplateApi.getByCode(templateCode.value)
      console.log('[print-view] loadTemplateData result:', data)
      if (data && data.contents) {
        try {
          templateRef.value = JSON.parse(data.contents)
          console.log('[print-view] templateRef parsed:', templateRef.value)
        } catch (e) {
          console.error('[print-view] contents JSON parse failed:', e)
          templateRef.value = {}
        }
      } else {
        console.warn('[print-view] template not found or contents empty')
        templateRef.value = {}
      }
    }

    const prePrint = async (code, data) => {
      console.log('[print-view] prePrint called with code:', code, 'data:', data)
      templateCode.value = code
      printDataOri = cloneDeep(data)
      printData.value = printDataOri
      await loadTemplateData()
      showPrint()
    }

    const showPrint = () => {
      console.log('[print-view] showPrint templateRef:', templateRef.value, 'printData:', printData.value)
      hiprintTemplate = newHiprintPrintTemplate(TEMPLATE_KEY, {
        template: templateRef.value
      })
      const html = hiprintTemplate.getHtml(printData.value)
      console.log('[print-view] getHtml return type:', typeof html, 'value:', html)
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
