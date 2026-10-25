<!--
  路由分支容器（FlowLong TaskType.routeBranch = 23）

  按条件把流程直接跳转到指定节点（不执行分支子链）。
  引擎语义：ConditionNode.nodeKey 存储 "route:目标节点key"。
-->
<template>
  <div class="branch-wrap">
    <div class="branch-box-wrap">
      <div class="branch-box">
        <el-button class="add-branch" type="success" plain round @click="addTerm">添加路由</el-button>
        <div v-for="(item, idx) in nodeConfig.routeNodes" :key="idx" class="col-box">
          <div class="condition-node">
            <div class="condition-node-box">
              <div class="auto-judge" @click="show(idx)">
                <div v-if="idx != 0" class="sort-left" @click.stop="arrTransfer(idx, -1)">
                  <el-icon><ArrowLeft /></el-icon>
                </div>
                <div class="title">
                  <span class="node-title">{{ item.nodeName }}</span>
                  <el-icon class="close" @click.stop="delTerm(idx)"><Close /></el-icon>
                </div>
                <div class="content">
                  <span>{{ routeSummary(item) }}</span>
                </div>
                <div
                  v-if="idx != nodeConfig.routeNodes!.length - 1"
                  class="sort-right"
                  @click.stop="arrTransfer(idx)"
                >
                  <el-icon><ArrowRight /></el-icon>
                </div>
              </div>
            </div>
          </div>
          <div v-if="idx == 0" class="top-left-cover-line"></div>
          <div v-if="idx == 0" class="bottom-left-cover-line"></div>
          <div v-if="idx == nodeConfig.routeNodes!.length - 1" class="top-right-cover-line"></div>
          <div v-if="idx == nodeConfig.routeNodes!.length - 1" class="bottom-right-cover-line"></div>
        </div>
      </div>
      <add-node :model-value="nodeConfig.childNode ?? null" @update:model-value="nodeConfig.childNode = $event"></add-node>
    </div>
    <el-drawer v-model="drawer" class="wf-node-drawer" title="路由设置" destroy-on-close append-to-body :size="600">
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
            <el-form-item label="跳转目标节点">
              <el-select v-model="form.targetKey" filterable placeholder="选择要跳转到的节点" style="width: 100%">
                <el-option v-for="opt in targetOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-form>
          <div class="top-tips">满足以下条件时跳转（都不满足时按正常流程走后续节点）</div>
          <template v-for="(conditionGroup, conditionGroupIdx) in form.conditionList" :key="conditionGroupIdx">
            <div v-if="conditionGroupIdx != 0" class="or-branch-link-tip">或满足</div>
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
                <div v-for="(condition, idx) in conditionGroup" :key="idx" class="condition-content">
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
                <el-button link type="primary" icon="Plus" @click="addConditionList(conditionGroup)">添加条件</el-button>
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
import { ref, watch, nextTick, computed, inject, type Ref } from 'vue'
import { ArrowLeft, ArrowRight, Close, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import addNode from './addNode.vue'
import { useNodeKeyOptions } from '@/composables/useNodeKeyOptions'
import type { FlowlongNodeModel, FlowlongConditionNode, FlowlongCondition } from '@/composables/useFlowLongDataTransform'

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
const index = ref(0)
const form = ref<FlowlongConditionNode & { targetKey?: string }>({} as FlowlongConditionNode)

// 设计器根引用（目标节点下拉数据源）
const designerRoot = inject<Ref<FlowlongNodeModel | null> | null>('designerRoot', null)
const { collect } = useNodeKeyOptions()

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      nodeConfig.value = val
    }
  },
  { immediate: true }
)

// 目标选项排除路由容器自身子树，避免跳回路由内部成环
const targetOptions = computed(() =>
  collect(designerRoot?.value ?? null, nodeConfig.value.nodeKey)
    // 排除发起人节点（跳到发起人语义不清）
    .filter(opt => !opt.value.startsWith('start_'))
)

const show = (idx: number) => {
  index.value = idx
  const raw = JSON.parse(JSON.stringify(nodeConfig.value.routeNodes![idx]))
  // nodeKey 形如 route:targetKey，抽屉内编辑目标 key
  form.value = { ...raw, targetKey: raw.nodeKey?.startsWith('route:') ? raw.nodeKey.slice('route:'.length) : '' }
  if (!form.value.conditionList) {
    form.value.conditionList = []
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
  if (!form.value.targetKey) {
    ElMessage.warning('请选择跳转目标节点')
    return
  }
  form.value.nodeKey = `route:${form.value.targetKey}`
  const { targetKey: _ignored, ...saved } = form.value
  nodeConfig.value.routeNodes![index.value] = saved as FlowlongConditionNode
  emit('update:modelValue', nodeConfig.value)
  drawer.value = false
}

const getNodeKey = () => {
  return 'flk' + Date.now() + Math.random().toString(36).slice(2, 6)
}

const addTerm = () => {
  const len = nodeConfig.value.routeNodes!.length + 1
  nodeConfig.value.routeNodes!.push({
    nodeName: '路由' + len,
    nodeKey: getNodeKey(),
    type: 3,
    priorityLevel: len,
    conditionMode: 1,
    conditionList: [],
    childNode: undefined
  })
  emit('update:modelValue', nodeConfig.value)
}

const delTerm = (idx: number) => {
  nodeConfig.value.routeNodes!.splice(idx, 1)
  if (nodeConfig.value.routeNodes!.length == 0) {
    // 路由全部删除后容器自删，保留容器后续节点
    emit('update:modelValue', nodeConfig.value.childNode || null)
  } else {
    nodeConfig.value.routeNodes!.map((item, i) => {
      item.priorityLevel = i + 1
    })
    emit('update:modelValue', nodeConfig.value)
  }
}

const arrTransfer = (idx: number, type: number = 1) => {
  nodeConfig.value.routeNodes![idx] = nodeConfig.value.routeNodes!.splice(
    idx + type,
    1,
    nodeConfig.value.routeNodes![idx]
  )[0]
  nodeConfig.value.routeNodes!.map((item, i) => {
    item.priorityLevel = i + 1
  })
  emit('update:modelValue', nodeConfig.value)
}

const addConditionList = (conditionList: FlowlongCondition[]) => {
  conditionList.push({ label: '', field: '', operator: '=', value: '' })
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

const targetName = (nodeKey: string | undefined) => {
  if (!nodeKey?.startsWith('route:')) return ''
  return targetOptions.value.find(opt => opt.value === nodeKey.slice('route:'.length))?.label || nodeKey.slice('route:'.length)
}

const routeSummary = (item: FlowlongConditionNode): string => {
  const target = targetName(item.nodeKey)
  if (!target) return '请设置跳转目标'
  const conditionCount = item.conditionList?.length || 0
  return conditionCount > 0 ? `满足 ${conditionCount} 组条件 → ${target}` : `无条件 → ${target}`
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
    }
  }

  .sub-content {
    padding: 12px;
  }
}
</style>
