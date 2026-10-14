<template>
  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" class="dialog-form-responsive">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px" :disabled="formLoading">
      <el-form-item label="模型名称" prop="modelName">
        <el-input v-model="formData.modelName" placeholder="请输入模型名称" />
      </el-form-item>
      <el-form-item label="模型代码" prop="modelCode">
        <el-input v-model="formData.modelCode" placeholder="请输入模型代码（如 deepseek-chat）" />
      </el-form-item>
      <el-form-item label="提供商" prop="provider">
        <el-input v-model="formData.provider" placeholder="请输入提供商（如 deepseek/qwen/glm）" />
      </el-form-item>
      <el-form-item label="模型类型" prop="modelType">
        <el-select v-model="formData.modelType" placeholder="请选择模型类型" style="width: 100%">
          <el-option
            v-for="item in dictData"
            :key="item.dictValue"
            :label="item.dictLabel"
            :value="item.dictValue"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="API Endpoint" prop="apiEndpoint">
        <el-input v-model="formData.apiEndpoint" placeholder="请输入 API Endpoint（OpenAI 兼容 base-url）" />
      </el-form-item>
      <el-form-item label="API Key" prop="apiKey">
        <el-input v-model="formData.apiKey" type="password"
          :placeholder="isEdit && formData.apiKey === '' ? `当前 ${apiKeyMask}，留空表示不修改` : '请输入 API Key'"
          show-password />
      </el-form-item>
      <el-form-item label="最大 Tokens" prop="maxTokens">
        <el-input-number v-model="formData.maxTokens" :min="1" :max="100000" :step="100" />
      </el-form-item>
      <el-form-item label="Temperature" prop="temperature">
        <el-slider v-model="formData.temperature" :min="0" :max="2" :step="0.1" show-input />
      </el-form-item>
      <el-form-item label="禁用思考">
        <el-switch v-model="formData.thinkingDisabled" :active-value="1" :inactive-value="0" />
        <span class="form-tip">思考类模型（GLM-4.x 等）建议开启：思考会消耗 max_tokens 预算导致输出为空</span>
      </el-form-item>
      <el-form-item v-if="!isEdit" label="备注" prop="remark">
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
import { addModel, configModel } from '@/api/ai/model'
import type { AddModelRequest, ModelConfigRequest, ModelConfigResponse } from '@/api/ai/model'
import { DICT_TYPE } from '@/constants/dict'
import { useDict } from '@/composables/useDict'

defineOptions({ name: 'ModelConfigForm' })

const { dictData } = useDict(DICT_TYPE.AI_MODEL_TYPE)

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const formLoading = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const currentId = ref<number>()
const apiKeyMask = ref('')

const formData = reactive<AddModelRequest>({
  modelName: '',
  modelCode: '',
  provider: '',
  modelType: '',
  apiEndpoint: '',
  apiKey: '',
  maxTokens: 4096,
  temperature: 0.7,
  thinkingDisabled: 0,
  remark: ''
})

const formRules: FormRules = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  modelCode: [{ required: true, message: '请输入模型代码', trigger: 'blur' }],
  provider: [{ required: true, message: '请输入提供商', trigger: 'blur' }],
  modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }]
}

const open = (type: 'create' | 'update', row?: ModelConfigResponse) => {
  resetForm()
  if (type === 'create') {
    dialogTitle.value = '新增模型配置'
    isEdit.value = false
  } else if (row) {
    dialogTitle.value = '编辑模型配置'
    isEdit.value = true
    currentId.value = row.id
    formLoading.value = true
    try {
      formData.modelName = row.modelName
      formData.modelCode = row.modelCode
      formData.provider = row.provider
      formData.modelType = row.modelType || ''
      formData.apiEndpoint = row.apiEndpoint || ''
      // 编辑态不回显 key（接口已脱敏为尾4位），留空提交表示不修改
      formData.apiKey = ''
      apiKeyMask.value = row.apiKey || ''
      formData.maxTokens = row.maxTokens
      formData.temperature = row.temperature
      formData.thinkingDisabled = row.thinkingDisabled ?? 0
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
      const data: ModelConfigRequest = {
        modelName: formData.modelName,
        modelCode: formData.modelCode,
        provider: formData.provider,
        modelType: formData.modelType,
        apiEndpoint: formData.apiEndpoint,
        apiKey: formData.apiKey?.trim() ? formData.apiKey.trim() : undefined,
        maxTokens: formData.maxTokens,
        temperature: formData.temperature,
        thinkingDisabled: formData.thinkingDisabled
      }
      await configModel(currentId.value!, data)
      ElMessage.success('更新成功')
    } else {
      await addModel(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    submitLoading.value = false
  }
}

const resetForm = () => {
  currentId.value = undefined
  apiKeyMask.value = ''
  Object.assign(formData, {
    modelName: '',
    modelCode: '',
    provider: '',
    modelType: '',
    apiEndpoint: '',
    apiKey: '',
    maxTokens: 4096,
    temperature: 0.7,
    thinkingDisabled: 0,
    remark: ''
  })
  formRef.value?.resetFields()
}

defineExpose({ open })
</script>

<style scoped lang="scss">
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
