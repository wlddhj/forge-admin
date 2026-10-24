<template>
  <div class="app-container">
    <div class="detail-header">
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
              <el-button type="primary" size="small" @click="diagramDialogVisible = true">
                查看流程图
              </el-button>
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

        <!-- 审批记录 -->
        <el-card shadow="never" class="timeline-card">
          <template #header>审批记录</template>
          <el-table v-if="comments.length" :data="comments" size="small">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="createTime" label="时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column prop="userName" label="审批人" width="100" />
            <el-table-column prop="actionType" label="动作" width="80" align="center">
              <template #default="{ row }">
                <dict-value :dict-type="DICT_TYPE.WF_ACTION_TYPE" :value="row.actionType" />
              </template>
            </el-table-column>
            <el-table-column prop="taskName" label="任务名称" min-width="120" />
            <el-table-column prop="commentText" label="审批意见" min-width="200">
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Paperclip } from '@element-plus/icons-vue'
import { processInstanceApi } from '@/api/workflow/process-instance'
import { processDefinitionApi } from '@/api/workflow/process-definition'
import { formApi } from '@/api/workflow/form'
import type { ProcessInstance, ApprovalNode, ApprovalComment } from '@/types/workflow'
import { formatDateTime } from '@/utils/dateFormat'
import { decodeFieldsDisabled } from '@/utils/formCreate'
import FlowDiagramDialog from '@/views/workflow/process/components/FlowDiagramDialog.vue'
import { useResponsive } from '@/composables/useResponsive'
import { DICT_TYPE } from '@/constants/dict'

const { isMobile } = useResponsive()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const instance = ref<ProcessInstance | null>(null)
const approvalNodes = ref<ApprovalNode[]>([])
const comments = ref<ApprovalComment[]>([])
const diagramDialogVisible = ref(false)

const instanceId = route.query.id as string

// 表单相关
const formCreateRef = ref<any>(null)
const formRule = ref<any[]>([])
const formOption = ref({ submitBtn: false, resetBtn: false, disabled: true } as any)
const formData = ref<Record<string, any>>({})
const formReady = ref(false)

// 获取实例详情
const getInstanceDetail = async () => {
  if (!instanceId) return
  loading.value = true
  try {
    const [instanceData, detailData, commentsData] = await Promise.allSettled([
      processInstanceApi.getById(instanceId),
      processInstanceApi.getApprovalDetail(instanceId),
      processInstanceApi.getComments(instanceId)
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
                const variables = await processInstanceApi.getVariables(instanceId)
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
</style>
