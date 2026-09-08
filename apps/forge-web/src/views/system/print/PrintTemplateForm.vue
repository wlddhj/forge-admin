<template>
  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" class="dialog-form-responsive">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px" :disabled="formLoading">
      <el-form-item label="模板编号" prop="templateCode">
        <el-input v-model="formData.templateCode" placeholder="请输入模板编号" :disabled="isEdit" />
      </el-form-item>
      <el-form-item label="模板名称" prop="templateName">
        <el-input v-model="formData.templateName" placeholder="请输入模板名称" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
      </el-form-item>
      <el-alert type="info" :closable="false" show-icon>
        模板内容（hiprint JSON）请在保存后通过"设计"按钮进入设计器编辑。
      </el-alert>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { PrintTemplateApi, type PrintTemplateRequest } from '@/api/system/print-template'

defineOptions({ name: 'PrintTemplateForm' })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const formLoading = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const formData = reactive<PrintTemplateRequest>({
  id: undefined,
  templateCode: '',
  templateName: '',
  contents: '',
  version: 1,
  status: 1,
  remark: ''
})

const formRules: FormRules = {
  templateCode: [{ required: true, message: '请输入模板编号', trigger: 'blur' }],
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }]
}

const open = async (type: string, id?: number) => {
  resetForm()
  if (type === 'create') {
    dialogTitle.value = '新增打印模板'
    isEdit.value = false
  } else if (id) {
    dialogTitle.value = '编辑打印模板'
    isEdit.value = true
    formLoading.value = true
    try {
      const data = await PrintTemplateApi.get(id)
      Object.assign(formData, data)
    } finally {
      formLoading.value = false
    }
  }
  dialogVisible.value = true
}

const emit = defineEmits(['success'])

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await PrintTemplateApi.update(formData)
      ElMessage.success('更新成功')
    } else {
      await PrintTemplateApi.create(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    submitLoading.value = false
  }
}

const resetForm = () => {
  Object.assign(formData, {
    id: undefined,
    templateCode: '',
    templateName: '',
    contents: '',
    version: 1,
    status: 1,
    remark: ''
  })
  formRef.value?.resetFields()
}

defineExpose({ open })
</script>
