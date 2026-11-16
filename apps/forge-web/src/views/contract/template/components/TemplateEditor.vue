<template>
  <el-drawer
    v-model="visible"
    :title="`编辑模板：${templateName}`"
    size="94%"
    destroy-on-close
    :close-on-click-modal="false"
    @opened="initEditor"
  >
    <div class="editor-layout">
      <!-- 主编辑区 -->
      <div class="editor-main">
        <!-- 工具栏：分组完整、单行不换行（窄屏横向滚动） -->
        <div class="editor-toolbar">
          <div class="toolbar-group">
            <el-button size="small" @click="editor?.chain().focus().undo().run()">撤销</el-button>
            <el-button size="small" @click="editor?.chain().focus().redo().run()">重做</el-button>
          </div>
          <div class="toolbar-group">
            <el-button size="small" :type="editor?.isActive('heading', { level: 1 }) ? 'primary' : ''"
              @click="editor?.chain().focus().toggleHeading({ level: 1 }).run()">
H1
</el-button>
            <el-button size="small" :type="editor?.isActive('heading', { level: 2 }) ? 'primary' : ''"
              @click="editor?.chain().focus().toggleHeading({ level: 2 }).run()">
H2
</el-button>
            <el-button size="small" :type="editor?.isActive('heading', { level: 3 }) ? 'primary' : ''"
              @click="editor?.chain().focus().toggleHeading({ level: 3 }).run()">
H3
</el-button>
            <el-button size="small" :type="editor?.isActive('paragraph') ? 'primary' : ''"
              @click="editor?.chain().focus().setParagraph().run()">
正文
</el-button>
          </div>
          <div class="toolbar-group">
            <el-button size="small" :type="editor?.isActive('bold') ? 'primary' : ''"
              @click="editor?.chain().focus().toggleBold().run()">
<b>加粗</b>
</el-button>
            <el-button size="small" :type="editor?.isActive('italic') ? 'primary' : ''"
              @click="editor?.chain().focus().toggleItalic().run()">
<i>斜体</i>
</el-button>
            <el-button size="small" :type="editor?.isActive('underline') ? 'primary' : ''"
              @click="editor?.chain().focus().toggleUnderline().run()">
<u>下划线</u>
</el-button>
          </div>
          <div class="toolbar-group">
            <el-button size="small" :type="editor?.isActive('bulletList') ? 'primary' : ''"
              @click="editor?.chain().focus().toggleBulletList().run()">
无序列表
</el-button>
            <el-button size="small" :type="editor?.isActive('orderedList') ? 'primary' : ''"
              @click="editor?.chain().focus().toggleOrderedList().run()">
