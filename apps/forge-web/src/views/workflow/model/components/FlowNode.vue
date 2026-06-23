<template>
  <div class="flow-node-wrapper">
    <!-- 当前节点 -->
    <div class="flow-node" :class="nodeClass" @click="handleClick">
      <div class="node-header">
        <el-icon v-if="nodeIcon" class="node-icon"><component :is="nodeIcon" /></el-icon>
        <span class="node-name">{{ node.nodeName }}</span>
      </div>

      <!-- 审批节点显示候选人信息 -->
      <div v-if="node.type === NodeType.APPROVAL && hasAssignee" class="node-body">
        <div class="assignee-info">
          <span v-if="node.nodeCandidate?.users && node.nodeCandidate.users.length">
            用户: {{ node.nodeCandidate.users.length }} 人
          </span>
          <span v-if="node.nodeCandidate?.roles && node.nodeCandidate.roles.length">
            角色: {{ node.nodeCandidate.roles.length }} 个
          </span>
          <span v-if="node.nodeCandidate?.initiator">
            发起人自己
          </span>
        </div>
      </div>

      <!-- 抄送节点显示抄送人信息 -->
      <div v-if="node.type === NodeType.CC && hasAssignee" class="node-body">
        <div class="assignee-info">
          <span v-if="node.nodeCandidate?.users && node.nodeCandidate.users.length">
            抄送: {{ node.nodeCandidate.users.length }} 人
          </span>
        </div>
      </div>
    </div>

    <!-- 添加节点按钮（开始和审批节点后可添加） -->
    <div v-if="canAddChild && !disabled" class="add-node-btn">
      <el-dropdown trigger="click" @command="handleAddCommand">
        <el-button type="primary" circle size="small">
          <el-icon><Plus /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :command="NodeType.APPROVAL">
              <el-icon><User /></el-icon>审批节点
            </el-dropdown-item>
            <el-dropdown-item :command="NodeType.CC">
              <el-icon><DocumentCopy /></el-icon>抄送节点
            </el-dropdown-item>
            <el-dropdown-item :command="NodeType.CONDITION">
              <el-icon><Share /></el-icon>条件分支
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 连线 -->
    <div v-if="node.childNode" class="node-line"></div>

    <!-- 条件分支节点 -->
    <div v-if="node.type === NodeType.CONDITION && node.conditionNodes" class="condition-branches">
      <div class="branch-container">
        <div
          v-for="(branch, index) in node.conditionNodes"
          :key="branch.nodeId"
          class="branch-item"
        >
          <div class="branch-header">
            <span class="branch-name">{{ branch.nodeName }}</span>
            <el-button
              v-if="!disabled && node.conditionNodes && node.conditionNodes.length > 2"
              type="danger"
              circle
              size="small"
              @click="handleDeleteBranch(index)"
            >
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
          <div class="branch-content">
            <FlowNode
              v-if="branch.childNode"
              :node="branch.childNode"
              :user-list="userList"
              :role-list="roleList"
              :disabled="disabled"
              @add-node="(parent, type) => emit('add-node', parent, type)"
              @delete-node="(n) => emit('delete-node', n)"
              @update-node="(n, updates) => emit('update-node', n, updates)"
            />
            <!-- 分支内没有子节点时，显示添加按钮 -->
            <div v-else-if="!disabled" class="branch-add-btn">
              <el-dropdown trigger="click" @command="(type: number) => handleAddNodeToBranch(index, type)">
                <el-button type="primary" circle size="small">
                  <el-icon><Plus /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :command="NodeType.APPROVAL">
                      <el-icon><User /></el-icon>审批节点
                    </el-dropdown-item>
                    <el-dropdown-item :command="NodeType.CC">
                      <el-icon><DocumentCopy /></el-icon>抄送节点
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
          <!-- 添加分支按钮 -->
          <div v-if="!disabled && index === node.conditionNodes!.length - 1" class="add-branch-btn">
            <el-button size="small" @click="handleAddBranch">
              <el-icon><Plus /></el-icon>添加分支
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 子节点 -->
    <FlowNode
      v-if="node.childNode && node.type !== NodeType.CONDITION"
      :node="node.childNode"
      :user-list="userList"
      :role-list="roleList"
      :disabled="disabled"
      @add-node="(parent, type) => emit('add-node', parent, type)"
      @delete-node="(n) => emit('delete-node', n)"
      @update-node="(n, updates) => emit('update-node', n, updates)"
    />

    <!-- 删除按钮（非开始/结束节点） -->
    <el-button
      v-if="canDelete && !disabled"
      type="danger"
      circle
      size="small"
      class="delete-btn"
      @click.stop="handleDelete"
    >
      <el-icon><Close /></el-icon>
    </el-button>

    <!-- 节点属性配置弹窗 -->
    <el-dialog
      v-model="configDialogVisible"
      :title="configDialogTitle"
      width="500px"
      append-to-body
      @close="handleConfigClose"
    >
      <el-form :model="configForm" label-width="100px">
        <el-form-item label="节点名称">
          <el-input v-model="configForm.nodeName" placeholder="请输入节点名称" />
        </el-form-item>

        <!-- 审批节点配置 -->
        <template v-if="node.type === NodeType.APPROVAL">
          <el-form-item label="候选人策略">
            <el-select v-model="configForm.strategy" placeholder="请选择候选人策略" style="width: 100%">
              <el-option label="指定用户" :value="10" />
              <el-option label="指定角色" :value="20" />
              <el-option label="发起人自己" :value="50" />
              <el-option label="发起人自选" :value="80" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="configForm.strategy === 10" label="审批用户">
            <el-select
              v-model="configForm.selectedUsers"
              multiple
              filterable
              placeholder="请选择审批用户"
              style="width: 100%"
            >
              <el-option
                v-for="user in userList"
                :key="user.value"
                :label="user.label"
                :value="user.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item v-if="configForm.strategy === 20" label="审批角色">
            <el-select
              v-model="configForm.selectedRoles"
              multiple
              filterable
              placeholder="请选择审批角色"
              style="width: 100%"
            >
              <el-option
                v-for="role in roleList"
                :key="role.value"
                :label="role.label"
                :value="role.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="审批类型">
            <el-select v-model="configForm.approveType" placeholder="请选择审批类型" style="width: 100%">
              <el-option label="会签（需所有人审批）" :value="1" />
              <el-option label="或签（一人审批即可）" :value="2" />
            </el-select>
          </el-form-item>
        </template>

        <!-- 抄送节点配置 -->
        <template v-if="node.type === NodeType.CC">
          <el-form-item label="抄送用户">
            <el-select
              v-model="configForm.selectedUsers"
              multiple
              filterable
              placeholder="请选择抄送用户"
              style="width: 100%"
            >
              <el-option
                v-for="user in userList"
                :key="user.value"
                :label="user.label"
                :value="user.value"
              />
            </el-select>
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfigSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import {
  Plus,
  User,
  DocumentCopy,
  Share,
  Close,
  CircleCheck,
  Flag
} from '@element-plus/icons-vue'
import type { FlowLongNodeModel, FlowLongNodeCandidate } from '@/composables/useFlowLongDesigner'
import { FlowLongNodeType as NodeType } from '@/composables/useFlowLongDesigner'

