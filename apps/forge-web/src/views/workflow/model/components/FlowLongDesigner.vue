<template>
  <div class="flowlong-designer" ref="containerRef">
    <!-- 缩放容器 -->
    <div class="designer-canvas" :style="canvasStyle" ref="canvasRef">
      <!-- 流程节点树 -->
      <div class="node-tree" v-if="localModel">
        <FlowNode
          :node="localModel.nodeConfig"
          :user-list="userList"
          :role-list="roleList"
          @add-node="handleAddNode"
          @delete-node="handleDeleteNode"
          @update-node="handleUpdateNode"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, shallowRef } from 'vue'
import FlowNode from './FlowNode.vue'
import type { FlowLongProcessModel, FlowLongNodeModel } from '@/composables/useFlowLongDesigner'
import { useFlowLongDesigner, FlowLongNodeType } from '@/composables/useFlowLongDesigner'

const props = defineProps<{
  modelValue: FlowLongProcessModel | null
  disabled?: boolean
  userList?: { label: string; value: string }[]
  roleList?: { label: string; value: string }[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: FlowLongProcessModel): void
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLDivElement | null>(null)

const { addApprovalNode, addCcNode, deleteNode } = useFlowLongDesigner()

// 使用 shallowRef 避免深度监听导致的循环
const localModel = shallowRef<FlowLongProcessModel | null>(null)

// 缩放比例
const scale = ref(1)
const minScale = 0.5
const maxScale = 2

// 清理节点的 parentNode 引用（用于导出）
const cleanParentNodeForExport = (node: FlowLongNodeModel | undefined): FlowLongNodeModel | undefined => {
  if (!node) return undefined
  const result: FlowLongNodeModel = { ...node, parentNode: undefined }
  if (result.childNode) {
    result.childNode = cleanParentNodeForExport(result.childNode)
  }
  if (result.conditionNodes) {
    result.conditionNodes = result.conditionNodes.map(cn => ({
      ...cn,
      childNode: cleanParentNodeForExport(cn.childNode)
    }))
  }
  return result
}

// 初始化：只在外部值首次变化时更新
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal && !localModel.value) {
      localModel.value = newVal
    }
  },
  { immediate: true }
)

// 计算画布样式
const canvasStyle = computed(() => ({
  transform: `scale(${scale.value})`,
  transformOrigin: 'center top'
}))

// 缩放控制
const zoomIn = () => {
  if (scale.value < maxScale) {
    scale.value = Math.min(maxScale, scale.value + 0.1)
  }
}

const zoomOut = () => {
  if (scale.value > minScale) {
    scale.value = Math.max(minScale, scale.value - 0.1)
  }
}

const resetZoom = () => {
  scale.value = 1
}

// 添加节点
const handleAddNode = (parentNode: FlowLongNodeModel, type: number) => {
  if (props.disabled || !localModel.value) return

  if (type === FlowLongNodeType.APPROVAL) {
    addApprovalNode(parentNode, '审批节点')
  } else if (type === FlowLongNodeType.CC) {
    addCcNode(parentNode, '抄送节点')
  } else if (type === FlowLongNodeType.CONDITION) {
    // 添加条件分支
    addConditionBranch(parentNode)
  }

  // 触发更新并 emit
  triggerUpdate()
}

// 添加条件分支
const addConditionBranch = (parentNode: FlowLongNodeModel) => {
  // 条件分支节点
  const conditionNode: FlowLongNodeModel = {
    nodeName: '条件分支',
    nodeKey: `condition_${Date.now()}`,
    type: FlowLongNodeType.CONDITION,
    parentNode,
    conditionNodes: [
      {
        nodeId: `branch_${Date.now()}_1`,
        nodeName: '分支1',
        childNode: parentNode.childNode
      },
      {
        nodeId: `branch_${Date.now()}_2`,
        nodeName: '分支2',
        childNode: undefined
      }
    ]
  }

  // 将父节点的 childNode 指向条件分支节点
  parentNode.childNode = conditionNode

  // 原来的子节点成为第一个分支的子节点
  if (conditionNode.conditionNodes && conditionNode.conditionNodes[0].childNode) {
    conditionNode.conditionNodes[0].childNode.parentNode = conditionNode
  }
}

// 删除节点
const handleDeleteNode = (node: FlowLongNodeModel) => {
  if (props.disabled || !localModel.value) return
  if (node.type === FlowLongNodeType.START || node.type === FlowLongNodeType.END) return

  deleteNode(node)
  triggerUpdate()
}

// 更新节点配置
const handleUpdateNode = (node: FlowLongNodeModel, updates: Partial<FlowLongNodeModel>) => {
  if (props.disabled || !localModel.value) return

  Object.assign(node, updates)
  triggerUpdate()
}

// 触发更新：创建新对象并 emit
const triggerUpdate = () => {
  if (!localModel.value) return

  // 强制创建新引用以触发视图更新
  const newModel = { ...localModel.value }
  localModel.value = newModel

  // Emit 清理后的模型（去除 parentNode 循环引用）
  const cleanModel = {
    ...newModel,
    nodeConfig: cleanParentNodeForExport(newModel.nodeConfig)!
  }
  emit('update:modelValue', cleanModel as FlowLongProcessModel)
}

// 导出 JSON
const exportJson = (): string => {
  if (!localModel.value) return ''

  const cleanModel: FlowLongProcessModel = {
    ...localModel.value,
    nodeConfig: cleanParentNodeForExport(localModel.value.nodeConfig)!
  }

  return JSON.stringify(cleanModel)
}

// 暴露方法给父组件
defineExpose({
  zoomIn,
  zoomOut,
  resetZoom,
  exportJson
})
</script>

<style scoped lang="scss">
.flowlong-designer {
  width: 100%;
  height: 100%;
  overflow: auto;
  background: #f5f7fa;
  display: flex;
  justify-content: center;
  padding: 40px 20px;
}

.designer-canvas {
  min-width: 800px;
  transition: transform 0.2s ease;
}

.node-tree {
  display: flex;
  flex-direction: column;
  align-items: center;
}
</style>