<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    :size="isMobile ? '100%' : '72%'"
    destroy-on-close
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <InstanceDetail v-if="modelValue && instanceId" :instance-id="instanceId" embedded />
  </el-drawer>
</template>

<script setup lang="ts">
import InstanceDetail from '../InstanceDetail.vue'
import { useResponsive } from '@/composables/useResponsive'

defineOptions({ name: 'InstanceDetailDrawer' })

withDefaults(defineProps<{
  modelValue: boolean
  instanceId?: string
  title?: string
}>(), {
  instanceId: '',
  title: '流程详情'
})

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const { isMobile } = useResponsive()
</script>
