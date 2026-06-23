<template>
  <el-dialog
    v-model="dialogVisible"
    :title="titleMap[type - 1]"
    :width="type == 1 ? 680 : 460"
    destroy-on-close
    append-to-body
    @closed="emit('closed')"
  >
    <template v-if="type == 1">
      <div class="sc-user-select">
        <div class="sc-user-select__left">
          <div class="sc-user-select__search">
            <el-input v-model="keyword" prefix-icon="Search" placeholder="搜索成员" clearable>
              <template #append>
                <el-button icon="Search" @click="search"></el-button>
              </template>
            </el-input>
          </div>
          <div class="sc-user-select__select">
            <div class="sc-user-select__tree" v-loading="showGroupLoading">
              <el-scrollbar>
                <el-tree
                  class="menu"
                  ref="groupTreeRef"
                  :data="group"
                  :node-key="groupProps.key"
                  :props="groupProps"
                  highlight-current
                  :expand-on-click-node="false"
                  :current-node-key="groupId"
                  @node-click="groupClick"
                />
              </el-scrollbar>
            </div>
            <div class="sc-user-select__user" v-loading="showUserLoading">
              <div class="sc-user-select__user__list">
                <el-scrollbar ref="userScrollbarRef">
                  <el-tree
                    class="menu"
                    ref="userTreeRef"
                    :data="user"
                    :node-key="userProps.key"
                    :props="userProps"
                    :default-checked-keys="selectedIds"
                    show-checkbox
                    check-on-click-node
                    @check-change="userClick"
                  />
                </el-scrollbar>
              </div>
            </div>
          </div>
        </div>
        <div class="sc-user-select__toicon"><el-icon><ArrowRight /></el-icon></div>
        <div class="sc-user-select__selected">
          <header>已选 ({{ selected.length }})</header>
          <ul>
            <el-scrollbar>
              <li v-for="(item, index) in selected" :key="item.id">
                <span class="name">
                  <el-avatar size="small">{{ item.name.substring(0, 1) }}</el-avatar>
                  <label>{{ item.name }}</label>
                </span>
                <span class="delete">
                  <el-button type="danger" icon="Delete" circle size="small" @click="deleteSelected(index)"></el-button>
                </span>
              </li>
            </el-scrollbar>
          </ul>
        </div>
      </div>
    </template>

    <template v-if="type == 2">
      <div class="sc-user-select sc-user-select-role">
        <div class="sc-user-select__left">
          <div class="sc-user-select__select">
            <div class="sc-user-select__tree" v-loading="showGroupLoading">
              <el-scrollbar>
                <el-tree
                  class="menu"
                  ref="roleTreeRef"
                  :data="role"
                  :node-key="roleProps.key"
                  :props="roleProps"
                  show-checkbox
                  check-strictly
                  check-on-click-node
                  :expand-on-click-node="false"
                  :default-checked-keys="selectedIds"
                  @check-change="roleClick"
                />
              </el-scrollbar>
            </div>
          </div>
        </div>
        <div class="sc-user-select__toicon"><el-icon><ArrowRight /></el-icon></div>
        <div class="sc-user-select__selected">
          <header>已选 ({{ selected.length }})</header>
          <ul>
            <el-scrollbar>
              <li v-for="(item, index) in selected" :key="item.id">
                <span class="name">
                  <label>{{ item.name }}</label>
                </span>
                <span class="delete">
                  <el-button type="danger" icon="Delete" circle size="small" @click="deleteSelected(index)"></el-button>
                </span>
              </li>
            </el-scrollbar>
          </ul>
        </div>
      </div>
    </template>

    <template #footer>
      <el-button @click="dialogVisible = false">取 消</el-button>
      <el-button type="primary" @click="save">确 认</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ArrowRight, Delete, Search } from '@element-plus/icons-vue'
import { getDeptTree, getAllUsersSimple, getAllRoles } from '@/api/system'
import type { FlowlongNodeAssignee } from '@/composables/useFlowLongDataTransform'

interface TreeNode {
  id: number | string
  deptName?: string
  roleName?: string
  nickname?: string
  children?: TreeNode[]
}

const emit = defineEmits<{
  (e: 'closed'): void
}>()

// 属性配置
const groupProps = { key: 'id', label: 'deptName', children: 'children' }
const userProps = { key: 'id', label: 'nickname' }
const roleProps = { key: 'id', label: 'roleName' }

const titleMap = ['人员选择', '角色选择']
const dialogVisible = ref(false)
const showGroupLoading = ref(false)
const showUserLoading = ref(false)
const keyword = ref('')
const groupId = ref<number | string>('')
const group = ref<TreeNode[]>([])
const user = ref<TreeNode[]>([])
const role = ref<TreeNode[]>([])
const type = ref(1)
const selected = ref<FlowlongNodeAssignee[]>([])
const value = ref<FlowlongNodeAssignee[]>([])

// refs
const groupTreeRef = ref()
const userTreeRef = ref()
const userScrollbarRef = ref()
const roleTreeRef = ref()

const selectedIds = computed(() => selected.value.map((t) => String(t.id)))

