<script setup lang="ts">
import { ref } from 'vue'
import { presetTemplates } from '@/views/screen/templates'
import { useScreenEditorStore } from '@/stores/screenEditor'

const emit = defineEmits<{ 'select': [code: string] }>()
const store = useScreenEditorStore()

const dialogVisible = ref(false)
const selected = ref('blank')

const handleSelect = () => {
  const tpl = presetTemplates.find(t => t.code === selected.value)!
  store.config = JSON.parse(JSON.stringify(tpl.config))
  store.reset()
  store.isDirty = true
  dialogVisible.value = false
  emit('select', selected.value)
}

const open = () => { dialogVisible.value = true }

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="dialogVisible" title="选择模板" width="640px">
    <el-radio-group v-model="selected" class="template-grid">
      <el-radio-button v-for="t in presetTemplates" :key="t.code" :value="t.code" class="template-item">
        <div class="template-card">
          <div class="template-name">{{ t.name }}</div>
          <div class="template-desc">{{ t.description }}</div>
          <div class="template-meta">{{ t.config.cards.length }} 张卡片</div>
        </div>
      </el-radio-button>
    </el-radio-group>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSelect">使用此模板</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.template-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; width: 100%; }
.template-card { padding: 12px; text-align: left; }
.template-name { font-weight: 600; }
.template-desc { font-size: 12px; color: #909399; margin-top: 4px; }
.template-meta { font-size: 11px; color: #1e88e5; margin-top: 4px; }
</style>
