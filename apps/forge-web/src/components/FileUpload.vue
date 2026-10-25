<template>
  <div class="file-upload">
    <el-upload
      :accept="accept"
      :disabled="disabled || uploading"
      :show-file-list="false"
      :http-request="handleUpload"
      :before-upload="handleBeforeUpload"
    >
      <el-button type="primary" :disabled="disabled" :loading="uploading">
        {{ uploading ? `上传中 ${progress}%` : '上传文件' }}
      </el-button>
    </el-upload>
    <el-progress v-if="uploading" :percentage="progress" :stroke-width="4" class="file-upload__progress" />
    <ul v-if="files.length" class="file-upload__list">
      <li v-for="file in files" :key="file.id" class="file-upload__item">
        <el-link :href="file.url" target="_blank" type="primary" :underline="false">
          {{ file.name }}
        </el-link>
        <span class="file-upload__size">{{ formatFileSize(file.size) }}</span>
        <el-button v-if="!disabled" type="danger" link size="small" @click="handleRemove(file.id)">删除</el-button>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { getAttachment, uploadAttachment } from '@/api/system'
import type { Attachment } from '@/types/system'
import { FILE_MAX_SIZE_MB } from '@/constants/upload'

const props = withDefaults(
  defineProps<{
    /** 附件ID列表 */
    modelValue: number[]
    /** 业务类型，落 sys_attachment.biz_type */
    bizType?: string
    /** 业务ID，编辑已有业务时传入 */
    bizId?: number
    accept?: string
    maxSizeMb?: number
    limit?: number
    disabled?: boolean
  }>(),
  {
    accept: '',
    maxSizeMb: FILE_MAX_SIZE_MB,
    limit: 5,
    disabled: false,
    bizType: undefined,
    bizId: undefined
  }
)

const emit = defineEmits<{ 'update:modelValue': [ids: number[]] }>()

interface FileItem {
  id: number
  name: string
  url: string
  size: number
}

const files = ref<FileItem[]>([])
const uploading = ref(false)
const progress = ref(0)

watch(
  () => props.modelValue,
  (ids) => syncFromIds(ids ?? []),
  { immediate: true }
)

/** 回显：按 ids 补拉缺失详情，过滤已被外部移除的项 */
async function syncFromIds(ids: number[]) {
  const known = new Map(files.value.map((f) => [f.id, f]))
  const missing = ids.filter((id) => !known.has(id))
  if (missing.length) {
    // silent：附件可能已被删除，回显失败不弹全局错误
    const details: (Attachment | null)[] = await Promise.all(
      missing.map((id) => getAttachment(id, { silent: true }).catch(() => null))
    )
    details.forEach((detail: Attachment | null) => {
      if (detail) {
        known.set(detail.id, {
          id: detail.id,
          name: detail.originalName || detail.fileName,
          url: detail.fileUrl,
          size: detail.fileSize
        })
      }
    })
  }
  files.value = ids.map((id) => known.get(id)).filter((f): f is FileItem => !!f)
}

function handleBeforeUpload(file: File) {
  if (files.value.length >= props.limit) {
    ElMessage.error(`最多上传 ${props.limit} 个文件`)
    return false
  }
  if (file.size > props.maxSizeMb * 1024 * 1024) {
    ElMessage.error(`文件大小不能超过 ${props.maxSizeMb}MB`)
    return false
  }
  return true
}

async function handleUpload(options: UploadRequestOptions) {
  uploading.value = true
  progress.value = 0
  try {
    const data = await uploadAttachment(options.file, props.bizType, props.bizId, (p) => {
      progress.value = p
    })
    files.value.push({
      id: data.id,
      name: data.originalName || data.fileName,
      url: data.fileUrl,
      size: data.fileSize
    })
    emit('update:modelValue', files.value.map((f) => f.id))
  } finally {
    uploading.value = false
  }
}

function handleRemove(id: number) {
  files.value = files.value.filter((f) => f.id !== id)
  emit('update:modelValue', files.value.map((f) => f.id))
}

function formatFileSize(bytes: number): string {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped lang="scss">
.file-upload {
  &__progress {
    width: 240px;
    margin-top: 8px;
  }

  &__list {
    list-style: none;
    margin: 8px 0 0;
    padding: 0;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 0;
  }

  &__size {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}
</style>
