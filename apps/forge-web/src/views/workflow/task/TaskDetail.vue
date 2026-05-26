<template>
  <div class="app-container">
    <div v-loading="loading" class="task-detail">
      <!-- 任务信息头 -->
      <el-card shadow="never" class="task-info-card">
        <el-page-header @back="router.back()">
          <template #content>
            <span class="page-title">{{ taskInfo?.name || '任务详情' }}</span>
          </template>
        </el-page-header>
        <el-descriptions :column="isMobile ? 1 : 4" size="small" class="task-descriptions">
          <el-descriptions-item label="任务名称">{{ taskInfo?.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="流程名称">{{ taskInfo?.processDefinitionName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="受理人">{{ taskInfo?.assigneeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(taskInfo?.createTime) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 内容区 - Tabs -->
      <el-tabs v-model="activeTab" class="detail-tabs">
        <!-- 审批表单 -->
        <el-tab-pane label="审批表单" name="approval">
          <el-card shadow="never">
            <!-- 流程表单渲染 -->
            <template v-if="formRule.length > 0">
              <el-divider content-position="left">流程表单</el-divider>
              <form-create
                v-model="fApi"
                :rule="formRule"
                :option="formOption"
              />
              <el-divider />
            </template>
            <el-form label-width="80px">
              <el-form-item label="审批意见">
                <el-input
                  v-model="comment"
                  type="textarea"
                  :rows="4"
                  placeholder="请输入审批意见"
                  maxlength="500"
                  show-word-limit
                />
              </el-form-item>
            </el-form>
            <div class="action-buttons">
              <el-button type="success" :loading="actionLoading" @click="handleApprove">
                <el-icon><Check /></el-icon>
                通过
              </el-button>
              <el-button type="danger" :loading="actionLoading" @click="handleReject">
                <el-icon><Close /></el-icon>
                驳回
              </el-button>
              <el-button type="warning" :loading="actionLoading" @click="delegateDialogVisible = true">
                <el-icon><User /></el-icon>
                委派
              </el-button>
              <el-button type="warning" :loading="actionLoading" @click="transferDialogVisible = true">
                <el-icon><Right /></el-icon>
                转办
              </el-button>
              <el-button type="info" :loading="actionLoading" @click="handleReturn">
                <el-icon><Back /></el-icon>
                退回
              </el-button>
            </div>
          </el-card>
        </el-tab-pane>

        <!-- 流程图 -->
        <el-tab-pane label="流程图" name="diagram">
          <el-card shadow="never">
            <BpmnPreview v-if="bpmnXml" :xml="bpmnXml" />
            <el-empty v-else description="暂无流程图" />
          </el-card>
        </el-tab-pane>

        <!-- 审批记录 -->
        <el-tab-pane label="审批记录" name="history">
          <el-card shadow="never">
            <el-timeline>
              <el-timeline-item
                v-for="comment in comments"
                :key="comment.id"
                :timestamp="formatDateTime(comment.createTime)"
                placement="top"
              >
                <div class="comment-item">
                  <div class="comment-header">
                    <span class="user-name">{{ comment.userName }}</span>
                    <el-tag size="small" :type="actionTagType(comment.actionType)">
                      {{ actionLabel(comment.actionType) }}
                    </el-tag>
                  </div>
                  <div v-if="comment.taskName" class="task-name">{{ comment.taskName }}</div>
                  <div v-if="comment.commentText" class="comment-text">{{ comment.commentText }}</div>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-if="!comments.length" description="暂无审批记录" :image-size="60" />
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 委派对话框 -->
    <el-dialog v-model="delegateDialogVisible" title="委派任务" width="500px" @close="handleDelegateDialogClose">
      <el-form label-width="80px">
        <el-form-item label="委派给">
          <el-select
            v-model="delegateUserId"
            filterable
            remote
            reserve-keyword
            placeholder="请输入用户名搜索"
            :remote-method="searchUsers"
            :loading="userSearchLoading"
            style="width: 100%"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="`${user.nickname}（${user.username}）`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="委派意见">
          <el-input
            v-model="delegateComment"
            type="textarea"
            :rows="3"
            placeholder="请输入委派意见"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="delegateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="handleDelegateConfirm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 转办对话框 -->
    <el-dialog v-model="transferDialogVisible" title="转办任务" width="500px" @close="handleTransferDialogClose">
      <el-form label-width="80px">
        <el-form-item label="转办给">
          <el-select
            v-model="transferUserId"
            filterable
            remote
            reserve-keyword
            placeholder="请输入用户名搜索"
            :remote-method="searchUsers"
            :loading="userSearchLoading"
            style="width: 100%"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="`${user.nickname}（${user.username}）`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="转办意见">
          <el-input
            v-model="transferComment"
            type="textarea"
            :rows="3"
            placeholder="请输入转办意见"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="handleTransferConfirm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 退回对话框 -->
    <el-dialog v-model="returnDialogVisible" title="退回任务" width="500px" @close="handleReturnDialogClose">
      <el-form label-width="80px">
        <el-form-item label="退回节点">
          <el-select
            v-model="returnTargetKey"
            placeholder="请选择退回目标节点"
            style="width: 100%"
          >
            <el-option
              v-for="node in returnNodes"
              :key="node.key"
              :label="node.name"
              :value="node.key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="退回意见">
          <el-input
            v-model="returnComment"
            type="textarea"
            :rows="3"
            placeholder="请输入退回意见"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="handleReturnConfirm" :disabled="!returnTargetKey">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { taskApi } from '@/api/workflow/task'
import { processInstanceApi } from '@/api/workflow/process-instance'
import { processDefinitionApi } from '@/api/workflow/process-definition'
import { formApi } from '@/api/workflow/form'
import { getUserList } from '@/api/system'
import type { TaskInfo, ApprovalComment } from '@/types/workflow'
import type { User } from '@/types/system'
import { formatDateTime } from '@/utils/dateFormat'
import { decodeFields } from '@/utils/formCreate'
import { useResponsive } from '@/composables/useResponsive'
import BpmnPreview from '@/views/workflow/process/components/BpmnPreview.vue'

interface ReturnNode {
  key: string
  name: string
}

const route = useRoute()
const router = useRouter()
const { isMobile } = useResponsive()

const taskId = route.query.id as string
const activeTab = ref('approval')

// 数据状态
const loading = ref(false)
const actionLoading = ref(false)
const taskInfo = ref<TaskInfo | null>(null)
const comments = ref<ApprovalComment[]>([])
const bpmnXml = ref('')
const comment = ref('')

// 表单相关
const fApi = ref<any>(null)
const formRule = ref<any[]>([])
const formOption = ref({ submitBtn: false, resetBtn: false } as any)

// 委派相关
const delegateDialogVisible = ref(false)
const delegateUserId = ref<number | undefined>()
const delegateComment = ref('')

// 转办相关
const transferDialogVisible = ref(false)
const transferUserId = ref<number | undefined>()
const transferComment = ref('')

// 退回相关
const returnDialogVisible = ref(false)
const returnTargetKey = ref('')
const returnComment = ref('')
const returnNodes = ref<ReturnNode[]>([])

// 用户搜索相关
const userSearchLoading = ref(false)
const userOptions = ref<User[]>([])

// 审批动作标签映射
const actionLabel = (actionType: string): string => {
  const map: Record<string, string> = {
    approve: '通过',
    reject: '驳回',
    delegate: '委派',
    transfer: '转办',
    return: '退回',
    claim: '认领'
  }
  return map[actionType] || actionType
}

// 审批动作标签类型映射
const actionTagType = (actionType: string): 'success' | 'danger' | 'warning' | 'info' | 'primary' => {
  const map: Record<string, 'success' | 'danger' | 'warning' | 'info' | 'primary'> = {
    approve: 'success',
    reject: 'danger',
    delegate: 'warning',
    transfer: 'warning',
    return: 'info',
    claim: 'primary'
  }
  return map[actionType] || 'info'
}

// 搜索用户（委派/转办时使用）
const searchUsers = async (query: string) => {
  if (!query) return
  userSearchLoading.value = true
  try {
    const res = await getUserList({ pageNum: 1, pageSize: 20, username: query })
    userOptions.value = res.list
  } finally {
    userSearchLoading.value = false
  }
}

// 获取任务详情
const getTaskDetail = async () => {
  if (!taskId) return
  loading.value = true
  try {
    const task = await taskApi.getById(taskId)
    taskInfo.value = task

    // 获取流程实例的 BPMN XML 和审批记录
    if (task.processInstanceId) {
      const [instanceData, commentsData] = await Promise.allSettled([
        processInstanceApi.getById(task.processInstanceId),
        processInstanceApi.getComments(task.processInstanceId)
      ])

      if (instanceData.status === 'fulfilled' && instanceData.value?.processDefinitionId) {
        const processDefId = instanceData.value.processDefinitionId
        // 通过流程定义ID获取 BPMN XML 和表单
        try {
          bpmnXml.value = await processDefinitionApi.getXml(processDefId) || ''
          // 加载流程定义详情获取表单ID
          const processDef = await processDefinitionApi.getById(processDefId)
          if (processDef.formId) {
            const formData = await formApi.getById(processDef.formId)
            if (formData.conf && formData.fields) {
              formRule.value = decodeFields(formData.fields)
              try {
                formOption.value = JSON.parse(formData.conf)
              } catch { /* ignore */ }
              formOption.value.submitBtn = false
              formOption.value.resetBtn = false
            }
          }
        } catch {
          // BPMN XML 或表单获取失败不影响页面展示
        }
      }

      if (commentsData.status === 'fulfilled') {
        comments.value = commentsData.value || []
      }
    }
  } catch (error) {
    console.error('获取任务详情失败:', error)
    ElMessage.error('获取任务详情失败')
  } finally {
    loading.value = false
  }
}

// 通过
const handleApprove = async () => {
  if (!comment.value.trim()) {
    ElMessage.warning('请输入审批意见')
    return
  }
  actionLoading.value = true
  try {
    const variables = fApi.value ? fApi.value.formData() : undefined
    await taskApi.approve(taskId, {
      comment: comment.value,
      variables: variables && Object.keys(variables).length > 0 ? variables : undefined
    })
    ElMessage.success('审批通过')
    router.back()
  } catch (error) {
    ElMessage.error('审批失败')
  } finally {
    actionLoading.value = false
  }
}

// 驳回
const handleReject = async () => {
  if (!comment.value.trim()) {
    ElMessage.warning('请输入驳回意见')
    return
  }
  try {
    await ElMessageBox.confirm('确定要驳回该任务吗？', '提示', { type: 'warning' })
    actionLoading.value = true
    try {
      const variables = fApi.value ? fApi.value.formData() : undefined
      await taskApi.reject(taskId, {
        comment: comment.value,
        variables: variables && Object.keys(variables).length > 0 ? variables : undefined
      })
      ElMessage.success('驳回成功')
      router.back()
    } finally {
      actionLoading.value = false
    }
  } catch {
    // 用户取消
  }
}

// 委派确认
const handleDelegateConfirm = async () => {
  if (!delegateUserId.value) {
    ElMessage.warning('请选择委派用户')
    return
  }
  if (!delegateComment.value.trim()) {
    ElMessage.warning('请输入委派意见')
    return
  }
  actionLoading.value = true
  try {
    await taskApi.delegate(taskId, {
      delegateUserId: delegateUserId.value,
      comment: delegateComment.value
    })
    ElMessage.success('委派成功')
    delegateDialogVisible.value = false
    router.back()
  } catch (error) {
    ElMessage.error('委派失败')
  } finally {
    actionLoading.value = false
  }
}

// 转办确认
const handleTransferConfirm = async () => {
  if (!transferUserId.value) {
    ElMessage.warning('请选择转办用户')
    return
  }
  if (!transferComment.value.trim()) {
    ElMessage.warning('请输入转办意见')
    return
  }
  actionLoading.value = true
  try {
    await taskApi.transfer(taskId, {
      transferUserId: transferUserId.value,
      comment: transferComment.value
    })
    ElMessage.success('转办成功')
    transferDialogVisible.value = false
    router.back()
  } catch (error) {
    ElMessage.error('转办失败')
  } finally {
    actionLoading.value = false
  }
}

// 退回 - 先获取可退回节点
const handleReturn = async () => {
  try {
    const nodes = await taskApi.getReturnNodes(taskId)
    returnNodes.value = nodes || []
    if (returnNodes.value.length === 0) {
      ElMessage.warning('当前任务没有可退回的节点')
      return
    }
    returnDialogVisible.value = true
  } catch (error) {
    ElMessage.error('获取退回节点失败')
  }
}

// 退回确认
const handleReturnConfirm = async () => {
  if (!returnTargetKey.value) {
    ElMessage.warning('请选择退回目标节点')
    return
  }
  if (!returnComment.value.trim()) {
    ElMessage.warning('请输入退回意见')
    return
  }
  actionLoading.value = true
  try {
    await taskApi.return(taskId, {
      targetTaskDefKey: returnTargetKey.value,
      comment: returnComment.value
    })
    ElMessage.success('退回成功')
    returnDialogVisible.value = false
    router.back()
  } catch (error) {
    ElMessage.error('退回失败')
  } finally {
    actionLoading.value = false
  }
}

// 对话框关闭时重置状态
const handleDelegateDialogClose = () => {
  delegateUserId.value = undefined
  delegateComment.value = ''
  userOptions.value = []
}

const handleTransferDialogClose = () => {
  transferUserId.value = undefined
  transferComment.value = ''
  userOptions.value = []
}

const handleReturnDialogClose = () => {
  returnTargetKey.value = ''
  returnComment.value = ''
}

onMounted(() => {
  getTaskDetail()
})
</script>

<style scoped lang="scss">
.task-detail {
  padding: 0;
}

.task-info-card {
  margin-bottom: 16px;

  .task-descriptions {
    margin-top: 16px;
  }

  .page-title {
    font-size: 16px;
    font-weight: 600;
  }
}

.detail-tabs {
  :deep(.el-tabs__content) {
    padding: 0;
  }
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.comment-item {
  .comment-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;

    .user-name {
      font-weight: 500;
      font-size: 14px;
    }
  }

  .task-name {
    font-size: 13px;
    color: #909399;
    margin-bottom: 4px;
  }

  .comment-text {
    font-size: 13px;
    color: #606266;
    line-height: 1.5;
  }
}

@media (max-width: 768px) {
  .action-buttons {
    .el-button {
      flex: 1;
      min-width: 0;
    }
  }
}
</style>
