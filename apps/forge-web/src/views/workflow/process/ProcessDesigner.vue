<template>
  <div class="designer-page">
    <!-- 顶部工具栏 -->
    <div class="designer-top-bar">
      <div class="top-bar-left">
        <el-button size="small" @click="handleGoBack">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <span class="page-title">{{ isEdit ? '编辑流程' : '新增流程' }}</span>
      </div>
      <div class="top-bar-right">
        <el-button size="small" @click="handleUndo" :disabled="!canUndo">
          <el-icon><RefreshLeft /></el-icon>撤销
        </el-button>
        <el-button size="small" @click="handleRedo" :disabled="!canRedo">
          <el-icon><RefreshRight /></el-icon>重做
        </el-button>
        <el-button size="small" @click="handleClear">
          <el-icon><Delete /></el-icon>清空
        </el-button>
        <el-button-group>
          <el-button size="small" @click="handleZoomOut">
            <el-icon><ZoomOut /></el-icon>
          </el-button>
          <el-button size="small" @click="handleResetZoom">适应</el-button>
          <el-button size="small" @click="handleZoomIn">
            <el-icon><ZoomIn /></el-icon>
          </el-button>
        </el-button-group>
        <el-button type="primary" size="small" @click="handleOpenDeployDialog">
          <el-icon><Check /></el-icon>保存并部署
        </el-button>
      </div>
    </div>

    <!-- 设计器主体 -->
    <div class="designer-body">
      <!-- 中间画布 -->
      <div class="designer-canvas">
        <div ref="canvasRef" class="bpmn-container"></div>
      </div>

      <!-- 右侧属性面板 -->
      <div ref="propertiesPanelRef" class="properties-panel-container"></div>
    </div>

    <!-- 部署对话框 -->
    <el-dialog v-model="deployDialogVisible" title="部署流程" width="500px">
      <el-form ref="deployFormRef" :model="deployForm" :rules="deployRules" label-width="100px">
        <el-form-item label="流程名称" prop="name">
          <el-input v-model="deployForm.name" placeholder="请输入流程名称" />
        </el-form-item>
        <el-form-item label="流程标识" prop="key">
          <el-input v-model="deployForm.key" placeholder="请输入流程标识（英文）" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="deployForm.categoryId" placeholder="请选择分类" clearable style="width: 100%">
            <el-option
              v-for="item in categoryList"
              :key="item.id"
              :label="item.categoryName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="deployForm.description" type="textarea" :rows="3" placeholder="请输入流程描述" />
        </el-form-item>
        <el-form-item label="表单类型">
          <el-select v-model="deployForm.formType" placeholder="请选择表单类型" clearable style="width: 100%">
            <el-option label="流程表单" :value="10" />
            <el-option label="业务表单" :value="20" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="deployForm.formType" label="关联表单">
          <el-select v-model="deployForm.formId" placeholder="请选择表单" clearable filterable style="width: 100%">
            <el-option
              v-for="item in formList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deployDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="deployLoading" @click="handleDeploy">确定部署</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import { BpmnPropertiesPanelModule, BpmnPropertiesProviderModule } from 'bpmn-js-properties-panel'
import { flowableExtensionModule, flowableModdle } from '@/views/workflow/process/bpmn-extension/FlowableExtension.js'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'
import { processDefinitionApi } from '@/api/workflow/process-definition'
import { categoryApi } from '@/api/workflow/category'
import { formApi } from '@/api/workflow/form'
import { createInitialBpmnXml } from '@/composables/useBpmnJsDesigner'

const router = useRouter()
const route = useRoute()

const canvasRef = ref<HTMLElement | null>(null)
const propertiesPanelRef = ref<HTMLElement | null>(null)
const modeler = ref<BpmnModeler | null>(null)

const canUndo = ref(false)
const canRedo = ref(false)

// 编辑模式
const isEdit = computed(() => !!route.query.id)

// 分类列表
const categoryList = ref<{ id: number; categoryName: string }[]>([])

// 表单列表
const formList = ref<{ id: number; name: string }[]>([])

