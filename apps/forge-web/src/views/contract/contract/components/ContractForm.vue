<template>
  <el-dialog
    v-model="visible"
    :title="contract ? '编辑合同' : '新建合同'"
    width="640px"
    destroy-on-close
    append-to-body
    @opened="onOpened"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item v-if="!contract" label="合同模板" prop="templateId">
        <el-select v-model="form.templateId" placeholder="请选择模板（启用中）" style="width: 100%"
          @change="onTemplateChange">
          <el-option v-for="t in templates" :key="t.id" :value="t.id"
            :label="`${t.name}（${t.categoryName ?? t.categoryCode ?? ''}）`" />
        </el-select>
      </el-form-item>
      <el-form-item v-else label="合同模板">
        <el-input :model-value="`${templateName} · v${versionNo}`" disabled />
      </el-form-item>
      <el-form-item label="合同标题" prop="title">
        <el-input v-model="form.title" placeholder="请输入合同标题" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="合同编号">
        <el-input v-model="form.contractNo" placeholder="选填，如 CT-LD-20261116-0001" maxlength="64">
          <template #append>
            <el-tooltip content="留空则审批通过后按 CT-分类码-日期-序列 自动生成" placement="top">
              <el-icon><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填，管理性信息，不参与合同内容"
          maxlength="500" show-word-limit />
      </el-form-item>

      <el-divider content-position="left">合同变量</el-divider>
      <el-empty v-if="!fieldDefs.length" description="选择模板后此处按模板变量生成填写项" :image-size="60" />
      <template v-for="def in fieldDefs" :key="def.key">
        <el-form-item :label="def.label || def.key" :prop="'fieldValues.' + def.key"
          :rules="fieldRules(def)">
          <el-input v-if="def.type === 'text'" v-model="form.fieldValues[def.key]"
            :placeholder="`请输入${def.label || def.key}`" maxlength="200" />
          <el-input v-else-if="def.type === 'textarea'" v-model="form.fieldValues[def.key]" type="textarea" :rows="3"
            :placeholder="`请输入${def.label || def.key}`" />
          <el-input-number v-else-if="def.type === 'number'" :model-value="numberModel(def.key)"
            controls-position="right" style="width: 200px"
            @update:model-value="v => setFieldValue(def.key, v)" />
          <el-date-picker v-else-if="def.type === 'date'" :model-value="dateModel(def.key)"
            type="date" value-format="YYYY-MM-DD" style="width: 200px"
            @update:model-value="v => setFieldValue(def.key, v)" />
          <el-select v-else-if="def.type === 'select'" v-model="form.fieldValues[def.key]"
            :placeholder="`请选择${def.label || def.key}`" clearable style="width: 220px">
            <el-option v-for="opt in def.options || []" :key="opt" :label="opt" :value="opt" />
          </el-select>
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button v-permission="'contract:contract:preview'" :disabled="!currentVersionId" :loading="previewing"
        @click="doPreviewDraft">
        预览 PDF
      </el-button>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="confirm">保存草稿</el-button>
    </template>
  </el-dialog>

  <!-- 临时预览弹窗（未保存也可看） -->
  <el-dialog v-model="previewVisible" :title="`PDF 预览：${form.title || '未命名合同'}`" fullscreen
    append-to-body destroy-on-close class="pdf-viewer-dialog">
    <iframe :src="previewUrl" class="pdf-viewer-frame" title="合同预览" />
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { contractApi } from '@/api/contract/contract'
import { templateApi } from '@/api/contract/template'
import type { ContractItem, FieldDef, TemplateBrief } from '@/types/contract'

defineOptions({ name: 'ContractForm' })

const emit = defineEmits<{ success: [] }>()
const visible = defineModel<boolean>({ default: false })
const props = defineProps<{ contract: ContractItem | null }>()

const formRef = ref<FormInstance | null>(null)
const saving = ref(false)
const templates = ref<TemplateBrief[]>([])
const fieldDefs = ref<FieldDef[]>([])
/** 当前锁定的模板版本（预览/渲染基准） */
const currentVersionId = ref<string | null>(null)
const previewing = ref(false)
const previewVisible = ref(false)
const previewUrl = ref('')
let previewBlobUrl = ''
const templateName = ref('')
const versionNo = ref<number | null>(null)