有序列表
</el-button>
          </div>
          <div class="toolbar-group">
            <el-button size="small" @click="editor?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()">插入表格</el-button>
            <el-button size="small" @click="editor?.chain().focus().addRowAfter().run()">+行</el-button>
            <el-button size="small" @click="editor?.chain().focus().addColumnAfter().run()">+列</el-button>
            <el-button size="small" @click="editor?.chain().focus().deleteRow().run()">-行</el-button>
            <el-button size="small" @click="editor?.chain().focus().deleteColumn().run()">-列</el-button>
          </div>
          <div class="toolbar-group">
            <el-button size="small" @click="triggerImageUpload">插入图片</el-button>
            <input ref="imageInputRef" type="file" accept="image/*" hidden @change="handleImageUpload" />
          </div>
        </div>

        <!-- 纸张画布：灰底衬托居中白纸，内容自适应高度 -->
        <div class="editor-canvas">
          <div class="editor-paper" :style="{ width: paperWidthPx + 'px' }">
            <editor-content v-if="editor" :editor="editor" class="editor-content" />
          </div>
          <div class="paper-hint">编辑区宽度按纸张内容宽约束 · 分页以预览/打印 PDF 为准</div>
        </div>
      </div>

      <!-- 右侧面板 -->
      <div class="editor-side">
        <el-card shadow="never" class="side-card">
          <template #header>
            <div class="side-header">插入变量</div>
          </template>
          <div class="var-form">
            <el-input v-model="newVarKey" placeholder="标识，如 party_a" size="small" />
            <el-input v-model="newVarLabel" placeholder="名称，如 甲方名称" size="small" />
            <el-button type="primary" size="small" @click="addVariable">插入芯片</el-button>
          </div>
          <div class="var-tip">标识仅限字母数字下划线；芯片整只增删不可拆分，插入后可随文字移动</div>
        </el-card>

        <el-card shadow="never" class="side-card side-card-grow">
          <template #header>
            <div class="side-header">
              <span>变量字段属性（{{ fieldDefs.length }}）</span>
              <el-tag v-if="hasDefDiff" type="warning" size="small">有未同步改动</el-tag>
            </div>
          </template>
          <el-empty v-if="!fieldDefs.length" description="正文中插入变量芯片后，此处自动生成字段配置" :image-size="60" />
          <div v-else class="def-list">
            <div v-for="row in fieldDefs" :key="row.key" class="def-item">
              <div class="def-item-head">
                <el-tag size="small" type="info" class="def-key">{{ row.key }}</el-tag>
                <el-select v-model="row.type" size="small" style="width: 92px">
                  <el-option label="单行" value="text" />
                  <el-option label="多行" value="textarea" />
                  <el-option label="数字" value="number" />
                  <el-option label="日期" value="date" />
                  <el-option label="下拉" value="select" />
                </el-select>
                <el-checkbox v-model="row.required" label="必填" size="small" />
              </div>
              <div class="def-item-body">
                <el-input v-model="row.label" size="small" placeholder="显示名称" />
                <el-input v-if="row.type === 'select'" v-model="row.optionsText" size="small"
                  placeholder="下拉选项，逗号分隔：选项1,选项2" />
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <template #footer>
      <div class="editor-footer">
        <el-input v-model="changeLog" placeholder="版本变更说明（选填）" maxlength="500" class="footer-log" />
        <div class="footer-actions">
          <el-button @click="visible = false">取消</el-button>
          <el-button type="primary" :loading="saving" :disabled="!editor" @click="save">保存为新版本</el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>


