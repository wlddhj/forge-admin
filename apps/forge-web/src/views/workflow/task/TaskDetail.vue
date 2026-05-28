<template>
  <div class="app-container">
    <div v-loading="loading" class="task-detail">
      <!-- 页面头部 -->
      <div class="detail-header">
        <el-page-header @back="router.back()">
          <template #content>
            <span class="page-title">{{ taskInfo?.processDefinitionName || '任务详情' }}</span>
          </template>
        </el-page-header>
      </div>

      <!-- 已办任务：采用实例详情的布局 -->
      <div v-if="isDoneTask" class="detail-content">
        <div class="info-section">
          <!-- 实例信息 -->
          <el-card shadow="never" class="info-card">
            <template #header>
              <div class="card-header">
                <span>实例信息</span>
                <el-button type="primary" size="small" @click="diagramDialogVisible = true">
                  查看流程图
                </el-button>
              </div>
            </template>
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="流程名称">
                {{ instanceInfo?.processDefinitionName || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="发起人">
                {{ instanceInfo?.startUserName || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="发起时间">
                {{ formatDateTime(instanceInfo?.startTime) }}
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag v-if="!instanceInfo?.endTime" type="primary">运行中</el-tag>
                <el-tag v-else-if="instanceInfo?.deleteReason" type="danger">已终止</el-tag>
                <el-tag v-else type="success">已结束</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="业务标识">
                {{ instanceInfo?.businessKey || '-' }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 申请表单 -->
          <el-card v-if="formReady && formRule.length > 0" shadow="never" class="form-card">
            <template #header>申请表单</template>
            <form-create
              ref="formCreateRef"
              v-model="formData"
              :rule="formRule"
              :option="formOption"
            />
          </el-card>

          <!-- 审批记录 -->
          <el-card shadow="never" class="timeline-card">
            <template #header>审批记录</template>
            <el-table :data="comments" size="small" v-if="comments.length">
              <el-table-column type="index" label="序号" width="60" align="center" />
              <el-table-column prop="createTime" label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
              </el-table-column>
              <el-table-column prop="userName" label="审批人" width="100" />
              <el-table-column prop="actionType" label="动作" width="80" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="actionTagType(row.actionType)">
                    {{ actionLabel(row.actionType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="taskName" label="任务名称" min-width="120" />
              <el-table-column prop="commentText" label="审批意见" min-width="200" show-overflow-tooltip />
            </el-table>
            <el-empty v-else description="暂无审批记录" :image-size="60" />
          </el-card>
        </div>
      </div>

      <!-- 待办任务：垂直布局 -->
      <template v-else>
        <div class="detail-content">
          <div class="info-section">
            <!-- 任务信息 -->
            <el-card shadow="never" class="info-card">
              <template #header>
                <div class="card-header">
                  <span>任务信息</span>
                  <el-button type="primary" size="small" @click="diagramDialogVisible = true">
                    查看流程图
                  </el-button>
                </div>
              </template>
              <el-descriptions :column="isMobile ? 1 : 2" size="small" border>
                <el-descriptions-item label="任务名称">{{ taskInfo?.name || '-' }}</el-descriptions-item>
                <el-descriptions-item label="流程名称">{{ taskInfo?.processDefinitionName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="受理人">{{ taskInfo?.assigneeName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ formatDateTime(taskInfo?.createTime) }}</el-descriptions-item>
              </el-descriptions>
            </el-card>

            <!-- 审批表单 -->
            <el-card shadow="never" class="form-card">
              <template #header>审批表单</template>
              <!-- 流程表单渲染 -->
              <template v-if="formReady && formRule.length > 0">
                <form-create
                  ref="formCreateRef"
                  v-model="formData"
                  :rule="formRule"
                  :option="formOption"
                />
              </template>
              <el-divider v-if="formReady && formRule.length > 0" />
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
                <template v-if="approveSelectTasks.length > 0">
                  <el-divider content-position="left">下一节点审批人选择</el-divider>
                  <el-form-item
                    v-for="task in approveSelectTasks"
                    :key="task.taskDefKey"
                    :label="task.taskName"
                    :required="true"
                  >
                    <el-select
                      v-model="nextSelectedUsers[task.taskDefKey]"
                      multiple
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
                </template>
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

            <!-- 审批记录 -->
            <el-card shadow="never" class="timeline-card">
              <template #header>审批记录</template>
              <el-table :data="comments" size="small" v-if="comments.length">
                <el-table-column type="index" label="序号" width="60" align="center" />
                <el-table-column prop="createTime" label="时间" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
                </el-table-column>
                <el-table-column prop="userName" label="审批人" width="100" />
                <el-table-column prop="actionType" label="动作" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="actionTagType(row.actionType)">
                      {{ actionLabel(row.actionType) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="taskName" label="任务名称" min-width="120" />
                <el-table-column prop="commentText" label="审批意见" min-width="200" show-overflow-tooltip />
              </el-table>
              <el-empty v-else description="暂无审批记录" :image-size="60" />
            </el-card>
          </div>
        </div>
      </template>
    </div>

    <!-- 流程图弹窗（已办任务使用） -->
    <el-dialog v-model="diagramDialogVisible" title="流程图" width="800px" top="5vh" @opened="handleDiagramOpened">
      <div ref="diagramContainerRef" style="height: 60vh">
        <BpmnPreview v-if="bpmnXml && diagramDialogVisible" :xml="bpmnXml" :key="diagramKey" />
        <el-empty v-else description="暂无流程图" />
      </div>
    </el-dialog>

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
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { taskApi } from '@/api/workflow/task'
import { processInstanceApi } from '@/api/workflow/process-instance'
import { processDefinitionApi } from '@/api/workflow/process-definition'
import { formApi } from '@/api/workflow/form'
import { getUserList } from '@/api/system'
import type { TaskInfo, ApprovalComment, ProcessInstance, UserTaskNode } from '@/types/workflow'
import type { User } from '@/types/system'
import { formatDateTime } from '@/utils/dateFormat'
import { decodeFieldsDisabled } from '@/utils/formCreate'
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
const instanceInfo = ref<ProcessInstance | null>(null)
const comments = ref<ApprovalComment[]>([])
const bpmnXml = ref('')
const comment = ref('')

// 判断是否为已办任务
const isDoneTask = computed(() => !!taskInfo.value?.endTime)

// 流程图弹窗
const diagramDialogVisible = ref(false)
const diagramContainerRef = ref<HTMLElement | null>(null)
const diagramKey = ref(0)

const handleDiagramOpened = () => {
  diagramKey.value++
}

// 表单相关
const formCreateRef = ref<any>(null)
const formRule = ref<any[]>([])
const formOption = ref({ submitBtn: false, resetBtn: false } as any)
const formData = ref<Record<string, any>>({})
const formReady = ref(false)

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

// 审批人自选相关
const approveSelectTasks = ref<UserTaskNode[]>([])
const nextSelectedUsers = ref<Record<string, number[]>>({})

// 审批动作标签映射
const actionLabel = (actionType: string): string => {
  const map: Record<string, string> = {
    approve: '通过',
    reject: '驳回',
    delegate: '委派',
    transfer: '转办',
    return: '退回',
    claim: '认领',
    submit: '提交',
    cancel: '取消'
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
    claim: 'primary',
    submit: 'primary',
    cancel: 'danger'
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

      if (instanceData.status === 'fulfilled') {
        instanceInfo.value = instanceData.value
      }

      if (instanceData.status === 'fulfilled' && instanceData.value?.processDefinitionId) {
        const processDefId = instanceData.value.processDefinitionId
        const procInstanceId = task.processInstanceId

        // 通过流程定义ID获取 BPMN XML 和表单
        try {
          bpmnXml.value = await processDefinitionApi.getXml(processDefId) || ''
          // 加载需要审批人自选的下游任务节点
          try {
            const nodes = await processDefinitionApi.getUserTaskNodes(processDefId)
            approveSelectTasks.value = (nodes || []).filter(n => n.candidateStrategy === 34)
          } catch { /* ignore */ }
          // 加载流程定义详情获取表单ID
          const processDef = await processDefinitionApi.getById(processDefId)
          if (processDef.formId) {
            const formDefData = await formApi.getById(processDef.formId)
            if (formDefData.conf && formDefData.fields) {
              formRule.value = decodeFieldsDisabled(formDefData.fields)
              try {
                formOption.value = JSON.parse(formDefData.conf)
              } catch { /* ignore */ }
              formOption.value.submitBtn = false
              formOption.value.resetBtn = false
              formOption.value.disabled = true

              // 获取流程变量并设置到表单
              try {
                const variables = await processInstanceApi.getVariables(procInstanceId)
                formData.value = variables || {}
              } catch { /* ignore */ }

              formReady.value = true

              // 表单渲染后设置为只读状态
              nextTick(() => {
                if (formCreateRef.value) {
                  const fApi = formCreateRef.value.fApi || formCreateRef.value
                  if (fApi && fApi.disabled) {
                    fApi.disabled(true)
                  }
                }
              })
            }
          } else {
            formReady.value = true
          }
        } catch {
          formReady.value = true
        }
      } else {
        formReady.value = true
      }

      if (commentsData.status === 'fulfilled') {
        comments.value = commentsData.value || []
      }
    } else {
      formReady.value = true
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
  // 校验审批人自选节点
  for (const task of approveSelectTasks.value) {
    const users = nextSelectedUsers.value[task.taskDefKey]
    if (!users || users.length === 0) {
      ElMessage.warning(`请为"${task.taskName}"选择审批人`)
      return
    }
  }
  actionLoading.value = true
  try {
    const variables: Record<string, any> = {}
    if (formData.value && Object.keys(formData.value).length > 0) {
      Object.assign(variables, formData.value)
    }
    // 审批人自选：设置 NEXT_{taskDefKey}_candidateUsers 变量
    for (const task of approveSelectTasks.value) {
      const users = nextSelectedUsers.value[task.taskDefKey] || []
      variables[`NEXT_${task.taskDefKey}_candidateUsers`] = users.join(',')
    }
    await taskApi.approve(taskId, {
      comment: comment.value,
      variables: Object.keys(variables).length > 0 ? variables : undefined
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
      const variables = formData.value && Object.keys(formData.value).length > 0 ? formData.value : undefined
      await taskApi.reject(taskId, {
        comment: comment.value,
        variables: variables
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
.detail-header {
  padding: 16px 20px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;

  .page-title {
    font-size: 16px;
    font-weight: 600;
  }
}

.detail-content {
  padding: 16px 20px;

  .info-section {
    display: flex;
    flex-direction: column;
    gap: 16px;
    max-width: 800px;

    .info-card {
      .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
      }
    }

    .form-card,
    .timeline-card {
      :deep(.el-card__body) {
        max-height: 400px;
        overflow-y: auto;
      }
    }

    .timeline-card {
      flex: 1;

      :deep(.el-card__body) {
        max-height: none;
        overflow-y: auto;
      }
    }
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

@media (max-width: 768px) {
  .detail-content {
    .info-section {
      max-width: 100%;
    }
  }

  .action-buttons {
    .el-button {
      flex: 1;
      min-width: 0;
    }
  }
}
</style>