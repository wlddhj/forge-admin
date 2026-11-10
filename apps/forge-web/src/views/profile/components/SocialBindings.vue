<template>
  <div class="social-bindings">
    <div class="binding-list">
      <div v-for="item in socialSources" :key="item.source" class="binding-item">
        <div class="binding-info">
          <div class="source-icon" :class="item.source">
            <svg v-if="item.source === 'wechat'" viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
              <path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178A1.17 1.17 0 0 1 4.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178 1.17 1.17 0 0 1-1.162-1.178c0-.651.52-1.18 1.162-1.18z" />
            </svg>
            <svg v-else-if="item.source === 'dingtalk'" viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
              <path d="M12 0C5.373 0 0 5.373 0 12s5.373 12 12 12 12-5.373 12-12S18.627 0 12 0zm5.562 12.152l-3.89 1.26s-.427.166-.304.49l.756 1.983s.213.567-.138.785c-.35.218-.735-.064-.735-.064l-2.574-1.87s-.277-.177-.558 0l-2.574 1.87s-.384.282-.735.064c-.35-.218-.138-.785-.138-.785l.756-1.983s.123-.324-.304-.49l-3.89-1.26s-.547-.177-.448-.611c.1-.434.636-.41.636-.41l4.09-.082s.381-.017.492-.39l1.274-3.838s.174-.536.625-.536.625.536.625.536l1.274 3.838c.11.373.493.39.493.39l4.089.082s.536-.024.636.41c.099.434-.448.611-.448.611z" />
            </svg>
            <!-- 企业微信 logo：TDesign logo-wecom-filled（CC-BY-4.0），viewBox 与既有品牌图标一致 -->
            <svg v-else-if="item.source === 'wework'" viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
              <path d="M12 1c6.075 0 11 4.925 11 11s-4.925 11-11 11S1 18.075 1 12S5.925 1 12 1m3.52 15.49a.35.35 0 0 0-.24.1c-.14.13-.16.34.02.53l.07.07c.44.44.74.99.85 1.57c0 .02.04.23.04.23c.05.19.15.37.29.5c.21.21.51.34.82.34c.3 0 .59-.12.8-.33c.44-.44.44-1.16 0-1.61c-.15-.15-.34-.26-.53-.3l-.15-.03c-.61-.11-1.17-.41-1.62-.86c-.03-.03-.07-.07-.1-.11c-.06-.074-.16-.1-.25-.1M11 4.75c-2.117 0-4.264.77-5.75 2.31C4.111 8.246 3.5 9.72 3.5 11.24c0 1.06.3 2.12.88 3.06c.47.695.993 1.371 1.66 1.89l-.384 1.624a.6.6 0 0 0 .856.673L8.64 17.41c.53.166 1.08.234 1.63.3a8.3 8.3 0 0 0 1.7-.03l.38-.05q.283-.046.564-.112a2.33 2.33 0 0 1-.92-1.605l-.254.037c-.62.067-1.232.03-1.85-.04c-.43-.057-.838-.185-1.25-.31l-1.02.5l.23-.67l-.74-.6c-.513-.401-.917-.934-1.28-1.47c-.4-.65-.61-1.38-.61-2.11c0-1.08.456-2.119 1.26-2.97c1.158-1.198 2.854-1.78 4.5-1.78c1.54 0 3.108.513 4.24 1.58c.365.365.707.75.95 1.21c.177.354.338.722.424 1.107a2.34 2.34 0 0 1 1.811.123c-.075-.716-.33-1.4-.665-2.04c-.329-.62-.776-1.155-1.27-1.65c-1.468-1.38-3.471-2.08-5.47-2.08m9.37 9.77a1.136 1.136 0 0 0-1.1.86l-.03.15a3.1 3.1 0 0 1-.86 1.63c-.04.03-.07.07-.11.1c-.14.13-.14.35 0 .49c.07.06.17.1.26.1h.01c.07 0 .15-.02.26-.13l.07-.07c.44-.44.99-.74 1.57-.85c.023 0 .227-.04.23-.04c.2-.06.37-.16.5-.3c.44-.44.44-1.17 0-1.61c-.21-.21-.5-.33-.8-.33m-4.21-1.07c-.08 0-.16.03-.27.14l-.07.07c-.44.44-.99.74-1.57.85c-.02 0-.23.04-.23.04c-.2.06-.37.16-.5.3c-.44.44-.44 1.17 0 1.61c.21.21.51.34.82.34c.3 0 .59-.12.8-.33c.15-.16.25-.34.29-.53a.4.4 0 0 0 .03-.16c.11-.61.41-1.18.86-1.63c.03-.03.06-.06.1-.09c.146-.115.13-.36 0-.49a.34.34 0 0 0-.26-.12m1.18-1.97c-.3 0-.59.12-.8.33c-.44.44-.44 1.16 0 1.61c.15.15.34.26.53.3c.054.006.144.029.15.03c.61.12 1.17.41 1.62.86c.03.03.07.07.1.11c.08.08.16.1.25.1c.1 0 .16-.04.23-.11c.12-.13.14-.32-.02-.52l-.08-.08c-.44-.44-.74-.99-.85-1.57c0-.02-.04-.23-.04-.23c-.05-.19-.15-.37-.29-.5c-.21-.21-.5-.33-.8-.33" />
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
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { socialApi } from '@/api/social'
import type { SocialBinding } from '@/types/social'

const bindings = ref<SocialBinding[]>([])
const unbindLoading = ref('')

const socialSources = [
  { source: 'wechat', name: '微信' },
  { source: 'dingtalk', name: '钉钉' },
  // 企业微信：绑定后 openId 即企微 userid，用于审批消息推送（workflow im.notify）
  { source: 'wework', name: '企业微信' }
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
  // 绑定/登录弹窗关闭回到本页时自动刷新绑定状态
  window.addEventListener('focus', loadBindings)
})

onUnmounted(() => {
  window.removeEventListener('focus', loadBindings)
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

  &.wework {
    background-color: #e8f3ff;
    color: #0084ff;
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
