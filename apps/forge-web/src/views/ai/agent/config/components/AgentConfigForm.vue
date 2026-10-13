<template>
  <el-dialog v-model="dialogVisible" title="编辑 Agent 配置" width="640px" class="dialog-form-responsive">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
      <el-form-item label="Agent 名称">
        <el-input v-model="formData.agentName" placeholder="请输入 Agent 名称" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="formData.description" placeholder="请输入描述" />
      </el-form-item>
      <el-form-item label="系统提示词">
        <el-input v-model="formData.systemPrompt" type="textarea" :rows="4" placeholder="决策/总结节点的系统提示词" />
      </el-form-item>
      <el-form-item label="用户提示词模板">
        <el-input v-model="formData.userPromptTemplate" type="textarea" :rows="2" placeholder="支持 {input} 占位符" />
      </el-form-item>
      <el-form-item label="模型绑定">
        <el-input v-model="formData.modelBindings" type="textarea" :rows="4"
          placeholder='JSON：{"decision":{"modelType":"text"},"multimodal":{"modelType":"multimodal"},"summary":{"modelType":"text"}}，modelId 优先' />
      </el-form-item>
      <el-form-item label="步数上限">
        <el-input-number v-model="formData.maxSteps" :min="1" :max="50" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="formData.remark" type="textarea" placeholder="请输入备注（可选）" />
      </el-form-item>
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
import { agentApi } from '@/api/ai/agent'
import type { AgentConfig } from '@/api/ai/agent'

defineOptions({ name: 'AgentConfigForm' })

const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const currentId = ref<number>()

const formData = reactive({
  agentName: '',
  description: '',
  systemPrompt: '',
  userPromptTemplate: '',
  modelBindings: '',
  maxSteps: 8,
  remark: ''
})

const formRules: FormRules = {
  agentName: [{ required: true, message: '请输入 Agent 名称', trigger: 'blur' }]
}

const open = (row: AgentConfig) => {
  currentId.value = row.id
  Object.assign(formData, {
    agentName: row.agentName,
    description: row.description || '',
    systemPrompt: row.systemPrompt || '',
    userPromptTemplate: row.userPromptTemplate || '',
    modelBindings: row.modelBindings || '',
    maxSteps: row.maxSteps,
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

const emit = defineEmits(['success'])

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  if (formData.modelBindings.trim()) {
    try {
      JSON.parse(formData.modelBindings)
    } catch {
      ElMessage.error('模型绑定必须是合法 JSON')
      return
    }
  }
  submitLoading.value = true
  try {
    await agentApi.updateConfig(currentId.value!, { ...formData })
    ElMessage.success('更新成功')
    dialogVisible.value = false
    emit('success')
  } finally {
    submitLoading.value = false
  }
}

defineExpose({ open })
</script>
