<template>
  <el-dialog v-model="visible" title="用户导入" width="450px" @close="handleClose">
    <el-upload
      ref="uploadRef"
      v-model:file-list="fileList"
      :auto-upload="false"
      :limit="1"
      :on-exceed="handleExceed"
      accept=".xlsx, .xls"
      drag
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">
          <el-checkbox v-model="updateSupport" /> 是否更新已存在的用户数据
        </div>
        <div class="el-upload__tip">
          仅允许导入 xls、xlsx 格式文件。
          <el-link type="primary" :underline="false" @click="handleDownloadTemplate">下载模板</el-link>
        </div>
      </template>
    </el-upload>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="uploading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, type UploadFile, type UploadInstance, type UploadRawFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { importUsers, downloadImportTemplate } from '@/api/system'

const emit = defineEmits<{
  success: []
}>()

const visible = ref(false)
const uploading = ref(false)
const updateSupport = ref(false)
const fileList = ref<UploadFile[]>([])
const uploadRef = ref<UploadInstance>()

const open = () => {
  fileList.value = []
  updateSupport.value = false
  visible.value = true
}

const handleClose = () => {
  fileList.value = []
  uploadRef.value?.clearFiles()
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件，请先删除已选文件')
}

const handleDownloadTemplate = async () => {
  try {
    await downloadImportTemplate()
    ElMessage.success('模板下载成功')
  } catch {
    ElMessage.error('模板下载失败')
  }
}

const handleSubmit = async () => {
  if (fileList.value.length === 0) {
    ElMessage.warning('请先选择文件')
    return
  }

  const file = fileList.value[0].raw as UploadRawFile
  uploading.value = true
  try {
    const result = await importUsers(file, updateSupport.value)

    let msg = ''
    if (result.createUsernames.length > 0) {
      msg += `新增成功 ${result.createUsernames.length} 条：${result.createUsernames.join('、')}；`
    }
    if (result.updateUsernames.length > 0) {
      msg += `更新成功 ${result.updateUsernames.length} 条：${result.updateUsernames.join('、')}；`
    }
    const failCount = Object.keys(result.failureUsernames).length
    if (failCount > 0) {
      msg += `失败 ${failCount} 条`
    }

    ElMessage.success(msg || '导入完成')
    visible.value = false
    emit('success')
  } catch {
    ElMessage.error('导入失败')
  } finally {
    uploading.value = false
  }
}

defineExpose({ open })
</script>
