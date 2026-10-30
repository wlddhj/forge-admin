<template>
  <el-drawer
    :model-value="modelValue"
    :title="drawerTitle"
    :size="isMobile ? '85%' : '700px'"
    @update:model-value="(val: boolean) => emit('update:modelValue', val)"
    @closed="emit('closed')"
  >
    <div ref="containerRef" style="height: 100%; display: flex; flex-direction: column;">
      <el-form inline class="assign-user-search" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="queryParams.username" placeholder="请输入用户名" clearable style="width: 130px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="queryParams.nickname" placeholder="请输入昵称" clearable style="width: 130px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="部门">
          <el-tree-select
            v-model="queryParams.deptId"
            :data="deptTree"
            :props="({ label: 'deptName', value: 'id' } as any)"
            placeholder="请选择部门"
            check-strictly
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
        <el-form-item>
          <span style="color: #909399; font-size: 12px;">已选 {{ selectedIdSet.size }} 个用户</span>
        </el-form-item>
      </el-form>
      <vxe-table
        id="roleAssignUserTable"
        ref="tableRef"
        :data="userList"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ keyField: 'id', isHover: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        @checkbox-change="handleCheckboxChange"
        @checkbox-all="handleCheckboxAll"
      >
        <vxe-column type="checkbox" width="44" align="center" />
        <vxe-column type="seq" title="序号" width="50" align="center" />
        <vxe-column field="username" title="用户名" min-width="100" />
        <vxe-column field="nickname" title="昵称" min-width="100" />
        <vxe-column field="deptName" title="部门" min-width="110" />
      </vxe-table>
      <TablePagination
        v-model:page-num="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        @change="getUserData"
      />
    </div>
    <template #footer>
      <div style="display: flex; gap: 12px">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="!role" @click="handleConfirm">确定</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserList, getDeptTree, getRoleUserIds, assignRoleUsers } from '@/api/system'
import type { User, Role, DeptTree } from '@/types/system'
import { useResponsive } from '@/composables/useResponsive'
import { useDrawerTableHeight } from '@/composables/useDrawerTableHeight'

const props = defineProps<{
  modelValue: boolean
  role: Role | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
  closed: []
}>()

const { isMobile } = useResponsive()

const containerRef = ref<HTMLElement>()
const { tableHeight, updateHeight } = useDrawerTableHeight({
  containerRef,
  excludeSelectors: ['.assign-user-alert', '.assign-user-search'],
  extraOffset: 25,
})

const tableRef = ref<{ clearCheckboxRow: () => void; setCheckboxRow: (rows: User | User[], checked: boolean) => void } | null>(null)
const loading = ref(false)
const submitLoading = ref(false)
const userList = ref<User[]>([])
const total = ref(0)
const deptTree = ref<DeptTree[]>([])
// 待提交的全量用户ID集（含从未翻页到的既有分配用户），不依赖表格内部选择态
const selectedIdSet = ref<Set<number>>(new Set())

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  username: '',
  nickname: '',
  deptId: undefined as number | undefined
})

const drawerTitle = computed(() =>
  props.role ? `分配用户 - ${props.role.roleName}` : '分配用户'
)

// 组件由父级 v-if 控制在打开时才挂载，modelValue 初始即 true，
// el-drawer 的 @open 不会触发（无 false→true 变化），故在 onMounted 加载
onMounted(() => {
  handleOpen()
})

const handleOpen = async () => {
  queryParams.value = { pageNum: 1, pageSize: 10, username: '', nickname: '', deptId: undefined }
  selectedIdSet.value = new Set()
  loading.value = true
  try {
    const [tree, roleUserIds] = await Promise.all([
      deptTree.value.length ? Promise.resolve(deptTree.value) : getDeptTree(),
      props.role ? getRoleUserIds(props.role.id) : Promise.resolve<number[]>([])
    ])
    deptTree.value = (tree as DeptTree[]) || []
    // 后端 LongSerializer 把所有 Long 序列化为字符串，Set 边界统一 Number() 归一，
    // 否则取消勾选 delete(数字) 删不掉初始化的字符串 id
    selectedIdSet.value = new Set((roleUserIds || []).map(Number))
    await getUserData()
    updateHeight()
  } finally {
    loading.value = false
  }
}

const getUserData = async () => {
  loading.value = true
  try {
    const res = await getUserList(queryParams.value)
    userList.value = res.list
    total.value = res.total
    // 数据到位后按已选集回显当前页勾选状态（user.id 运行时为字符串，同样归一）
    await nextTick()
    tableRef.value?.clearCheckboxRow()
    const checkedRows = userList.value.filter(user => selectedIdSet.value.has(Number(user.id)))
    if (checkedRows.length) {
      tableRef.value?.setCheckboxRow(checkedRows, true)
    }
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.value.pageNum = 1
  getUserData()
}

// 单行勾选/取消（vxe checkbox-change：{ row, checked }）：增量维护已选集
const handleCheckboxChange = ({ row, checked }: { row: User; checked: boolean }) => {
  const rowId = Number(row.id)
  if (checked) {
    selectedIdSet.value.add(rowId)
  } else {
    selectedIdSet.value.delete(rowId)
  }
}

// 当前页全选/全不选（vxe checkbox-all：{ checked }）
const handleCheckboxAll = ({ checked }: { checked: boolean }) => {
  userList.value.forEach(user => {
    const rowId = Number(user.id)
    if (checked) {
      selectedIdSet.value.add(rowId)
    } else {
      selectedIdSet.value.delete(rowId)
    }
  })
}

const handleConfirm = async () => {
  if (!props.role) return
  submitLoading.value = true
  try {
    await assignRoleUsers(props.role.id, [...selectedIdSet.value])
    ElMessage.success('分配成功')
    emit('update:modelValue', false)
    emit('success')
  } finally {
    submitLoading.value = false
  }
}
</script>
