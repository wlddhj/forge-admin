<template>
  <div class="app-container">
    <div v-if="!embedded" class="detail-header">
      <el-page-header @back="router.back()">
        <template #content>
          <span class="page-title">{{ instance?.processDefinitionName || '流程实例详情' }}</span>
        </template>
      </el-page-header>
    </div>

    <div v-loading="loading" class="detail-content">
      <div class="info-section">
        <!-- 实例信息 -->
        <el-card shadow="never" class="info-card">
          <template #header>
            <div class="card-header">
              <span>实例信息</span>
              <div>
                <el-button
                  v-if="!instance?.endTime"
                  v-permission="'workflow:instance:urge'"
                  type="warning"
                  size="small"
                  :loading="urging"
                  @click="handleUrge"
                >
                  催办
                </el-button>
                <el-button
                  v-if="!instance?.endTime"
                  v-permission="'workflow:instance:query'"
                  size="small"
                  @click="copyDialogVisible = true"
                >
                  抄送
                </el-button>
                <el-button
                  v-if="instance?.deleteReason"
                  v-permission="'workflow:instance:resume'"
                  type="success"
                  size="small"
                  :loading="resuming"
                  @click="handleResume"
                >
                  唤醒
                </el-button>
                <el-button type="primary" size="small" @click="diagramDialogVisible = true">
                  查看流程图
                </el-button>
                <el-button type="primary" size="small" plain @click="handlePrint">
                  打印
                </el-button>
              </div>
            </div>
          </template>
          <el-descriptions :column="isMobile ? 1 : 2" size="small" border>
            <el-descriptions-item label="流程编号">
              {{ instance?.processNo || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="流程名称">
              {{ instance?.processDefinitionName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="发起人">
              {{ instance?.startUserName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="发起时间">
              {{ formatDateTime(instance?.startTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag v-if="!instance?.endTime" type="primary">运行中</el-tag>
              <el-tag v-else-if="instance?.deleteReason" type="danger">已终止</el-tag>
              <el-tag v-else type="success">已结束</el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="!instance?.endTime" label="当前节点">
              <el-tag v-if="instance?.currentActivityName" type="warning">{{ instance.currentActivityName }}</el-tag>
              <span v-else style="color: #909399">-</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="!instance?.endTime" label="处理人">
              <template v-if="instance?.currentAssigneeNames && instance.currentAssigneeNames.length > 0">
                {{ instance.currentAssigneeNames.join(', ') }}
              </template>
              <template v-else-if="instance?.currentCandidateNames && instance.currentCandidateNames.length > 0">
                <el-tag type="warning" size="small">待认领</el-tag>
                <span style="margin-left: 4px; color: #909399; font-size: 12px">{{ instance.currentCandidateNames.join(', ') }}</span>
              </template>
              <span v-else style="color: #909399">-</span>
            </el-descriptions-item>
            <el-descriptions-item label="业务标识">
              {{ instance?.businessKey || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 流程表单 -->
        <el-card v-if="formReady && formRule.length > 0" shadow="never" class="form-card">
          <template #header>申请表单</template>
          <form-create
            ref="formCreateRef"
            v-model="formData"
            :rule="formRule"
            :option="formOption"
          />
        </el-card>

        <!-- 审批进度时间线（默认收起，点击头部展开） -->
        <el-card v-if="approvalNodes.length" shadow="never" class="timeline-card">
          <template #header>
            <div class="collapsible-header" @click="timelineExpanded = !timelineExpanded">
              <span>审批进度</span>
              <el-icon class="toggle-icon" :class="{ expanded: timelineExpanded }">
                <ArrowDown />
              </el-icon>
            </div>
          </template>
          <ApprovalTimeline v-show="timelineExpanded" :nodes="approvalNodes" />
        </el-card>

        <!-- AI 决策记录（分支路由 + 审批决策，两组皆空隐藏） -->
        <el-card v-if="aiDecisions && (aiDecisions.routings.length || aiDecisions.approvals.length)" shadow="never" class="timeline-card">
          <template #header>AI 决策记录</template>

          <div v-if="aiDecisions.routings.length" class="ai-decision-block">
            <div class="ai-decision-block__title">分支路由</div>
            <el-table :data="aiDecisions.routings" size="small">
              <el-table-column prop="createTime" label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="网关" min-width="140">
                <template #default="{ row }">
                  {{ row.gatewayNodeName || row.gatewayNodeKey }}
                  <el-tag size="small" type="info" style="margin-left: 4px">{{ row.gatewayTypeName }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="选中分支" min-width="140">
                <template #default="{ row }">
                  <template v-if="row.selectedBranchNames.length">
                    <el-tag v-for="name in row.selectedBranchNames" :key="name" size="small" style="margin-right: 4px">
                      {{ name }}
                    </el-tag>
                  </template>
                  <span v-else style="color: #909399">未采纳（已降级）</span>
                </template>
              </el-table-column>
              <el-table-column prop="confidence" label="置信度" width="90" align="center">
                <template #default="{ row }">{{ row.confidence != null ? row.confidence : '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="120" align="center">
                <template #default="{ row }">
                  <el-tag :type="aiStatusTagType(row.status)" size="small">{{ aiStatusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="reasoning" label="AI 理由" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">{{ row.reasoning || '-' }}</template>
              </el-table-column>
            </el-table>
          </div>

          <div v-if="aiDecisions.approvals.length" class="ai-decision-block">
            <div class="ai-decision-block__title">审批决策</div>
            <el-table :data="aiDecisions.approvals" size="small">
              <el-table-column prop="createTime" label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
              </el-table-column>
              <el-table-column prop="taskName" label="审批节点" min-width="140" />
              <el-table-column label="决策" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.decision === 'APPROVE' ? 'success' : row.decision === 'REJECT' ? 'danger' : 'info'" size="small">
                    {{ row.decision === 'APPROVE' ? '通过' : row.decision === 'REJECT' ? '驳回' : '转人工' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="confidence" label="置信度" width="90" align="center">
                <template #default="{ row }">{{ row.confidence != null ? row.confidence : '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="120" align="center">
                <template #default="{ row }">
                  <el-tag :type="aiStatusTagType(row.status)" size="small">{{ aiStatusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="reasoning" label="AI 理由" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">{{ row.reasoning || '-' }}</template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>

        <!-- 审批记录 -->
        <el-card shadow="never" class="timeline-card">
          <template #header>审批记录</template>
          <el-table v-if="comments.length" :data="comments" size="small">
            <el-table-column type="index" label="序号" width="50" align="center" />
            <el-table-column prop="userName" label="审批人" width="100" />
            <el-table-column prop="actionType" label="动作" width="80" align="center">
              <template #default="{ row }">
                <dict-value :dict-type="DICT_TYPE.WF_ACTION_TYPE" :value="row.actionType" />
              </template>
            </el-table-column>
            <el-table-column prop="taskName" label="任务名称" width="110" />
            <el-table-column prop="commentText" label="审批意见" min-width="200">
              <template #default="{ row }">
                <div class="comment-text">{{ row.commentText || '-' }}</div>
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
            <el-table-column prop="createTime" label="时间" width="120">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无审批记录" :image-size="60" />
        </el-card>
      </div>
    </div>

    <!-- 流程图弹窗 -->
    <FlowDiagramDialog
      v-model="diagramDialogVisible"
      :process-instance-id="instanceId"
    />

    <!-- 手动抄送弹窗 -->
    <InstanceCopyDialog
      v-model="copyDialogVisible"
      :instance-id="instanceId"
    />

    <!-- 打印区域（屏幕隐藏，打印时挂到 body 顶层输出：实例信息/申请表单/审批记录） -->
    <div ref="printAreaRef" class="flow-print-area">
      <h1 class="print-title">{{ instance?.processDefinitionName || '流程审批单' }}</h1>
      <div class="print-meta">
        <span>流程编号：{{ instance?.processNo || instance?.id }}</span>
        <span>打印时间：{{ formatDateTime(printTime) }}</span>
      </div>

      <h2 class="print-section">一、实例信息</h2>
      <table class="print-table">
        <tbody>
          <tr>
            <th>流程名称</th>
            <td>{{ instance?.processDefinitionName || '-' }}</td>
            <th>业务标识</th>
            <td>{{ instance?.businessKey || '-' }}</td>
          </tr>
          <tr>
            <th>发起人</th>
            <td>{{ instance?.startUserName || '-' }}</td>
            <th>发起时间</th>
            <td>{{ formatDateTime(instance?.startTime) }}</td>
          </tr>
          <tr>
            <th>结束时间</th>
            <td>{{ instance?.endTime ? formatDateTime(instance.endTime) : '-' }}</td>
            <th>紧急程度</th>
            <td>{{ instance?.urgentName || '常规' }}</td>
          </tr>
          <tr>
            <th>当前状态</th>
            <td colspan="3">
              {{ instance?.endTime ? (instance.deleteReason ? `已终止（${instance.deleteReason}）` : '已结束') : `运行中（当前节点：${instance?.currentActivityName || '-'}）` }}
            </td>
          </tr>
        </tbody>
      </table>

      <h2 class="print-section">二、申请表单</h2>
      <table v-if="printFormItems.length" class="print-table">
        <tbody>
          <!-- 与详情页栅格一致：一行两个属性，末行奇数项值列跨到行尾 -->
          <tr v-for="(row, rowIndex) in printFormRows" :key="rowIndex">
            <template v-for="(item, colIndex) in row" :key="item.field">
              <th class="print-form-label">{{ item.title }}</th>
              <td v-if="colIndex === 0 && row.length === 1" colspan="3">{{ printFormField(item.field) }}</td>
              <td v-else>{{ printFormField(item.field) }}</td>
            </template>
          </tr>
        </tbody>
      </table>
      <p v-else class="print-empty">（本流程未配置表单）</p>

      <h2 class="print-section">三、审批记录</h2>
      <table v-if="printRecords.length" class="print-table print-records">
        <thead>
          <tr>
            <th style="width: 18%">审批节点</th>
            <th style="width: 14%">处理人</th>
            <th style="width: 14%">动作</th>
            <th style="width: 34%">审批意见</th>
            <th style="width: 20%">时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(record, index) in printRecords" :key="index">
            <td style="text-align: center">{{ record.nodeName }}</td>
            <td style="text-align: center">{{ record.userName }}</td>
            <td style="text-align: center">
              <!-- 打印用纯文本（发起人节点固定"提交"），不带字典标签样式 -->
              {{ record.actionText }}
            </td>
            <td class="print-pre">{{ record.comment || '-' }}</td>
            <td>{{ record.time ? formatDateTime(record.time) : '-' }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="print-empty">（暂无审批记录）</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Paperclip } from '@element-plus/icons-vue'
import { processInstanceApi } from '@/api/workflow/process-instance'
import { processDefinitionApi } from '@/api/workflow/process-definition'
import { formApi } from '@/api/workflow/form'
import type { ProcessInstance, ApprovalNode, ApprovalComment, AiDecisionRecords } from '@/types/workflow'
import { formatDateTime } from '@/utils/dateFormat'
import { decodeFieldsDisabled } from '@/utils/formCreate'
import FlowDiagramDialog from '@/views/workflow/process/components/FlowDiagramDialog.vue'
import ApprovalTimeline from './components/ApprovalTimeline.vue'
import InstanceCopyDialog from './components/InstanceCopyDialog.vue'
import { useResponsive } from '@/composables/useResponsive'
import { DICT_TYPE } from '@/constants/dict'
import { useDict } from '@/composables/useDict'

const props = defineProps<{
  /** 实例ID：抽屉等嵌入场景注入；不传则取路由 query.id（页面模式） */
  instanceId?: string
  /** 嵌入模式：隐藏页面头与返回按钮（由容器提供标题） */
  embedded?: boolean
}>()

const { isMobile } = useResponsive()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const instance = ref<ProcessInstance | null>(null)
const approvalNodes = ref<ApprovalNode[]>([])
const comments = ref<ApprovalComment[]>([])
const aiDecisions = ref<AiDecisionRecords | null>(null)
const diagramDialogVisible = ref(false)
// 审批进度默认收起
const timelineExpanded = ref(false)

// ========== 打印（实例信息/申请表单/审批记录） ==========
const printAreaRef = ref<HTMLElement | null>(null)
// 毫秒时间戳（formatDateTime 字符串分支不认 ISO 格式）；打印瞬间刷新为当前时间
const printTime = ref<number>(Date.now())
const { getDictLabel: wfActionLabel } = useDict(DICT_TYPE.WF_ACTION_TYPE)

// 扁平化 form-create 规则（fcRow/col 嵌套 children）为 field/title 列表
const printFormItems = computed(() => {
  const items: { field: string; title: string }[] = []
  const walk = (rules: { field?: string; title?: string; children?: unknown[] }[] | undefined) => {
    for (const rule of rules || []) {
      if (rule.field && rule.title) {
        items.push({ field: rule.field, title: rule.title })
      }
      if (Array.isArray(rule.children)) {
        walk(rule.children as { field?: string; title?: string; children?: unknown[] }[])
      }
    }
  }
  walk(formRule.value)
  return items
})

// 两两成行（与详情页一行两属性对齐），末行奇数项由模板跨列补齐
const printFormRows = computed(() => {
  const rows: { field: string; title: string }[][] = []
  for (let i = 0; i < printFormItems.value.length; i += 2) {
    rows.push(printFormItems.value.slice(i, i + 2))
  }
  return rows
})

const printFormField = (field: string): string => {
  const value = formData.value?.[field]
  if (value == null || value === '') return '-'
  return String(value)
}

// 已到达节点的任务记录（未到达不输出）；发起人节点（activityType=0）动作固定显示"提交"
const printRecords = computed(() =>
  approvalNodes.value
    .filter(node => node.status !== 0)
    .flatMap(node => {
      const isStartNode = node.activityType === '0'
      const actionFallback = node.status === 1 ? '进行中' : '已完成'
      const rows = (node.tasks || []).map(task => ({
        nodeName: node.activityName,
        userName: task.userName,
        actionText: isStartNode
          ? '提交'
          : (task.actionType ? wfActionLabel(task.actionType) : actionFallback),
        comment: task.comment,
        time: task.endTime || task.createTime
      }))
      if (rows.length) return rows
      // 节点已到达但无任务明细（如自动通过/触发器节点），输出节点级记录
      return [{
        nodeName: node.activityName,
        userName: '-',
        actionText: isStartNode ? '提交' : actionFallback,
        comment: '',
        time: node.endTime || node.startTime
      }]
    })
)

const handlePrint = async () => {
  printTime.value = Date.now()
  await nextTick()
  const area = printAreaRef.value
  if (!area) return
  // 抽屉/弹窗带 transform 形成 containing block，打印区须挂到 body 顶层再输出
  const parent = area.parentNode
  document.body.appendChild(area)
  const restore = () => {
    parent?.appendChild(area)
    window.removeEventListener('afterprint', restore)
  }
  window.addEventListener('afterprint', restore)
  window.print()
  // Safari 等不触发 afterprint 的兜底：同步还原由打印快照机制兜住，视觉无残留
}
const copyDialogVisible = ref(false)
const urging = ref(false)

// 唤醒已结束实例（被驳回/终止/撤销/超时结束的可唤醒）
const resuming = ref(false)
const handleResume = async () => {
  try {
    await ElMessageBox.confirm('确定唤醒该流程？流程将恢复到上次中断的节点继续办理', '提示', { type: 'warning' })
    resuming.value = true
    await processInstanceApi.resume(instanceId.value)
    ElMessage.success('唤醒成功')
    getInstanceDetail()
  } catch {
    // 用户取消或后端错误
  } finally {
    resuming.value = false
  }
}

// AI 决策状态展示
const aiStatusTagType = (status: string): 'success' | 'warning' | 'danger' | 'info' => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'TIMEOUT' || status === 'ERROR') return 'danger'
  if (status === 'FALLBACK' || status === 'LOW_CONFIDENCE' || status === 'FAILURE') return 'warning'
  return 'info'
}
const aiStatusText = (status: string): string => {
  const map: Record<string, string> = {
    SUCCESS: '已采纳',
    FALLBACK: '已降级',
    LOW_CONFIDENCE: '低置信度',
    TIMEOUT: '超时',
    ERROR: '异常',
    FAILURE: '失败'
  }
  return map[status] || status
}

// 催办当前全部活动审批任务
const handleUrge = async () => {
  let comment = ''
  try {
    const { value } = await ElMessageBox.prompt('将通知当前所有审批任务的处理人', '催办', {
      confirmButtonText: '催办',
      cancelButtonText: '取消',
      inputPlaceholder: '催办留言（选填）',
      inputType: 'textarea',
      inputValue: ''
    })
    comment = value || ''
  } catch {
    return
  }
  urging.value = true
  try {
    await processInstanceApi.urge(instanceId.value, comment ? { comment } : undefined)
    ElMessage.success('催办成功，已通知当前审批人')
  } finally {
    urging.value = false
  }
}

const instanceId = computed(() => props.instanceId || (route.query.id as string))

// 抽屉复用同一组件实例切换实例时重载
watch(() => props.instanceId, (val) => {
  if (val) getInstanceDetail()
})

// 表单相关
const formCreateRef = ref<any>(null)
const formRule = ref<any[]>([])
const formOption = ref({ submitBtn: false, resetBtn: false, disabled: true } as any)
const formData = ref<Record<string, any>>({})
const formReady = ref(false)

// 获取实例详情
const getInstanceDetail = async () => {
  if (!instanceId.value) return
  loading.value = true
  try {
    const [instanceData, detailData, commentsData, aiDecisionsData] = await Promise.allSettled([
      processInstanceApi.getById(instanceId.value),
      processInstanceApi.getApprovalDetail(instanceId.value),
      processInstanceApi.getComments(instanceId.value),
      processInstanceApi.getAiDecisions(instanceId.value)
    ])

    if (instanceData.status === 'fulfilled') {
      instance.value = instanceData.value
      // 通过流程定义ID获取表单
      if (instanceData.value?.processDefinitionId) {
        const processDefId = instanceData.value.processDefinitionId
        try {
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

              try {
                const variables = await processInstanceApi.getVariables(instanceId.value)
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
          } else {
            formReady.value = true
          }
        } catch {
          formReady.value = true
        }
      } else {
        formReady.value = true
      }
    }

    if (detailData.status === 'fulfilled' && detailData.value) {
      approvalNodes.value = detailData.value.nodes || []
    }

    if (commentsData.status === 'fulfilled') {
      comments.value = commentsData.value || []
    }

    if (aiDecisionsData.status === 'fulfilled') {
      aiDecisions.value = aiDecisionsData.value || { routings: [], approvals: [] }
    }
  } catch (error) {
    console.error('获取实例详情失败:', error)
    ElMessage.error('获取实例详情失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  getInstanceDetail()
})
</script>

<style scoped lang="scss">
// 审批意见保留审批时输入的换行
.comment-text {
  white-space: pre-wrap;
}

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
    max-width: 900px;

    .info-card {
      .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
      }
    }

    .collapsible-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      cursor: pointer;
      user-select: none;

      .toggle-icon {
        transition: transform 0.2s;
        color: var(--el-text-color-secondary);

        &.expanded {
          transform: rotate(180deg);
        }
      }
    }

    .form-card,
    .timeline-card {
      :deep(.el-card__body) {
        max-height: 500px;
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

@media (max-width: 768px) {
  .detail-content {
    .info-section {
      max-width: 100%;
    }
  }
}

.ai-decision-block {
  &:not(:first-child) {
    margin-top: 12px;
  }

  &__title {
    font-size: 13px;
    font-weight: 600;
    color: #646a73;
    margin-bottom: 8px;
  }
}
</style>

<!-- 打印样式：打印时只输出打印区（打印区会被临时挂到 body 顶层，须全局作用） -->
<style lang="scss">
.flow-print-area {
  display: none;
}

@media print {
  body * {
    visibility: hidden;
  }

  .flow-print-area,
  .flow-print-area * {
    visibility: visible;
  }

  .flow-print-area {
    display: block;
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    box-sizing: border-box;
    padding: 16px;
    color: #000;
    background: #fff;

    .print-title {
      text-align: center;
      font-size: 20px;
      margin: 0 0 8px;
    }

    .print-meta {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      color: #333;
      margin-bottom: 12px;
    }

    .print-section {
      font-size: 15px;
      margin: 14px 0 6px;
    }

    .print-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
      table-layout: fixed;

      th,
      td {
        border: 1px solid #333;
        padding: 5px 8px;
        text-align: left;
        word-break: break-all;
      }

      th {
        background: #f2f2f2;
        width: 15%;
        font-weight: 600;
      }

      thead th {
        text-align: center;
      }

      .print-form-label {
        width: 22%;
      }
    }

    .print-pre {
      white-space: pre-wrap;
    }

    .print-empty {
      font-size: 13px;
      color: #666;
    }
  }
}
</style>
