<template>
  <div class="approval-timeline">
    <!-- 已发生与进行中的节点 -->
    <el-timeline v-if="activeNodes.length">
      <el-timeline-item
        v-for="node in activeNodes"
        :key="node.activityId"
        :type="timelineType(node.status)"
        :hollow="node.status !== 1"
        :timestamp="nodeTimestamp(node)"
        timestamp-placement="top"
      >
        <div class="node-header">
          <span class="node-name">{{ node.activityName }}</span>
          <el-tag :type="statusTagType(node.status)" size="small">
            {{ statusText(node.status) }}
          </el-tag>
        </div>
        <div v-if="node.status === 1 && node.candidateUsers?.length" class="node-candidates">
          候选：{{ node.candidateUsers.join('、') }}
        </div>
        <div v-for="task in node.tasks" :key="task.taskId" class="node-task">
          <span class="task-user">{{ task.userName || '-' }}</span>
          <dict-value
            v-if="task.actionType"
            :dict-type="DICT_TYPE.WF_ACTION_TYPE"
            :value="task.actionType"
            class="task-action"
          />
          <span v-if="task.status === 1" class="task-pending">待处理</span>
          <span v-else-if="task.endTime" class="task-time">{{ formatDateTime(task.endTime) }}</span>
          <div v-if="task.comment" class="task-comment">{{ task.comment }}</div>
        </div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="暂无审批进度" :image-size="60" />

    <!-- 未到达节点折叠区 -->
    <div v-if="pendingNodes.length" class="pending-section">
      <el-button link type="info" size="small" @click="showPending = !showPending">
        {{ showPending ? '收起' : `后续 ${pendingNodes.length} 个节点未到达` }}
      </el-button>
      <div v-show="showPending" class="pending-nodes">
        <el-tag
          v-for="node in pendingNodes"
          :key="node.activityId"
          type="info"
          size="small"
          effect="plain"
          class="pending-node"
        >
          {{ node.activityName }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ApprovalNode } from '@/types/workflow'
import { formatDateTime } from '@/utils/dateFormat'
import { DICT_TYPE } from '@/constants/dict'

const props = defineProps<{
  nodes: ApprovalNode[]
}>()

const showPending = ref(false)

const activeNodes = computed(() => props.nodes.filter(node => node.status !== 0))
const pendingNodes = computed(() => props.nodes.filter(node => node.status === 0))

const statusText = (status: number) => {
  if (status === 1) return '进行中'
  if (status === 2) return '已完成'
  return '未到达'
}

const statusTagType = (status: number) => {
  if (status === 1) return 'primary'
  if (status === 2) return 'success'
  return 'info'
}

const timelineType = (status: number) => {
  if (status === 1) return 'primary'
  if (status === 2) return 'success'
  return 'info'
}

const nodeTimestamp = (node: ApprovalNode) => {
  if (node.startTime && node.endTime) {
    return `${formatDateTime(node.startTime)} ~ ${formatDateTime(node.endTime)}`
  }
  return node.startTime ? formatDateTime(node.startTime) : ''
}
</script>

<style scoped lang="scss">
.approval-timeline {
  .node-header {
    display: flex;
    align-items: center;
    gap: 8px;

    .node-name {
      font-weight: 600;
    }
  }

  .node-candidates {
    margin-top: 4px;
    font-size: 12px;
    color: #909399;
  }

  .node-task {
    margin-top: 6px;
    font-size: 13px;

    .task-user {
      font-weight: 500;
    }

    .task-action {
      margin-left: 8px;
    }

    .task-pending {
      margin-left: 8px;
      color: #e6a23c;
      font-size: 12px;
    }

    .task-time {
      margin-left: 8px;
      color: #909399;
      font-size: 12px;
    }

    .task-comment {
      margin-top: 2px;
      padding: 4px 8px;
      background: #f5f7fa;
      border-radius: 4px;
      color: #606266;
    }
  }

  .pending-section {
    margin-top: 12px;

    .pending-nodes {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin-top: 8px;
    }
  }
}
</style>
