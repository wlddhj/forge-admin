<!--
  并行分支容器（FlowLong TaskType.parallelBranch = 8）

  全部分支同时执行，各分支末任务全部完成后汇聚进入容器后续节点。
  与条件分支的差异：无条件表达式、无优先级。
-->
<template>
  <div class="branch-wrap">
    <!-- 引擎汇聚语义风险提示：子流程分支若最后完成，父实例会悬挂（非阻断，运行时审批顺序决定） -->
    <el-alert
      v-if="hasSubProcess"
      class="subprocess-risk-tip"
      type="warning"
      :closable="false"
      show-icon
    >
      并行分支内含子流程：请确保子流程分支<b>不要最后完成</b>（普通分支末任务全部完成后才触发汇聚，子流程返回不触发），否则父流程可能悬挂。建议子流程分支先审。
    </el-alert>
    <div class="branch-box-wrap">
      <div class="branch-box">
        <el-button class="add-branch" type="success" plain round @click="addTerm">添加分支</el-button>
        <div v-for="(item, idx) in nodeConfig.parallelNodes" :key="idx" class="col-box">
          <div class="condition-node">
            <div class="condition-node-box">
              <div class="auto-judge">
                <div class="title">
                  <span class="node-title">{{ item.nodeName }}</span>
                  <el-icon class="close" @click.stop="delTerm(idx)"><Close /></el-icon>
                </div>
                <div class="content">
                  <span>分支内任务并行执行</span>
                </div>
              </div>
              <add-node :model-value="item.childNode ?? null" @update:model-value="item.childNode = $event"></add-node>
            </div>
          </div>
          <slot v-if="item.childNode" :node="item"></slot>
          <div v-if="idx == 0" class="top-left-cover-line"></div>
          <div v-if="idx == 0" class="bottom-left-cover-line"></div>
          <div v-if="idx == nodeConfig.parallelNodes!.length - 1" class="top-right-cover-line"></div>
          <div v-if="idx == nodeConfig.parallelNodes!.length - 1" class="bottom-right-cover-line"></div>
        </div>
      </div>
      <add-node :model-value="nodeConfig.childNode ?? null" @update:model-value="nodeConfig.childNode = $event"></add-node>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { Close } from '@element-plus/icons-vue'
import addNode from './addNode.vue'
import type { FlowlongNodeModel, FlowlongConditionNode } from '@/composables/useFlowLongDataTransform'

const props = defineProps<{
  modelValue: FlowlongNodeModel
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: FlowlongNodeModel | null): void
}>()

const nodeConfig = ref<FlowlongNodeModel>({} as FlowlongNodeModel)

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      nodeConfig.value = val
    }
  },
  { immediate: true }
)

const getNodeKey = () => {
  return 'flk' + Date.now() + Math.random().toString(36).slice(2, 6)
}

// 任一分支链（childNode 递归）含子流程节点(type=5)时提示引擎汇聚语义风险
const hasSubProcess = computed(() => {
  const chainHasSub = (node?: FlowlongNodeModel): boolean => {
    for (let cur = node; cur; cur = cur.childNode) {
      if (cur.type === 5) return true
    }
    return false
  }
  return (nodeConfig.value.parallelNodes || []).some((b) => chainHasSub(b.childNode))
})

const addTerm = () => {
  const len = nodeConfig.value.parallelNodes!.length + 1
  nodeConfig.value.parallelNodes!.push({
    nodeName: '分支' + len,
    nodeKey: getNodeKey(),
    type: 3,
    priorityLevel: len,
    conditionMode: 1,
    conditionList: [],
    childNode: undefined
  })
  emit('update:modelValue', nodeConfig.value)
}

const delTerm = (idx: number) => {
  nodeConfig.value.parallelNodes!.splice(idx, 1)
  if (nodeConfig.value.parallelNodes!.length == 1) {
    // 仅剩一条分支时分支结构失去意义：把容器后续节点接到该分支链尾后自删
    if (nodeConfig.value.childNode) {
      if (nodeConfig.value.parallelNodes![0].childNode) {
        reData(nodeConfig.value.parallelNodes![0].childNode!, nodeConfig.value.childNode)
      } else {
        nodeConfig.value.parallelNodes![0].childNode = nodeConfig.value.childNode
      }
    }
    emit('update:modelValue', nodeConfig.value.parallelNodes![0].childNode || null)
  } else {
    emit('update:modelValue', nodeConfig.value)
  }
}

const reData = (data: FlowlongConditionNode | FlowlongNodeModel, addData: FlowlongNodeModel | undefined) => {
  if (!data.childNode) {
    data.childNode = addData
  } else {
    reData(data.childNode!, addData)
  }
}
</script>

<style scoped lang="scss">
.subprocess-risk-tip {
  margin-bottom: 12px;
}
</style>
