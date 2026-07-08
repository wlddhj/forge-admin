<script setup lang="ts">
import { ref, computed } from 'vue'
import { useScreenEditorStore } from '@/stores/screenEditor'
import { ElMessage } from 'element-plus'
import { updateScreen, publishScreen } from '@/api/screen'
import { applyScreenTheme } from '@/themes/screen'
import { SCREEN_THEMES } from '@/constants/screen'
import type { ScreenConfig } from '@/types/screen'

const emit = defineEmits<{ 'preview': [] }>()

const store = useScreenEditorStore()
const saving = ref(false)
const publishing = ref(false)

const canPreview = computed(() => Boolean(store.screenId && store.screenCode))
const canPublish = computed(() => Boolean(store.screenId && store.screenCode))
const canSave = computed(() => Boolean(store.screenId))

const handleThemeChange = (theme: string) => {
  store.applyChange(d => { d.theme = theme as ScreenConfig['theme'] })
  applyScreenTheme(theme as ScreenConfig['theme'])
}

const handleSave = async () => {
  if (!store.screenId) { ElMessage.warning('无 screenId'); return }
  saving.value = true
  try {
    await updateScreen({
      id: store.screenId,
      code: store.screenCode,
      name: store.name || '未命名大屏',
      theme: store.config.theme,
      description: store.config.description as string | undefined
    })
    store.markClean()
    ElMessage.success('已暂存')
  } catch (e) {
    console.error('保存草稿失败', e)
    ElMessage.error('保存草稿失败：' + (e instanceof Error ? e.message : String(e)))
  } finally { saving.value = false }
}

const handlePublish = async () => {
  if (!store.screenCode) { ElMessage.warning('请先打开一个大屏'); return }
  publishing.value = true
  try {
    await publishScreen(store.screenCode)
    ElMessage.success('发布成功')
    store.markClean()
  } catch (e) {
    console.error('发布失败', e)
    ElMessage.error('发布失败：' + (e instanceof Error ? e.message : String(e)))
  } finally { publishing.value = false }
}
</script>

<template>
  <div class="history-toolbar">
    <el-input
      v-model="store.name"
      placeholder="大屏名称"
      size="default"
      class="name-input"
      maxlength="128"
    />
    <el-divider direction="vertical" />
    <span class="theme-label">主题</span>
    <el-select
      :model-value="store.config.theme"
      size="default"
      class="theme-select"
      @update:model-value="handleThemeChange"
    >
      <el-option
        v-for="t in SCREEN_THEMES"
        :key="t.value"
        :label="t.label"
        :value="t.value"
      />
    </el-select>
    <el-divider direction="vertical" />
    <el-button-group>
      <el-button :disabled="!store.canUndo" @click="store.undo()">
        <el-icon><RefreshLeft /></el-icon> 撤销
      </el-button>
      <el-button :disabled="!store.canRedo" @click="store.redo()">
        <el-icon><RefreshRight /></el-icon> 重做
      </el-button>
    </el-button-group>
    <el-divider direction="vertical" />
    <el-button :loading="saving" :disabled="!canSave" @click="handleSave">保存草稿</el-button>
    <el-button :disabled="!canPreview" @click="emit('preview')">预览</el-button>
    <el-button type="primary" :loading="publishing" :disabled="!canPublish" @click="handlePublish">发布</el-button>
  </div>
</template>

<style scoped>
.history-toolbar { display: flex; align-items: center; gap: 8px; padding: 8px 16px; background: rgba(8,22,40,0.95); border-bottom: 1px solid #1e3a5f; color: #e0e6f1; }
.name-input { width: 200px; }
.theme-label { color: #8a96a8; font-size: 12px; }
.theme-select { width: 130px; }
</style>
