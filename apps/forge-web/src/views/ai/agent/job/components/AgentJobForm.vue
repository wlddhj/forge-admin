<template>
  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" class="dialog-form-responsive">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
      <el-form-item label="任务名称" prop="jobName">
        <el-input v-model="formData.jobName" placeholder="请输入任务名称" />
      </el-form-item>
      <el-form-item label="Agent" prop="agentCode">
        <el-select v-model="formData.agentCode" placeholder="请选择 Agent" style="width: 100%">
          <el-option v-for="agent in agentList" :key="agent.agentCode" :label="agent.agentName" :value="agent.agentCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="cron 表达式" prop="cronExpression">
        <el-input v-model="formData.cronExpression" placeholder="如 0 0 8 * * ?（每天 8 点）" />
      </el-form-item>
      <el-form-item label="输入模板" prop="inputTemplate">
        <el-input v-model="formData.inputTemplate" type="textarea" :rows="4"
          placeholder="每次调度发给 Agent 的固定指令，支持占位符：{date} 当日、{yesterday} 昨日、{datetime} 当前时刻" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停止</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { agentJobApi } from '@/api/ai/agent'
import { getAgentConfigs } from '@/api/ai/agent'
import type { AgentConfig, AgentJob } from '@/api/ai/agent'

defineOptions({ name: 'AgentJobForm' })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const currentId = ref<number>()
const agentList = ref<AgentConfig[]>([])

const formData = reactive({
  jobName: '',
  agentCode: '',
  cronExpression: '',
  inputTemplate: '',
  status: 1
})

const formRules: FormRules = {
  jobName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  agentCode: [{ required: true, message: '请选择 Agent', trigger: 'change' }],
  cronExpression: [{ required: true, message: '请输入 cron 表达式', trigger: 'blur' }]
}

onMounted(() => {
  getAgentConfigs(1).then(list => { agentList.value = list }).catch(() => { })
})

const open = (type: 'create' | 'update', row?: AgentJob) => {
  formRef.value?.resetFields()
  if (type === 'create') {
    dialogTitle.value = '新增 Agent 任务'
    isEdit.value = false
    Object.assign(formData, { jobName: '', agentCode: '', cronExpression: '', inputTemplate: '', status: 1 })
  } else if (row) {
    dialogTitle.value = '编辑 Agent 任务'
    isEdit.value = true
    currentId.value = row.id
    Object.assign(formData, {
      jobName: row.jobName,
      agentCode: row.agentCode,
      cronExpression: row.cronExpression,
      inputTemplate: row.inputTemplate || '',
      status: row.status
    })
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
      await agentJobApi.update(currentId.value!, { ...formData })
      ElMessage.success('更新成功')
    } else {
      await agentJobApi.add({ ...formData })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    submitLoading.value = false
  }
}

defineExpose({ open })
</script>
