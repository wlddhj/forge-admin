<template>
  <el-dialog
    v-model="visible"
    title="减签"
    width="480px"
    :close-on-click-modal="false"
    @open="loadChildTasks"
    @closed="resetForm"
  >
    <el-form label-width="90px">
      <el-form-item label="减签人员" required>
        <el-select
          v-model="actorId"
          filterable
          clearable
          placeholder="请选择当前节点的参与人"
          :loading="loadingTasks"
          style="width: 100%"
        >
          <el-option
            v-for="item in childTasks"
            :key="item.taskId + '-' + item.actorId"
            :label="item.actorName"
            :value="item.actorId"
          />
        </el-select>
        <div v-if="!loadingTasks && !childTasks.length" class="empty-tip">
          当前节点没有可减签的参与人
        </div>
      </el-form-item>
      <el-form-item label="减签原因">
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
      <el-button type="primary" :loading="submitting" :disabled="!actorId" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { taskApi } from '@/api/workflow/task'

const visible = defineModel<boolean>({ default: false })
const props = defineProps<{
  taskId: string
}>()
const emit = defineEmits<{ success: [] }>()

interface ChildTaskItem {
  taskId: string
  actorId: string
  actorName: string
  performType?: string
}

const childTasks = ref<ChildTaskItem[]>([])
const loadingTasks = ref(false)
const actorId = ref('')
const reason = ref('')
const submitting = ref(false)

const loadChildTasks = async () => {
  loadingTasks.value = true
  try {
    childTasks.value = (await taskApi.getChildTasks(props.taskId)) || []
  } finally {
    loadingTasks.value = false
  }
}

const resetForm = () => {
  actorId.value = ''
  reason.value = ''
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    await taskApi.signDelete(props.taskId, {
      actorId: actorId.value,
      reason: reason.value || undefined
    })
    ElMessage.success('减签成功')
    visible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.empty-tip {
  width: 100%;
  font-size: 12px;
  color: #909399;
}
</style>