const form = reactive({
  templateId: '',
  title: '',
  contractNo: '',
  remark: '',
  fieldValues: {} as Record<string, string | number | undefined>
})

const rules: FormRules = {
  templateId: [{ required: true, message: '请选择合同模板', trigger: 'change' }],
  title: [{ required: true, message: '请输入合同标题', trigger: 'blur' }]
}

const onOpened = async () => {
  templates.value = await templateApi.listEnabled()
  if (props.contract) {
    // 编辑：模板已锁定，回填标题/编号与字段值，字段定义取锁定版本
    form.templateId = props.contract.templateId
    currentVersionId.value = props.contract.templateVersionId
    form.title = props.contract.title
    form.contractNo = props.contract.contractNo ?? ''
    form.remark = props.contract.remark ?? ''
    form.fieldValues = { ...(props.contract.fieldValues ?? {}) } as Record<string, string | number | undefined>
    templateName.value = props.contract.templateName ?? ''
    versionNo.value = props.contract.templateVersionNo ?? null
    const version = await templateApi.getVersion(props.contract.templateVersionId)
    fieldDefs.value = version.fieldDefs ?? []
  }
}

const onTemplateChange = async (templateId: string) => {
  const tpl = templates.value.find(t => t.id === templateId)
  form.fieldValues = {}
  if (!tpl || !tpl.currentVersionId) {
    fieldDefs.value = []
    return
  }
  if (!form.title) {
    form.title = tpl.name
  }
  const version = await templateApi.getVersion(tpl.currentVersionId)
  fieldDefs.value = version.fieldDefs ?? []
  currentVersionId.value = tpl.currentVersionId
}

const fieldRules = (def: FieldDef) => {
  if (!def.required) return []
  if (def.type === 'number') {
    return [{ required: true, message: `请输入${def.label || def.key}`, trigger: 'blur' }]
  }
  return [{ required: true, message: `请${def.type === 'select' || def.type === 'date' ? '选择' : '输入'}${def.label || def.key}`, trigger: def.type === 'select' || def.type === 'date' ? 'change' : 'blur' }]
}

/** number 控件的受控包装（值为空时置 undefined） */
const numberModel = (key: string) => {
  const v = form.fieldValues[key]
  return v === undefined || v === null || v === '' ? undefined : Number(v)
}
const dateModel = (key: string) => form.fieldValues[key] as string | undefined
const setFieldValue = (key: string, v: unknown) => {
  form.fieldValues[key] = v as string
}

/** 临时预览：按当前表单值渲染（未保存也可看，不落库） */
const doPreviewDraft = async () => {
  if (!currentVersionId.value) {
    ElMessage.warning('请先选择合同模板')
    return
  }
  previewing.value = true
  try {
    const blob = await contractApi.previewDraft({
      templateVersionId: currentVersionId.value,
      fieldValues: form.fieldValues as Record<string, unknown>
    })
    if (previewBlobUrl) URL.revokeObjectURL(previewBlobUrl)
    previewBlobUrl = URL.createObjectURL(blob)
    previewUrl.value = previewBlobUrl
    previewVisible.value = true
  } finally {
    previewing.value = false
  }
}

const confirm = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (props.contract) {
      await contractApi.update({
        id: props.contract.id,
        title: form.title,
        contractNo: form.contractNo.trim() || undefined,
        remark: form.remark.trim() || undefined,
        fieldValues: form.fieldValues as Record<string, unknown>
      })
      ElMessage.success('草稿已保存')
    } else {
      await contractApi.create({
        templateId: form.templateId,
        title: form.title,
        contractNo: form.contractNo.trim() || undefined,
        remark: form.remark.trim() || undefined,
        fieldValues: form.fieldValues as Record<string, unknown>
      })
      ElMessage.success('草稿已创建')
    }
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.pdf-viewer-frame {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
}
</style>
