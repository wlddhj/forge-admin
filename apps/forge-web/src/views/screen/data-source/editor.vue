<template>
  <el-drawer
    :model-value="modelValue"
    :title="isEdit ? '编辑数据源' : '新增数据源'"
    direction="rtl"
    size="640px"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" v-loading="loading">
      <el-form-item label="编码" prop="code">
        <el-input v-model="form.code" :disabled="isEdit" />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-radio-group v-model="form.type" :disabled="isEdit">
          <el-radio-button value="HTTP">HTTP</el-radio-button>
          <el-radio-button value="SQL">SQL</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <template v-if="form.type === 'HTTP'">
        <el-form-item label="Method">
          <el-select v-model="httpCfg.method" style="width: 120px">
            <el-option v-for="m in ['GET','POST','PUT','DELETE']" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="URL" required>
          <el-input v-model="httpCfg.url" placeholder="https://internal-api.example.com/path" />
        </el-form-item>
        <el-form-item label="Headers">
          <el-input v-model="httpCfg.headers" type="textarea" :rows="3" placeholder='{"Authorization":"Bearer xxx"}' />
        </el-form-item>
        <el-form-item label="Params">
          <el-input v-model="httpCfg.params" type="textarea" :rows="3" placeholder='{"key":"value"}' />
        </el-form-item>
        <el-form-item label="Timeout(s)">
          <el-input-number v-model="httpCfg.timeout" :min="1" :max="60" />
        </el-form-item>
      </template>

      <template v-else>
        <el-form-item label="SQL 模板" required>
          <el-input v-model="sqlCfg.sqlTemplate" type="textarea" :rows="6"
            placeholder="SELECT id, name FROM sys_user WHERE create_time > :startTime LIMIT 100" />
        </el-form-item>
        <el-form-item label="参数 Schema">
          <el-input v-model="sqlCfg.paramSchema" type="textarea" :rows="3"
            placeholder='{"startTime":{"type":"string","required":true}}' />
        </el-form-item>
        <el-form-item label="最大行数">
          <el-input-number v-model="sqlCfg.maxRows" :min="1" :max="1000" />
        </el-form-item>
      </template>

      <el-form-item label="缓存(s)">
        <el-input-number v-model="form.cacheSeconds" :min="0" :max="3600" />
        <span class="form-tip">0=不缓存；最长 1 小时</span>
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>

      <el-form-item v-if="testResult" label="测试结果">
        <el-card shadow="never" class="result-card">
          <template #header>
            <span>来自缓存：{{ testResult.fromCache ? '是' : '否' }} | 执行时间：{{ testResult.executedAt }}</span>
          </template>
          <el-table v-if="testRows.length > 0" :data="testRows" stripe border size="small">
            <el-table-column v-for="col in testColumns" :key="col" :prop="col" :label="col" />
          </el-table>
          <el-empty v-else description="无数据" :image-size="60" />
        </el-card>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      <el-button type="success" :loading="testing" :disabled="!form.id" @click="handleTest">测试</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getDataSourceDetail, createDataSource, updateDataSource, executeDataSource,
  type ScreenDataSource, type DataSourceExecuteResponse
} from '@/api/screen'

const props = defineProps<{
  modelValue: boolean
  dataSource: ScreenDataSource | null
}>()

const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  'saved': []
}>()

const formRef = ref()
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const testResult = ref<DataSourceExecuteResponse | null>(null)

const isEdit = computed(() => Boolean(props.dataSource?.id))
const form = reactive<ScreenDataSource>({
  id: undefined, code: '', name: '', type: 'HTTP', config: '{}',
  cacheSeconds: 0, enabled: 1, remark: ''
})
const httpCfg = reactive({ method: 'GET', url: '', headers: '{}', params: '{}', timeout: 5 })
const sqlCfg = reactive({ sqlTemplate: '', paramSchema: '{}', maxRows: 1000 })

const rules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

const testColumns = computed<string[]>(() => {
  const data = testResult.value?.data
  if (Array.isArray(data) && data.length > 0 && typeof data[0] === 'object') {
    return Object.keys(data[0] as object)
  }
  return []
})
const testRows = computed<Record<string, unknown>[]>(() =>
  Array.isArray(testResult.value?.data) ? (testResult.value!.data as Record<string, unknown>[]) : []
)

const resetForm = () => {
  Object.assign(form, { id: undefined, code: '', name: '', type: 'HTTP', config: '{}', cacheSeconds: 0, enabled: 1, remark: '' })
  Object.assign(httpCfg, { method: 'GET', url: '', headers: '{}', params: '{}', timeout: 5 })
  Object.assign(sqlCfg, { sqlTemplate: '', paramSchema: '{}', maxRows: 1000 })
  testResult.value = null
}

const parseConfig = () => {
  try {
    const cfg = JSON.parse(form.config || '{}')
    if (form.type === 'HTTP') Object.assign(httpCfg, cfg)
    else Object.assign(sqlCfg, cfg)
  } catch { /* ignore */ }
}

watch(
  () => [props.modelValue, props.dataSource] as const,
  async ([visible, ds]) => {
    if (!visible) return
    testResult.value = null
    if (ds?.id) {
      loading.value = true
      try {
        const detail = await getDataSourceDetail(ds.id)
        Object.assign(form, detail)
        parseConfig()
      } finally {
        loading.value = false
      }
    } else {
      resetForm()
    }
  },
  { immediate: true }
)

const buildConfig = (): string => {
  if (form.type === 'HTTP') return JSON.stringify({ ...httpCfg })
  return JSON.stringify({ ...sqlCfg })
}

const handleSave = async () => {
  await formRef.value?.validate()
  form.config = buildConfig()
  saving.value = true
  try {
    if (isEdit.value) {
      await updateDataSource(form)
      ElMessage.success('保存成功')
    } else {
      const id = await createDataSource(form)
      form.id = id
      ElMessage.success('创建成功')
    }
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}

const handleTest = async () => {
  if (!form.id) { ElMessage.warning('请先保存'); return }
  testing.value = true
  try {
    testResult.value = await executeDataSource(form.id, { params: {} })
  } finally {
    testing.value = false
  }
}
</script>

<style scoped>
.form-tip { margin-left: 12px; color: #909399; font-size: 12px; }
.result-card { width: 100%; }
</style>
