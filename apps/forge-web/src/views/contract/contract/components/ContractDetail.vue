<template>
  <el-drawer v-model="visible" title="合同详情" size="860px" destroy-on-close append-to-body @opened="loadDetail">
    <div v-loading="loading" class="detail-body">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="合同标题" :span="2">{{ detail?.title || '-' }}</el-descriptions-item>
        <el-descriptions-item label="合同编号">{{ detail?.contractNo || '定稿后生成' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="CONTRACT_STATUS_TAG[detail?.status ?? -1] || 'info'" size="small">
            {{ CONTRACT_STATUS_LABEL[detail?.status ?? -1] || '-' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="模板">
          {{ detail?.templateName ? `${detail.templateName} · v${detail.templateVersionNo}` : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="分类">
          {{ detail?.categoryName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="流程编号">{{ detail?.processNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(detail?.createTime) }}</el-descriptions-item>
      </el-descriptions>

      <!-- 变量值 -->
      <el-divider content-position="left">变量内容</el-divider>
      <el-empty v-if="!fieldEntries.length" description="无变量" :image-size="60" />
      <el-descriptions v-else :column="2" border size="small">
        <el-descriptions-item v-for="[key, value] in fieldEntries" :key="key" :label="key">
          {{ value ?? '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- PDF 预览 -->
      <el-divider content-position="left">PDF 预览</el-divider>
      <div v-if="pdfUrl" class="pdf-frame">
        <iframe :src="pdfUrl" width="100%" height="520" :title="detail?.title || '合同预览'" />
      </div>
      <div v-else class="pdf-actions">
        <el-alert :closable="false" type="info" show-icon
          :title="previewTip"
          :description="detail?.status === 1 ? '审批中的合同暂不可预览/打印' : '点击下方按钮实时渲染当前内容的 PDF 预览'" />
        <el-button v-if="canPreview" v-permission="'contract:contract:preview'" type="primary" plain
          style="margin-top: 10px" :loading="previewing" @click="doPreview">
          实时预览 PDF
        </el-button>
      </div>
    </div>

    <template #footer>
      <div class="detail-footer">
        <el-button v-if="detail?.pdfFileUrl" tag="a" :href="detail.pdfFileUrl" target="_blank" type="success">
          下载定稿 PDF
        </el-button>
        <el-button v-if="detail?.processInstanceId" @click="viewProcess">查看流程</el-button>
        <el-button v-if="canPrint" v-permission="'contract:contract:print'" type="primary" @click="printVisible = true">
          打印
        </el-button>
      </div>
    </template>

    <PrintDialog v-model="printVisible" :contract-id="contractId" />
    <InstanceDetailDrawer v-model="processDrawerVisible" :instance-id="processInstanceId" />
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { contractApi } from '@/api/contract/contract'
import { formatDateTime } from '@/utils/dateFormat'
import { CONTRACT_STATUS_LABEL, CONTRACT_STATUS_TAG, isEditableStatus, type ContractItem } from '@/types/contract'
import InstanceDetailDrawer from '@/views/workflow/instance/components/InstanceDetailDrawer.vue'
import PrintDialog from './PrintDialog.vue'

defineOptions({ name: 'ContractDetail' })

const props = defineProps<{ contractId: string }>()
const visible = defineModel<boolean>({ default: false })

const loading = ref(false)
const detail = ref<ContractItem | null>(null)
const pdfUrl = ref('')
const previewing = ref(false)
const printVisible = ref(false)
const processDrawerVisible = ref(false)
const processInstanceId = ref('')

const fieldEntries = computed<Array<[string, unknown]>>(() =>
  Object.entries(detail.value?.fieldValues ?? {})
)

const canPreview = computed(() => detail.value != null && isEditableStatus(detail.value.status))
const canPrint = computed(() => detail.value != null && detail.value.status !== 1)
const previewTip = computed(() =>
  detail.value?.status === 2 ? '定稿合同预览归档件' : '实时预览（不落盘）'
)

const loadDetail = async () => {
  loading.value = true
  try {
    detail.value = await contractApi.detail(props.contractId)
    pdfUrl.value = detail.value?.pdfFileUrl ?? ''
    processInstanceId.value = detail.value?.processInstanceId ?? ''
  } finally {
    loading.value = false
  }
}

let previewUrl = ''
const doPreview = async () => {
  previewing.value = true
  try {
    const blob = await contractApi.previewPdf(props.contractId)
    if (previewUrl) URL.revokeObjectURL(previewUrl)
    previewUrl = URL.createObjectURL(blob)
    pdfUrl.value = previewUrl
  } finally {
    previewing.value = false
  }
}

const viewProcess = () => {
  processDrawerVisible.value = true
}
</script>

<style scoped>
.detail-body {
  padding: 0 4px;
}
.pdf-frame {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}
.pdf-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.detail-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
