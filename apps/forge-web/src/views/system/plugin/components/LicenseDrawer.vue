<template>
  <el-drawer v-model="visible" :title="`License 管理${plugin ? ' - ' + plugin.name : ''}`" size="1000px" append-to-body>
    <div ref="containerRef" class="drawer-body" style="height: 100%">
      <!-- 导入与筛选 -->
      <div class="import-bar">
        <el-upload :show-file-list="false" :auto-upload="false" accept=".lic,.json" :on-change="handleImport">
          <el-button v-permission="'plugin:license:import'" type="primary">
            导入 License
          </el-button>
        </el-upload>
        <el-button
          v-if="issueEnabled"
          v-permission="'plugin:license:issue'"
          type="success"
          @click="issueDialogRef?.open()"
        >
          在线签发
        </el-button>
        <el-select
          v-model="queryPluginCode"
          placeholder="按插件筛选"
          clearable
          style="width: 160px"
          @change="handleQuery"
        >
          <el-option v-for="p in plugins" :key="p.pluginCode" :label="p.name" :value="p.pluginCode" />
        </el-select>
        <el-select v-model="queryStatus" placeholder="状态" clearable style="width: 120px" @change="handleQuery">
          <el-option label="有效" :value="1" />
          <el-option label="已失效" :value="0" />
          <el-option label="已替换" :value="2" />
        </el-select>
        <span class="hint">一码多插件原子导入：任一插件未注册则整个文件拒绝</span>
      </div>

      <vxe-table
        id="pluginLicenseTable"
        ref="tableRef"
        :data="records"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isHover: true }"
        border
        stripe
        show-overflow="tooltip"
      >
        <vxe-column field="licenseKey" title="License 编号" min-width="150" />

        <vxe-column field="licenseeName" title="被授权方" min-width="140" />

        <vxe-column field="tenantCode" title="租户编码" width="110" />

        <vxe-column field="pluginCode" title="插件" width="90" />

        <vxe-column title="有效期" width="200">
          <template #default="{ row }">
            {{ formatDate(row.notBefore) }} ~ {{ formatDate(row.notAfter) }}
          </template>
        </vxe-column>

        <vxe-column title="内置 scope" width="130">
          <template #default="{ row }">
            {{ row.scopes || '-' }}
          </template>
        </vxe-column>

        <vxe-column title="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type" size="small">
              {{ statusTag(row.status).text }}
            </el-tag>
          </template>
        </vxe-column>

        <vxe-column title="操作" width="70" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              v-permission="'plugin:license:revoke'"
              link
              type="danger"
              @click="handleRevoke(row)"
            >
              撤销
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        class="mt-3"
        @current-change="loadLicenses"
      />
    </div>

    <!-- 在线签发弹窗（双模式：仅私钥已配置时入口可见） -->
    <IssueLicenseDialog ref="issueDialogRef" @issued="onIssued" />
  </el-drawer>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import type { VxeTableInstance } from 'vxe-table'
import { pluginApi } from '@/api/system/plugin'
import type { PluginEntity, PluginLicenseEntity } from '@/api/system/plugin'
import { formatDate } from '@/utils/dateFormat'
import { useDrawerTableHeight } from '@/composables/useDrawerTableHeight'
import IssueLicenseDialog from './IssueLicenseDialog.vue'

defineOptions({ name: 'LicenseDrawer' })

const visible = ref(false)
const loading = ref(false)
const issueEnabled = ref(false)
const issueDialogRef = ref<InstanceType<typeof IssueLicenseDialog>>()
const plugin = ref<PluginEntity | null>(null)
const plugins = ref<PluginEntity[]>([])
const records = ref<PluginLicenseEntity[]>([])
const tableRef = ref<VxeTableInstance | null>(null)
const containerRef = ref<HTMLElement>()
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const queryPluginCode = ref<string | undefined>(undefined)
const queryStatus = ref<number | undefined>(undefined)

const { tableHeight, updateHeight } = useDrawerTableHeight({
  containerRef,
  excludeSelectors: ['.import-bar', '.el-pagination']
})

const open = async (row: PluginEntity | null) => {
  plugin.value = row
  queryPluginCode.value = row?.pluginCode
  visible.value = true
  if (plugins.value.length === 0) {
    const res = await pluginApi.list()
    plugins.value = res.data ?? []
  }
  // 签发能力探测（双模式：未配置私钥=false，入口隐藏）
  try {
    const form = await pluginApi.issueFormData()
    issueEnabled.value = form.data?.enabled ?? false
  } catch {
    issueEnabled.value = false
  }
  await nextTick()
  requestAnimationFrame(() => updateHeight())
  await loadLicenses()
}

const onIssued = async (activated: boolean) => {
  if (activated) {
    await loadLicenses()
  }
}

const handleQuery = () => {
  pageNum.value = 1
  loadLicenses()
}

const loadLicenses = async () => {
  loading.value = true
  try {
    const res = await pluginApi.licensePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      pluginCode: queryPluginCode.value,
      status: queryStatus.value
    })
    records.value = res.data?.list ?? []
    total.value = res.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

const handleImport = async (file: UploadFile) => {
  if (!file.raw) return
  try {
    const res = await pluginApi.importLicense(file.raw)
    ElMessage.success(`导入成功：授权 ${res.data?.imported ?? 0} 个插件`)
    await loadLicenses()
  } catch {
    // 错误提示由 axios 拦截器统一弹出（验签失败/租户不匹配/未注册等）
  }
}

const handleRevoke = async (row: PluginLicenseEntity) => {
  await ElMessageBox.confirm(
    `撤销后「${row.licenseeName ?? row.tenantCode}」的插件 ${row.pluginCode} 将立即停用（数据保留）。确定撤销？`,
    '撤销 License',
    { type: 'warning' }
  )
  await pluginApi.revokeLicense(row.id)
  ElMessage.success('已撤销并联动停用')
  await loadLicenses()
}

const statusTag = (status: number) => {
  if (status === 1) return { type: 'success' as const, text: '有效' }
  if (status === 2) return { type: 'info' as const, text: '已替换' }
  return { type: 'danger' as const, text: '已失效' }
}

defineExpose({ open })
</script>

<style scoped>
.drawer-body {
  display: flex;
  flex-direction: column;
}
.import-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
