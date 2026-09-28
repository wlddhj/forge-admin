<template>
  <el-sub-menu v-if="visibleChildren.length" :index="menu.routePath || `menu_${menu.id}`">
    <template #title>
      <IconPreview v-if="menu.icon" :icon="menu.icon" :size="18" />
      <span>{{ menu.menuName || menu.meta?.title }}</span>
    </template>
    <SidebarMenuItem
      v-for="child in visibleChildren"
      :key="child.id"
      :menu="child"
      :parent-path="fullPath"
    />
  </el-sub-menu>
  <el-menu-item v-else :index="fullPath">
    <IconPreview v-if="menu.icon" :icon="menu.icon" :size="18" />
    <span>{{ menu.menuName || menu.meta?.title }}</span>
  </el-menu-item>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import IconPreview from '@/components/IconPreview.vue'

const props = defineProps<{
  menu: any
  parentPath?: string
}>()

// 过滤按钮与隐藏项；目录节点（含多级）由此进入递归分支
const visibleChildren = computed(() =>
  (props.menu.children || []).filter((c: any) => c.menuType !== 2 && c.visible !== 0)
)

const fullPath = computed(() => {
  const p = props.menu.routePath || props.menu.path || ''
  if (p.startsWith('/')) return p
  return props.parentPath ? `${props.parentPath}/${p}` : p
})
</script>
