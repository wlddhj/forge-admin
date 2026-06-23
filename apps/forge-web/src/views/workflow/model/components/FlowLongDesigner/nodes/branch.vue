<template>
  <div class="branch-wrap">
    <div class="branch-box-wrap">
      <div class="branch-box">
        <el-button class="add-branch" type="success" plain round @click="addTerm">添加条件</el-button>
        <div class="col-box" v-for="(item, index) in nodeConfig.conditionNodes" :key="index">
          <div class="condition-node">
            <div class="condition-node-box">
              <div class="auto-judge" @click="show(index)">
                <div class="sort-left" v-if="index != 0" @click.stop="arrTransfer(index, -1)">
                  <el-icon><ArrowLeft /></el-icon>
                </div>
                <div class="title">
                  <span class="node-title">{{ item.nodeName }}</span>
                  <span class="priority-title">优先级{{ item.priorityLevel }}</span>
                  <el-icon class="close" @click.stop="delTerm(index)"><Close /></el-icon>
                </div>
                <div class="content">
                  <span v-if="toText(nodeConfig, index)">{{ toText(nodeConfig, index) }}</span>
                  <span v-else class="placeholder">请设置条件</span>
                </div>
                <div
                  class="sort-right"
                  v-if="index != nodeConfig.conditionNodes!.length - 1"
                  @click.stop="arrTransfer(index)"
                >
                  <el-icon><ArrowRight /></el-icon>
                </div>
              </div>
              <add-node v-model="item.childNode"></add-node>
            </div>
          </div>
          <slot v-if="item.childNode" :node="item"></slot>
          <div class="top-left-cover-line" v-if="index == 0"></div>
          <div class="bottom-left-cover-line" v-if="index == 0"></div>
          <div class="top-right-cover-line" v-if="index == nodeConfig.conditionNodes!.length - 1"></div>
          <div class="bottom-right-cover-line" v-if="index == nodeConfig.conditionNodes!.length - 1"></div>
        </div>
      </div>
      <add-node v-model="nodeConfig.childNode"></add-node>
    </div>
    <el-drawer title="条件设置" v-model="drawer" destroy-on-close append-to-body :size="600">
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
          <div class="top-tips">满足以下条件时进入当前分支</div>
          <template v-for="(conditionGroup, conditionGroupIdx) in form.conditionList" :key="conditionGroupIdx">
            <div class="or-branch-link-tip" v-if="conditionGroupIdx != 0">或满足</div>
            <div class="condition-group-editor">
              <div class="header">
                <span>条件组 {{ conditionGroupIdx + 1 }}</span>
                <el-icon class="branch-delete-icon" @click="deleteConditionGroup(conditionGroupIdx)"><Delete /></el-icon>
              </div>

              <div class="main-content">
                <div class="condition-content-box cell-box">
                  <div>描述</div>
                  <div>条件字段</div>
                  <div>运算符</div>
                  <div>值</div>
                </div>
                <div class="condition-content" v-for="(condition, idx) in conditionGroup" :key="idx">
                  <div class="condition-relation">
                    <span>{{ idx == 0 ? '当' : '且' }}</span>
                    <el-icon class="branch-delete-icon" @click="deleteConditionList(conditionGroup, idx)"><Delete /></el-icon>
                  </div>
                  <div class="condition-content">
                    <div class="condition-content-box">
                      <el-input v-model="condition.label" placeholder="描述" />
                      <el-input v-model="condition.field" placeholder="条件字段" />
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
              </div>
              <div class="sub-content">
                <el-button link type="primary" @click="addConditionList(conditionGroup)" icon="Plus">添加条件</el-button>
              </div>
            </div>
          </template>
          <el-button style="width: 100%" type="info" icon="Plus" text bg @click="addConditionGroup">
            添加条件组
          </el-button>
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
import { ref, watch, nextTick } from 'vue'
import { ArrowLeft, ArrowRight, Close, Edit, Delete, Plus } from '@element-plus/icons-vue'
import addNode from './addNode.vue'
import type { FlowlongNodeModel, FlowlongConditionNode, FlowlongCondition } from '@/composables/useFlowLongDataTransform'

const props = defineProps<{
  modelValue: FlowlongNodeModel
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: FlowlongNodeModel): void
}>()

const nodeConfig = ref<FlowlongNodeModel>({})
const drawer = ref(false)
const isEditTitle = ref(false)
const nodeTitleRef = ref<HTMLInputElement | null>(null)
const index = ref(0)
const form = ref<FlowlongConditionNode>({})

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      nodeConfig.value = val
    }
  },
  { immediate: true }
)

