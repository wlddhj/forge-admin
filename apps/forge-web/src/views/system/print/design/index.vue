<template>
  <div class="design-container">
    <div class="design-toolbar">
      <!-- 纸张大小 -->
      <div class="paper-group">
        <button
          v-for="(value, type) in paperTypes"
          :key="type"
          :class="['paper-btn', curPaperType === type ? 'active' : '']"
          @click="setPaper(type, value)"
        >{{ type }}</button>
        <button
          :class="['paper-btn', curPaperType === 'other' ? 'active' : '']"
          @click="paperPopVisible = !paperPopVisible"
        >自定义</button>
        <div class="popover" v-show="paperPopVisible">
          <div class="popover-content">
            <div style="font-size: 14px; font-weight: bold; margin-bottom: 6px">设置纸张宽高(mm)</div>
            <div style="display: flex; align-items: center; gap: 6px">
              <input class="paper-input" v-model.number="paperWidth" type="number" placeholder="宽" />
              <span>x</span>
              <input class="paper-input" v-model.number="paperHeight" type="number" placeholder="高" />
              <button class="primary-btn" @click.stop="setPaperOther">确定</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 缩放 -->
      <div class="zoom-group">
        <button class="icon-btn" @click="changeScale(false)">-</button>
        <div class="zoom-value">{{ (scaleValue * 100).toFixed(0) }}%</div>
        <button class="icon-btn" @click="changeScale(true)">+</button>
      </div>

      <button class="action-btn" @click.stop="rotatePaper">旋转纸张</button>
      <button class="action-btn" @click.stop="clearPaper">清空</button>
      <button class="action-btn" @click.stop="exportJson">导出JSON</button>
      <button class="action-btn primary" @click.stop="saveJson">保存模板</button>
      <button class="action-btn" @click.stop="getHtml">预览</button>
      <button class="action-btn" @click.stop="print">浏览器打印</button>
    </div>

    <div class="design-body">
      <div class="left-panel">
        <div class="panel-title">默认元素</div>
        <div id="provider-container1" class="provider-container"></div>
        <div class="panel-title">自定义元素</div>
        <div id="provider-container2" class="provider-container"></div>
        <div class="panel-title">基础元素</div>
        <div id="provider-container3" class="provider-container"></div>
      </div>

      <div class="center-panel">
        <div id="hiprint-printTemplate"></div>
      </div>

      <div class="right-panel">
        <div id="PrintElementOptionSetting"></div>
      </div>
    </div>

    <start-preview ref="preview" />
  </div>
</template>

<script>
import { onMounted, ref, getCurrentInstance } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { hiprint, defaultElementTypeProvider } from 'vue-plugin-hiprint'
import { provider1 } from './provider1'
import { provider2 } from './provider2'
import printData from './printData'
import { newHiprintPrintTemplate } from '@/utils/hiprint/template-helper'
import startPreview from '@/components/hiprint/preview.vue'
import { PrintTemplateApi } from '@/api/system/print-template'

