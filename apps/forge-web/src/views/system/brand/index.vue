<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <span>品牌设置</span>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" class="brand-form">
        <el-form-item label="Logo">
          <div class="logo-editor">
            <div class="logo-preview">
              <img :src="form.logo || '/logo.svg'" alt="logo 预览" />
            </div>
            <div class="logo-actions">
              <el-upload
                :action="uploadUrl"
                :headers="uploadHeaders"
                :show-file-list="false"
                accept="image/*"
                :before-upload="beforeUpload"
                :on-success="handleUploadSuccess"
                :on-error="handleUploadError"
              >
                <el-button v-permission="'system:brand:update'" type="primary">上传 Logo</el-button>
              </el-upload>
              <el-button v-if="form.logo" @click="form.logo = ''">恢复默认</el-button>
              <div class="logo-tip">支持 png/jpg/jpeg/webp，不超过 2MB；留空使用系统默认 Logo</div>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="项目名称" prop="name">
          <el-input
            v-model="form.name"
            maxlength="50"
            show-word-limit
            placeholder="侧边栏、顶栏与浏览器标签页展示的名称"
          />
        </el-form-item>

        <el-form-item label="登录页主标题" prop="loginTitle">
          <el-input v-model="form.loginTitle" maxlength="50" show-word-limit placeholder="登录页大标题" />
        </el-form-item>

        <el-form-item label="登录页副标题" prop="loginSubtitle">
          <el-input v-model="form.loginSubtitle" maxlength="100" show-word-limit placeholder="登录页副标题" />
        </el-form-item>

        <el-form-item>
          <el-button v-permission="'system:brand:update'" type="primary" :loading="saving" @click="handleSave">
            保存
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { brandApi, type BrandConfig } from '@/api/system/brand'
import { useBrandStore } from '@/stores/brand'
import { useUserStore } from '@/stores/user'

const brandStore = useBrandStore()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive<BrandConfig>({
  logo: '',
  name: '',
  loginTitle: '',
  loginSubtitle: ''
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
}

const uploadUrl = computed(() => import.meta.env.VITE_API_BASE_URL + '/system/brand/logo')
const uploadHeaders = computed(() => ({ Authorization: `Bearer ${userStore.token}` }))

onMounted(async () => {
  try {
    const data = await brandApi.getBrand()
    if (data) Object.assign(form, data)
  } catch (error) {
    console.error('加载品牌配置失败', error)
  }
})

const ALLOWED_TYPES = ['image/png', 'image/jpeg', 'image/webp']

const beforeUpload = (file: File) => {
  if (!ALLOWED_TYPES.includes(file.type)) {
    ElMessage.error('仅支持 png/jpg/jpeg/webp 格式的图片')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('Logo 图片大小不能超过 2MB')
    return false
  }
  return true
}

const handleUploadSuccess = (response: { code: number; message: string; data: string }) => {
  if (response.code === 200) {
    form.logo = response.data
    ElMessage.success('Logo 上传成功，保存后生效')
  } else {
    ElMessage.error(response.message || 'Logo 上传失败')
  }
}

const handleUploadError = () => {
  ElMessage.error('Logo 上传失败，请重试')
}

const handleSave = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await brandApi.updateBrand({ ...form })
    ElMessage.success('保存成功')
    brandStore.loadBrand()
  } catch (error) {
    console.error('保存品牌配置失败', error)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.brand-form {
  max-width: 560px;
}

.logo-editor {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.logo-preview {
  width: 80px;
  height: 80px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--el-fill-color-light);
  flex-shrink: 0;

  img {
    max-width: 64px;
    max-height: 64px;
    object-fit: contain;
  }
}

.logo-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.logo-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
