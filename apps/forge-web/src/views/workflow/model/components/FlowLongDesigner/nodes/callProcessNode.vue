<!--
  子流程节点（FlowLong TaskType.callProcess = 5）

  进入节点时自动发起指定流程（同步等待），子流程结束后回到主流程继续。
  callProcess 存储格式："processId:processName"
-->
<template>
  <div class="node-wrap">
    <div class="node-wrap-box" @click="show">
      <div class="title" style="background: #2f54eb">
        <el-icon class="icon"><Connection /></el-icon>
        <span>{{ nodeConfig.nodeName }}</span>
        <el-icon class="close" @click.stop="delNode()"><Close /></el-icon>
      </div>
      <div class="content">
        <span>{{ processName || '请选择子流程' }}</span>
      </div>
    </div>
    <add-node :model-value="nodeConfig.childNode ?? null" @update:model-value="nodeConfig.childNode = $event"></add-node>
    <el-drawer v-model="drawer" class="wf-node-drawer" title="子流程设置" destroy-on-close append-to-body :size="480">
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
            <el-form-item label="选择子流程（仅可用状态的流程）">
              <el-select
                v-model="selectedProcessId"
                filterable
                remote
                clearable
                reserve-keyword
                placeholder="输入流程名称搜索"
                :remote-method="loadProcesses"
                :loading="processLoading"
                style="width: 100%"
              >
                <el-option
                  v-for="p in processOptions"
                  :key="p.id"
                  :label="`${p.name}（v${p.version}）`"
                  :value="p.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-alert
                title="主流程到达该节点后自动发起子流程并等待其结束，子流程结束后回到本节点继续向下流转。请避免子流程内部再引用本主流程形成循环。"
                type="warning"
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
import { Connection, Close, Edit } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import addNode from './addNode.vue'
import { processDefinitionApi } from '@/api/workflow/process-definition'
import type { ProcessDefinition } from '@/types/workflow'
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

const processOptions = ref<ProcessDefinition[]>([])
const processLoading = ref(false)
const selectedProcessId = ref<string>('')

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      nodeConfig.value = val
    }
  },
  { immediate: true }
)

// callProcess = "processId:processName"（流程名本身可含冒号，拆分取第一段为 id）
const processName = computed(() => {
  const raw = nodeConfig.value.callProcess
  if (!raw) return ''
  return raw.substring(raw.indexOf(':') + 1)
})

const loadProcesses = async (query: string) => {
  processLoading.value = true
  try {
    const res = await processDefinitionApi.page({ pageNum: 1, pageSize: 50, name: query || undefined })
    // 仅可选可用状态的流程
    processOptions.value = (res.list || []).filter(p => p.processState === 1)
  } finally {
    processLoading.value = false
  }
}

const show = async () => {
  form.value = JSON.parse(JSON.stringify(nodeConfig.value))
  const raw = form.value.callProcess || ''
  selectedProcessId.value = raw ? raw.split(':')[0] : ''
  drawer.value = true
  loadProcesses('')
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
  if (!selectedProcessId.value) {
    ElMessage.warning('请选择子流程')
    return
  }
  const selected = processOptions.value.find(p => String(p.id) === String(selectedProcessId.value))
  form.value.callProcess = `${selectedProcessId.value}:${selected?.name || selectedProcessId.value}`
  emit('update:modelValue', form.value)
  drawer.value = false
}

const delNode = () => {
  emit('update:modelValue', nodeConfig.value.childNode || null)
}
</script>