// 打开赋值
const open = (openType: number, data: FlowlongNodeAssignee[]) => {
  type.value = openType
  value.value = data || []
  selected.value = JSON.parse(JSON.stringify(data || []))
  dialogVisible.value = true

  if (type.value == 1) {
    getGroup()
    getUser()
  } else if (type.value == 2) {
    getRole()
  }
}

// 获取部门树
const getGroup = async () => {
  showGroupLoading.value = true
  try {
    const res = await getDeptTree()
    // 添加"所有"节点
    const allNode = { id: '', deptName: '所有', children: [] }
    group.value = [allNode as TreeNode, ...res]
  } finally {
    showGroupLoading.value = false
  }
}

// 获取用户列表
const getUser = async () => {
  showUserLoading.value = true
  try {
    const res = await getAllUsersSimple()
    // 根据 keyword 和 groupId 过滤
    let filteredUsers = res
    if (keyword.value) {
      filteredUsers = filteredUsers.filter((u) => u.nickname?.includes(keyword.value))
    }
    // 转换为树组件需要的格式
    user.value = filteredUsers.map((u) => ({
      id: u.id,
      nickname: u.nickname
    }))
  } finally {
    showUserLoading.value = false
    userScrollbarRef.value?.setScrollTop(0)
  }
}

// 获取角色列表
const getRole = async () => {
  showGroupLoading.value = true
  try {
    const res = await getAllRoles()
    // 转换为树组件需要的格式（扁平列表）
    role.value = res.map((r) => ({
      id: r.id,
      roleName: r.roleName
    }))
  } finally {
    showGroupLoading.value = false
  }
}

// 部门点击
const groupClick = (data: TreeNode) => {
  keyword.value = ''
  groupId.value = data.id
  getUser()
}

// 用户点击
const userClick = (data: TreeNode, checked: boolean) => {
  if (checked) {
    selected.value.push({
      id: String(data.id),
      name: data.nickname || ''
    })
  } else {
    selected.value = selected.value.filter((item) => String(item.id) != String(data.id))
  }
}

// 用户搜索
const search = () => {
  groupId.value = ''
  groupTreeRef.value?.setCurrentKey('')
  getUser()
}

// 删除已选
const deleteSelected = (index: number) => {
  selected.value.splice(index, 1)
  if (type.value == 1) {
    userTreeRef.value?.setCheckedKeys(selectedIds.value)
  } else if (type.value == 2) {
    roleTreeRef.value?.setCheckedKeys(selectedIds.value)
  }
}

// 角色点击
const roleClick = (data: TreeNode, checked: boolean) => {
  if (checked) {
    selected.value.push({
      id: String(data.id),
      name: data.roleName || ''
    })
  } else {
    selected.value = selected.value.filter((item) => String(item.id) != String(data.id))
  }
}

// 提交保存
const save = () => {
  value.value.splice(0, value.value.length)
  selected.value.forEach((item) => {
    value.value.push(item)
  })
  dialogVisible.value = false
}

defineExpose({
  open
})
</script>

<style scoped>
.sc-user-select {
  display: flex;
}
.sc-user-select__left {
  width: 400px;
}

.sc-user-select__search {
  padding-bottom: 10px;
}

.sc-user-select__select {
  display: flex;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-color-white);
}
.sc-user-select__tree {
  width: 200px;
  height: 300px;
  border-right: 1px solid var(--el-border-color-light);
}
.sc-user-select__user {
  width: 200px;
  height: 300px;
  display: flex;
  flex-direction: column;
}
.sc-user-select__user__list {
  flex: 1;
  overflow: auto;
}

.sc-user-select__toicon {
  display: flex;
  justify-content: center;
  align-items: center;
  margin: 0 10px;
}
.sc-user-select__toicon i {
  display: flex;
  justify-content: center;
  align-items: center;
  background: #ccc;
  width: 20px;
  height: 20px;
  text-align: center;
  line-height: 20px;
  border-radius: 50%;
  color: #fff;
}

.sc-user-select__selected {
  height: 345px;
  width: 200px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-color-white);
}
.sc-user-select__selected header {
  height: 43px;
  line-height: 43px;
  border-bottom: 1px solid var(--el-border-color-light);
  padding: 0 15px;
  font-size: 12px;
}
.sc-user-select__selected ul {
  height: 300px;
  overflow: auto;
}
.sc-user-select__selected li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 5px 5px 15px;
  height: 38px;
}
.sc-user-select__selected li .name {
  display: flex;
  align-items: center;
}
.sc-user-select__selected li .name .el-avatar {
  background: #409eff;
  margin-right: 10px;
}
.sc-user-select__selected li .delete {
  display: none;
}
.sc-user-select__selected li:hover {
  background: var(--el-color-primary-light-9);
}
.sc-user-select__selected li:hover .delete {
  display: inline-block;
}

.sc-user-select-role .sc-user-select__left {
  width: 200px;
}
.sc-user-select-role .sc-user-select__tree {
  border: none;
  height: 343px;
}

[data-theme='dark'] .sc-user-select__selected li:hover {
  background: rgba(0, 0, 0, 0.2);
}
[data-theme='dark'] .sc-user-select__toicon i {
  background: #383838;
}
</style>