const props = defineProps<{
  node: FlowLongNodeModel
  userList?: { label: string; value: string }[]
  roleList?: { label: string; value: string }[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'add-node', parentNode: FlowLongNodeModel, type: number): void
  (e: 'delete-node', node: FlowLongNodeModel): void
  (e: 'update-node', node: FlowLongNodeModel, updates: Partial<FlowLongNodeModel>): void
}>()

// 配置弹窗
const configDialogVisible = ref(false)
const configDialogTitle = computed(() => {
  switch (props.node.type) {
    case NodeType.START: return '开始节点'
    case NodeType.APPROVAL: return '审批节点配置'
    case NodeType.CC: return '抄送节点配置'
    case NodeType.END: return '结束节点'
    default: return '节点配置'
  }
})

// 配置表单
const configForm = reactive({
  nodeName: '',
  strategy: 10,
  selectedUsers: [] as string[],
  selectedRoles: [] as string[],
  approveType: 2
})

// 节点样式类
const nodeClass = computed(() => {
  const classes: string[] = []
  switch (props.node.type) {
    case NodeType.START:
      classes.push('node-start')
      break
    case NodeType.APPROVAL:
      classes.push('node-approval')
      break
    case NodeType.CC:
      classes.push('node-cc')
      break
    case NodeType.END:
      classes.push('node-end')
      break
    case NodeType.CONDITION:
      classes.push('node-condition')
      break
  }
  return classes
})