<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { Editor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { Table, TableCell, TableHeader, TableRow } from '@tiptap/extension-table'
import Image from '@tiptap/extension-image'
import { ElMessage } from 'element-plus'
import { templateApi } from '@/api/contract/template'
import { uploadAttachment } from '@/api/system'
import { isValidFieldKey, VariableNode } from '../extensions/variable'
import type { FieldDef, PaperSizeCode, TemplateItem } from '@/types/contract'

defineOptions({ name: 'TemplateEditor' })

const props = defineProps<{
  template: TemplateItem | null
}>()
const emit = defineEmits<{ saved: [] }>()

const visible = defineModel<boolean>({ default: false })

const editor = shallowRef<Editor | null>(null)
const templateName = computed(() => props.template?.name ?? '')
const saving = ref(false)
const changeLog = ref('')
const newVarKey = ref('')
const newVarLabel = ref('')
const imageInputRef = ref<HTMLInputElement | null>(null)

/** 字段定义（含编辑态 optionsText 便捷字段） */
interface EditableFieldDef extends FieldDef {
  optionsText?: string
}
const fieldDefs = ref<EditableFieldDef[]>([])
/** 内容芯片集与字段定义是否失配（保存前提醒） */
const contentKeys = ref<Set<string>>(new Set())
const hasDefDiff = computed(() => {
  const defKeys = new Set(fieldDefs.value.map(d => d.key))
  if (defKeys.size !== contentKeys.value.size) return true
  for (const k of defKeys) if (!contentKeys.value.has(k)) return true
  return false
})

/** 纸张内容宽（mm→px @96dpi，减左右页边距 31.8mm×2） */
const paperWidthPx = computed(() => {
  const size = props.template?.paperSize ?? 'A4'
  const table: Record<PaperSizeCode, number> = { A4: 210, A5: 148, LETTER: 215.9, LEGAL: 215.9, CUSTOM: 0 }
  const widthMm = size === 'CUSTOM' ? Number(props.template?.customWidthMm ?? 210) : table[size]
  // 钳制最小 120px，防窄纸张出现负宽/零宽
  return Math.max(120, Math.round((widthMm - 31.8 * 2) * (96 / 25.4)))
})

const initEditor = async () => {
  const t = props.template
  if (!t || !t.currentVersionId) {
    // 无历史版本：空编辑器
    buildEditor('')
    syncDefsFromContent()
    return
  }
  const version = await templateApi.getVersion(t.currentVersionId)
  buildEditor(version.contentHtml ?? '')
  fieldDefs.value = (version.fieldDefs ?? []).map(d => ({ ...d, optionsText: (d.options ?? []).join(',') }))
  syncDefsFromContent()
}

const buildEditor = (content: string) => {
  editor.value?.destroy()
  editor.value = new Editor({
    content: content || '<h1>合同标题</h1><p>正文段落…</p>',
    extensions: [
      StarterKit,
      Table.configure({ resizable: false }),
      TableRow,
      TableHeader,
      TableCell,
      Image,
      VariableNode
    ],
    onUpdate: () => syncDefsFromContent()
  })
}

/** 从编辑器文档提取芯片 → 合并进字段定义（保留既有属性，新增项给默认值） */
const syncDefsFromContent = () => {
  const ed = editor.value
  if (!ed) return
  const chips: Array<{ fieldKey: string; label: string }> = []
  ed.state.doc.descendants(node => {
    if (node.type.name === 'variable' && node.attrs.fieldKey) {
      chips.push({ fieldKey: node.attrs.fieldKey, label: node.attrs.label || node.attrs.fieldKey })
    }
    return true
  })
  contentKeys.value = new Set(chips.map(c => c.fieldKey))
  const byKey = new Map(fieldDefs.value.map(d => [d.key, d]))
  fieldDefs.value = chips.map(chip => {
    const existing = byKey.get(chip.fieldKey)
    if (existing) return existing
    return { key: chip.fieldKey, label: chip.label, type: 'text' as const, required: false, optionsText: '' }
  })
}

const addVariable = () => {
  const key = newVarKey.value.trim()
  const label = newVarLabel.value.trim() || key
  if (!key) {
    ElMessage.warning('请填写变量标识')
    return
  }
  if (!isValidFieldKey(key)) {
    ElMessage.warning('变量标识仅限字母数字下划线')
    return
  }
  if (contentKeys.value.has(key)) {
    ElMessage.warning(`变量「${key}」已在正文中`)
    return
  }
  editor.value?.chain().focus().insertVariable({ fieldKey: key, label }).run()
  newVarKey.value = ''
  newVarLabel.value = ''
}

const triggerImageUpload = () => imageInputRef.value?.click()

const handleImageUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不超过 5MB')
    input.value = ''
    return
  }
  const attachment = await uploadAttachment(file, 'contract_template')
  editor.value?.chain().focus().setImage({ src: attachment.fileUrl }).run()
  input.value = ''
}

