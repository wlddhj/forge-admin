<!--
  条件审批节点（FlowLong TaskType.conditionNode = 3）

  与 approver.vue 的差异: 多了 conditionList 配置,引擎会根据条件是否满足
  决定是否生成该审批人的任务。满足条件时按 setType 配置的审批人走,不满足时跳过。
-->
<template>
  <div class="node-wrap">
    <div class="node-wrap-box" @click="show">
      <div class="title" style="background: #3296fa">
        <el-icon class="icon"><Filter /></el-icon>
        <span>{{ nodeConfig.nodeName }}</span>
        <el-icon class="close" @click.stop="delNode()"><Close /></el-icon>
      </div>
      <div class="content">
        <div>{{ conditionSummary || '请设置条件' }}</div>
        <div v-if="assigneeSummary" style="color: #909399; font-size: 12px; margin-top: 4px">
          审批人: {{ assigneeSummary }}
        </div>
        <span v-if="!conditionSummary && !assigneeSummary" class="placeholder">请选择</span>
      </div>
    </div>
    <add-node v-model="nodeConfig.childNode"></add-node>
    <el-drawer title="条件审批设置" v-model="drawer" destroy-on-close append-to-body :size="640">
      <template #header>
        <div class="node-wrap-drawer__title">
          <label @click="editTitle" v-if="!isEditTitle">
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
            <!-- 条件列表 -->
            <el-form-item label="触发条件（满足条件时由下方审批人审批，否则跳过该节点）">
              <div class="condition-group-editor">
                <div class="main-content">
                  <div class="condition-content-box cell-box">
                    <div>描述</div>
                    <div>条件字段</div>
                    <div>运算符</div>
                    <div>值</div>
                  </div>
                  <div
                    class="condition-content"
                    v-for="(condition, idx) in (form.conditionList && form.conditionList[0]) || []"
                    :key="idx"
                  >
                    <div class="condition-relation">
                      <span>{{ idx == 0 ? '当' : '且' }}</span>
                      <el-icon
                        class="branch-delete-icon"
                        @click="deleteCondition(idx)"
                      ><Delete /></el-icon>
                    </div>
                    <div class="condition-content">
                      <div class="condition-content-box">
                        <el-input v-model="condition.label" placeholder="描述" />
                        <el-input v-model="condition.field" placeholder="如 amount" />
                        <el-select v-model="condition.operator" placeholder="运算符">
                          <el-option label="等于" value="=="></el-option>
                          <el-option label="不等于" value="!="></el-option>
                          <el-option label="大于" value=">"></el-option>
                          <el-option label="大于等于" value=">="></el-option>
                          <el-option label="小于" value="<"></el-option>
                          <el-option label="小于等于" value="<="></el-option>
                          <el-option label="包含" value="include"></el-option>
                          <el-option label="不包含" value="notinclude"></el-option>
                        </el-select>
                        <el-input v-model="condition.value" placeholder="值" />
                      </div>
                    </div>
                  </div>
                  <template v-if="!form.conditionList || !form.conditionList[0] || form.conditionList[0].length === 0">
                    <el-button link type="primary" @click="addCondition" icon="Plus" style="margin: 12px 0 0 0">
                      添加条件
                    </el-button>
                  </template>
                  <template v-else>
                    <el-button link type="primary" @click="addCondition" icon="Plus" style="margin: 12px 0 0 0">
                      添加条件
                    </el-button>
                  </template>
                </div>
              </div>
            </el-form-item>

            <el-divider content-position="left">审批人</el-divider>

            <el-form-item label="审批人员类型">
              <el-select v-model="form.setType" @change="changeSetType">
                <el-option :value="1" label="指定成员"></el-option>
                <el-option :value="2" label="部门负责人"></el-option>
                <el-option :value="3" label="指定角色"></el-option>
                <el-option :value="5" label="发起人自己"></el-option>
                <el-option :value="8" label="表达式"></el-option>
              </el-select>
            </el-form-item>

            <el-form-item v-if="form.setType == 1" label="选择成员">
              <el-button type="primary" icon="Plus" round @click="selectHandle(1, form.nodeAssigneeList)">
                选择人员
              </el-button>
              <div class="tags-list">
                <el-tag v-for="(user, index) in form.nodeAssigneeList" :key="user.id" closable @close="delUser(index)">
                  {{ user.name }}
                </el-tag>
              </div>
            </el-form-item>

            <el-form-item v-if="form.setType == 2" label="指定部门负责人">
              发起人的第
              <el-input-number v-model="form.examineLevel" :min="1" />
              级部门负责人
            </el-form-item>

            <el-form-item v-if="form.setType == 3" label="选择角色">
              <el-button type="primary" icon="Plus" round @click="selectHandle(2, form.nodeAssigneeList)">
                选择角色
              </el-button>
              <div class="tags-list">
                <el-tag
                  v-for="(role, index) in form.nodeAssigneeList"
                  :key="role.id"
                  type="info"
                  closable
                  @close="delUser(index)"
                >
                  {{ role.name }}
                </el-tag>
              </div>
            </el-form-item>

            <el-form-item v-if="form.setType == 5" label="发起人自己">
              <el-alert title="审批人为发起人自己" type="info" :closable="false" />
            </el-form-item>

            <el-form-item v-if="form.setType == 8" label="表达式">
              <el-input v-model="form.expression" placeholder="输入表达式，如：${approver}" />
              <el-alert
                title="表达式将在运行时动态计算审批人"
                type="info"
                :closable="false"
                style="margin-top: 10px"
              />
            </el-form-item>

            <el-divider></el-divider>
            <el-form-item label="多人审批时审批方式">
              <el-radio-group v-model="form.examineMode">
                <p style="width: 100%"><el-radio :value="1">按顺序依次审批</el-radio></p>
                <p style="width: 100%"><el-radio :value="2">会签 (可同时审批，每个人必须审批通过)</el-radio></p>
                <p style="width: 100%"><el-radio :value="3">或签 (有一人审批通过即可)</el-radio></p>
              </el-radio-group>
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
import { ref, watch, nextTick, inject, computed } from 'vue'
import { Filter, Close, Edit, Delete, Plus } from '@element-plus/icons-vue'
import addNode from './addNode.vue'
import type {
  FlowlongNodeModel,
  FlowlongNodeAssignee,
  FlowlongCondition
} from '@/composables/useFlowLongDataTransform'

