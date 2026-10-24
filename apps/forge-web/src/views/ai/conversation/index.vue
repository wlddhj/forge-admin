<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="对话标题">
          <el-input v-model="queryParams.title" placeholder="请输入标题关键词" clearable />
        </el-form-item>
        <el-form-item label="模型">
          <el-select v-model="queryParams.modelId" placeholder="请选择模型" clearable style="width: 150px">
            <el-option v-for="model in modelList" :key="model.id" :label="model.modelName" :value="model.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'ai:conversation:delete'" type="danger" :disabled="selectedIds.length === 0" @click="handleBatchDelete">
            <el-icon><Delete /></el-icon>
            批量删除
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" @click="getList"></vxe-button>
        </template>
      </vxe-toolbar>

      <vxe-table
        id="aiConversationTable"
        ref="tableRef"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isCurrent: true, isHover: true }"
        :checkbox-config="{ reserve: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        @checkbox-change="handleCheckboxChange"
      >
        <vxe-column type="checkbox" width="50" />
        <vxe-column type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="title" title="对话标题" min-width="200" />
        <vxe-column field="modelName" title="模型" width="150" />
        <vxe-column field="totalMessages" title="消息数" width="80" />
        <vxe-column field="totalTokens" title="Token数" width="100" />
        <vxe-column field="totalCost" title="费用(元)" width="100">
          <template #default="{ row }">
            {{ row.totalCost ? row.totalCost.toFixed(4) : '0.00' }}
          </template>
        </vxe-column>
        <vxe-column title="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">进行中</el-tag>
            <el-tag v-else type="info">已关闭</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="createTime" title="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </vxe-column>
        <vxe-column field="lastMessageTime" title="最后消息" width="180">
          <template #default="{ row }">
            {{ row.lastMessageTime ? formatDateTime(row.lastMessageTime) : '-' }}
          </template>
        </vxe-column>
        <vxe-column title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'ai:conversation:detail'" type="primary" link size="small" @click="handleDetail(row)">
              查看消息
            </el-button>
            <el-button v-permission="'ai:conversation:delete'" type="danger" link size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>

    <!-- 消息详情抽屉 -->
    <el-drawer v-model="detailVisible" title="对话消息详情" size="50%" destroy-on-close>
      <div class="message-list">
        <div v-for="msg in messageList" :key="msg.id" :class="['message-item', msg.role]">
          <div class="message-header">
            <el-tag :type="msg.role === 'user' ? 'primary' : 'success'" size="small">
              {{ msg.role === 'user' ? '用户' : 'AI' }}
            </el-tag>
            <span class="message-time">{{ formatDateTime(msg.createTime) }}</span>
          </div>
          <div class="message-content">
            <MarkdownRenderer :content="msg.content" />
          </div>
        </div>
        <el-empty v-if="messageList.length === 0" description="暂无消息记录" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { formatDateTime } from '@/utils/dateFormat'
import { getConversationList, deleteConversation, getConversationMessages } from '@/api/ai/chat'
import { getModelList, AI_MODEL_TYPE_TEXT } from '@/api/ai/model'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import type { ConversationResponse, MessageResponse } from '@/api/ai/chat'
import type { ModelConfigResponse } from '@/api/ai/model'

const { tableHeight } = useTableHeight()
const queryParams = ref({
  title: '',
  modelId: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10
})
const { seqMethod } = useTableSeq({
  currentPage: computed(() => queryParams.value.pageNum),
  pageSize: computed(() => queryParams.value.pageSize)
})

const tableRef = ref()
const toolbarRef = ref()
const loading = ref(false)
const tableData = ref<ConversationResponse[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])
const modelList = ref<ModelConfigResponse[]>([])

const detailVisible = ref(false)
const messageList = ref<MessageResponse[]>([])

onMounted(async () => {
  tableRef.value?.connect(toolbarRef.value!)
  modelList.value = await getModelList(AI_MODEL_TYPE_TEXT)
  getList()
})

const getList = async () => {
  loading.value = true
  try {
    const result = await getConversationList(queryParams.value)
    tableData.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.value.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.value = { title: '', modelId: undefined, pageNum: 1, pageSize: 10 }
  getList()
}

const handleCheckboxChange = () => {
  const records = tableRef.value?.getCheckboxRecords() || []
  selectedIds.value = records.map((r: ConversationResponse) => r.id)
}

const handleDetail = async (row: ConversationResponse) => {
  messageList.value = await getConversationMessages(row.id)
  detailVisible.value = true
}

const handleDelete = (row: ConversationResponse) => {
  ElMessageBox.confirm(`确定要删除对话「${row.title || '新对话'}」吗？删除后将无法恢复。`, '提示', { type: 'warning' }).then(async () => {
    await deleteConversation(row.id)
    ElMessage.success('删除成功')
    getList()
  })
}

const handleBatchDelete = () => {
  ElMessageBox.confirm(`确定要删除选中的 ${selectedIds.value.length} 个对话吗？删除后将无法恢复。`, '提示', { type: 'warning' }).then(async () => {
    for (const id of selectedIds.value) {
      await deleteConversation(id)
    }
    ElMessage.success('批量删除成功')
    tableRef.value?.clearCheckboxRow()
    selectedIds.value = []
    getList()
  })
}
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;

  .search-card {
    margin-bottom: 15px;
  }

  .table-card {
    .el-pagination {
      margin-top: 15px;
      justify-content: flex-end;
    }
  }
}

.message-list {
  padding: 0 20px;

  .message-item {
    margin-bottom: 20px;
    padding: 15px;
    border-radius: 8px;
    background: #f5f7fa;

    &.user {
      background: #ecf5ff;
    }

    .message-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;

      .message-time {
        font-size: 12px;
        color: #909399;
      }
    }

    .message-content {
      line-height: 1.6;
    }
  }
}
</style>