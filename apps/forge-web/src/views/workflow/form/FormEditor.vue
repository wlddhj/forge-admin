<template>
  <div class="form-editor-page">
    <div class="editor-header">
      <div class="header-left">
        <el-button @click="handleGoBack">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
      </div>
      <span class="page-title">{{ isEdit ? '编辑表单' : '新增表单' }}</span>
      <div class="header-right">
        <el-button type="primary" @click="handleOpenSaveDialog">
          <el-icon><Check /></el-icon>
          保存
        </el-button>
      </div>
    </div>

    <div class="editor-body">
      <FcDesigner ref="designerRef" height="calc(100vh - 60px)" :fieldReadonly="false" :config="designerConfig" />
    </div>

    <!-- 保存对话框 -->
    <el-dialog v-model="saveDialogVisible" title="保存表单" width="500px" class="dialog-form-responsive">
      <el-form ref="formRef" :model="saveForm" :rules="formRules" label-width="80px">
        <el-form-item label="表单名称" prop="name">
          <el-input v-model="saveForm.name" placeholder="请输入表单名称" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="saveForm.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="saveForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saveLoading" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { formApi } from '@/api/workflow/form'
import { encodeConf, encodeFields, setConfAndFields } from '@/utils/formCreate'

const route = useRoute()
const router = useRouter()

const designerRef = ref<any>(null)
const saveDialogVisible = ref(false)
const saveLoading = ref(false)
const formRef = ref<FormInstance>()

const isEdit = computed(() => !!route.query.id)

// FcDesigner 配置，包含 AI 智能助理
// API-KEY 建议从环境变量或后端配置获取
const designerConfig = {
  showAi: true,  // 显示 AI 模块
  ai: {
    // AI 接口地址（可使用 OpenAI 或其他兼容接口）
    api: import.meta.env.VITE_AI_API_URL || '',
    // AI Token / API-KEY
    token: import.meta.env.VITE_AI_API_KEY || ''
  },
  fieldReadonly: false,
}

const saveForm = reactive({
  id: undefined as number | undefined,
  name: '',
  status: 0,
  remark: ''
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入表单名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

/** 返回列表页 */
const handleGoBack = () => {
  router.push('/workflow/form')
}

/** 打开保存对话框 */
const handleOpenSaveDialog = () => {
  saveDialogVisible.value = true
}

/** 保存表单 */
const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  saveLoading.value = true
  try {
    const data = {
      ...saveForm,
      conf: encodeConf(designerRef.value),
      fields: encodeFields(designerRef.value)
    }

    if (isEdit.value) {
      await formApi.update(data)
      ElMessage.success('更新成功')
    } else {
      await formApi.add(data)
      ElMessage.success('新增成功')
    }
    saveDialogVisible.value = false
    handleGoBack()
  } finally {
    saveLoading.value = false
  }
}

/** 初始化 */
onMounted(async () => {
  const id = route.query.id as unknown as number
  if (!id) return

  // 编辑模式：加载已有表单数据
  const data = await formApi.getById(id)
  saveForm.id = data.id
  saveForm.name = data.name
  saveForm.status = data.status
  saveForm.remark = data.remark

  // 等待 designer 渲染完成后设置配置
  setTimeout(() => {
    if (designerRef.value) {
      setConfAndFields(designerRef.value, data.conf, data.fields)
    }
  }, 100)
})
</script>

<style scoped lang="scss">
.form-editor-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f5f5;
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 50px;
  padding: 0 16px;
  background-color: #fff;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;

  .header-left,
  .header-right {
    width: 120px;
  }

  .header-right {
    display: flex;
    justify-content: flex-end;
  }

  .page-title {
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }
}

.editor-body {
  flex: 1;
  overflow: hidden;
}
</style>
