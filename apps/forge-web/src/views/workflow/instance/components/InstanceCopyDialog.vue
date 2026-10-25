<template>
  <el-dialog
    v-model="visible"
    title="手动抄送"
    width="480px"
    :close-on-click-modal="false"
    @closed="resetForm"
  >
    <el-form label-width="80px">
      <el-form-item label="抄送给" required>
        <el-select
          v-model="copyUserIds"
          multiple
          filterable
          remote
          clearable
          reserve-keyword
          placeholder="输入用户名搜索"
          :remote-method="searchUsers"
          :loading="userSearchLoading"
          style="width: 100%"
        >
          <el-option
            v-for="user in userOptions"
            :key="user.id"
            :label="`${user.nickname}（${user.username}）`"
            :value="user.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="抄送原因">
        <el-input
          v-model="reason"
          type="textarea"
          :rows="2"
          maxlength="200"
          placeholder="选填"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { processInstanceApi } from '@/api/workflow/process-instance'
import { getUserList } from '@/api/system'

const visible = defineModel<boolean>({ default: false })
const props = defineProps<{
  instanceId: string
}>()
const emit = defineEmits<{ success: [] }>()

const copyUserIds = ref<number[]>([])
const reason = ref('')
const submitting = ref(false)

interface UserOption {
  id: number
  username: string
  nickname: string
}

const userOptions = ref<UserOption[]>([])
const userSearchLoading = ref(false)

const searchUsers = async (query: string) => {
  if (!query) return
  userSearchLoading.value = true
  try {
    const res = await getUserList({ pageNum: 1, pageSize: 20, username: query })
    userOptions.value = res.list
  } finally {
    userSearchLoading.value = false
  }
}

const resetForm = () => {
  copyUserIds.value = []
  reason.value = ''
}

const handleSubmit = async () => {
  if (!copyUserIds.value.length) {
    ElMessage.warning('请选择抄送用户')
    return
  }
  submitting.value = true
  try {
    await processInstanceApi.copy(props.instanceId, {
      copyUserIds: copyUserIds.value,
      reason: reason.value || undefined
    })
    ElMessage.success('抄送成功')
    visible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}
</script>
