<template>
  <el-dialog
    v-model="visible"
    title="License 在线签发"
    width="640px"
    append-to-body
    @open="loadFormData"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="目标租户" prop="tenantId">
        <el-select v-model="form.tenantId" placeholder="请选择租户" filterable style="width: 100%">
          <el-option
            v-for="t in tenants"
            :key="t.id"
            :label="`${t.name}（${t.code}）`"
            :value="t.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="授权插件" prop="pluginCodes">
        <el-select v-model="form.pluginCodes" multiple placeholder="请选择插件（可多选）" style="width: 100%">
          <el-option
            v-for="p in plugins"
            :key="p.pluginCode"
            :label="p.freeOpen ? `${p.name}（免费·无需授权）` : p.name"
            :value="p.pluginCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="被授权方" prop="licensee">
        <el-input v-model="form.licensee" placeholder="公司主体名称（展示用）" />
      </el-form-item>
      <el-form-item label="有效期" prop="dateRange">
        <el-date-picker
          v-model="form.dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="生效日期（默认今天）"
          end-placeholder="到期日期"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item v-if="selectedScopes.length" label="内置数据权限">
        <el-checkbox-group v-model="form.scopes">
          <div v-for="s in selectedScopes" :key="s.pluginName + s.scope" class="scope-line">
            <el-checkbox :value="s.scope">
              {{ s.name }}
              <span class="scope-code">{{ s.pluginName }} · {{ s.scope }}</span>
            </el-checkbox>
          </div>
        </el-checkbox-group>
        <div class="scope-tip">勾选项将内置于 License（所选每个插件都已声明的 scope 才有效；管理端勾选授予是另一条路径）</div>
      </el-form-item>
      <el-form-item label="License编号">
        <el-input v-model="form.licenseKey" placeholder="留空自动生成 LIC-yyyyMMdd-XXXX" />
      </el-form-item>
      <el-form-item label="签发后生效">
        <el-switch v-model="form.activate" />
        <span class="scope-tip ml-2">
          {{ form.activate ? '签发并落库，目标租户立即可启用插件' : '仅生成文件供下载，后续经「导入」生效' }}
        </span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">签发</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { pluginApi } from '@/api/system/plugin'

defineOptions({ name: 'IssueLicenseDialog' })

const emit = defineEmits<{ issued: [activated: boolean] }>()

interface ScopeOption {
  scope: string
  name: string
  description: string
  pluginName: string
}

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const tenants = ref<Array<{ id: number; name: string; code: string }>>([])
const plugins = ref<
  Array<{
    pluginCode: string
    name: string
    freeOpen: boolean
    declaredScopes: Array<{ scope: string; name: string; description: string; grantedByDefault: boolean }>
  }>
>([])

const form = reactive({
  tenantId: undefined as number | undefined,
  pluginCodes: [] as string[],
  licensee: '',
  dateRange: [] as [Date, Date] | [],
  scopes: [] as string[],
  licenseKey: '',
  activate: true
})

const rules: FormRules = {
  tenantId: [{ required: true, message: '请选择租户', trigger: 'change' }],
  pluginCodes: [{ required: true, message: '请选择插件', trigger: 'change' }],
  licensee: [{ required: true, message: '请输入被授权方名称', trigger: 'blur' }],
  dateRange: [{ required: true, message: '请选择有效期', trigger: 'change' }]
}

/** 所选插件的声明集并集（一码多插件：scope 须每个所选插件都声明才有效） */
const selectedScopes = computed<ScopeOption[]>(() => {
  const chosen = form.pluginCodes
  if (chosen.length === 0) return []
  return plugins.value
    .filter(p => chosen.includes(p.pluginCode))
    .flatMap(p =>
      p.declaredScopes
        .filter(s => !s.grantedByDefault)
        .map(s => ({
          scope: s.scope,
          name: s.name,
          description: s.description,
          pluginName: p.name
        }))
    )
})

const open = () => {
  form.tenantId = undefined
  form.pluginCodes = []
  form.licensee = ''
  const now = new Date()
  const yearLater = new Date(now.getTime() + 365 * 24 * 3600 * 1000)
  form.dateRange = [now, yearLater]
  form.scopes = []
  form.licenseKey = ''
  form.activate = true
  visible.value = true
}

const loadFormData = async () => {
  const res = await pluginApi.issueFormData()
  tenants.value = res.data?.tenants ?? []
  plugins.value = res.data?.plugins ?? []
}

const toIso = (d: Date) => {
  // 本地时区 → ISO-8601 含 +08:00 偏移（后端按 OffsetDateTime 解析）
  const offsetMinutes = -d.getTimezoneOffset()
  const sign = offsetMinutes >= 0 ? '+' : '-'
  const abs = Math.abs(offsetMinutes)
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}` +
    `${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
  )
}

/** 到期日收口到当天 23:59:59：选 2027-11-13 = 该日全天有效 */
const endOfDay = (d: Date) => {
  const x = new Date(d)
  x.setHours(23, 59, 59, 0)
  return x
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  const [start, end] = form.dateRange as [Date, Date]
  submitting.value = true
  try {
    const res = await pluginApi.issueLicense({
      tenantId: form.tenantId!,
      pluginCodes: form.pluginCodes,
      licensee: form.licensee,
      notBefore: toIso(start),
      notAfter: toIso(endOfDay(end)),
      scopes: form.scopes,
      licenseKey: form.licenseKey || undefined,
      activate: form.activate
    })
    const result = res.data
    if (result?.activated) {
      ElMessage.success(`已签发并生效：${result.licenseKey}（可到租户授权启用插件）`)
    } else if (result?.licenseContent) {
      download(result.licenseKey, result.licenseContent)
      ElMessage.success(`已生成 ${result.licenseKey}，文件已开始下载，导入后生效`)
    }
    emit('issued', result?.activated ?? false)
    visible.value = false
  } finally {
    submitting.value = false
  }
}

const download = (licenseKey: string, content: string) => {
  const blob = new Blob([content], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${licenseKey}.lic`
  link.click()
  URL.revokeObjectURL(url)
}

defineExpose({ open })
</script>

<style scoped>
.scope-line {
  padding: 1px 0;
}
.scope-code {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.scope-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.ml-2 {
  margin-left: 8px;
}
</style>