const save = async () => {
  const ed = editor.value
  const t = props.template
  if (!ed || !t) return
  if (!ed.getText().trim()) {
    ElMessage.warning('模板内容为空')
    return
  }
  if (hasDefDiff.value) {
    ElMessage.warning('变量字段属性与正文芯片不一致，保存前请先补全右侧面板')
    return
  }
  saving.value = true
  try {
    const defs: FieldDef[] = fieldDefs.value.map(d => ({
      key: d.key,
      label: d.label,
      type: d.type,
      required: !!d.required,
      options: d.type === 'select' && d.optionsText
        ? d.optionsText.split(/[,，]/).map(s => s.trim()).filter(Boolean)
        : undefined
    }))
    await templateApi.saveVersion(t.id, {
      contentHtml: ed.getHTML(),
      fieldDefs: defs,
      changeLog: changeLog.value || undefined
    })
    ElMessage.success('已保存为新版本')
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}

watch(visible, open => {
  if (!open) {
    editor.value?.destroy()
    editor.value = null
    changeLog.value = ''
  }
})
</script>

<style scoped>
/* 抽屉 body 撑满：布局高度不再 calc 硬编码（跟随 SettingsPanel 先例：非 append-to-body + :deep） */
:deep(.el-drawer__body) {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
:deep(.el-drawer__footer) {
  padding: 8px 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.editor-layout {
  display: flex;
  gap: 12px;
  flex: 1;
  min-height: 0;
}
.editor-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 工具栏：单行不换行，分组完整（窄屏横向滚动），分组间留白替代 divider */
.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 6px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-bg-color);
  overflow-x: auto;
  white-space: nowrap;
  flex-shrink: 0;
}
.toolbar-group {
  display: inline-flex;
  gap: 0;
}
.toolbar-group .el-button + .el-button {
  margin-left: 0;
}
.toolbar-group .el-button {
  border-radius: 0;
}
.toolbar-group .el-button:first-child {
  border-radius: 4px 0 0 4px;
}
.toolbar-group .el-button:last-child {
  border-radius: 0 4px 4px 0;
}
.toolbar-group .el-button + .el-button::before {
  content: '';
  position: absolute;
  left: -1px;
  top: 50%;
  transform: translateY(-50%);
  height: 55%;
  border-left: 1px solid var(--el-border-color);
  opacity: 0.6;
}

/* 纸张画布：灰底衬托 + 居中白纸（内容自适应，min-height 兜底） */
.editor-canvas {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  padding: 20px 16px 8px;
}
.editor-paper {
  margin: 0 auto;
  max-width: 100%;
  padding: 32px 36px;
  background: var(--el-bg-color);
  border-radius: 4px;
  box-shadow: 0 2px 10px rgb(0 0 0 / 8%);
  box-sizing: border-box;
}
.paper-hint {
  margin: 10px 0 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

/* 右侧面板：400px，两卡上下；属性行改两行卡片式（不再一行五件套） */
.editor-side {
  width: 400px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}
.side-card :deep(.el-card__header) {
  padding: 8px 12px;
  font-weight: 600;
}
.side-card :deep(.el-card__body) {
  padding: 10px 12px;
}
.side-card-grow {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.side-card-grow :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.side-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.var-form {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 8px;
}
.var-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  margin-top: 8px;
}

/* 字段属性行：头部（标识+类型+必填）一行，主体（名称+选项）一行 */
.def-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.def-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--el-bg-color);
}
.def-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.def-key {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: monospace;
}
.def-item-head .el-checkbox {
  margin-left: auto;
}
.def-item-body {
  display: flex;
  gap: 8px;
}
.def-item-body .el-input:first-child {
  flex: 0 0 132px;
}

/* footer：说明框自适应 + 按钮组右靠不缩 */
.editor-footer {
  display: flex;
  align-items: center;
  gap: 16px;
}
.footer-log {
  flex: 1;
  min-width: 200px;
  max-width: 520px;
}
.footer-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* 编辑器正文基础样式（与 PDF 基础样式对齐：宋体正文/黑体标题） */
.editor-content :deep(.tiptap) {
  outline: none;
  min-height: 200px;
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 14px;
  line-height: 1.7;
}
.editor-content :deep(.tiptap h1) { font-family: 'Noto Sans SC', 'PingFang SC', sans-serif; text-align: center; }
.editor-content :deep(.tiptap h2),
.editor-content :deep(.tiptap h3) { font-family: 'Noto Sans SC', 'PingFang SC', sans-serif; }
.editor-content :deep(.tiptap table) {
  border-collapse: collapse;
  width: 100%;
  margin: 6px 0;
}
.editor-content :deep(.tiptap th),
.editor-content :deep(.tiptap td) {
  border: 1px solid #333;
  padding: 4px 6px;
}
.editor-content :deep(.tiptap th) { background: #f2f2f2; }
.editor-content :deep(.tiptap p) { margin: 0 0 6pt; }

/* 变量芯片 */
.editor-content :deep(.ct-var) {
  display: inline-block;
  padding: 0 6px;
  margin: 0 2px;
  border-radius: 3px;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  border: 1px solid var(--el-color-primary-light-5);
  font-size: 13px;
  white-space: nowrap;
}
</style>
