<!--
  定时器节点（FlowLong TaskType.timer = 6）

  两种模式（对应引擎 extendConfig.time）：
  - duration : 时长式 "数值:单位"（如 2:h = 等待2小时）
  - clock    : 时刻式 "HH:mm:ss"（到达该时刻自动继续）
-->
<template>
  <div class="node-wrap">
    <div class="node-wrap-box" @click="show">
      <div class="title" style="background: #fa8c16">
        <el-icon class="icon"><Clock /></el-icon>
        <span>{{ nodeConfig.nodeName }}</span>
        <el-icon class="close" @click.stop="delNode()"><Close /></el-icon>
      </div>
      <div class="content">
        <span>{{ timeSummary || '请设置等待时间' }}</span>
      </div>
    </div>
    <add-node :model-value="nodeConfig.childNode ?? null" @update:model-value="nodeConfig.childNode = $event"></add-node>
    <el-drawer v-model="drawer" title="定时器设置" destroy-on-close append-to-body :size="480">
      <template #header>
        <div class="node-wrap-drawer__title">
          <label v-if="!isEditTitle" @click="editTitle">
            {{ form.nodeName }}
            <el-icon class="node-wrap-drawer__title-edit"><Edit /></el-icon>
          </label>
          <el-input
            v-if="isEditTitle"
            ref="nodeTitleRef"
            v-model="form.nodeName"
            clearable
            @blur="saveTitle"
            @keyup.enter="saveTitle"
          ></el-input>
        </div>
      </template>
      <el-container>
        <el-main style="padding: 0 20px 20px 20px">
          <el-form label-position="top">
            <el-form-item label="等待方式">
              <el-radio-group v-model="form.timerMode">
                <el-radio value="duration">等待时长</el-radio>
                <el-radio value="clock">到达时刻</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="form.timerMode === 'duration'" label="等待时长">
              <div style="display: flex; gap: 8px; width: 100%">
                <el-input-number v-model="form.timerValue" :min="1" :max="9999" style="flex: 1" />
                <el-select v-model="form.timerUnit" style="width: 100px">
                  <el-option label="天" value="d" />
                  <el-option label="小时" value="h" />
                  <el-option label="分钟" value="m" />
                </el-select>
              </div>
            </el-form-item>
            <el-form-item v-else label="到达时刻（HH:mm:ss）">
              <el-input v-model="form.timerClock" placeholder="例如 17:30:00" />
            </el-form-item>
            <el-form-item>
              <el-alert
                title="流程到达该节点后暂停，到点由超时调度任务自动完成并继续流转。"
                type="info"
                :closable="false"
              />
            </el-form-item>
          </el-form>
        </el-main>
        <el-footer>
          <el-button type="primary" @click="save">保存</el-button>
          <el-button @click="drawer = false">取消</el-button>
        </el-footer>
      </el-container>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import { Clock, Close, Edit } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import addNode from './addNode.vue'
import type { FlowlongNodeModel } from '@/composables/useFlowLongDataTransform'

const props = defineProps<{
  modelValue: FlowlongNodeModel
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: FlowlongNodeModel | null): void
}>()

const nodeConfig = ref<FlowlongNodeModel>({} as FlowlongNodeModel)
const drawer = ref(false)
const isEditTitle = ref(false)
const nodeTitleRef = ref<HTMLInputElement | null>(null)
const form = ref<FlowlongNodeModel>({} as FlowlongNodeModel)

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      nodeConfig.value = val
    }
  },
  { immediate: true }
)

const unitText: Record<string, string> = { d: '天', h: '小时', m: '分钟' }

const timeSummary = computed(() => {
  const node = nodeConfig.value
  if (node.timerMode === 'clock') return `到达 ${node.timerClock || '--'} 继续`
  if (node.timerMode === 'duration' && node.timerValue) {
    return `等待 ${node.timerValue} ${unitText[node.timerUnit || 'h']}后继续`
  }
  return ''
})

const show = () => {
  form.value = JSON.parse(JSON.stringify(nodeConfig.value))
  if (form.value.timerMode === undefined) {
    form.value.timerMode = 'duration'
    form.value.timerValue = 1
    form.value.timerUnit = 'h'
  }
  drawer.value = true
}

const editTitle = () => {
  isEditTitle.value = true
  nextTick(() => {
    nodeTitleRef.value?.focus()
  })
}

const saveTitle = () => {
  isEditTitle.value = false
}

const save = () => {
  if (form.value.timerMode === 'duration' && (!form.value.timerValue || form.value.timerValue <= 0)) {
    ElMessage.warning('请填写有效的等待时长')
    return
  }
  if (form.value.timerMode === 'clock' && !/^\d{2}:\d{2}:\d{2}$/.test(form.value.timerClock || '')) {
    ElMessage.warning('时刻格式应为 HH:mm:ss，例如 17:30:00')
    return
  }
  emit('update:modelValue', form.value)
  drawer.value = false
}

const delNode = () => {
  emit('update:modelValue', nodeConfig.value.childNode || null)
}
</script>
