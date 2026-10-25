<!--
  自动通过/自动拒绝节点（FlowLong TaskType.autoPass = 30 / autoReject = 31）

  引擎约束：必须放在分支内且无后续节点（到达即直接以 autoPass/autoReject 终结实例）。
-->
<template>
  <div class="node-wrap">
    <div class="node-wrap-box" @click="show">
      <div class="title" :style="{ background: nodeConfig.type == 30 ? '#52c41a' : '#f5222d' }">
        <el-icon class="icon"><component :is="nodeConfig.type == 30 ? CircleCheck : CircleClose" /></el-icon>
        <span>{{ nodeConfig.nodeName }}</span>
        <el-icon class="close" @click.stop="delNode()"><Close /></el-icon>
      </div>
      <div class="content">
        <span>{{ nodeConfig.type == 30 ? '流程到此自动通过' : '流程到此自动拒绝' }}</span>
      </div>
    </div>
    <el-drawer v-model="drawer" class="wf-node-drawer" :title="nodeConfig.type == 30 ? '自动通过设置' : '自动拒绝设置'" destroy-on-close append-to-body :size="420">
      <template #header>
        <div class="node-wrap-drawer__title">
          <label v-if="!isEditTitle" @click="editTitle">
            {{ form.nodeName }}
            <el-icon class="node-wrap-drawer__title-edit"><Edit /></el-icon>
          </label>
          <el-input
            v-if="isEditTitle"
            ref="nodeTitleRef"
            v-model="form.nodeName"
            clearable
            @blur="saveTitle"
            @keyup.enter="saveTitle"
          ></el-input>
        </div>
      </template>
      <el-container>
        <el-main style="padding: 0 20px 20px 20px">
          <el-alert
            :title="nodeConfig.type == 30
              ? '流程到达该节点时直接以「自动通过」结束。请将其放在条件分支内且不要添加后续节点。'
              : '流程到达该节点时直接以「自动拒绝」结束。请将其放在条件分支内且不要添加后续节点。'"
            type="warning"
            :closable="false"
          />
        </el-main>
        <el-footer>
          <el-button type="primary" @click="save">保存</el-button>
          <el-button @click="drawer = false">取消</el-button>
        </el-footer>
      </el-container>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { CircleCheck, CircleClose, Close, Edit } from '@element-plus/icons-vue'
import type { FlowlongNodeModel } from '@/composables/useFlowLongDataTransform'

const props = defineProps<{
  modelValue: FlowlongNodeModel
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: FlowlongNodeModel | null): void
}>()

const nodeConfig = ref<FlowlongNodeModel>({} as FlowlongNodeModel)
const drawer = ref(false)
const isEditTitle = ref(false)
const nodeTitleRef = ref<HTMLInputElement | null>(null)
const form = ref<FlowlongNodeModel>({} as FlowlongNodeModel)

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      nodeConfig.value = val
    }
  },
  { immediate: true }
)

const show = () => {
  form.value = JSON.parse(JSON.stringify(nodeConfig.value))
  drawer.value = true
}

const editTitle = () => {
  isEditTitle.value = true
  nextTick(() => {
    nodeTitleRef.value?.focus()
  })
}

const saveTitle = () => {
  isEditTitle.value = false
}

const save = () => {
  emit('update:modelValue', form.value)
  drawer.value = false
}

// 自动节点不允许有后续节点（引擎约束），删除时直接把自身移除
const delNode = () => {
  emit('update:modelValue', null)
}
</script>
