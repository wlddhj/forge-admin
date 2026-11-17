<template>
  <el-card shadow="never" class="bizview-card">
    <template #header>
      <div class="card-header">
        <span>业务单据</span>
        <el-button
          v-if="data.previewEnabled"
          type="primary"
          size="small"
          :loading="previewing"
          @click="doPreview"
        >
          预览单据
        </el-button>
      </div>
    </template>

    <el-descriptions :column="isMobile ? 1 : 2" size="small" border>
      <el-descriptions-item v-for="field in data.fields || []" :key="field.label" :label="field.label">
        {{ field.value ?? '-' }}
      </el-descriptions-item>
    </el-descriptions>

    <div v-if="(data.attachments || []).length > 0" class="attachments">
      <div class="attachments-title">附件（{{ (data.attachments || []).length }}）</div>
      <div v-for="att in data.attachments" :key="att.id" class="attachment-row">
        <el-icon class="attachment-icon"><Document /></el-icon>
        <span class="attachment-name" :title="att.name">{{ att.name }}</span>
        <span class="attachment-size">{{ formatSize(att.size) }}</span>
        <el-button
          link
          type="primary"
          size="small"
          :loading="downloadingId === att.id"
          @click="download(att)"
        >
          下载
        </el-button>
      </div>
    </div>

    <!-- 单据预览弹窗（满高 iframe，交互复刻合同 PDF 预览） -->
    <el-dialog
      v-model="previewVisible"
      :title="`单据预览：${data.title || ''}`"
      fullscreen
      append-to-body
      destroy-on-close
      class="bizview-preview-dialog"
    >
      <iframe :src="previewUrl" class="preview-frame" :title="data.title || '单据预览'" />
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { Document } from '@element-plus/icons-vue'
import { processInstanceApi } from '@/api/workflow/process-instance'
import { useResponsive } from '@/composables/useResponsive'
import type { BizViewAttachment, BizViewData } from '@/types/workflow'

defineOptions({ name: 'BizViewCard' })

const props = defineProps<{
  /** 流程实例ID（预览/附件代理端点入参） */
  instanceId: string
  /** 业务单据视图数据（父组件经 getBizView 获取） */
  data: BizViewData
}>()

const { isMobile } = useResponsive()

const previewing = ref(false)
const previewVisible = ref(false)
const previewUrl = ref('')
const downloadingId = ref<number | null>(null)

const doPreview = async () => {
  previewing.value = true
  try {
    const blob = await processInstanceApi.getBizPreview(props.instanceId)
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = URL.createObjectURL(blob)
    previewVisible.value = true
  } finally {
    previewing.value = false
  }
}

const download = async (att: BizViewAttachment) => {
  downloadingId.value = att.id
  try {
    const blob = await processInstanceApi.downloadBizAttachment(props.instanceId, att.id)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = att.name || `attachment-${att.id}`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  } finally {
    downloadingId.value = null
  }
}

const formatSize = (size: number | null | undefined): string => {
  if (!size || size <= 0) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

onBeforeUnmount(() => {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})
</script>

<style scoped>
.bizview-card {
  margin-top: 12px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.attachments {
  margin-top: 12px;
}

.attachments-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}

.attachment-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
}

.attachment-icon {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}

.attachment-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-size {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}

.preview-frame {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
}
</style>

<style>
/* 单据预览弹窗 body：fullscreen 下让 iframe 撑满（append-to-body 传送门须非 scoped 覆盖，
   scoped :deep 时 data-v 挂 .el-overlay 而 custom class 落 .el-dialog，选择器两边凑不齐整条规则失效） */
.bizview-preview-dialog .el-dialog__body {
  padding: 0;
  height: calc(100vh - 55px);
}
</style>
