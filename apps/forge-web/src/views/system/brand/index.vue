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

        <el-divider content-position="left">默认主题</el-divider>
        <p class="theme-section-desc">新用户或清除本地缓存的用户首次进入系统时使用的初始主题；用户自行修改后将始终尊重用户选择</p>

        <el-form-item label="主题套餐">
          <div class="theme-preset-grid">
            <div
              v-for="preset in PRESETS"
              :key="preset.id"
              class="theme-preset-card"
              :class="{ active: isPresetActive(preset) }"
              @click="applyPreset(preset)"
            >
              <span class="theme-preset-dot" :data-palette="preset.palette"></span>
              <span class="theme-preset-name">{{ preset.name }}</span>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="调色板">
          <el-segmented v-model="form.defaultTheme.palette" :options="paletteOptions" />
        </el-form-item>

        <el-form-item label="布局">
          <el-segmented v-model="form.defaultTheme.layout" :options="layoutOptions" />
        </el-form-item>

        <el-form-item label="风格">
          <el-segmented v-model="form.defaultTheme.style" :options="styleOptions" />
        </el-form-item>

        <el-form-item label="明暗模式">
          <el-radio-group v-model="form.defaultTheme.mode">
            <el-radio value="light">明亮</el-radio>
            <el-radio value="dark">暗黑</el-radio>
            <el-radio value="auto">跟随浏览器</el-radio>
          </el-radio-group>
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
import { brandApi, type BrandConfig, type BrandThemeConfig } from '@/api/system/brand'
import { useBrandStore } from '@/stores/brand'
import { useUserStore } from '@/stores/user'
import { PRESETS, DEFAULT_THEME, type Preset } from '@/themes'

const brandStore = useBrandStore()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const saving = ref(false)

// 表单中 defaultTheme 恒有值（后端缺失字段由 DEFAULT_THEME 兜底）
type BrandFormData = Omit<BrandConfig, 'defaultTheme'> & { defaultTheme: BrandThemeConfig }

const form = reactive<BrandFormData>({
  logo: '',
  name: '',
  loginTitle: '',
  loginSubtitle: '',
  defaultTheme: { ...DEFAULT_THEME }
})

// 默认主题选项（palette 不含 custom：系统默认无自定义主色入口）
const paletteOptions = [
  { label: '蓝', value: 'blue' },
  { label: '紫', value: 'purple' },
  { label: '绿', value: 'green' },
  { label: '红', value: 'crimson' },
  { label: '橙', value: 'orange' },
  { label: '青', value: 'cyan' },
  { label: '碧', value: 'teal' }
]
const layoutOptions = [
  { label: '侧栏', value: 'sidebar' },
  { label: '顶栏', value: 'top' }
]
const styleOptions = [
  { label: '扁平', value: 'flat' },
  { label: '玻璃', value: 'glass' },
  { label: '卡片', value: 'card' },
  { label: '紧凑', value: 'compact' }
]

const isPresetActive = (preset: Preset) =>
  form.defaultTheme?.palette === preset.palette
  && form.defaultTheme?.layout === preset.layout
  && form.defaultTheme?.style === preset.style

const applyPreset = (preset: Preset) => {
  if (!form.defaultTheme) form.defaultTheme = { ...DEFAULT_THEME }
  form.defaultTheme.palette = preset.palette
  form.defaultTheme.layout = preset.layout
  form.defaultTheme.style = preset.style
}

const rules: FormRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
}

const uploadUrl = computed(() => import.meta.env.VITE_API_BASE_URL + '/system/brand/logo')
const uploadHeaders = computed(() => ({ Authorization: `Bearer ${userStore.token}` }))

onMounted(async () => {
  try {
    const data = await brandApi.getBrand()
    if (data) {
      const { defaultTheme, ...rest } = data
      Object.assign(form, rest)
      form.defaultTheme = { ...DEFAULT_THEME, ...defaultTheme } as BrandThemeConfig
    }
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

  .theme-section-desc {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.5;
    margin: 0 0 16px 0;
  }

  .theme-preset-grid {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 8px;
    width: 100%;
  }

  .theme-preset-card {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    padding: 8px 4px;
    border: 2px solid var(--el-border-color-lighter);
    border-radius: var(--el-border-radius-base);
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: var(--app-color-primary);
    }

    &.active {
      border-color: var(--app-color-primary);
      background: var(--el-color-primary-light-9);
    }
  }

  .theme-preset-dot {
    width: 20px;
    height: 20px;
    border-radius: 50%;

    &[data-palette='blue']    { background: #409EFF; }
    &[data-palette='purple']  { background: #722ed1; }
    &[data-palette='green']   { background: #52c41a; }
    &[data-palette='crimson'] { background: #f5222d; }
    &[data-palette='orange']  { background: #fa8c16; }
    &[data-palette='cyan']    { background: #13c2c2; }
    &[data-palette='teal']    { background: #0d9488; }
  }

  .theme-preset-name {
    font-size: 12px;
    color: var(--el-text-color-primary);
  }
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