// 节点图标
const nodeIcon = computed(() => {
  switch (props.node.type) {
    case NodeType.START:
      return CircleCheck
    case NodeType.APPROVAL:
      return User
    case NodeType.CC:
      return DocumentCopy
    case NodeType.END:
      return Flag
    case NodeType.CONDITION:
      return Share
    default:
      return null
  }
})

// 是否有候选人配置
const hasAssignee = computed(() => {
  const candidate = props.node.nodeCandidate
  return candidate && (
    (candidate.users && candidate.users.length > 0) ||
    (candidate.roles && candidate.roles.length > 0) ||
    candidate.initiator
  )
})

// 是否可以添加子节点
const canAddChild = computed(() => {
  return props.node.type === NodeType.START ||
         props.node.type === NodeType.APPROVAL ||
         props.node.type === NodeType.CC
})

// 是否可以删除
const canDelete = computed(() => {
  return props.node.type !== NodeType.START && props.node.type !== NodeType.END
})

// 是否可以配置
const canConfig = computed(() => {
  return props.node.type === NodeType.APPROVAL || props.node.type === NodeType.CC
})

// 点击节点（打开配置）
const handleClick = () => {
  if (props.disabled) return

  if (canConfig.value) {
    // 初始化表单数据
    configForm.nodeName = props.node.nodeName || ''

    const candidate = props.node.nodeCandidate || {}
    configForm.selectedUsers = candidate.users || []
    configForm.selectedRoles = candidate.roles || []

    // 根据候选人情况推断策略
    if (candidate.initiator) {
      configForm.strategy = 50
    } else if (candidate.users && candidate.users.length > 0) {
      configForm.strategy = 10
    } else if (candidate.roles && candidate.roles.length > 0) {
      configForm.strategy = 20
    } else {
      configForm.strategy = 80 // 默认发起人自选
    }

    // 审批类型：从 extendConfig 获取或默认或签
    configForm.approveType = props.node.extendConfig?.approveType || 2

    configDialogVisible.value = true
  }
}

// 保存配置
const handleConfigSave = () => {
  const updates: Partial<FlowLongNodeModel> = {
    nodeName: configForm.nodeName
  }

  // 构建候选人配置
  if (props.node.type === NodeType.APPROVAL || props.node.type === NodeType.CC) {
    const candidate: FlowLongNodeCandidate = {}

    if (configForm.strategy === 10) {
      candidate.users = configForm.selectedUsers
    } else if (configForm.strategy === 20) {
      candidate.roles = configForm.selectedRoles
    } else if (configForm.strategy === 50) {
      candidate.initiator = true
    }

    candidate.strategy = configForm.strategy
    updates.nodeCandidate = candidate

    // 审批类型存储在 extendConfig
    if (props.node.type === NodeType.APPROVAL) {
      updates.extendConfig = {
        ...props.node.extendConfig,
        approveType: configForm.approveType
      }
    }
  }

  emit('update-node', props.node, updates)
  configDialogVisible.value = false
}

// 关闭弹窗
const handleConfigClose = () => {
  // 重置表单
  configForm.nodeName = ''
  configForm.selectedUsers = []
  configForm.selectedRoles = []
}

// 添加节点
const handleAddCommand = (type: number) => {
  emit('add-node', props.node, type)
}

// 删除节点
const handleDelete = () => {
  emit('delete-node', props.node)
}

// 添加分支
const handleAddBranch = () => {
  if (!props.node.conditionNodes) return

  const newBranch = {
    nodeId: `branch_${Date.now()}`,
    nodeName: `分支${props.node.conditionNodes.length + 1}`,
    childNode: undefined
  }

  const updates: Partial<FlowLongNodeModel> = {
    conditionNodes: [...props.node.conditionNodes, newBranch]
  }

  emit('update-node', props.node, updates)
}

// 删除分支
const handleDeleteBranch = (index: number) => {
  if (!props.node.conditionNodes) return

  const newBranches = [...props.node.conditionNodes]
  newBranches.splice(index, 1)

  const updates: Partial<FlowLongNodeModel> = {
    conditionNodes: newBranches
  }

  emit('update-node', props.node, updates)
}