// 部署对话框
const deployDialogVisible = ref(false)
const deployLoading = ref(false)
const deployFormRef = ref<FormInstance>()
const processDetail = ref<any>(null)
const deployForm = reactive({
  name: '',
  key: '',
  categoryId: undefined as number | undefined,
  description: '',
  formType: undefined as number | undefined,
  formId: undefined as number | undefined,
})
const deployRules: FormRules = {
  name: [{ required: true, message: '请输入流程名称', trigger: 'blur' }],
  key: [
    { required: true, message: '请输入流程标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_-]*$/, message: '流程标识需以字母开头，仅允许字母、数字、下划线和横线', trigger: 'blur' }
  ]
}

/** 初始化设计器 */
const initModeler = () => {
  if (!canvasRef.value) return

  const instance = new BpmnModeler({
    container: canvasRef.value,
    propertiesPanel: {
      parent: propertiesPanelRef.value!,
    },
    additionalModules: [
      BpmnPropertiesPanelModule,
      BpmnPropertiesProviderModule,
      flowableExtensionModule,
    ],
    moddleExtensions: {
      flowable: flowableModdle,
    },
  })

  modeler.value = instance
  // 设置全局引用，以便属性面板组件可以访问 modeling 服务
  window.bpmnModeler = instance

  // 监听命令栈变化
  const commandStack = instance.get('commandStack')
  instance.on('commandStack.changed', () => {
    canUndo.value = commandStack.canUndo()
    canRedo.value = commandStack.canRedo()
  })

  // 如果是编辑模式，加载已有XML
  if (route.query.id) {
    loadExistingProcess(route.query.id as string)
  } else {
    // 新增模式：创建初始流程（包含开始事件和结束事件）
    createInitialProcess()
  }
}

/** 创建初始流程 */
const createInitialProcess = async () => {
  if (!modeler.value) return

  try {
    const initialXml = createInitialBpmnXml('Process_1', '新流程')
    await modeler.value.importXML(initialXml)
    modeler.value.get('canvas').zoom('fit-viewport')
  } catch (err) {
    console.error('创建初始流程失败:', err)
  }
}

/** 加载已有流程定义 */
const loadExistingProcess = async (id: string) => {
  try {
    const [xml, detail] = await Promise.all([
      processDefinitionApi.getXml(id),
      processDefinitionApi.getById(id),
    ])

    if (modeler.value) {
      if (xml) {
        try {
          await modeler.value.importXML(xml)
          modeler.value.get('canvas').zoom('fit-viewport')
        } catch (err) {
          console.error('导入 BPMN XML 失败:', err)
        }
      }
    }

    // 缓存详情并预填部署表单
    if (detail) {
      processDetail.value = detail
      deployForm.name = detail.name || ''
      deployForm.key = detail.key || ''
      deployForm.categoryId = detail.categoryId || undefined
      deployForm.description = detail.description || ''
      deployForm.formType = detail.formType || undefined
      deployForm.formId = detail.formId || undefined
    }
  } catch (e) {
    ElMessage.error('加载流程定义失败')
  }
}

/** 撤销 */
const handleUndo = () => {
  if (!modeler.value) return
  const commandStack = modeler.value.get('commandStack')
  commandStack.undo()
}

/** 重做 */
const handleRedo = () => {
  if (!modeler.value) return
  const commandStack = modeler.value.get('commandStack')
  commandStack.redo()
}

/** 清空 */
const handleClear = async () => {
  try {
    await ElMessageBox.confirm('确定清空画布？清空后不可恢复', '警告', { type: 'warning' })
    if (modeler.value) {
      modeler.value.clear()
    }
  } catch (e) {
    // 用户取消
  }
}

/** 放大 */
const handleZoomIn = () => {
  if (!modeler.value) return
  const canvas = modeler.value.get('canvas')
  canvas.zoom(canvas.zoom() * 1.1)
}

/** 缩小 */
const handleZoomOut = () => {
  if (!modeler.value) return
  const canvas = modeler.value.get('canvas')
  canvas.zoom(canvas.zoom() * 0.9)
}

/** 适应画布 */
const handleResetZoom = () => {
  if (!modeler.value) return
  const canvas = modeler.value.get('canvas')
  canvas.zoom('fit-viewport')
}

/** 返回列表页 */
const handleGoBack = () => {
  router.push('/workflow/process')
}

/** 打开部署对话框 */
const handleOpenDeployDialog = () => {
  if (!isEdit.value) {
    // 新增模式：重置表单
    deployForm.name = ''
    deployForm.key = ''
    deployForm.categoryId = undefined
    deployForm.description = ''
    deployForm.formType = undefined
    deployForm.formId = undefined
  }
  deployDialogVisible.value = true
}

/** 部署流程 */
const handleDeploy = async () => {
  if (!deployFormRef.value) return
  await deployFormRef.value.validate()

  if (!modeler.value) return

  try {
    const { xml } = await modeler.value.saveXML({ format: true })
    if (!xml) {
      ElMessage.warning('导出 BPMN XML 失败')
      return
    }

    // 将用户填写的 name/key 注入到 BPMN XML 的 <bpmn:process> 元素中
    let bpmnXml = xml
      .replace(/(<bpmn:process[^>]*?)\s+id="[^"]*"/, `$1 id="${deployForm.key}"`)
      .replace(/(<bpmn:process[^>]*?)\s+name="[^"]*"/, `$1 name="${deployForm.name}"`)
    // 如果 process 没有 name 属性，则添加
    if (!/<bpmn:process[^>]*name="/.test(bpmnXml)) {
      bpmnXml = bpmnXml.replace(
        /(<bpmn:process[^>]*?)\s+id="[^"]*"/,
        `$1 id="${deployForm.key}" name="${deployForm.name}"`
      )
    }

    deployLoading.value = true
    await processDefinitionApi.deploy({
      name: deployForm.name,
      key: deployForm.key,
      categoryId: deployForm.categoryId,
      description: deployForm.description,
      formType: deployForm.formType,
      formId: deployForm.formId,
      bpmnXml
    })
    ElMessage.success('部署成功')
    deployDialogVisible.value = false
    router.push('/workflow/process')
  } catch (e) {
    ElMessage.error('部署失败')
  } finally {
    deployLoading.value = false
  }
}

