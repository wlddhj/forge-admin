<template>
  <el-drawer
    :model-value="modelValue"
    :title="drawerTitle"
    :size="isMobile ? '85%' : '520px'"
    @update:model-value="(val: boolean) => emit('update:modelValue', val)"
    @closed="emit('closed')"
  >
    <div ref="containerRef" style="height: 100%">
      <el-alert
        :title="mode === 'single' ? '请勾选该用户的角色，保存后覆盖原角色' : `将所选 ${userIds.length} 个用户的角色统一覆盖为勾选的角色`"
        type="info"
        :closable="false"
        show-icon
        class="assign-role-alert"
        style="margin-bottom: 12px"
      />
      <el-form inline class="assign-role-search" @submit.prevent>
        <el-form-item label="角色名称">
          <el-input v-model="queryRoleName" placeholder="请输入角色名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <span style="color: #909399; font-size: 12px;">已选 {{ selectedRoleIds.length }} 个角色</span>
        </el-form-item>
      </el-form>
      <el-table
        ref="tableRef"
        v-loading="fetching"
        :data="filteredRoles"
        row-key="id"
        :height="tableHeight"
        border
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="44" reserve-selection />
        <el-table-column prop="roleName" label="角色名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      </el-table>
    </div>
    <template #footer>
      <div style="display: flex; gap: 12px">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleConfirm">确定</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAllRoles, getUserRoles, assignUserRoles, batchAssignUserRoles } from '@/api/system'
import { useResponsive } from '@/composables/useResponsive'
import { useDrawerTableHeight } from '@/composables/useDrawerTableHeight'
import type { User, Role } from '@/types/system'

const props = defineProps<{
  modelValue: boolean
  mode: 'single' | 'batch'
  user: User | null
  userIds: number[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
  closed: []
}>()

const { isMobile } = useResponsive()

const containerRef = ref<HTMLElement>()
const { tableHeight } = useDrawerTableHeight({
  containerRef,
  excludeSelectors: ['.assign-role-alert', '.assign-role-search'],
})

const tableRef = ref()
const fetching = ref(false)
const submitLoading = ref(false)
const roles = ref<Role[]>([])
const queryRoleName = ref('')
const selectedRoleIds = ref<number[]>([])

const drawerTitle = computed(() => {
  if (props.mode === 'single' && props.user) {
    return `分配角色 - ${props.user.nickname || props.user.username}`
  }
  return `批量分配角色（已选 ${props.userIds.length} 个用户）`
})

const filteredRoles = computed(() => {
  const keyword = queryRoleName.value.trim().toLowerCase()
  if (!keyword) return roles.value
  return roles.value.filter(role => role.roleName.toLowerCase().includes(keyword))
})

// 组件由父级 v-if 控制在打开时才挂载，modelValue 初始即 true，
// el-drawer 的 @open 不会触发（无 false→true 变化），故在 onMounted 加载
onMounted(() => {
  handleOpen()
})

const handleOpen = async () => {
  queryRoleName.value = ''
  tableRef.value?.clearSelection()
  selectedRoleIds.value = []
  fetching.value = true
  try {
    roles.value = (await getAllRoles()) || []
    if (props.mode === 'single' && props.user) {
      const roleIds = (await getUserRoles(props.user.id)) || []
      await nextTick()
      roleIds.forEach(id => {
        const row = roles.value.find(role => role.id === id)
        if (row) tableRef.value?.toggleRowSelection(row, true)
      })
    }
  } finally {
    fetching.value = false
  }
}

const handleSelectionChange = (rows: Role[]) => {
  selectedRoleIds.value = rows.map(row => row.id)
}

const handleConfirm = async () => {
  submitLoading.value = true
  try {
    if (props.mode === 'single' && props.user) {
      await assignUserRoles(props.user.id, selectedRoleIds.value)
    } else {
      await batchAssignUserRoles(props.userIds, selectedRoleIds.value)
    }
    ElMessage.success('分配成功')
    emit('update:modelValue', false)
    emit('success')
  } finally {
    submitLoading.value = false
  }
}
</script>
