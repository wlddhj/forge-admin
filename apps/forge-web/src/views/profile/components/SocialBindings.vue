<template>
  <div class="social-bindings">
    <div class="binding-list">
      <div v-for="item in socialSources" :key="item.source" class="binding-item">
        <div class="binding-info">
          <div class="source-icon" :class="item.source">
            <svg v-if="item.source === 'wechat'" viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
              <path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178A1.17 1.17 0 0 1 4.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178 1.17 1.17 0 0 1-1.162-1.178c0-.651.52-1.18 1.162-1.18z"/>
            </svg>
            <svg v-else-if="item.source === 'dingtalk'" viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
              <path d="M12 0C5.373 0 0 5.373 0 12s5.373 12 12 12 12-5.373 12-12S18.627 0 12 0zm5.562 12.152l-3.89 1.26s-.427.166-.304.49l.756 1.983s.213.567-.138.785c-.35.218-.735-.064-.735-.064l-2.574-1.87s-.277-.177-.558 0l-2.574 1.87s-.384.282-.735.064c-.35-.218-.138-.785-.138-.785l.756-1.983s.123-.324-.304-.49l-3.89-1.26s-.547-.177-.448-.611c.1-.434.636-.41.636-.41l4.09-.082s.381-.017.492-.39l1.274-3.838s.174-.536.625-.536.625.536.625.536l1.274 3.838c.11.373.493.39.493.39l4.089.082s.536-.024.636.41c.099.434-.448.611-.448.611z"/>
            </svg>
          </div>
          <div class="source-info">
            <span class="source-name">{{ item.name }}</span>
            <span v-if="getBinding(item.source)" class="bind-status bound">已绑定</span>
            <span v-else class="bind-status unbound">未绑定</span>
          </div>
          <div v-if="getBinding(item.source)" class="bound-detail">
            <el-avatar :size="24" :src="getBinding(item.source)!.avatar">
              <el-icon :size="12"><User /></el-icon>
            </el-avatar>
            <span class="bound-nickname">{{ getBinding(item.source)!.nickname }}</span>
          </div>
        </div>
        <div class="binding-action">
          <el-button
            v-if="getBinding(item.source)"
            type="danger"
            plain
            size="small"
            :loading="unbindLoading === item.source"
            @click="handleUnbind(item.source)"
          >
            解绑
          </el-button>
          <el-button
            v-else
            type="primary"
            plain
            size="small"
            @click="handleBind(item.source)"
          >
            绑定
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { socialApi } from '@/api/social'
import type { SocialBinding } from '@/types/social'

const bindings = ref<SocialBinding[]>([])
const unbindLoading = ref('')

const socialSources = [
  { source: 'wechat', name: '微信' },
  { source: 'dingtalk', name: '钉钉' }
]

const getBinding = (source: string) => {
  return bindings.value.find(b => b.source === source)
}

const loadBindings = async () => {
  try {
    const res = await socialApi.listBindings()
    bindings.value = res.data || []
  } catch {
    // ignore
  }
}

const handleBind = (source: string) => {
  const width = 600
  const height = 500
  const left = (window.screen.width - width) / 2
  const top = (window.screen.height - height) / 2
  window.open(
    `/api/auth/social/authorize/${source}`,
    `${source}_bind`,
    `width=${width},height=${height},left=${left},top=${top},menubar=0,scrollbars=1,resizable=1,status=1,titlebar=0,toolbar=0`
  )
}

const handleUnbind = async (source: string) => {
  const name = socialSources.find(s => s.source === source)?.name || source
  await ElMessageBox.confirm(`确认解绑${name}账号？解绑后将无法使用该账号登录。`, '提示', { type: 'warning' })
  unbindLoading.value = source
  try {
    await socialApi.unbind(source)
    ElMessage.success('解绑成功')
    loadBindings()
  } finally {
    unbindLoading.value = ''
  }
}

onMounted(() => {
  loadBindings()
})
</script>

<style scoped lang="scss">
.social-bindings {
  max-width: 500px;
}

.binding-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.binding-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  transition: box-shadow 0.3s;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  }
}

.binding-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.source-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;

  &.wechat {
    background-color: #e6f8e6;
    color: #07c160;
  }

  &.dingtalk {
    background-color: #e6f0ff;
    color: #0089ff;
  }
}

.source-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.source-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.bind-status {
  font-size: 12px;

  &.bound {
    color: var(--el-color-success);
  }

  &.unbound {
    color: var(--el-text-color-placeholder);
  }
}

.bound-detail {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 16px;
}

.bound-nickname {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
