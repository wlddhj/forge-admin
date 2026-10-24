<template>
  <div class="agent-timeline">
    <el-timeline>
      <el-timeline-item
        v-for="(item, index) in steps"
        :key="index"
        :type="item.tagType"
        :hollow="item.running"
        :timestamp="item.timestamp"
        placement="top"
      >
        <div class="step-head">
          <el-icon class="step-icon"><component :is="item.icon" /></el-icon>
          <span class="step-title">{{ item.title }}</span>
          <el-tag v-if="item.status" :type="item.tagType" size="small">{{ item.statusLabel }}</el-tag>
          <el-tag v-if="item.cacheHit" type="success" size="small" effect="plain">缓存命中</el-tag>
          <span v-if="item.duration" class="step-duration">{{ item.duration }}ms</span>
        </div>
        <div v-if="item.detail" class="step-detail" :class="{ thought: item.thought }">{{ item.detail }}</div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AgentEventFrame } from '@/api/ai/agent'

defineOptions({ name: 'AgentStepTimeline' })

const props = defineProps<{ frames: AgentEventFrame[] }>()

interface TimelineItem {
  title: string
  status?: string
  statusLabel?: string
  detail?: string
  duration?: number
  tagType: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  icon: string
  running: boolean
  timestamp: string
  /** 思考流条目(THOUGHT 聚合,STEP_END 归档) */
  thought?: boolean
  /** 图片缓存命中标注 */
  cacheHit?: boolean
}

const NODE_TITLES: Record<string, string> = {
  multimodal: '图片理解',
  decision: '工具决策',
  tools: '工具执行',
  summary: '总结回答',
  supervisor: '调度决策',
  expert_decide: '专家决策',
  expert_tools: '专家工具执行',
  expert_approval: '专家人工确认',
  expert_report: '专家汇报'
}

const steps = computed<TimelineItem[]>(() => {
  const items: TimelineItem[] = []
  const nodeTitle = (frame: AgentEventFrame) => frame.name || NODE_TITLES[frame.node || ''] || frame.node || '步骤'
  for (const frame of props.frames) {
    const d = frame.data || {}
    if (frame.event === 'STEP_START') {
      items.push({
        title: nodeTitle(frame),
        status: 'RUNNING',
        statusLabel: '执行中',
        detail: d.model ? `模型: ${d.model}` : undefined,
        tagType: 'primary',
        icon: 'Loading',
        running: true,
        timestamp: `#${d.seq ?? ''}`
      })
    } else if (frame.event === 'THOUGHT') {
      // 决策思考流:同节点连续增量聚合为一个条目,2000 字符封顶防洪泛
      const title = nodeTitle(frame)
      const existing = [...items].reverse().find(i => i.thought && i.running && i.title === title)
      const delta = String(d.delta ?? '')
      if (existing) {
        existing.detail = ((existing.detail || '') + delta).slice(0, 2000)
      } else {
        items.push({
          title,
          status: 'THINKING',
          statusLabel: '思考中',
          detail: delta.slice(0, 2000),
          tagType: 'info',
          icon: 'Loading',
          running: true,
          timestamp: '',
          thought: true
        })
      }
    } else if (frame.event === 'STEP_END') {
      const title = nodeTitle(frame)
      // 先归档同节点的思考流条目(避免被下方运行条目匹配误吞)
      items.filter(i => i.thought && i.running && i.title === title).forEach(i => {
        i.running = false
        i.status = 'DONE'
        i.statusLabel = '思考完成'
        i.icon = 'ChatDotRound'
      })
      const lastStart = [...items].reverse().find(i => i.running && !i.thought && i.title === title)
      if (lastStart) {
        lastStart.running = false
        lastStart.status = d.status === 'SKIPPED' ? 'SKIPPED' : 'SUCCESS'
        lastStart.statusLabel = d.status === 'SKIPPED' ? '跳过' : d.status === 'FAILED' ? '失败' : '完成'
        lastStart.tagType = d.status === 'FAILED' ? 'danger' : d.status === 'SKIPPED' ? 'info' : 'success'
        lastStart.icon = d.status === 'FAILED' ? 'CircleCloseFilled' : 'CircleCheckFilled'
        lastStart.duration = d.durationMs
        if (d.cacheHit) lastStart.cacheHit = true
        if (d.output) lastStart.detail = String(d.output).slice(0, 200)
      } else {
        items.push({
          title,
          status: d.status === 'FAILED' ? 'FAILED' : 'SUCCESS',
          statusLabel: d.status === 'SKIPPED' ? '跳过' : '完成',
          detail: d.output ? String(d.output).slice(0, 200) : undefined,
          duration: d.durationMs,
          tagType: d.status === 'FAILED' ? 'danger' : 'success',
          icon: 'CircleCheckFilled',
          running: false,
          timestamp: `#${d.seq ?? ''}`,
          cacheHit: Boolean(d.cacheHit)
        })
      }
    } else if (frame.event === 'TOOL_CALL') {
      items.push({
        title: `调用工具: ${frame.name}`,
        detail: d.args ? JSON.stringify(d.args).slice(0, 200) : undefined,
        tagType: 'warning',
        icon: 'SetUp',
        running: true,
        timestamp: ''
      })
    } else if (frame.event === 'TOOL_RESULT') {
      items.push({
        title: `工具结果: ${frame.name}`,
        status: d.status,
        statusLabel: d.status === 'FAILED' ? '失败' : '完成',
        detail: (d.error || d.output || '').toString().slice(0, 200),
        duration: d.durationMs,
        tagType: d.status === 'FAILED' ? 'danger' : 'success',
        icon: d.status === 'FAILED' ? 'CircleCloseFilled' : 'CircleCheckFilled',
        running: false,
        timestamp: ''
      })
    } else if (frame.event === 'APPROVAL_REQUIRED') {
      items.push({
        title: '等待人工确认',
        detail: '该操作被判定为高危（如 SQL 查询），已生成待办，请在「AI 管理 → 人工待办」中处理',
        tagType: 'warning',
        icon: 'WarningFilled',
        running: false,
        timestamp: ''
      })
    }
  }
  return items
})
</script>

<style scoped lang="scss">
.agent-timeline {
  .step-head {
    display: flex;
    align-items: center;
    gap: 6px;

    .step-icon {
      animation: none;
    }

    .step-title {
      font-weight: 500;
    }

    .step-duration {
      color: var(--el-text-color-secondary);
      font-size: 12px;
    }
  }

  .step-detail {
    margin-top: 4px;
    color: var(--el-text-color-regular);
    font-size: 12px;
    word-break: break-all;
    max-height: 120px;
    overflow-y: auto;

    &.thought {
      color: var(--el-text-color-secondary);
      font-style: italic;
    }
  }
}
</style>