export default {
  name: 'print-template-design',
  setup() {
    const TEMPLATE_KEY = getCurrentInstance().type.name
    const route = useRoute()
    const preview = ref(null)

    const paperTypes = {
      A3: { width: 420, height: 297 },
      A4: { width: 210, height: 297 },
      A5: { width: 210, height: 148 },
      B3: { width: 500, height: 353 },
      B4: { width: 250, height: 353 },
      B5: { width: 250, height: 176 }
    }
    const curPaperType = ref('A4')
    const paperPopVisible = ref(false)
    const paperWidth = ref(210)
    const paperHeight = ref(297)
    const scaleValue = ref(1)

    let hiprintTemplate
    const templateRef = ref({})
    const templateDataDO = ref({})

    const setPaper = (type, value) => {
      curPaperType.value = type
      paperWidth.value = value.width
      paperHeight.value = value.height
      paperPopVisible.value = false
      if (hiprintTemplate) {
        hiprintTemplate.setPaper(value.width, value.height)
      }
    }
    const setPaperOther = () => {
      curPaperType.value = 'other'
      paperPopVisible.value = false
      if (hiprintTemplate) {
        hiprintTemplate.setPaper(paperWidth.value, paperHeight.value)
      }
    }
    const changeScale = (big) => {
      if (big) {
        scaleValue.value += 0.1
      } else {
        scaleValue.value -= 0.1
      }
      if (scaleValue.value < 0.5) scaleValue.value = 0.5
      if (scaleValue.value > 3) scaleValue.value = 3
      if (hiprintTemplate) {
        hiprintTemplate.scale(scaleValue.value)
      }
    }
    const rotatePaper = () => hiprintTemplate && hiprintTemplate.rotatePaper()
    const clearPaper = () => hiprintTemplate && hiprintTemplate.clear()

    const exportJson = () => {
      if (!hiprintTemplate) return
      const json = hiprintTemplate.getJson()
      const blob = new Blob([JSON.stringify(json, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'hiprint-template.json'
      a.click()
      URL.revokeObjectURL(url)
    }

    const saveJson = async () => {
      if (!hiprintTemplate) return
      try {
        const json = hiprintTemplate.getJson()
        templateDataDO.value.contents = JSON.stringify(json, null, 2)
        await PrintTemplateApi.update(templateDataDO.value)
        ElMessage.success('保存成功')
      } catch (e) {
        ElMessage.error('保存失败')
      }
    }

    const getHtml = () => {
      if (!hiprintTemplate) return
      const html = hiprintTemplate.getHtml(printData)
      preview.value.showModal(html)
    }

    const print = () => {
      if (!hiprintTemplate) return
      const options = { leftOffset: -1, topOffset: -1 }
      const ext = {
        callback: () => {
          console.log('浏览器打印窗口已打开')
        }
      }
      hiprintTemplate.print(printData, options, ext)
    }

    const buildLeftElement = () => {
      const $ = window.jQuery || window.$
      $('#provider-container1').empty()
      hiprint.PrintElementTypeManager.build($('#provider-container1'), 'providerModule1')
      $('#provider-container2').empty()
      hiprint.PrintElementTypeManager.build($('#provider-container2'), 'providerModule2')
      $('#provider-container3').empty()
      hiprint.PrintElementTypeManager.build($('#provider-container3'), 'defaultModule')
    }

    const buildDesigner = () => {
      const $ = window.jQuery || window.$
      $('#hiprint-printTemplate').empty()
      hiprintTemplate = newHiprintPrintTemplate(TEMPLATE_KEY, {
        template: templateRef.value,
        settingContainer: '#PrintElementOptionSetting'
      })
      hiprintTemplate.design('#hiprint-printTemplate')
    }

    const loadTemplateData = async () => {
      const id = route.query.id
      if (!id) {
        ElMessage.warning('缺少模板ID')
        return
      }
      const data = await PrintTemplateApi.get(Number(id))
      if (!data || !data.id) {
        ElMessage.error('模板不存在或已删除')
        return
      }
      templateDataDO.value = data
      if (data.contents) {
        try {
          templateRef.value = JSON.parse(data.contents)
        } catch (e) {
          templateRef.value = {}
          ElMessage.warning('模板内容解析失败，将使用空模板')
        }
      }
      buildDesigner()
    }

    onMounted(() => {
      const options = {}
      hiprint.init({
        providers: [provider1(options), provider2(options), defaultElementTypeProvider()]
      })
      buildLeftElement()
      loadTemplateData()
    })

    return {
      paperTypes,
      curPaperType,
      paperPopVisible,
      paperWidth,
      paperHeight,
      scaleValue,
      preview,
      setPaper,
      setPaperOther,
      changeScale,
      rotatePaper,
      clearPaper,
      exportJson,
      saveJson,
      getHtml,
      print
    }
  }
}
</script>

<style scoped>
.design-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  background: #f0f2f5;
}
.design-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  background: #fff;
  border-bottom: 1px solid #e9e9e9;
  flex-wrap: wrap;
}
.paper-group {
  display: flex;
  align-items: center;
  position: relative;
}
.paper-btn {
  padding: 4px 10px;
  border: 1px solid #dcdfe6;
  background: #fff;
  cursor: pointer;
  font-size: 12px;
}
.paper-btn.active {
  background: #409eff;
  color: #fff;
  border-color: #409eff;
}
.popover {
  position: absolute;
  top: 32px;
  left: 0;
  z-index: 10;
}
.popover-content {
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 10px 14px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.paper-input {
  width: 60px;
  height: 24px;
  padding: 2px 6px;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
}
.zoom-group {
  display: flex;
  align-items: center;
  gap: 4px;
}
.icon-btn {
  width: 24px;
  height: 24px;
  border: 1px solid #dcdfe6;
  background: #fff;
  cursor: pointer;
}
.zoom-value {
  width: 40px;
  text-align: center;
  font-size: 12px;
}
.action-btn {
  padding: 4px 10px;
  border: 1px solid #dcdfe6;
  background: #fff;
  cursor: pointer;
  font-size: 12px;
  border-radius: 3px;
}
.action-btn.primary {
  background: #67c23a;
  color: #fff;
  border-color: #67c23a;
}
.action-btn:hover {
  opacity: 0.85;
}
.design-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}
.left-panel {
  width: 240px;
  background: #fff;
  border-right: 1px solid #d9d9d9;
  overflow: auto;
  padding: 8px;
}
.panel-title {
  font-size: 13px;
  font-weight: 500;
  margin: 4px 0;
  padding: 4px 8px;
  color: #303133;
}
.provider-container {
  min-height: 100px;
  background: #fafafa;
  margin-bottom: 8px;
  padding: 4px;
}
.center-panel {
  flex: 1;
  background: #fff;
  overflow: auto;
  padding: 20px;
}
.right-panel {
  width: 320px;
  background: #fff;
  border-left: 1px solid #d9d9d9;
  overflow: auto;
  padding: 8px 0;
}
</style>

<style>
.rect-printElement-types .hiprint-printElement-type > li > ul > li > a {
  color: #409eff !important;
}
.custom-style-types .hiprint-printElement-type {
  display: block;
  padding: 0;
  list-style: none;
}
.custom-style-types .hiprint-printElement-type > li > .title {
  display: block;
  padding: 4px 0;
  color: #409eff;
}
.custom-style-types .hiprint-printElement-type > li > ul {
  padding: 0;
  list-style: none;
}
.custom-style-types .hiprint-printElement-type > li > ul > li {
  display: block;
  width: 50%;
  float: left;
  max-width: 100px;
}
.custom-style-types .hiprint-printElement-type > li > ul > li > a {
  padding: 8px 6px;
  color: #409eff;
  text-decoration: none;
  background: #fff;
  border: 1px solid #ddd;
  margin-right: 5px;
  width: 95%;
  display: inline-block;
  text-align: center;
  margin-bottom: 5px;
  box-sizing: border-box;
  border-radius: 4px;
  box-shadow: 0 1px 0 0 rgba(0,0,0,0.15);
}
#PrintElementOptionSetting .hiprint-option-item .hiprint-option-item-label {
  margin: 10px 5px 3px 0;
  font-size: 12px;
}
#PrintElementOptionSetting .hiprint-option-item-field {
  font-size: 12px;
}
</style>
