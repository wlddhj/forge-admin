<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDataSourceList, executeDataSource, type ScreenDataSource, type DataSourceExecuteResponse } from '@/api/screen'
import type { ScreenCard } from '@/types/screen'

const props = defineProps<{ card: ScreenCard }>()
const emit = defineEmits<{ 'bind': [id: number | null] }>()

const dialogVisible = ref(false)
const dataSources = ref<ScreenDataSource[]>([])
const selectedId = ref<number | null>(props.card.dataSourceId ?? null)
const testResult = ref<DataSourceExecuteResponse | null>(null)
const testing = ref(false)

const loadList = async () => {
  const res = await getDataSourceList({ pageNum: 1, pageSize: 100 })
  dataSources.value = res.list
}

const handleTest = async () => {
  if (!selectedId.value) return
  testing.value = true
  try { testResult.value = await executeDataSource(selectedId.value, { params: {} }) }
  finally { testing.value = false }
}

const handleBind = () => {
  emit('bind', selectedId.value)
  dialogVisible.value = false
}

const handleUnbind = () => {
  selectedId.value = null
  emit('bind', null)
  dialogVisible.value = false
}

onMounted(loadList)
</script>

<template>
  <div class="ds-binder">
    <el-button size="small" @click="dialogVisible = true">
      {{ card.dataSourceId ? `已绑定 #${card.dataSourceId}` : '绑定数据源' }}
    </el-button>
    <el-button v-if="card.dataSourceId" size="small" type="danger" link @click="handleUnbind">解绑</el-button>

    <el-dialog v-model="dialogVisible" title="选择数据源" width="600px">
      <el-table :data="dataSources" highlight-current-row @current-change="row => selectedId = row?.id ?? null">
        <el-table-column type="index" width="50" />
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="type" label="类型" width="80" />
      </el-table>
      <el-button @click="handleTest" :disabled="!selectedId" :loading="testing">测试</el-button>
      <pre v-if="testResult" class="test-result">{{ JSON.stringify(testResult.data, null, 2).slice(0, 500) }}</pre>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleBind" :disabled="!selectedId">绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
