<template>
  <el-drawer
    v-model="visible"
    :title="isDoneTask ? '任务详情' : '处理任务'"
    size="800px"
    direction="rtl"
    :before-close="handleClose"
  >
    <div v-loading="loading" class="drawer-content">
      <!-- 统一的实例信息卡片 -->
      <el-card shadow="never" class="info-card">
        <template #header>
          <div class="card-header">
            <span>实例信息</span>
            <div>
              <el-button
                v-if="isDoneTask"
                type="warning"
                size="small"
                :loading="actionLoading"
                @click="handleWithdraw"
              >
撤回
</el-button>
              <el-button type="primary" size="small" @click="diagramDialogVisible = true">
                查看流程图
              </el-button>
            </div>
          </div>
        </template>
        <!-- 加载中不渲染，避免兜底文案（运行中/常规）随加载闪变；加载失败仍渲染占位 -->
        <el-descriptions v-if="instanceInfo || !loading" :column="isMobile ? 1 : 2" size="small" border>
          <el-descriptions-item label="流程编号">
            {{ instanceInfo?.processNo || taskInfo?.processNo || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="流程名称">
            {{ instanceInfo?.processDefinitionName || taskInfo?.processDefinitionName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="!isDoneTask" label="任务名称">
            {{ taskInfo?.name || '-' }}
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
          <el-descriptions-item v-if="!isDoneTask" label="受理人">
            {{ taskInfo?.assigneeName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="业务标识">
            {{ instanceInfo?.businessKey || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="紧急程度">
            <el-tag
              :type="urgentTagType"
              size="small"
            >
{{ instanceInfo?.urgentName || taskInfo?.urgentName || '常规' }}
</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 已办任务：申请表单 -->
      <el-card v-if="isDoneTask && formReady && formRule.length > 0" shadow="never" class="form-card">
        <template #header>申请表单</template>
        <form-create
          ref="formCreateRef"
          v-model="formData"
          :rule="formRule"
          :option="formOption"
        />
      </el-card>

      <!-- 待办任务：审批表单 + 操作 -->
      <el-card v-if="!isDoneTask" shadow="never" class="form-card">
        <template #header>审批表单</template>
        <template v-if="formReady && formRule.length > 0">
          <form-create
            ref="formCreateRef"
            v-model="formData"
            :rule="formRule"
            :option="formOption"
          />
          <el-divider />
        </template>
        <template v-if="approveSelectTasks.length > 0">
          <el-form label-width="80px">
            <el-form-item
              v-for="task in approveSelectTasks"
              :key="task.taskDefKey"
              :label="task.taskName"
              :required="true"
            >
              <el-select
                v-model="selectedUsers[task.taskDefKey]"
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
          </el-form>
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
          <el-form-item label="附件">
            <FileUpload v-model="commentAttachmentIds" biz-type="wf_comment" :limit="5" />
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
          <el-button :loading="actionLoading" @click="copyDialogVisible = true">
            <el-icon><DocumentCopy /></el-icon>
            抄送
          </el-button>
          <el-button
            v-permission="'workflow:task:complete'"
            :loading="actionLoading"
            @click="signCreateDialogVisible = true"
          >
            加签
          </el-button>
          <el-button
            v-permission="'workflow:task:complete'"
            :loading="actionLoading"
            @click="signDeleteDialogVisible = true"
          >
            减签
          </el-button>
        </div>
      </el-card>

      <!-- 审批记录 -->
      <el-card shadow="never" class="timeline-card">
        <template #header>审批记录</template>
        <el-table v-if="comments.length" :data="comments" size="small">
          <el-table-column type="index" label="序号" width="50" align="center" />

          <el-table-column prop="userName" label="审批人" width="80" />
          <el-table-column prop="actionType" label="动作" width="70" align="center">
            <template #default="{ row }">
              <dict-value :dict-type="DICT_TYPE.WF_ACTION_TYPE" :value="row.actionType" />
            </template>
          </el-table-column>
          <el-table-column prop="taskName" label="任务" width="110" />
          <el-table-column prop="commentText" label="意见" min-width="150">
            <template #default="{ row }">
              <div>{{ row.commentText || '-' }}</div>
              <div v-if="row.attachments?.length" class="comment-attachments">
                <el-link
                  v-for="att in row.attachments"
                  :key="att.id"
                  :href="att.fileUrl"
                  target="_blank"
                  type="primary"
                  :underline="false"
                  class="attachment-link"
                >
                  <el-icon><Paperclip /></el-icon>{{ att.originalName }}
                </el-link>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="130">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无审批记录" :image-size="60" />
      </el-card>
    </div>

    <!-- 流程图弹窗 -->
    <FlowDiagramDialog
      v-model="diagramDialogVisible"
      :process-instance-id="taskInfo?.processInstanceId"
    />

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
              :key="node.taskDefKey"
              :label="node.taskName"
              :value="node.taskDefKey"
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
        <el-button type="primary" :loading="actionLoading" :disabled="!returnTargetKey" @click="handleReturnConfirm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 抄送对话框 -->
    <el-dialog v-model="copyDialogVisible" title="抄送任务" width="500px" @close="handleCopyDialogClose">
      <el-form label-width="80px">
        <el-form-item label="抄送给" required>
          <el-select
            v-model="copyUserIds"
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
        <el-form-item label="抄送原因">
          <el-input
            v-model="copyReason"
            type="textarea"
            :rows="3"
            placeholder="请输入抄送原因"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="copyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="handleCopyConfirm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 加签对话框 -->
    <SignCreateDialog
      v-model="signCreateDialogVisible"
      :task-id="taskId"
      @success="getTaskDetail"
    />

    <!-- 减签对话框 -->
    <SignDeleteDialog
      v-model="signDeleteDialogVisible"
      :task-id="taskId"
      @success="getTaskDetail"
    />
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Paperclip } from '@element-plus/icons-vue'
import { taskApi } from '@/api/workflow/task'
import { processInstanceApi } from '@/api/workflow/process-instance'
import { processDefinitionApi } from '@/api/workflow/process-definition'
import { formApi } from '@/api/workflow/form'
import { getUserList } from '@/api/system'
import type { TaskInfo, ApprovalComment, ProcessInstance, ReturnNode, UserTaskNode, ApprovalNode } from '@/types/workflow'
import type { User } from '@/types/system'
import { formatDateTime } from '@/utils/dateFormat'
import { decodeFieldsDisabled } from '@/utils/formCreate'
import FlowDiagramDialog from '@/views/workflow/process/components/FlowDiagramDialog.vue'
import SignCreateDialog from './SignCreateDialog.vue'
import SignDeleteDialog from './SignDeleteDialog.vue'
import { useResponsive } from '@/composables/useResponsive'
import { DICT_TYPE } from '@/constants/dict'

const { isMobile } = useResponsive()

const visible = defineModel<boolean>('visible', { default: false })

const emit = defineEmits<{
  (e: 'success'): void
}>()

// 任务ID
const taskId = ref<string>('')

// 数据状态
const loading = ref(false)
const actionLoading = ref(false)
const taskInfo = ref<TaskInfo | null>(null)
const instanceInfo = ref<ProcessInstance | null>(null)
const comments = ref<ApprovalComment[]>([])
const bpmnXml = ref('')
const comment = ref('')
const commentAttachmentIds = ref<number[]>([])
const approvalNodes = ref<ApprovalNode[]>([])

// 审批人自选（策略34）
const approveSelectTasks = ref<UserTaskNode[]>([])
const selectedUsers = reactive<Record<string, number[]>>({})

// 判断是否为已办任务
const isDoneTask = computed(() => !!taskInfo.value?.endTime)

// 紧急程度标签着色（与流程实例列表口径一致：3 danger / 2 warning / 1 primary / 常规 info）
const urgentTagType = computed<'primary' | 'success' | 'warning' | 'info' | 'danger'>(() => {
  const urgent = (instanceInfo.value?.urgent ?? taskInfo.value?.urgent) ?? 0
  const map: Record<number, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { 1: 'primary', 2: 'warning', 3: 'danger' }
  return map[urgent] || 'info'
})


// 流程图弹窗
const diagramDialogVisible = ref(false)

// 表单相关
const formCreateRef = ref<any>(null)
const formRule = ref<any[]>([])
const formOption = ref<Record<string, unknown>>({ submitBtn: false, resetBtn: false })
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

// 抄送相关
const copyDialogVisible = ref(false)
const copyUserIds = ref<number[]>([])
const copyReason = ref('')

// 加签/减签
const signCreateDialogVisible = ref(false)
const signDeleteDialogVisible = ref(false)

// 打开抽屉
const open = async (id: string) => {
  taskId.value = id
  visible.value = true
  await getTaskDetail()
}

// 关闭抽屉
const handleClose = () => {
  resetState()
  visible.value = false
}

// 重置状态
const resetState = () => {
  taskInfo.value = null
  instanceInfo.value = null
  comments.value = []
  bpmnXml.value = ''
  comment.value = ''
  commentAttachmentIds.value = []
  formRule.value = []
  formData.value = {}
  formReady.value = false
  approveSelectTasks.value = []
  Object.keys(selectedUsers).forEach(k => delete selectedUsers[k])
}

// 搜索用户
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
  if (!taskId.value) return
  loading.value = true
  try {
    const task = await taskApi.getById(taskId.value)
    taskInfo.value = task

    if (task.processInstanceId) {
      const [instanceData, commentsData, detailData] = await Promise.allSettled([
        processInstanceApi.getById(task.processInstanceId),
        processInstanceApi.getComments(task.processInstanceId),
        processInstanceApi.getApprovalDetail(task.processInstanceId)
      ])

      if (instanceData.status === 'fulfilled') {
        instanceInfo.value = instanceData.value
      }

      if (instanceData.status === 'fulfilled' && instanceData.value?.processDefinitionId) {
        const processDefId = instanceData.value.processDefinitionId
        const procInstanceId = task.processInstanceId

        const [xmlResult, processDefResult] = await Promise.allSettled([
          processDefinitionApi.getXml(processDefId),
          processDefinitionApi.getById(processDefId)
        ])

        if (xmlResult.status === 'fulfilled') {
          bpmnXml.value = xmlResult.value || ''
        }

        if (processDefResult.status === 'fulfilled' && processDefResult.value?.formId) {
          try {
            const formDefData = await formApi.getById(processDefResult.value.formId)
            if (formDefData.conf && formDefData.fields) {
              formRule.value = decodeFieldsDisabled(formDefData.fields)
              try {
                formOption.value = JSON.parse(formDefData.conf)
              } catch { /* ignore */ }
              formOption.value.submitBtn = false
              formOption.value.resetBtn = false
              formOption.value.disabled = true

              try {
                const variables = await processInstanceApi.getVariables(procInstanceId)
                formData.value = variables || {}
              } catch { /* ignore */ }

              formReady.value = true

              nextTick(() => {
                if (formCreateRef.value) {
                  const fApi = formCreateRef.value.fApi || formCreateRef.value
                  if (fApi && fApi.disabled) {
                    fApi.disabled(true)
                  }
                }
              })
            }
          } catch {
            formReady.value = true
          }
        } else {
          formReady.value = true
        }

        // 待办任务：加载需要自选审批人的任务节点（策略34）
        if (!task.endTime && processDefResult.status === 'fulfilled') {
          try {
            const nodes = await processDefinitionApi.getUserTaskNodes(processDefId)
            approveSelectTasks.value = (nodes || []).filter(n => n.candidateStrategy === 34)
          } catch { /* ignore */ }
        }
      } else {
        formReady.value = true
      }

      if (commentsData.status === 'fulfilled') {
        comments.value = commentsData.value || []
      }

      if (detailData.status === 'fulfilled' && detailData.value) {
        approvalNodes.value = detailData.value.nodes || []
        if (detailData.value.bpmnXml && !bpmnXml.value) {
          bpmnXml.value = detailData.value.bpmnXml
        }
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
    const users = selectedUsers[task.taskDefKey]
    if (!users || users.length === 0) {
      ElMessage.warning(`请为"${task.taskName}"选择审批人`)
      return
    }
  }
  actionLoading.value = true
  try {
    let variables: Record<string, any> = {}
    if (formData.value && Object.keys(formData.value).length > 0) {
      Object.assign(variables, formData.value)
    }
    // 审批人自选：设置 NEXT_{taskDefKey}_candidateUsers 变量
    for (const task of approveSelectTasks.value) {
      const users = selectedUsers[task.taskDefKey] || []
      variables[`NEXT_${task.taskDefKey}_candidateUsers`] = users.join(',')
    }
    await taskApi.approve(taskId.value, {
      comment: comment.value,
      attachmentIds: commentAttachmentIds.value.length > 0 ? commentAttachmentIds.value : undefined,
      variables: Object.keys(variables).length > 0 ? variables : undefined,
    })
    ElMessage.success('审批通过')
    emit('success')
    handleClose()
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
      await taskApi.reject(taskId.value, {
        comment: comment.value,
        attachmentIds: commentAttachmentIds.value.length > 0 ? commentAttachmentIds.value : undefined,
        variables
      })
      ElMessage.success('驳回成功')
      emit('success')
      handleClose()
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
    await taskApi.delegate(taskId.value, { delegateUserId: delegateUserId.value, comment: delegateComment.value })
    ElMessage.success('委派成功')
    delegateDialogVisible.value = false
    emit('success')
    handleClose()
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
    await taskApi.transfer(taskId.value, { transferUserId: transferUserId.value, comment: transferComment.value })
    ElMessage.success('转办成功')
    transferDialogVisible.value = false
    emit('success')
    handleClose()
  } catch (error) {
    ElMessage.error('转办失败')
  } finally {
    actionLoading.value = false
  }
}

// 退回
const handleReturn = async () => {
  try {
    const nodes = await taskApi.getReturnNodes(taskId.value)
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
    await taskApi.return(taskId.value, { targetTaskDefKey: returnTargetKey.value, comment: returnComment.value })
    ElMessage.success('退回成功')
    returnDialogVisible.value = false
    emit('success')
    handleClose()
  } catch (error) {
    ElMessage.error('退回失败')
  } finally {
    actionLoading.value = false
  }
}

// 撤回任务（已办任务）
const handleWithdraw = async () => {
  try {
    await ElMessageBox.confirm(
      `确定撤回任务"${taskInfo.value?.name}"？撤回后流程将退回到该任务节点`,
      '提示',
      { type: 'warning' }
    )
    actionLoading.value = true
    try {
      await taskApi.withdraw(taskId.value)
      ElMessage.success('撤回成功')
      emit('success')
      handleClose()
    } finally {
      actionLoading.value = false
    }
  } catch {
    // 用户取消
  }
}

// 对话框关闭时重置
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

// 抄送确认
const handleCopyConfirm = async () => {
  if (copyUserIds.value.length === 0) {
    ElMessage.warning('请选择抄送用户')
    return
  }
  actionLoading.value = true
  try {
    await taskApi.copy(taskId.value, {
      copyUserIds: copyUserIds.value,
      reason: copyReason.value || undefined,
    })
    ElMessage.success('抄送成功')
    copyDialogVisible.value = false
  } catch {
    ElMessage.error('抄送失败')
  } finally {
    actionLoading.value = false
  }
}

const handleCopyDialogClose = () => {
  copyUserIds.value = []
  copyReason.value = ''
  userOptions.value = []
}

defineExpose({ open })
</script>

<style scoped lang="scss">
.comment-attachments {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 4px;

  .attachment-link {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    width: fit-content;
    font-size: 12px;
  }
}

.drawer-content {
  display: flex;
  flex-direction: column;
  gap: 16px;

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
      padding: 16px;
    }
  }
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
</style>