const show = (idx: number) => {
  index.value = idx
  form.value = JSON.parse(JSON.stringify(nodeConfig.value.conditionNodes![idx]))
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
  nodeConfig.value.conditionNodes![index.value] = form.value
  emit('update:modelValue', nodeConfig.value)
  drawer.value = false
}

const getNodeKey = () => {
  return 'flk' + Date.now()
}

const addTerm = () => {
  const len = nodeConfig.value.conditionNodes!.length + 1
  nodeConfig.value.conditionNodes!.push({
    nodeName: '条件' + len,
    nodeKey: getNodeKey(),
    type: 3,
    priorityLevel: len,
    conditionMode: 1,
    conditionList: [],
    childNode: undefined
  })
}

const delTerm = (idx: number) => {
  nodeConfig.value.conditionNodes!.splice(idx, 1)
  if (nodeConfig.value.conditionNodes!.length == 1) {
    if (nodeConfig.value.childNode) {
      if (nodeConfig.value.conditionNodes![0].childNode) {
        reData(nodeConfig.value.conditionNodes![0].childNode!, nodeConfig.value.childNode)
      } else {
        nodeConfig.value.conditionNodes![0].childNode = nodeConfig.value.childNode
      }
    }
    emit('update:modelValue', nodeConfig.value.conditionNodes![0].childNode || null)
  }
}

const reData = (data: FlowlongConditionNode, addData: FlowlongNodeModel | undefined) => {
  if (!data.childNode) {
    data.childNode = addData
  } else {
    reData(data.childNode!, addData)
  }
}

const arrTransfer = (idx: number, type: number = 1) => {
  nodeConfig.value.conditionNodes![idx] = nodeConfig.value.conditionNodes!.splice(
    idx + type,
    1,
    nodeConfig.value.conditionNodes![idx]
  )[0]
  nodeConfig.value.conditionNodes!.map((item, i) => {
    item.priorityLevel = i + 1
  })
  emit('update:modelValue', nodeConfig.value)
}

const addConditionList = (conditionList: FlowlongCondition[]) => {
  conditionList.push({
    label: '',
    field: '',
    operator: '=',
    value: ''
  })
}

const deleteConditionList = (conditionList: FlowlongCondition[], idx: number) => {
  conditionList.splice(idx, 1)
}

const addConditionGroup = () => {
  form.value.conditionList!.push([])
  addConditionList(form.value.conditionList![form.value.conditionList!.length - 1])
}

const deleteConditionGroup = (idx: number) => {
  form.value.conditionList!.splice(idx, 1)
}

const toText = (nodeConfig: FlowlongNodeModel, idx: number): string | false => {
  const { conditionList } = nodeConfig.conditionNodes![idx]
  if (conditionList && conditionList.length == 1) {
    const text = conditionList
      .map((conditionGroup) => conditionGroup.map((item) => `${item.label}${item.operator}${item.value}`).join(' 和 '))
      .join(' 或 ')
    return text
  } else if (conditionList && conditionList.length > 1) {
    return conditionList.length + '个条件，或满足'
  } else {
    if (idx == nodeConfig.conditionNodes!.length - 1) {
      return '其他条件进入此流程'
    } else {
      return false
    }
  }
}
</script>

<style scoped lang="scss">
.top-tips {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  color: #646a73;
}

.or-branch-link-tip {
  margin: 10px 0;
  color: #646a73;
}

.condition-group-editor {
  user-select: none;
  border-radius: 4px;
  border: 1px solid #e4e5e7;
  position: relative;
  margin-bottom: 16px;

  .branch-delete-icon {
    font-size: 18px;
    cursor: pointer;
    color: #909399;
    &:hover {
      color: #f56c6c;
    }
  }

  .header {
    background-color: #f4f6f8;
    padding: 0 12px;
    font-size: 14px;
    color: #171e31;
    height: 36px;
    display: flex;
    align-items: center;

    span {
      flex: 1;
    }
  }

  .main-content {
    padding: 0 12px;

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

      div {
        width: 100%;
        min-width: 120px;
      }

      div:not(:first-child) {
        margin-left: 16px;
      }
    }

    .cell-box {
      div {
        padding: 16px 0;
        width: 100%;
        min-width: 120px;
        color: #909399;
        font-size: 14px;
        font-weight: 600;
        text-align: center;
      }
    }

    .condition-content {
      display: flex;
      flex-direction: column;

      :deep(.el-input__wrapper) {
        border-top-left-radius: 0;
        border-bottom-left-radius: 0;
      }

      .content {
        flex: 1;
        padding: 0 0 4px 0;
        display: flex;
        align-items: center;
        min-height: 31.6px;
        flex-wrap: wrap;
      }
    }
  }

  .sub-content {
    padding: 12px;
  }
}
</style>