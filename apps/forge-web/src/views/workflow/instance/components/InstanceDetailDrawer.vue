<template>
  <el-drawer
    class="instance-detail-drawer"
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

<!-- append-to-body 传送门元素，scoped 样式够不到，用全局类收窄作用范围 -->
<style lang="scss">
.instance-detail-drawer.el-drawer {
  // 详情卡片在灰底上呈现，与列表页内容区观感一致（暗色模式随主题变量自适应）
  --el-drawer-bg-color: var(--el-bg-color-page);
}
</style>
