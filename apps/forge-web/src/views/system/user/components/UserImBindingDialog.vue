<template>
  <el-dialog
    :model-value="modelValue"
    :title="`企业IM绑定 - ${user?.nickname || user?.username || ''}`"
    width="520px"
    @update:model-value="(val: boolean) => emit('update:modelValue', val)"
    @open="handleOpen"
  >
    <el-alert
      title="审批待办与流程结果将推送到企业微信/钉钉工作通知；钉钉用户若已扫码绑定，自动按 unionId 关联，此处可直接留空"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />
    <el-form v-loading="fetching" label-width="130px">
      <el-form-item label="企业微信 userid">
        <el-input
          v-model="form.weworkUserid"
          placeholder="企微管理后台通讯录中的账号（留空=解绑）"
          clearable
          maxlength="64"
        />
      </el-form-item>
      <el-form-item label="钉钉 userid">
        <el-input
          v-model="form.dingtalkUserid"
          placeholder="钉钉后台通讯录中的 userid（留空=解绑）"
          clearable
          maxlength="64"
        />
        <div v-if="dingtalkUnionIdPresent" class="bind-tip">该用户已有钉钉扫码绑定，未填写时自动通过 unionId 关联</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <div style="display: flex; gap: 12px">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleConfirm">保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserCorpBinding, saveUserCorpBinding } from '@/api/system'
import type { User } from '@/types/system'

const props = defineProps<{
  modelValue: boolean
  user: User | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'saved'): void
}>()

const fetching = ref(false)
const submitLoading = ref(false)
const dingtalkUnionIdPresent = ref(false)
const form = reactive({
  weworkUserid: '',
  dingtalkUserid: ''
})

async function handleOpen() {
  if (!props.user?.id) return
  fetching.value = true
  try {
    const binding = await getUserCorpBinding(props.user.id)
    form.weworkUserid = binding.weworkUserid || ''
    form.dingtalkUserid = binding.dingtalkUserid || ''
    dingtalkUnionIdPresent.value = !!binding.dingtalkUnionIdPresent
  } finally {
    fetching.value = false
  }
}

async function handleConfirm() {
  if (!props.user?.id) return
  if (form.weworkUserid.length > 64 || form.dingtalkUserid.length > 64) {
    ElMessage.warning('userid 长度不能超过 64 个字符')
    return
  }
  submitLoading.value = true
  try {
    await saveUserCorpBinding(props.user.id, {
      weworkUserid: form.weworkUserid.trim() || undefined,
      dingtalkUserid: form.dingtalkUserid.trim() || undefined
    })
    ElMessage.success('保存成功')
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    submitLoading.value = false
  }
}
</script>

<style scoped>
.bind-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
</style>
