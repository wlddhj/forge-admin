<template>
  <div class="callback-container">
    <div class="callback-box">
      <el-icon class="loading-icon" :size="32"><Loading /></el-icon>
      <p v-if="status === 'processing'">正在处理登录...</p>
      <p v-else-if="status === 'binding'" class="bind-tip">
        该{{ sourceName }}账号未绑定系统账号，请登录您的账号进行绑定
      </p>
    </div>

    <!-- 未绑定时显示登录表单 -->
    <div v-if="status === 'binding'" class="bind-form">
      <el-form ref="formRef" :model="loginForm" :rules="loginRules" class="login-form">
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleBindLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="handleBindLogin"
          >
            登录并绑定
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { socialApi } from '@/api/social'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const status = ref<'processing' | 'binding' | 'success' | 'error'>('processing')
const loading = ref(false)
const formRef = ref<FormInstance>()
const tempToken = ref('')

const sourceNameMap: Record<string, string> = {
  wechat: '微信',
  dingtalk: '钉钉'
}
const sourceName = ref('')

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(async () => {
  // 从 URL 参数中获取 token
  const accessToken = route.query.accessToken as string
  const refreshToken = route.query.refreshToken as string
  const error = route.query.error as string
  const source = route.query.source as string
  const token = route.query.tempToken as string

  sourceName.value = sourceNameMap[source] || source || '第三方'

  if (accessToken && refreshToken) {
    // 已绑定，直接登录
    try {
      userStore.updateToken(accessToken)
      userStore.updateRefreshToken(refreshToken)
      ElMessage.success('登录成功')
      router.push('/dashboard')
    } catch {
      ElMessage.error('登录失败')
      router.push('/login')
    }
  } else if (error === 'social_not_bound' && token) {
    // 未绑定，显示绑定表单
    tempToken.value = token
    status.value = 'binding'
  } else {
    ElMessage.error('第三方登录失败')
    router.push('/login')
  }
})

const handleBindLogin = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        // 先用用户名密码登录
        await userStore.loginAction(loginForm)
        // 登录成功后绑定社交账号
        await socialApi.bind(tempToken.value)
        ElMessage.success('登录并绑定成功')
        router.push('/dashboard')
      } catch (error) {
        console.error('绑定登录失败', error)
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped lang="scss">
.callback-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e0e0e0 0%, #f5f5f5 50%, #ffffff 100%);
}

.callback-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;

  p {
    color: var(--el-text-color-regular);
    font-size: 14px;
  }

  .bind-tip {
    color: var(--el-color-warning);
  }
}

.loading-icon {
  animation: rotate 1.5s linear infinite;
  color: var(--el-color-primary);
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.bind-form {
  width: 360px;
  padding: 30px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);

  .login-btn {
    width: 100%;
  }
}
</style>
