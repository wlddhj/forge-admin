<template>
  <div class="flowlong-model-designer">
    <!-- 顶部工具栏 -->
    <div class="designer-toolbar">
      <div class="toolbar-left">
        <el-button size="small" @click="handleGoBack">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <span class="page-title">{{ modelData?.name || '流程设计' }}</span>
      </div>
      <div class="toolbar-center">
        <el-button-group>
          <el-button size="small" @click="handleZoomOut">
            <el-icon><ZoomOut /></el-icon>
          </el-button>
          <el-button size="small" @click="handleResetZoom">适应</el-button>
          <el-button size="small" @click="handleZoomIn">
            <el-icon><ZoomIn /></el-icon>
          </el-button>
        </el-button-group>
      </div>
      <div class="toolbar-right">
        <el-button size="small" @click="showJsonDrawer">
          <el-icon><View /></el-icon>
          查看 JSON
        </el-button>
        <el-button type="success" size="small" @click="handleSave">
          <el-icon><Check /></el-icon>保存
        </el-button>
        <el-button type="primary" size="small" @click="handleDeploy">
          <el-icon><Upload /></el-icon>部署
        </el-button>
      </div>
    </div>

    <!-- 设计器主体 -->
    <div class="designer-content" :style="canvasStyle" v-if="processModel">
      <ScWorkflow
        ref="workflowRef"
        v-model="processModel.nodeConfig"
      />
    </div>
    <div class="designer-content loading-content" v-else>
      <el-icon class="is-loading" size="40"><Loading /></el-icon>
    </div>

    <!-- JSON 查看抽屉 -->
    <el-drawer v-model="jsonDrawerVisible" title="流程模型 JSON" :size="500">
      <el-button type="primary" plain @click="copyJson">复制 JSON</el-button>
      <pre class="json-preview">{{ modelJsonStr }}</pre>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ZoomOut, ZoomIn, Check, Upload, View, Loading } from '@element-plus/icons-vue'
import ScWorkflow from './components/FlowLongDesigner/index.vue'
import { modelApi } from '@/api/workflow/model'
import { useFlowLongDataTransform } from '@/composables/useFlowLongDataTransform'
import type { FlowlongProcessModel } from '@/composables/useFlowLongDataTransform'

const router = useRouter()
const route = useRoute()
const workflowRef = ref<InstanceType<typeof ScWorkflow> | null>(null)

// 数据转换 Hook
const { createInitialModel, transformDesignerToBackend, transformBackendToDesigner } = useFlowLongDataTransform()

// 流程模型数据（flowlong-designer 格式）
const processModel = ref<FlowlongProcessModel | null>(null)

// 模型数据缓存（用于保存其他字段）
const modelData = ref<any>(null)

// 缩放
const scale = ref(1)

// JSON 抽屉
const jsonDrawerVisible = ref(false)

const canvasStyle = computed(() => ({
  transform: `scale(${scale.value})`,
  transformOrigin: 'center top'
}))

const modelJsonStr = computed(() => {
  if (!processModel.value) return ''
  return JSON.stringify(processModel.value, null, 2)
})

// 修复旧数据中可能被污染的节点类型（旧保存逻辑可能导致发起人节点 type=1）
const fixNodeTypes = (node: any): any => {
  if (!node) return node

  // 发起人节点特征：nodeName 包含"发起人" 或 nodeKey 以 "start_" 开头
  // 修复：type 应为 0，移除 setType 等审批人字段
  if ((node.nodeName?.includes('发起人') || node.nodeKey?.startsWith('start_')) && node.type !== 0) {
    node.type = 0
    // 发起人节点不应该有 setType、examineMode 等审批配置字段
    delete node.setType
    delete node.examineMode
    delete node.termAuto
    delete node.term
    delete node.termMode
  }

  // 递归处理子节点
  if (node.childNode) {
    fixNodeTypes(node.childNode)
  }

  // 处理条件分支
  if (node.conditionNodes) {
    node.conditionNodes.forEach((cn: any) => {
      if (cn.childNode) {
        fixNodeTypes(cn.childNode)
      }
    })
  }

  return node
}

