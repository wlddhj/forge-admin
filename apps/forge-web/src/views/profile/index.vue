<template>
  <div class="profile-container">
    <el-row :gutter="20">
      <!-- 左侧用户信息卡片 -->
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="user-card">
            <div class="avatar-wrapper">
              <el-avatar :size="100" :src="userInfo.avatar || defaultAvatar">
                <el-icon :size="40"><User /></el-icon>
              </el-avatar>
              <el-upload
                class="avatar-upload"
                :action="uploadUrl"
                :headers="uploadHeaders"
                :show-file-list="false"
                :on-success="handleAvatarSuccess"
                :before-upload="beforeAvatarUpload"
              >
                <el-button type="primary" link size="small">更换头像</el-button>
              </el-upload>
            </div>
            <div class="user-info">
              <h2>{{ userInfo.nickname || userInfo.username }}</h2>
              <p class="role">{{ userInfo.accountType === 1 ? '管理员' : '普通用户' }}</p>
              <p class="dept">{{ userInfo.deptName || '暂无部门' }}</p>
            </div>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="用户名">{{ userInfo.username }}</el-descriptions-item>
              <el-descriptions-item label="手机号">{{ userInfo.phone || '未设置' }}</el-descriptions-item>
              <el-descriptions-item label="邮箱">{{ userInfo.email || '未设置' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatDateTime(userInfo.createTime) }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧表单 -->
      <el-col :span="16">
        <el-card shadow="hover">
          <el-tabs v-model="activeTab">
            <!-- 基本信息 -->
            <el-tab-pane label="基本信息" name="info">
              <el-form ref="infoFormRef" :model="infoForm" :rules="infoRules" label-width="100px" style="max-width: 500px">
                <el-form-item label="昵称" prop="nickname">
                  <el-input v-model="infoForm.nickname" placeholder="请输入昵称" />
                </el-form-item>
                <el-form-item label="手机号" prop="phone">
                  <el-input v-model="infoForm.phone" placeholder="请输入手机号" />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                  <el-input v-model="infoForm.email" placeholder="请输入邮箱" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="infoLoading" @click="handleUpdateInfo">保存修改</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- 修改密码 -->
            <el-tab-pane label="修改密码" name="password">
              <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px" style="max-width: 500px">
                <el-form-item label="当前密码" prop="oldPassword">
                  <el-input v-model="pwdForm.oldPassword" type="password" placeholder="请输入当前密码" show-password />
                </el-form-item>
                <el-form-item label="新密码" prop="newPassword">
                  <el-input v-model="pwdForm.newPassword" type="password" placeholder="请输入新密码" show-password />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                  <el-input v-model="pwdForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="pwdLoading" @click="handleUpdatePassword">修改密码</el-button>
                  <el-button @click="resetPwdForm">重置</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, UploadProps } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/dateFormat'
import { updateUserInfo, updatePassword, updateAvatar } from '@/api/user'
import type { User as UserType } from '@/types/system'

const userStore = useUserStore()
const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

const userInfo = ref<Partial<UserType>>({})
const activeTab = ref('info')

// 基本信息
const infoFormRef = ref<FormInstance>()
const infoLoading = ref(false)
const infoForm = reactive({
  nickname: '',
  phone: '',
  email: ''
})

const infoRules: FormRules = {
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }]
}

// 修改密码
const pwdFormRef = ref<FormInstance>()
const pwdLoading = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// 头像上传
const uploadUrl = computed(() => import.meta.env.VITE_API_BASE_URL + '/system/attachment/avatar')
const uploadHeaders = computed(() => ({ Authorization: `Bearer ${userStore.token}` }))

import { computed } from 'vue'

const beforeAvatarUpload: UploadProps['beforeUpload'] = (rawFile) => {
  const isImage = rawFile.type.startsWith('image/')
  const isLt2M = rawFile.size / 1024 / 1024 < 2

  if (!isImage) {
    ElMessage.error('头像只能是图片格式!')
    return false
  }
  if (!isLt2M) {
    ElMessage.error('头像图片大小不能超过 2MB!')
    return false
  }
  return true
}

const handleAvatarSuccess: UploadProps['onSuccess'] = async (response: { code: number; data: string; message: string }) => {
  if (response.code === 200 && response.data) {
    try {
      await updateAvatar({ avatar: response.data })
      userInfo.value.avatar = response.data
      userStore.updateUserInfo({ avatar: response.data })
      ElMessage.success('头像更新成功')
    } catch (e) {
      ElMessage.error('头像更新失败')
    }
  } else {
    ElMessage.error(response.message || '头像上传失败')
  }
}

const loadUserInfo = () => {
  const user = userStore.userInfo
  if (user) {
    userInfo.value = user
    infoForm.nickname = user.nickname || ''
    infoForm.phone = user.phone || ''
    infoForm.email = user.email || ''
  }
}

const handleUpdateInfo = async () => {
  if (!infoFormRef.value) return
  await infoFormRef.value.validate()
  infoLoading.value = true
  try {
    await updateUserInfo({
      nickname: infoForm.nickname,
      phone: infoForm.phone,
      email: infoForm.email
    })
    userInfo.value = { ...userInfo.value, ...infoForm }
    userStore.updateUserInfo(infoForm)
    ElMessage.success('更新成功')
  } finally {
    infoLoading.value = false
  }
}

const handleUpdatePassword = async () => {
  if (!pwdFormRef.value) return
  await pwdFormRef.value.validate()
  pwdLoading.value = true
  try {
    await updatePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    resetPwdForm()
    // 可选：自动退出登录
    // userStore.logout()
    // router.push('/login')
  } finally {
    pwdLoading.value = false
  }
}

const resetPwdForm = () => {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdFormRef.value?.resetFields()
}

onMounted(() => {
  loadUserInfo()
})
</script>

<style scoped lang="scss">
.profile-container {
  .user-card {
    text-align: center;

    .avatar-wrapper {
      margin-bottom: 20px;

      .avatar-upload {
        margin-top: 10px;
      }
    }

    .user-info {
      margin-bottom: 20px;

      h2 {
        margin: 0 0 10px;
        font-size: 20px;
        color: var(--el-text-color-primary);
      }

      .role {
        color: var(--el-color-primary);
        margin: 5px 0;
      }

      .dept {
        color: var(--el-text-color-secondary);
        font-size: 14px;
        margin: 5px 0;
      }
    }
  }
}
</style>
