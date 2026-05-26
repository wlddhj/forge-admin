<template>
  <div class="bpmn-properties-panel">
    <div v-if="!selectedNode" class="empty-tip">
      <el-empty description="请选择一个节点" :image-size="60" />
    </div>
    <div v-else class="properties-form">
      <h4 class="panel-title">{{ nodeTypeLabel }}</h4>
      <el-form label-width="80px" size="small">
        <el-form-item label="节点ID">
          <el-input :model-value="selectedNode.id" disabled />
        </el-form-item>
        <el-form-item label="节点名称">
          <el-input v-model="formData.name" @change="handleNameChange" />
        </el-form-item>
        <template v-if="isTaskNode">
          <el-form-item label="候选人策略">
            <el-select
              v-model="formData.candidateStrategy"
              clearable
              placeholder="选择候选人策略"
              style="width: 100%"
              @change="handleStrategyChange"
            >
              <el-option
                v-for="s in strategyList"
                :key="s.strategy"
                :label="s.description"
                :value="s.strategy"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="formData.candidateStrategy" label="策略参数">
            <!-- 角色策略: 多选角色 -->
            <el-select
              v-if="formData.candidateStrategy === 10"
              v-model="strategyParamIds"
              filterable
              multiple
              clearable
              placeholder="选择角色"
              style="width: 100%"
              @change="handleStrategyParamChange"
            >
              <el-option
                v-for="role in roleList"
                :key="role.id"
                :label="role.roleName"
                :value="String(role.id)"
              />
            </el-select>
            <!-- 部门成员策略: 多选部门 -->
            <el-tree-select
              v-else-if="formData.candidateStrategy === 20"
              v-model="strategyParamIds"
              :data="deptTree"
              multiple
              filterable
              clearable
              check-strictly
              placeholder="选择部门"
              style="width: 100%"
              @change="handleStrategyParamChange"
            />
            <!-- 部门负责人策略: 多选部门 -->
            <el-tree-select
              v-else-if="formData.candidateStrategy === 21"
              v-model="strategyParamIds"
              :data="deptTree"
              multiple
              filterable
              clearable
              check-strictly
              placeholder="选择部门"
              style="width: 100%"
              @change="handleStrategyParamChange"
            />
            <!-- 用户策略: 多选用户 -->
            <el-select
              v-else-if="formData.candidateStrategy === 30"
              v-model="strategyParamIds"
              filterable
              multiple
              clearable
              placeholder="选择用户"
              style="width: 100%"
              @change="handleStrategyParamChange"
            >
              <el-option
                v-for="user in userList"
                :key="user.id"
                :label="`${user.nickname}(${user.username})`"
                :value="String(user.id)"
              />
            </el-select>
            <!-- 表达式策略: 输入框 -->
            <el-input
              v-else-if="formData.candidateStrategy === 60"
              v-model="formData.candidateParam"
              placeholder="${initiator} 或 ${approved == true}"
              @change="handlePropertyChange"
            />
          </el-form-item>
          <el-form-item label="表单标识">
            <el-input v-model="formData.formKey" @change="handlePropertyChange" />
          </el-form-item>
          <el-form-item label="关联表单">
            <el-select
              v-model="formData.formId"
              filterable
              clearable
              placeholder="选择关联表单"
              style="width: 100%"
              @change="handlePropertyChange"
            >
              <el-option
                v-for="form in formList"
                :key="form.id"
                :label="form.name"
                :value="form.id"
              />
            </el-select>
          </el-form-item>
        </template>
        <template v-if="isSequenceFlow">
          <el-form-item label="条件表达式">
            <el-input v-model="formData.conditionExpression" type="textarea" :rows="2" placeholder="${approved == true}" @change="handlePropertyChange" />
          </el-form-item>
        </template>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import type LogicFlow from '@logicflow/core'
import { getUserList } from '@/api/system'
import { getRoleList } from '@/api/system'
import { getDeptTree } from '@/api/system'
import { formApi } from '@/api/workflow/form'
import type { WfFormSimple } from '@/api/workflow/form'

const props = defineProps<{
  lf: LogicFlow | null
}>()

const selectedNode = ref<any>(null)
const userList = ref<any[]>([])
const roleList = ref<any[]>([])
const deptTree = ref<any[]>([])
const formList = ref<WfFormSimple[]>([])

// 候选人策略列表
const strategyList = [
  { strategy: 10, description: '指定角色' },
  { strategy: 20, description: '部门成员' },
  { strategy: 21, description: '部门负责人' },
  { strategy: 30, description: '指定用户' },
  { strategy: 60, description: '表达式' },
]

