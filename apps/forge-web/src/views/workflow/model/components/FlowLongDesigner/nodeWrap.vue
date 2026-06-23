<template>
  <promoter v-if="nodeConfig.type == 0" v-model="nodeConfig"></promoter>

  <approver v-if="nodeConfig.type == 1" v-model="nodeConfig"></approver>

  <send v-if="nodeConfig.type == 2" v-model="nodeConfig"></send>

  <branch v-if="nodeConfig.type == 4" v-model="nodeConfig">
    <template v-slot="slot">
      <node-wrap v-if="slot.node" v-model="slot.node.childNode"></node-wrap>
    </template>
  </branch>

  <node-wrap v-if="nodeConfig.childNode" v-model="nodeConfig.childNode"></node-wrap>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import approver from './nodes/approver.vue'
import promoter from './nodes/promoter.vue'
import branch from './nodes/branch.vue'
import send from './nodes/send.vue'
import type { FlowlongNodeModel } from '@/composables/useFlowLongDataTransform'

const props = defineProps<{
  modelValue: FlowlongNodeModel | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: FlowlongNodeModel): void
}>()

const nodeConfig = ref<FlowlongNodeModel>({})

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      nodeConfig.value = val
    }
  },
  { immediate: true }
)

watch(
  nodeConfig,
  (val) => {
    emit('update:modelValue', val)
  },
  { deep: true }
)
</script>

<style></style>