/** 加载分类列表 */
const getCategoryList = async () => {
  try {
    categoryList.value = await categoryApi.listAll()
  } catch (e) {
    // ignore
  }
}

/** 加载表单列表 */
const getFormList = async () => {
  try {
    formList.value = await formApi.listAll()
  } catch (e) {
    // ignore
  }
}

onMounted(() => {
  getCategoryList()
  getFormList()
  initModeler()
})

onBeforeUnmount(() => {
  if (modeler.value) {
    modeler.value.destroy()
  }
})
</script>

<style scoped lang="scss">
.designer-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  background: var(--el-bg-color);
  border-radius: 4px;
  overflow: hidden;
}

.designer-top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  flex-shrink: 0;
}

.top-bar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.designer-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.designer-canvas {
  flex: 1;
  overflow: hidden;
}

.bpmn-container {
  width: 100%;
  height: 100%;
  background: #f8f8f8;
}

.properties-panel-container {
  width: 300px;
  border-left: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  overflow-y: auto;
}
</style>

<!-- 非 scoped 样式，用于覆盖 bpmn-js-properties-panel 的默认样式 -->
<style lang="scss">
.properties-panel-container {
  .bio-properties-panel-container {
    height: 100%;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
    font-size: 13px;
    color: #606266;

    // ========== 顶部元素类型标题 ==========
    .bio-properties-panel-header {
      padding: 10px 12px;
      background: var(--el-bg-color);
      border-bottom: 1px solid var(--el-border-color-light);
      display: flex;
      align-items: center;
      gap: 8px;

      .bio-properties-panel-header-icon {
        width: 24px;
        height: 24px;
        padding: 0;
        background: transparent;
        border-radius: 4px;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        margin: 0;

        svg {
          width: 20px;
          height: 20px;
        }
      }

      .bio-properties-panel-header-type {
        font-size: 14px;
        font-weight: 600;
        color: var(--el-text-color-primary);
        flex: 1;
        line-height: 1;
      }
    }

    // ========== 属性组 ==========
    .bio-properties-panel-group {
      border-bottom: 1px solid var(--el-border-color-lighter);
    }

    // 属性组标题
    .bio-properties-panel-group-header {
      background: var(--el-fill-color-lighter);
      padding: 8px 12px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      cursor: pointer;
      transition: background-color 0.15s;
      user-select: none;

      &:hover {
        background: var(--el-fill-color);
      }

      // 标题文字
      .bio-properties-panel-group-header-title {
        font-size: 12px;
        font-weight: 600;
        color: var(--el-text-color-primary);
        letter-spacing: 0.3px;
      }

      // 按钮区域
      .bio-properties-panel-group-header-buttons {
        display: flex;
        align-items: center;
        gap: 6px;
      }

      // 编辑指示点 - 隐藏
      .bio-properties-panel-dot {
        display: none;
      }

      // 箭头按钮
      .bio-properties-panel-arrow {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 20px;
        height: 20px;
        padding: 0;
        border: none;
        background: transparent;
        cursor: pointer;
        border-radius: 3px;
        color: var(--el-text-color-secondary);
        transition: color 0.15s, background-color 0.15s;

        &:hover {
          background: var(--el-fill-color);
          color: var(--el-text-color-primary);
        }

        svg {
          width: 14px;
          height: 14px;
          fill: currentColor;
          transition: transform 0.2s ease;
        }

        // 展开状态箭头旋转90度向下
        .bio-properties-panel-arrow-down {
          transform: rotate(90deg);
        }
      }
    }

    // 属性组内容
    .bio-properties-panel-group-entries {
      overflow: hidden;
      transition: max-height 0.2s ease;

      &:not(.open) {
        max-height: 0 !important;
        padding: 0;
        border: none;
      }
    }

    // ========== 属性条目容器 ==========
    .bio-properties-panel-entry {
      padding: 8px 12px;
      border-bottom: 1px solid var(--el-border-color-extra-light);

      &:last-child {
        border-bottom: none;
      }
    }

    // ========== 输入组件容器 - 左右布局 ==========
    .bio-properties-panel-textfield,
    .bio-properties-panel-textarea,
    .bio-properties-panel-select {
      display: flex;
      align-items: flex-start;
      gap: 12px;
    }

    // Label 左侧
    .bio-properties-panel-label {
      font-size: 13px;
      color: var(--el-text-color-regular);
      min-width: 75px;
      max-width: 85px;
      flex-shrink: 0;
      line-height: 32px;
      text-align: left;
      font-weight: 500;
    }

    // 输入组件右侧 - 占据剩余空间
    .bio-properties-panel-input {
      flex: 1;
      min-width: 0;
    }

    // 文本输入框和文本区域样式
    .bio-properties-panel-input {
      width: 100%;
      padding: 6px 10px;
      border: 1px solid var(--el-border-color);
      border-radius: 4px;
      font-size: 13px;
      color: var(--el-text-color-primary);
      background: var(--el-bg-color);
      outline: none;
      transition: border-color 0.2s, box-shadow 0.2s;
      box-sizing: border-box;
      resize: none;

      &:focus {
        border-color: var(--el-color-primary);
        box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
      }

      &:hover:not(:focus) {
        border-color: var(--el-border-color-hover);
      }

      &::placeholder {
        color: var(--el-text-color-placeholder);
      }
    }

    // 下拉选择框容器
    .bio-properties-panel-select {
      .bio-properties-panel-input {
        height: 32px;
        appearance: none;
        cursor: pointer;
        background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23606266' d='M6 8L1 3h10z'/%3E%3C/svg%3E");
        background-repeat: no-repeat;
        background-position: right 10px center;
      }
    }

    // 复选框样式
    .bio-properties-panel-checkbox {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-left: 87px;

      input[type="checkbox"] {
        width: 16px;
        height: 16px;
        cursor: pointer;
        accent-color: var(--el-color-primary);
      }

      label {
        font-size: 13px;
        color: var(--el-text-color-primary);
        cursor: pointer;
        user-select: none;
      }
    }

    // 描述文本样式
    .bio-properties-panel-description {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      margin-left: 87px;
      margin-top: 4px;
      line-height: 1.4;
    }

    // 错误提示样式
    .bio-properties-panel-error {
      color: var(--el-color-danger);
      font-size: 12px;
      margin-left: 87px;
      margin-top: 4px;
    }

    // 空状态提示
    .bio-properties-panel-placeholder {
      padding: 20px;
      text-align: center;
      color: var(--el-text-color-placeholder);
      font-size: 14px;
    }
  }
}
</style>