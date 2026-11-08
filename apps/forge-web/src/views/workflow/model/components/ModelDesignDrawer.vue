<template>
  <el-drawer
    class="model-design-drawer"
    :model-value="modelValue"
    :size="isMobile ? '100%' : '85%'"
    :with-header="false"
    :close-on-click-modal="false"
    destroy-on-close
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <FlowLongModelDesigner
      v-if="modelValue && modelId"
      :model-id="modelId"
      embedded
      @close="emit('update:modelValue', false)"
      @saved="emit('saved')"
      @deployed="emit('deployed')"
    />
  </el-drawer>
</template>

<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { useResponsive } from '@/composables/useResponsive'

defineOptions({ name: 'ModelDesignDrawer' })

// 设计器（ScWorkflow + 全部节点组件）打开时才加载，不打进列表页 chunk
const FlowLongModelDesigner = defineAsyncComponent(() => import('../FlowLongModelDesigner.vue'))

withDefaults(defineProps<{
  modelValue: boolean
  modelId?: string
}>(), {
  modelId: ''
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
  deployed: []
}>()

const { isMobile } = useResponsive()
</script>

<!-- append-to-body 传送门元素，scoped 样式够不到，用全局类收窄作用范围 -->
<style lang="scss">
.model-design-drawer.el-drawer .el-drawer__body {
  // 设计器自带工具栏与滚动画布：抽屉 body 不留内边距、不自行滚动
  padding: 0;
  overflow: hidden;
}
</style>
