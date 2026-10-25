<template>
  <div class="add-node-btn-box">
    <div class="add-node-btn">
      <el-popover placement="right-start" :width="270" trigger="click" :hide-after="0" :show-after="0">
        <template #reference>
          <el-button type="primary" icon="Plus" circle></el-button>
        </template>
        <div class="add-node-popover-body">
          <ul>
            <li>
              <el-icon style="color: #ff943e" @click="addType(1)"><UserFilled /></el-icon>
              <p>审批节点</p>
            </li>
            <li>
              <el-icon style="color: #3296fa" @click="addType(2)"><Promotion /></el-icon>
              <p>抄送节点</p>
            </li>
            <li>
              <el-icon style="color: #15BC83" @click="addType(4)"><Share /></el-icon>
              <p>条件分支</p>
            </li>
            <li>
              <el-icon style="color: #e6a23c" @click="addType(3)"><Filter /></el-icon>
              <p>条件审批</p>
            </li>
            <li>
              <el-icon style="color: #9b59b6" @click="addType(7)"><Lightning /></el-icon>
              <p>触发器任务</p>
            </li>
            <li>
              <el-icon style="color: #13c2c2" @click="addType(8)"><Operation /></el-icon>
              <p>并行分支</p>
            </li>
            <li>
              <el-icon style="color: #722ed1" @click="addType(9)"><Switch /></el-icon>
              <p>包容分支</p>
            </li>
            <li>
              <el-icon style="color: #2f54eb" @click="addType(5)"><Connection /></el-icon>
              <p>子流程</p>
            </li>
            <li>
              <el-icon style="color: #fa8c16" @click="addType(6)"><Clock /></el-icon>
              <p>定时器</p>
            </li>
            <li>
              <el-icon style="color: #52c41a" @click="addType(30)"><CircleCheck /></el-icon>
              <p>自动通过</p>
            </li>
            <li>
              <el-icon style="color: #f5222d" @click="addType(31)"><CircleClose /></el-icon>
              <p>自动拒绝</p>
            </li>
            <li>
              <el-icon style="color: #faad14" @click="addType(23)"><Guide /></el-icon>
              <p>路由分支</p>
            </li>
          </ul>
        </div>
      </el-popover>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  UserFilled, Promotion, Share, Filter, Lightning,
  Operation, Switch, Connection, Clock, CircleCheck, CircleClose, Guide
} from '@element-plus/icons-vue'
import type { FlowlongNodeModel, FlowlongConditionNode } from '@/composables/useFlowLongDataTransform'

const props = defineProps<{
  modelValue: FlowlongNodeModel | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: FlowlongNodeModel): void
}>()

// 随机后缀防止同毫秒连点撞 key（引擎以 nodeKey 寻址）
const getNodeKey = () => {
  return 'flk' + Date.now() + Math.random().toString(36).slice(2, 6)
}

const newBranchItems = (count: number): FlowlongConditionNode[] =>
  Array.from({ length: count }, (_, i) => ({
    nodeName: `分支${i + 1}`,
    nodeKey: getNodeKey(),
    type: 3,
    priorityLevel: i + 1,
    conditionMode: 1,
    conditionList: [] as never[][],
    childNode: undefined
  }))

const addType = (type: number) => {
  let node = {} as FlowlongNodeModel

  if (type == 1) {
    node = {
      nodeName: '审核人',
      nodeKey: getNodeKey(),
      type: 1, // 节点类型
      setType: 1, // 审核人类型 1，选择成员 3，选择角色
      nodeAssigneeList: [], // 审核人员，根据 setType 确定成员还是角色
      examineLevel: 1, // 指定主管层级
      directorLevel: 1, // 自定义连续主管审批层级
      selectMode: 1, // 发起人自选类型
      termAuto: false, // 审批期限超时自动审批
      term: 0, // 审批期限
      termMode: 1, // 审批期限超时后执行类型
      examineMode: 1, // 多人审批时审批方式
      directorMode: 0, // 连续主管审批方式
      childNode: props.modelValue || undefined
    }
  } else if (type == 2) {
    node = {
      nodeName: '抄送人',
      nodeKey: getNodeKey(),
      type: 2,
      userSelectFlag: true,
      nodeAssigneeList: [],
      childNode: props.modelValue || undefined
    }
  } else if (type == 4) {
    node = {
      nodeName: '条件路由',
      nodeKey: getNodeKey(),
      type: 4,
      conditionNodes: newBranchItems(2),
      childNode: props.modelValue || undefined
    }
  } else if (type == 3) {
    // 条件审批节点: 满足条件时由该审批人审批,否则跳过
    node = {
      nodeName: '条件审批',
      nodeKey: getNodeKey(),
      type: 3,
      setType: 1,
      nodeAssigneeList: [],
      examineMode: 1,
      conditionList: [[]],
      childNode: props.modelValue || undefined
    }
  } else if (type == 7) {
    // 触发器任务节点: 默认走 expression 模式
    node = {
      nodeName: '触发器',
      nodeKey: getNodeKey(),
      type: 7,
      triggerType: 'expression',
      triggerExpression: '${true}',
      continueOnError: false,
      childNode: props.modelValue || undefined
    }
  } else if (type == 8) {
    // 并行分支：全部分支同时执行
    node = {
      nodeName: '并行分支',
      nodeKey: getNodeKey(),
      type: 8,
      parallelNodes: newBranchItems(2),
      childNode: props.modelValue || undefined
    }
  } else if (type == 9) {
    // 包容分支：满足条件的分支全部执行（建议配置一条无条件兜底分支）
    node = {
      nodeName: '包容分支',
      nodeKey: getNodeKey(),
      type: 9,
      inclusiveNodes: newBranchItems(2),
      childNode: props.modelValue || undefined
    }
  } else if (type == 5) {
    // 子流程：进入节点时自动发起指定流程，结束后回主流程
    node = {
      nodeName: '子流程',
      nodeKey: getNodeKey(),
      type: 5,
      callProcess: '',
      childNode: props.modelValue || undefined
    }
  } else if (type == 6) {
    // 定时器：等待指定时长后自动继续
    node = {
      nodeName: '定时器',
      nodeKey: getNodeKey(),
      type: 6,
      timerMode: 'duration',
      timerValue: 1,
      timerUnit: 'h',
      childNode: props.modelValue || undefined
    }
  } else if (type == 30 || type == 31) {
    // 自动通过/拒绝：仅可在分支内使用且无后续节点（引擎约束）
    node = {
      nodeName: type == 30 ? '自动通过' : '自动拒绝',
      nodeKey: getNodeKey(),
      type,
      childNode: props.modelValue || undefined
    }
  } else if (type == 23) {
    // 路由分支：按条件跳转到指定节点
    node = {
      nodeName: '路由分支',
      nodeKey: getNodeKey(),
      type: 23,
      routeNodes: newBranchItems(1),
      childNode: props.modelValue || undefined
    }
  }

  emit('update:modelValue', node)
}
</script>

<style></style>
