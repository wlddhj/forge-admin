<!--
  并行分支容器（FlowLong TaskType.parallelBranch = 8）

  全部分支同时执行，各分支末任务全部完成后汇聚进入容器后续节点。
  与条件分支的差异：无条件表达式、无优先级。
-->
<template>
  <div class="branch-wrap">
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
import { ref, watch } from 'vue'
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