const props = defineProps<{
  modelValue: FlowlongNodeModel
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: FlowlongNodeModel): void
}>()

const select = inject<(type: number, data: FlowlongNodeAssignee[]) => void>('select')

const nodeConfig = ref<FlowlongNodeModel>({})
const drawer = ref(false)
const isEditTitle = ref(false)
const nodeTitleRef = ref<HTMLInputElement | null>(null)
const form = ref<FlowlongNodeModel>({})

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      nodeConfig.value = val
    }
  },
  { immediate: true }
)

const show = () => {
  form.value = JSON.parse(JSON.stringify(nodeConfig.value))
  // 初始化条件列表结构: [[Condition, ...]]
  if (!form.value.conditionList || !Array.isArray(form.value.conditionList) || form.value.conditionList.length === 0) {
    form.value.conditionList = [[]]
  }
  // 兜底默认 setType
  if (form.value.setType === undefined) {
    form.value.setType = 1
  }
  if (form.value.examineMode === undefined) {
    form.value.examineMode = 1
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
  // 清空空条件组(只有第一个条件组生效,简化模型)
  if (form.value.conditionList) {
    form.value.conditionList = form.value.conditionList.filter(
      (group) => Array.isArray(group) && group.length > 0
    )
    if (form.value.conditionList.length === 0) {
      form.value.conditionList = undefined
    }
  }
  emit('update:modelValue', form.value)
  drawer.value = false
}

const delNode = () => {
  emit('update:modelValue', nodeConfig.value.childNode || null)
}

const delUser = (index: number) => {
  form.value.nodeAssigneeList?.splice(index, 1)
}

const selectHandle = (type: number, data: FlowlongNodeAssignee[]) => {
  select?.(type, data)
}

const changeSetType = () => {
  form.value.nodeAssigneeList = []
}

const addCondition = () => {
  if (!form.value.conditionList || form.value.conditionList.length === 0) {
    form.value.conditionList = [[]]
  }
  const group = form.value.conditionList[0]
  group.push({
    label: '',
    field: '',
    operator: '==',
    value: ''
  })
}

const deleteCondition = (idx: number) => {
  form.value.conditionList![0].splice(idx, 1)
}

const conditionSummary = computed(() => {
  const groups = nodeConfig.value.conditionList
  if (!groups || groups.length === 0) return ''
  const all = groups.flat()
  if (all.length === 0) return ''
  return all.map((c) => `${c.label || c.field || '条件'}${c.operator}${c.value}`).join(' 且 ')
})

const assigneeSummary = computed(() => {
  const node = nodeConfig.value
  if (node.setType === 1 || node.setType === 3) {
    if (node.nodeAssigneeList && node.nodeAssigneeList.length > 0) {
      return node.nodeAssigneeList.map((u) => u.name).join('、')
    }
  } else if (node.setType === 2) {
    return `第${node.examineLevel || 1}级部门负责人`
  } else if (node.setType === 5) {
    return '发起人自己'
  } else if (node.setType === 8) {
    return `表达式: ${node.expression || ''}`
  }
  return ''
})
</script>

<style scoped>
.condition-group-editor {
  user-select: none;
  border-radius: 4px;
  border: 1px solid #e4e5e7;
  position: relative;
  margin-bottom: 8px;
}
.branch-delete-icon {
  font-size: 18px;
  cursor: pointer;
  color: #909399;
}
.branch-delete-icon:hover {
  color: #f56c6c;
}
.main-content {
  padding: 0 12px;
}
.condition-relation {
  color: #9ca2a9;
  display: flex;
  align-items: center;
  height: 36px;
  justify-content: space-between;
  padding: 0 2px;
}
.condition-content-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.condition-content-box div {
  width: 100%;
  min-width: 100px;
}
.condition-content-box div:not(:first-child) {
  margin-left: 8px;
}
.cell-box div {
  padding: 8px 0;
  width: 100%;
  min-width: 100px;
  color: #909399;
  font-size: 12px;
  text-align: center;
}
.condition-content {
  display: flex;
  flex-direction: column;
}
.tags-list {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
