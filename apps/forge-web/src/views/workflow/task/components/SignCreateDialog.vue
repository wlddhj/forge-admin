<template>
  <el-dialog
    v-model="visible"
    title="加签"
    width="520px"
    :close-on-click-modal="false"
    @closed="resetForm"
  >
    <el-form label-width="90px">
      <el-form-item label="加签方式" required>
        <el-radio-group v-model="type">
          <el-radio value="before">前加签</el-radio>
          <el-radio value="after">后加签</el-radio>
          <el-radio value="parallel">并行加签</el-radio>
        </el-radio-group>
        <div class="type-tip">{{ typeTip }}</div>
      </el-form-item>
      <el-form-item label="加签人员" required>
        <el-select
          v-model="userIds"
          multiple
          filterable
          remote
          clearable
          reserve-keyword
          placeholder="请输入用户名搜索"
          :remote-method="searchUsers"
          :loading="userSearchLoading"
          style="width: 100%"
        >
          <el-option
            v-for="user in userOptions"
            :key="user.id"
            :label="`${user.nickname}（${user.username}）`"
            :value="String(user.id)"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="type !== 'parallel'" label="审批方式">
        <el-radio-group v-model="examineMode">
          <el-radio :value="1">依次审批</el-radio>
          <el-radio :value="2">会签</el-radio>
          <el-radio :value="3">或签</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="加签原因">
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
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { taskApi } from '@/api/workflow/task'
import { getUserList } from '@/api/system'

const visible = defineModel<boolean>({ default: false })
const props = defineProps<{
  taskId: string
}>()
const emit = defineEmits<{ success: [] }>()

const type = ref<'before' | 'after' | 'parallel'>('parallel')
const userIds = ref<string[]>([])
const examineMode = ref(2)
const reason = ref('')
const submitting = ref(false)

interface UserOption {
  id: number
  username: string
  nickname: string
}

const userOptions = ref<UserOption[]>([])
const userSearchLoading = ref(false)

const typeTip = computed(() => {
  if (type.value === 'before') return '先由加签人审批，完成后再回到当前任务'
  if (type.value === 'after') return '当前任务审批通过后，先由加签人审批再进入下一节点'
  return '在当前节点追加参与人，共同处理本任务'
})

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
  userIds.value = []
  reason.value = ''
}

const handleSubmit = async () => {
  if (!userIds.value.length) {
    ElMessage.warning('请选择加签人员')
    return
  }
  submitting.value = true
  try {
    await taskApi.signCreate(props.taskId, {
      taskId: props.taskId,
      type: type.value,
      userIds: userIds.value,
      examineMode: type.value === 'parallel' ? undefined : examineMode.value,
      reason: reason.value || undefined
    })
    ElMessage.success('加签成功')
    visible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.type-tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
</style>
