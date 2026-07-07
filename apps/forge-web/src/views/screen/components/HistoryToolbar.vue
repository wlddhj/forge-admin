<script setup lang="ts">
import { ref } from 'vue'
import { useScreenEditorStore } from '@/stores/screenEditor'
import { ElMessage } from 'element-plus'
import { updateScreen, publishScreen } from '@/api/screen'

defineEmits<{ 'preview': [] }>()

const store = useScreenEditorStore()
const saving = ref(false)
const publishing = ref(false)

const handleSave = async () => {
  if (!store.screenId) { ElMessage.warning('无 screenId'); return }
  saving.value = true
  try {
    await updateScreen({
      id: store.screenId,
      code: store.screenCode,
      name: ''
    })
    store.markClean()
    ElMessage.success('已暂存')
  } finally { saving.value = false }
}

const handlePublish = async () => {
  if (!store.screenCode) return
  publishing.value = true
  try {
    await publishScreen(store.screenCode)
    ElMessage.success('发布成功')
    store.markClean()
  } finally { publishing.value = false }
}
</script>

<template>
  <div class="history-toolbar">
    <el-button-group>
      <el-button :disabled="!store.canUndo" @click="store.undo()">
        <el-icon><RefreshLeft /></el-icon> 撤销
      </el-button>
      <el-button :disabled="!store.canRedo" @click="store.redo()">
        <el-icon><RefreshRight /></el-icon> 重做
      </el-button>
    </el-button-group>
    <el-divider direction="vertical" />
    <el-button :loading="saving" :disabled="!store.isDirty" @click="handleSave">保存草稿</el-button>
    <el-button @click="$emit('preview')">预览</el-button>
    <el-button type="primary" :loading="publishing" @click="handlePublish">发布</el-button>
  </div>
</template>

<style scoped>
.history-toolbar { display: flex; align-items: center; gap: 8px; padding: 8px 16px; background: rgba(8,22,40,0.95); border-bottom: 1px solid #1e3a5f; }
</style>