// 在分支内添加节点
const handleAddNodeToBranch = (branchIndex: number, type: number) => {
  if (!props.node.conditionNodes) return

  const branch = props.node.conditionNodes[branchIndex]
  const newNode: FlowLongNodeModel = {
    nodeName: type === NodeType.APPROVAL ? '审批节点' : '抄送节点',
    nodeKey: `${type === NodeType.APPROVAL ? 'task' : 'cc'}_${Date.now()}`,
    type,
    parentNode: props.node
  }

  // 如果分支已有子节点，新节点插入到最前面
  if (branch.childNode) {
    newNode.childNode = branch.childNode
    branch.childNode.parentNode = newNode
  }

  branch.childNode = newNode

  // 触发更新
  emit('update-node', props.node, {
    conditionNodes: [...props.node.conditionNodes]
  })
}
</script>

<style scoped lang="scss">
.flow-node-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.flow-node {
  min-width: 180px;
  padding: 12px 16px;
  border-radius: 8px;
  background: #fff;
  border: 2px solid #e4e7ed;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);

  &:hover {
    border-color: #409eff;
    box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
  }

  &.node-start {
    border-color: #67c23a;
    background: linear-gradient(135deg, #f0f9eb 0%, #fff 100%);

    .node-icon { color: #67c23a; }
  }

  &.node-approval {
    border-color: #409eff;
    background: linear-gradient(135deg, #ecf5ff 0%, #fff 100%);

    .node-icon { color: #409eff; }
  }

  &.node-cc {
    border-color: #e6a23c;
    background: linear-gradient(135deg, #fdf6ec 0%, #fff 100%);

    .node-icon { color: #e6a23c; }
  }

  &.node-end {
    border-color: #f56c6c;
    background: linear-gradient(135deg, #fef0f0 0%, #fff 100%);

    .node-icon { color: #f56c6c; }
  }

  &.node-condition {
    border-color: #909399;
    background: linear-gradient(135deg, #f4f4f5 0%, #fff 100%);

    .node-icon { color: #909399; }
  }

  .node-header {
    display: flex;
    align-items: center;
    gap: 8px;

    .node-icon {
      font-size: 18px;
    }

    .node-name {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
    }
  }

  .node-body {
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px dashed #e4e7ed;

    .assignee-info {
      font-size: 12px;
      color: #606266;
      display: flex;
      flex-wrap: wrap;
      gap: 8px;

      span {
        background: #f4f4f5;
        padding: 2px 8px;
        border-radius: 4px;
      }
    }
  }
}

.add-node-btn {
  margin-top: 10px;
}

.node-line {
  width: 2px;
  height: 30px;
  background: #e4e7ed;
  margin: 10px 0;
}

.delete-btn {
  position: absolute;
  top: -8px;
  right: -8px;
  opacity: 0;
  transition: opacity 0.2s;

  &:hover {
    opacity: 1;
  }
}

.flow-node-wrapper:hover .delete-btn {
  opacity: 1;
}

// 条件分支样式
.condition-branches {
  margin-top: 20px;
  width: 100%;
}

.branch-container {
  display: flex;
  justify-content: center;
  gap: 20px;
  position: relative;

  // 分支连接线
  &::before {
    content: '';
    position: absolute;
    top: -15px;
    left: 50%;
    width: 2px;
    height: 15px;
    background: #e4e7ed;
  }
}

.branch-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 150px;
  position: relative;

  // 分支顶部连接线
  &::before {
    content: '';
    position: absolute;
    top: -15px;
    left: 50%;
    width: calc(50% + 10px);
    height: 2px;
    background: #e4e7ed;
  }

  &:first-child::before {
    left: 50%;
    width: calc(50% + 10px);
  }

  &:last-child::before {
    left: calc(50% - 10px);
    width: calc(50% + 10px);
  }
}

.branch-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f4f4f5;
  border-radius: 4px;
  margin-bottom: 10px;

  .branch-name {
    font-size: 12px;
    color: #606266;
  }
}

.branch-content {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.add-branch-btn {
  margin-top: 10px;
}

.branch-add-btn {
  margin-top: 10px;
}
</style>