const nodeTypeMap: Record<string, string> = {
  'bpmn:startEvent': '开始事件',
  'bpmn:endEvent': '结束事件',
  'bpmn:userTask': '用户任务',
  'bpmn:serviceTask': '系统任务',
  'bpmn:exclusiveGateway': '排他网关',
  'bpmn:parallelGateway': '并行网关',
  'bpmn:inclusiveGateway': '包容网关',
  'bpmn:subProcess': '子流程',
  'bpmn:sequenceFlow': '顺序流',
  'bpmn:boundaryEvent': '边界事件',
  'bpmn:intermediateCatchEvent': '中间捕获事件',
}

const nodeTypeLabel = computed(() => nodeTypeMap[selectedNode.value?.type] || selectedNode.value?.type || '')
const isTaskNode = computed(() => ['bpmn:userTask', 'bpmn:serviceTask'].includes(selectedNode.value?.type))
const isSequenceFlow = computed(() => selectedNode.value?.type === 'bpmn:sequenceFlow')

const formData = ref({
  name: '',
  assignee: '',
  candidateUsers: '',
  candidateGroups: '',
  candidateStrategy: undefined as number | undefined,
  candidateParam: '',
  formKey: '',
  formId: undefined as number | undefined,
  conditionExpression: '',
})

// 策略参数多选值 (用于角色/部门/用户多选)
const strategyParamIds = computed({
  get: () => formData.value.candidateParam ? formData.value.candidateParam.split(',').filter(Boolean) : [],
  set: () => {},
})

const handleNameChange = () => {
  if (!props.lf || !selectedNode.value) return
  props.lf.updateText(selectedNode.value.id, formData.value.name)
}

const handleStrategyChange = () => {
  formData.value.candidateParam = ''
  handlePropertyChange()
}

const handleStrategyParamChange = (val: string[]) => {
  formData.value.candidateParam = val.join(',')
  handlePropertyChange()
}

const handlePropertyChange = () => {
  if (!props.lf || !selectedNode.value) return
  props.lf.setProperties(selectedNode.value.id, {
    ...selectedNode.value.properties,
    assignee: formData.value.assignee,
    candidateUsers: formData.value.candidateUsers,
    candidateGroups: formData.value.candidateGroups,
    candidateStrategy: formData.value.candidateStrategy,
    candidateParam: formData.value.candidateParam,
    formKey: formData.value.formKey,
    formId: formData.value.formId,
    conditionExpression: formData.value.conditionExpression,
  })
}

watch(
  () => props.lf,
  (lf) => {
    if (!lf) return
    lf.on('node:click', ({ data }: any) => {
      selectedNode.value = data
      loadFormData(data)
    })
    lf.on('edge:click', ({ data }: any) => {
      selectedNode.value = data
      loadFormData(data)
    })
    lf.on('blank:click', () => {
      selectedNode.value = null
    })
  },
  { immediate: true },
)

const loadFormData = (data: any) => {
  const nodeProps = data.properties || {}
  formData.value = {
    name: data.text?.value || data.text || '',
    assignee: nodeProps.assignee || '',
    candidateUsers: nodeProps.candidateUsers || '',
    candidateGroups: nodeProps.candidateGroups || '',
    candidateStrategy: nodeProps.candidateStrategy || undefined,
    candidateParam: nodeProps.candidateParam || '',
    formKey: nodeProps.formKey || '',
    formId: nodeProps.formId || undefined,
    conditionExpression: nodeProps.conditionExpression || '',
  }
}

const loadUsers = async () => {
  try {
    const res = await getUserList({ pageNum: 1, pageSize: 200, status: 1 })
    userList.value = res.list || []
  } catch { /* ignore */ }
}

const loadRoles = async () => {
  try {
    const res = await getRoleList({ pageNum: 1, pageSize: 100, status: 1 })
    roleList.value = res.list || []
  } catch { /* ignore */ }
}

const loadDepts = async () => {
  try {
    deptTree.value = await getDeptTree()
  } catch { /* ignore */ }
}

const loadForms = async () => {
  try {
    formList.value = await formApi.listAll()
  } catch { /* ignore */ }
}

onMounted(() => {
  loadUsers()
  loadRoles()
  loadDepts()
  loadForms()
})
</script>

<style scoped>
.bpmn-properties-panel {
  width: 320px;
  border-left: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  overflow-y: auto;
}
.empty-tip {
  padding: 40px 20px;
}
.panel-title {
  padding: 12px 16px;
  margin: 0;
  border-bottom: 1px solid var(--el-border-color-light);
  font-size: 14px;
  color: var(--el-text-color-primary);
}
.properties-form {
  padding: 16px;
}
</style>
