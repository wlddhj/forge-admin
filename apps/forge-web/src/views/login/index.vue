<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <img src="/vite.svg" alt="logo" />
        <h1>forge-admin</h1>
        <p>聚能后台管理系统</p>
      </div>

      <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" class="login-form">
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
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-tips">
        <p>默认账号: admin / password</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)

const loginForm = reactive({
  username: 'admin',
  password: '123456'
})

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await userStore.loginAction(loginForm)
        ElMessage.success('登录成功')

        const redirect = route.query.redirect as string
        router.push(redirect || '/dashboard')
      } catch (error) {
        console.error('登录失败', error)
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped lang="scss">
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  // 极光灰渐变背景
  background: linear-gradient(135deg, #e0e0e0 0%, #f5f5f5 50%, #ffffff 100%);
  position: relative;

  // 添加微妙的装饰纹理
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-image:
      radial-gradient(circle at 20% 30%, rgba(131, 149, 167, 0.05) 0%, transparent 50%),
      radial-gradient(circle at 80% 70%, rgba(90, 94, 102, 0.03) 0%, transparent 50%);
    pointer-events: none;
  }
}

.login-box {
  width: 400px;
  padding: 40px;
  background: #ffffff;
  border-radius: 16px;
  // 优雅的多层阴影
  box-shadow:
    0 4px 20px rgba(0, 0, 0, 0.05),
    0 8px 40px rgba(0, 0, 0, 0.08),
    0 1px 3px rgba(0, 0, 0, 0.1);
  position: relative;
  z-index: 1;
  // 添加微妙的边框
  border: 1px solid rgba(255, 255, 255, 0.8);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;

  img {
    width: 60px;
    height: 60px;
  }

  h1 {
    margin: 15px 0 10px;
    font-size: 28px;
    // 高级灰
    color: #5a5e66;
    font-weight: 500;
    letter-spacing: 1px;
  }

  p {
    color: #8395a7;
    font-size: 14px;
  }
}

.login-form {
  :deep(.el-input) {
    .el-input__wrapper {
      border-radius: 8px;
      box-shadow: 0 0 0 1px #e4e7ed inset;
      transition: all 0.3s ease;

      &:hover {
        box-shadow: 0 0 0 1px #c0c4cc inset;
      }

      &.is-focus {
        box-shadow: 0 0 0 1px #8395a7 inset;
      }
    }

    .el-input__inner {
      color: #5a5e66;
      &::placeholder {
        color: #a8abb2;
      }
    }
  }

  .el-form-item {
    margin-bottom: 25px;
  }

  .login-btn {
    width: 100%;
    border-radius: 8px;
    // 浅蓝灰主色
    background: linear-gradient(135deg, #8395a7 0%, #5a5e66 100%);
    border: none;
    height: 44px;
    font-size: 16px;
    transition: all 0.3s ease;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(90, 94, 102, 0.3);
    }

    &:active {
      transform: translateY(0);
    }
  }
}

.login-tips {
  text-align: center;
  color: #a8abb2;
  font-size: 12px;
  margin-top: 20px;

  p {
    color: #8395a7;
  }
}

// ==================== 移动端适配 ====================

// 平板横屏 (1024px 及以下)
@media screen and (max-width: 1024px) {
  .login-box {
    width: 380px;
    padding: 35px;
  }
}

// 平板竖屏 (768px 及以下)
@media screen and (max-width: 768px) {
  .login-box {
    width: 360px;
    padding: 30px;
  }

  .login-header {
    margin-bottom: 25px;

    img {
      width: 50px;
      height: 50px;
    }

    h1 {
      font-size: 24px;
      margin: 12px 0 8px;
    }

    p {
      font-size: 13px;
    }
  }
}

// 手机横屏 (640px 及以下)
@media screen and (max-width: 640px) {
  .login-container {
    padding: 20px;
    // 移动端简洁纯色背景
    background: #f5f7fa;

    // 移除装饰纹理
    &::before {
      display: none;
    }
  }

  .login-box {
    width: 100%;
    max-width: 400px;
    padding: 30px 24px;
    border-radius: 12px;
    // 移动端减少阴影强度
    box-shadow:
      0 2px 12px rgba(0, 0, 0, 0.04),
      0 4px 24px rgba(0, 0, 0, 0.06);
  }

  .login-header {
    margin-bottom: 24px;

    img {
      width: 48px;
      height: 48px;
    }

    h1 {
      font-size: 22px;
      margin: 12px 0 8px;
    }

    p {
      font-size: 13px;
    }
  }

  .login-form {
    .el-form-item {
      margin-bottom: 20px;
    }

    :deep(.el-input) {
      .el-input__wrapper {
        padding: 8px 12px;
      }
    }

    .login-btn {
      height: 48px;
      font-size: 16px;
      // 移动端减少悬停效果
      &:hover {
        transform: none;
        box-shadow: none;
      }

      &:active {
        transform: scale(0.98);
      }
    }
  }

  .login-tips {
    margin-top: 16px;
    font-size: 11px;

    p {
      line-height: 1.6;
    }
  }
}

// 小屏手机 (480px 及以下)
@media screen and (max-width: 480px) {
  .login-container {
    padding: 16px;
  }

  .login-box {
    padding: 24px 20px;
    border-radius: 12px;
  }

  .login-header {
    margin-bottom: 20px;

    img {
      width: 44px;
      height: 44px;
    }

    h1 {
      font-size: 20px;
      margin: 10px 0 6px;
    }

    p {
      font-size: 12px;
    }
  }

  .login-form {
    .el-form-item {
      margin-bottom: 18px;
    }

    .login-btn {
      height: 46px;
      font-size: 15px;
    }
  }
}

// 超小屏 (375px 及以下，如 iPhone SE)
@media screen and (max-width: 375px) {
  .login-container {
    padding: 12px;
  }

  .login-box {
    padding: 20px 16px;
  }

  .login-header {
    img {
      width: 40px;
      height: 40px;
    }

    h1 {
      font-size: 18px;
    }
  }
}

// 横屏适配（高度较小时）
@media screen and (max-height: 600px) and (orientation: landscape) {
  .login-container {
    align-items: flex-start;
    padding-top: 20px;
  }

  .login-box {
    margin-top: 20px;
    padding: 20px;
  }

  .login-header {
    margin-bottom: 16px;

    img {
      width: 40px;
      height: 40px;
    }

    h1 {
      font-size: 20px;
      margin: 8px 0 4px;
    }

    p {
      font-size: 12px;
    }
  }

  .login-form {
    .el-form-item {
      margin-bottom: 14px;
    }
  }

  .login-tips {
    margin-top: 12px;
  }
}
</style>
