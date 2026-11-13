<template>
  <div v-loading="loading" class="scope-group">
    <div v-if="!enabled" class="scope-tip">插件未启用，勾选将在启用后生效</div>
    <el-checkbox-group v-model="checked" :disabled="!canGrant" @change="handleChange">
      <div v-for="item in scopes" :key="item.scope" class="scope-item">
        <el-checkbox :value="item.scope" :disabled="item.grantedByDefault || !canGrant">
          <span class="scope-name">{{ item.name }}</span>
          <el-tag v-if="item.grantedByDefault" size="small" type="info" class="ml-1">默认</el-tag>
          <el-tag v-else-if="item.source === 'license'" size="small" type="warning" class="ml-1">
            License 内置（只读）
          </el-tag>
        </el-checkbox>
        <div class="scope-desc">{{ item.scope }} · {{ item.description }}</div>
      </div>
    </el-checkbox-group>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pluginApi } from '@/api/system/plugin'
import type { PluginScopeVO } from '@/api/system/plugin'
import { hasAllPermissions } from '@/directives/permission'

defineOptions({ name: 'ScopeCheckGroup' })

const props = defineProps<{
  tenantId: number
  pluginCode: string
  enabled: boolean
}>()

const emit = defineEmits<{ changed: [] }>()

const loading = ref(false)
const scopes = ref<PluginScopeVO[]>([])
const checked = ref<string[]>([])

/** 授予/回收权限（plugin:scope:grant）；License 内置项只读展示 */
const canGrant = computed(() => hasAllPermissions(['plugin:scope:grant']))

const loadScopes = async () => {
  loading.value = true
  try {
    const res = await pluginApi.scopeList(props.tenantId, props.pluginCode)
    scopes.value = res.data ?? []
    checked.value = scopes.value
      .filter(item => item.granted && !item.grantedByDefault && item.source === 'admin')
      .map(item => item.scope)
  } finally {
    loading.value = false
  }
}

/** 勾选=授予、取消=回收（读类默认项不可取消；License 内置项只读） */
const handleChange = async (nextValue: Array<string | number | boolean>) => {
  const next = nextValue.map(String)
  const prev = scopes.value
    .filter(item => item.granted && !item.grantedByDefault && item.source === 'admin')
    .map(item => item.scope)
  const granted = next.find(scope => !prev.includes(scope))
  const revoked = prev.find(scope => !next.includes(scope))
  try {
    if (granted) {
      await pluginApi.grantScope(props.tenantId, props.pluginCode, granted)
      ElMessage.success(`已授予 ${granted}`)
    } else if (revoked) {
      await pluginApi.revokeScope(props.tenantId, props.pluginCode, revoked)
      ElMessage.success(`已回收 ${revoked}`)
    }
    emit('changed')
    await loadScopes()
  } catch {
    checked.value = prev
  }
}

onMounted(loadScopes)
</script>

<style scoped>
.scope-group {
  padding: 4px 12px;
}
.scope-tip {
  color: var(--el-color-warning);
  font-size: 12px;
  margin-bottom: 8px;
}
.scope-item {
  padding: 2px 0;
}
.scope-name {
  font-weight: 500;
}
.scope-desc {
  margin: 0 0 6px 24px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
