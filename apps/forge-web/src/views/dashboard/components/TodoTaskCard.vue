<template>
  <el-card v-if="visible" shadow="never" class="todo-task-card">
    <template #header>
      <div class="card-header">
        <span class="card-title">
          <el-icon><Bell /></el-icon>
          我的待办任务
          <el-tag v-if="total > 0" type="danger" size="small" effect="plain" round class="todo-count">
            {{ total > 99 ? '99+' : total }}
          </el-tag>
        </span>
        <el-button type="primary" link @click="goTodoPage">
          查看更多
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
    </template>

    <div v-loading="loading" class="todo-task-list">
      <div v-for="task in tasks" :key="task.id" class="todo-task-item" @click="handleProcess(task)">
        <span class="task-no">{{ task.processNo }}</span>
        <el-tag
          v-if="(task.urgent ?? 0) > 0"
          :type="urgentTagType(task.urgent!)"
          size="small"
          effect="light"
          class="task-urgent"
        >
          {{ urgentLabel(task.urgent!) }}
        </el-tag>
        <span class="task-process" :title="task.instanceTitle || task.processDefinitionName">
          {{ task.instanceTitle || task.processDefinitionName }}
        </span>
        <span class="task-name" :title="task.name">{{ task.name }}</span>
        <span class="task-time">{{ formatDateTime(task.createTime) }}</span>
        <span class="task-actions" @click.stop>
          <el-button
            v-if="task.candidate && canClaim"
            type="warning"
            link
            size="small"
            @click="handleClaim(task)"
          >
            认领
          </el-button>
          <el-button type="primary" link size="small" @click="handleProcess(task)">处理</el-button>
        </span>
      </div>
      <el-empty v-if="!loading && tasks.length === 0" description="暂无待办任务" :image-size="60" />
    </div>
  </el-card>

  <TaskDetailDrawer v-if="visible" ref="drawerRef" @success="loadTodos" />
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { taskApi } from '@/api/workflow/task'
import type { TaskInfo } from '@/types/workflow'
import { formatDateTime } from '@/utils/dateFormat'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import { hasPermission } from '@/directives/permission'
import TaskDetailDrawer from '@/views/workflow/task/components/TaskDetailDrawer.vue'

const router = useRouter()

// 无待办查询权限的用户整卡隐藏，避免接口 403
const visible = hasPermission('workflow:task:list')
const canClaim = hasPermission('workflow:task:claim')

const { getDictLabel: urgentLabel, getTagType } = useDict(DICT_TYPE.WF_URGENT)

// el-tag 的 type 只收字面量联合类型，useDict 的 getTagType 返回宽泛 string，此处收窄
const urgentTagType = (value: string | number) =>
  getTagType(value) as 'primary' | 'success' | 'warning' | 'info' | 'danger'

const loading = ref(false)
const tasks = ref<TaskInfo[]>([])
const total = ref(0)
const drawerRef = ref<InstanceType<typeof TaskDetailDrawer> | null>(null)

// 首页只展示最近 20 条不做翻页；total 用于角标提示，完整列表走「查看更多」
const loadTodos = async () => {
  loading.value = true
  try {
    const res = await taskApi.todo({ pageNum: 1, pageSize: 20 })
    tasks.value = res.list
    total.value = res.total
  } catch {
    // handled by request util
  } finally {
    loading.value = false
  }
}

const handleProcess = (task: TaskInfo) => {
  drawerRef.value?.open(task.id)
}

const handleClaim = async (task: TaskInfo) => {
  try {
    await taskApi.claim(task.id)
    ElMessage.success('认领成功')
    loadTodos()
  } catch {
    // handled by request util
  }
}

const goTodoPage = () => {
  router.push('/workflow/task/todo')
}

onMounted(() => {
  if (visible) {
    loadTodos()
  }
})
</script>

<style scoped lang="scss">
.todo-task-card {
  margin-bottom: 16px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .card-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-weight: 500;
  }

  .todo-count {
    margin-left: 2px;
  }

  .todo-task-list {
    max-height: 480px;
    overflow-y: auto;
  }

  .todo-task-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 9px 4px;
    border-bottom: 1px solid var(--el-border-color-lighter);
    cursor: pointer;
    transition: background 0.3s;

    &:last-child {
      border-bottom: none;
    }

    &:hover {
      background: var(--el-bg-color-page);
    }

    .task-urgent {
      flex-shrink: 0;
    }

    .task-no {
      flex-shrink: 0;
      font-size: 12px;
      color: var(--el-text-color-secondary);
      font-variant-numeric: tabular-nums;
    }

    .task-process {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 14px;
      font-weight: 500;
      color: var(--el-text-color-primary);
    }

    .task-name {
      flex-shrink: 0;
      max-width: 160px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 13px;
      color: var(--el-text-color-secondary);
    }

    .task-time {
      flex-shrink: 0;
      font-size: 12px;
      color: var(--el-text-color-placeholder);
      font-variant-numeric: tabular-nums;
    }

    .task-actions {
      flex-shrink: 0;
      display: flex;
      align-items: center;

      .el-button + .el-button {
        margin-left: 8px;
      }
    }
  }
}

// 移动端隐藏任务名称列，保流程名称/时间/操作
@media (max-width: 768px) {
  .todo-task-card .todo-task-item .task-name {
    display: none;
  }
}
</style>
