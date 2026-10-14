<template>
  <el-dialog :model-value="visible" :title="isEdit ? '编辑白名单' : '新增白名单'" width="520px"
    :close-on-click-modal="false" @update:model-value="handleVisibleChange">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="表名" prop="tableName">
        <el-input v-model="form.tableName" placeholder="如 ai_document（小写下划线）" />
      </el-form-item>
      <el-form-item label="表注释" prop="tableComment">
        <el-input v-model="form.tableComment" placeholder="展示给 Agent 的表说明，如：AI文档表" />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleVisibleChange(false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { sqlWhitelistApi, type SqlWhitelistItem } from '@/api/ai/sql-whitelist'

const props = defineProps<{ visible: boolean; row?: SqlWhitelistItem | null }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void; (e: 'success'): void }>()

const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive<SqlWhitelistItem>({
  tableName: '',
  tableComment: '',
  enabled: 1,
  remark: ''
})

const isEdit = computed(() => Boolean(props.row?.id))

const rules = {
  tableName: [
    { required: true, message: '请输入表名', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]*$/, message: '表名须为小写字母开头的下划线格式', trigger: 'blur' }
  ]
}

watch(() => props.visible, (visible) => {
  if (visible) {
    Object.assign(form, { tableName: '', tableComment: '', enabled: 1, remark: '' }, props.row ?? {})
  }
})

const handleVisibleChange = (v: boolean) => emit('update:visible', v)

const handleSave = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await sqlWhitelistApi.update({ ...form })
      ElMessage.success('保存成功')
    } else {
      await sqlWhitelistApi.add({ ...form })
      ElMessage.success('新增成功')
    }
    handleVisibleChange(false)
    emit('success')
  } finally {
    saving.value = false
  }
}
</script>