// 加载模型数据
const loadModelData = async (id: string) => {
  try {
    const data = await modelApi.getById(id)
    modelData.value = data

    // 加载流程模型 JSON
    if (data.modelJson) {
      try {
        const savedModel = JSON.parse(data.modelJson)
        // 获取节点配置（可能是整个模型对象，也可能是单独的节点配置）
        const nodeConfig = savedModel.nodeConfig || savedModel

        // 修复旧数据中可能被污染的节点类型
        const fixedNodeConfig = fixNodeTypes(nodeConfig)
        processModel.value = {
          name: data.name,
          key: data.key,
          nodeConfig: fixedNodeConfig
        }
      } catch (e) {
        // JSON 解析失败，创建初始模型
        processModel.value = createInitialModel(data.key, data.name)
      }
    } else {
      // 没有流程模型，创建初始模型
      processModel.value = createInitialModel(data.key, data.name)
    }
  } catch (e) {
    ElMessage.error('加载模型失败')
    router.push('/workflow/model')
  }
}

// 缩放控制
const handleZoomIn = () => {
  if (scale.value < 2) {
    scale.value = Math.min(2, scale.value + 0.1)
  }
}

const handleZoomOut = () => {
  if (scale.value > 0.5) {
    scale.value = Math.max(0.5, scale.value - 0.1)
  }
}

const handleResetZoom = () => {
  scale.value = 1
}

// 返回列表
const handleGoBack = () => {
  router.push('/workflow/model')
}

// 显示 JSON 抽屉
const showJsonDrawer = () => {
  jsonDrawerVisible.value = true
}

// 复制 JSON
const copyJson = async () => {
  try {
    await navigator.clipboard.writeText(modelJsonStr.value)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

// 保存模型
const handleSave = async () => {
  const id = route.query.id as string
  if (!id) {
    ElMessage.warning('缺少模型 ID')
    return
  }

  if (!processModel.value) {
    ElMessage.warning('流程模型为空')
    return
  }

  try {
    await ElMessageBox.confirm('确定保存模型？', '保存确认', { type: 'info' })

    // FlowLong 期望的格式: { name, key, nodeConfig: {...} }
    // 设计器格式与 FlowLong 格式一致，直接保存
    const processModelJson = {
      name: modelData.value?.name || processModel.value.name,
      key: modelData.value?.key || processModel.value.key,
      nodeConfig: processModel.value.nodeConfig
    }
    await modelApi.update({
      id,
      name: modelData.value?.name || '',
      key: modelData.value?.key || '',
      category: modelData.value?.category,
      description: modelData.value?.description,
      formType: modelData.value?.formType,
      formId: modelData.value?.formId,
      autoCopyStrategy: modelData.value?.autoCopyStrategy,
      autoCopyParam: modelData.value?.autoCopyParam,
      metaInfo: modelData.value?.metaInfo,
      modelJson: JSON.stringify(processModelJson)
    })
    ElMessage.success('保存成功')
  } catch (e) {
    // 用户取消或其他错误
  }
}

// 部署模型
const handleDeploy = async () => {
  const id = route.query.id as string
  if (!id) {
    ElMessage.warning('缺少模型 ID')
    return
  }

  try {
    await ElMessageBox.confirm('确定部署该模型？部署后将创建流程定义。', '部署确认', { type: 'info' })
    await modelApi.deploy(id)
    ElMessage.success('部署成功')
    router.push('/workflow/model')
  } catch (e) {
    // 用户取消或其他错误
  }
}

onMounted(async () => {
  if (route.query.id) {
    await loadModelData(route.query.id as string)
  } else {
    ElMessage.warning('缺少模型 ID')
    router.push('/workflow/model')
  }
})
</script>

<style scoped lang="scss">
.flowlong-model-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  background: #f5f7fa;
}

.designer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .page-title {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
    }
  }

  .toolbar-center {
    display: flex;
    align-items: center;
  }

  .toolbar-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.designer-content {
  flex: 1;
  overflow: auto;
  padding: 40px 20px;
  display: flex;
  justify-content: center;
}

.loading-content {
  align-items: center;
}

.json-preview {
  margin-top: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
  overflow: auto;
  max-height: calc(100vh - 100px);
  font-size: 12px;
  white-space: pre-wrap;
}
</style>