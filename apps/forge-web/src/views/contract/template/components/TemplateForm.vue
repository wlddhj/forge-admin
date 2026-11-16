<template>
  <el-dialog
    v-model="visible"
    :title="form.id ? '修改模板' : '新建模板'"
    width="520px"
    destroy-on-close
    append-to-body
    @closed="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="模板名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入模板名称" maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item label="合同分类" prop="categoryCode">
        <el-select v-model="form.categoryCode" placeholder="请选择分类" style="width: 100%">
          <el-option v-for="d in categoryOptions" :key="d.dictValue" :label="d.dictLabel" :value="d.dictValue" />
        </el-select>
      </el-form-item>
      <el-form-item label="纸张大小" prop="paperSize">
        <el-select v-model="form.paperSize" style="width: 160px">
          <el-option label="A4" value="A4" />
          <el-option label="A5" value="A5" />
          <el-option label="Letter" value="LETTER" />
          <el-option label="Legal" value="LEGAL" />
          <el-option label="自定义" value="CUSTOM" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.paperSize === 'CUSTOM'" label="宽×高 mm">
        <el-input-number v-model="form.customWidthMm" :min="50" :max="600" :precision="1" controls-position="right" style="width: 120px" />
        <span style="margin: 0 6px">×</span>
        <el-input-number v-model="form.customHeightMm" :min="50" :max="900" :precision="1" controls-position="right" style="width: 120px" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { templateApi } from '@/api/contract/template'
import { useDict } from '@/composables/useDict'
import type { TemplateForm, TemplateItem } from '@/types/contract'

defineOptions({ name: 'TemplateForm' })

const DICT_CONTRACT_CATEGORY = 'contract_category'
const emit = defineEmits<{ success: [] }>()
const visible = defineModel<boolean>({ default: false })

const { dictData: categoryOptions } = useDict(DICT_CONTRACT_CATEGORY)
const formRef = ref<FormInstance | null>(null)
const saving = ref(false)

const form = reactive<TemplateForm>({
  id: undefined,
  categoryCode: '',
  name: '',
  paperSize: 'A4',
  customWidthMm: undefined,
  customHeightMm: undefined,
  remark: ''
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  categoryCode: [{ required: true, message: '请选择分类', trigger: 'change' }],
  paperSize: [{ required: true, message: '请选择纸张', trigger: 'change' }]
}

/** 打开（传模板=编辑回填，不传=新建） */
const open = (template?: TemplateItem | null) => {
  if (template) {
    Object.assign(form, {
      id: template.id,
      categoryCode: template.categoryCode,
      name: template.name,
      paperSize: template.paperSize,
      customWidthMm: template.customWidthMm ?? undefined,
      customHeightMm: template.customHeightMm ?? undefined,
      remark: template.remark ?? ''
    })
  }
  visible.value = true
}
defineExpose({ open })

const resetForm = () => {
  Object.assign(form, {
    id: undefined,
    categoryCode: '',
    name: '',
    paperSize: 'A4',
    customWidthMm: undefined,
    customHeightMm: undefined,
    remark: ''
  })
  formRef.value?.clearValidate()
}

const confirm = async () => {
  await formRef.value?.validate()
  if (form.paperSize === 'CUSTOM' && (!form.customWidthMm || !form.customHeightMm)) {
    ElMessage.warning('自定义纸张须填写宽高（mm）')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await templateApi.update(form as TemplateForm & { id: string })
      ElMessage.success('模板已更新')
    } else {
      await templateApi.create(form)
      ElMessage.success('模板已创建，请编辑内容并保存版本')
    }
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}
</script>
