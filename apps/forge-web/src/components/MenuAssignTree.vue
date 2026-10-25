<template>
  <div v-loading="loading" class="menu-assign-tree">
    <div class="menu-assign-toolbar">
      <el-checkbox v-model="checkAll" @change="handleCheckAll">全选</el-checkbox>
      <el-button link type="primary" @click="handleExpandToggle(!expandAll)">
        {{ expandAll ? '折叠全部' : '展开全部' }}
      </el-button>
      <span class="menu-assign-count">
        菜单 {{ checkedMenuCount }}/{{ totalMenuCount }}
        <template v-if="totalButtonCount"> · 按钮 {{ checkedButtonIds.size }}/{{ totalButtonCount }}</template>
        · 合计 {{ checkedCount }}/{{ totalCount }}
      </span>
    </div>
    <div class="menu-assign-container" :style="{ maxHeight }">
      <el-tree
        ref="treeRef"
        :data="skeletonTree"
        :props="{ label: 'menuName', children: 'children' }"
        node-key="id"
        show-checkbox
        @check="handleTreeCheck"
      >
        <template #default="{ data: node }">
          <div class="menu-node">
            <span class="menu-node-label">{{ node.menuName }}</span>
            <!-- @click.stop 阻断冒泡到树节点 content，避免点击按钮时连带切换菜单的树勾选 -->
            <div v-if="buttonsMap.get(node.id)?.length" class="menu-node-buttons" @click.stop>
              <el-checkbox
                v-for="btn in buttonsMap.get(node.id)"
                :key="btn.id"
                :model-value="checkedButtonIds.has(btn.id)"
                class="menu-node-button"
                @update:model-value="(val: boolean | string | number) => handleButtonToggle(btn.id, !!val)"
              >
                {{ btn.menuName }}
              </el-checkbox>
            </div>
          </div>
        </template>
      </el-tree>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { getMenuTree } from '@/api/system'
import type { MenuTree } from '@/types/system'

const props = withDefaults(
  defineProps<{
    /** 选中集合：全选节点（含全选父目录）+ 勾选按钮 ID */
    modelValue: number[]
    /** 可选注入全量菜单树（含按钮节点），缺省组件内加载 */
    data?: MenuTree[]
    /** 容器 max-height */
    maxHeight?: string
  }>(),
  {
    modelValue: () => [],
    data: undefined,
    maxHeight: 'max(320px, calc(100vh - 350px))'
  }
)

const emit = defineEmits<{
  'update:modelValue': [ids: number[]]
}>()

defineOptions({ name: 'MenuAssignTree' })

const MENU_TYPE_BUTTON = 2

const loading = ref(false)
const treeRef = ref<any>(null)
const skeletonTree = ref<MenuTree[]>([])
const buttonsMap = ref(new Map<number, MenuTree[]>())
const checkedButtonIds = ref(new Set<number>())
const checkAll = ref(false)
const expandAll = ref(false)
// el-tree 勾选态不在 Vue 响应式内，须在勾选/回显后手动刷新
const checkedMenuCount = ref(0)
const totalMenuCount = computed(() => getAllTreeIds(skeletonTree.value).length)
const totalButtonCount = computed(() => allButtonIds.value.length)
// 勾选数 = 完全勾选的树节点 + 勾选的功能按钮
const checkedCount = computed(() => checkedMenuCount.value + checkedButtonIds.value.size)
const totalCount = computed(() => totalMenuCount.value + totalButtonCount.value)

// 记录最近一次上报的集合，父组件 v-model 回写时若未变化则跳过回显，避免冗余 setCheckedKeys
let lastEmitted = ''

const allButtonIds = computed(() =>
  [...buttonsMap.value.values()].flat().map((b) => b.id)
)

// 剔除按钮节点生成骨架树；按钮按父节点归入 buttonsMap
const transform = (tree: MenuTree[]) => {
  const map = new Map<number, MenuTree[]>()
  const collectButtons = (nodes: MenuTree[]) => {
    nodes.forEach((n) => {
      const children = n.children || []
      const btns = children.filter((c) => c.menuType === MENU_TYPE_BUTTON)
      if (btns.length) map.set(n.id, btns)
      collectButtons(children.filter((c) => c.menuType !== MENU_TYPE_BUTTON))
    })
  }
  collectButtons(tree)

  const build = (nodes: MenuTree[]): MenuTree[] =>
    nodes
      .filter((n) => n.menuType !== MENU_TYPE_BUTTON)
      .map((n) => {
        const children = n.children?.length ? build(n.children) : []
        return children.length ? { ...n, children } : { ...n, children: undefined }
      })

  buttonsMap.value = map
  skeletonTree.value = build(tree)
}

const getAllTreeIds = (nodes: MenuTree[]): number[] =>
  nodes.flatMap((n) => [n.id, ...(n.children?.length ? getAllTreeIds(n.children) : [])])

const getLeafIds = (nodes: MenuTree[]): number[] =>
  nodes.flatMap((n) => (n.children?.length ? getLeafIds(n.children) : [n.id]))

// 勾选集只认叶子节点：父目录的半选/全选由 el-tree 父子联动推导，
// 若把（半选）父 ID 传入 setCheckedKeys 会被视为完全勾选进而勾上全部子菜单
const applyValue = (ids: number[]) => {
  const idSet = new Set(ids)
  // 存量数据按旧树语义可能只存了按钮 ID 而未存其所属菜单，
  // 任一按钮勾选须回推所属菜单勾选，父目录半选才能正确推导
  const leafIdSet = new Set(getLeafIds(skeletonTree.value).filter((id) => idSet.has(id)))
  buttonsMap.value.forEach((btns, menuId) => {
    if (btns.some((b) => idSet.has(b.id))) leafIdSet.add(menuId)
  })
  treeRef.value?.setCheckedKeys([...leafIdSet], false)
  checkedButtonIds.value = new Set(allButtonIds.value.filter((id) => idSet.has(id)))
  syncCheckAll()
}

const emitValue = () => {
  const treeChecked: number[] = treeRef.value?.getCheckedKeys(false) || []
  const ids = [...treeChecked, ...checkedButtonIds.value]
  lastEmitted = [...ids].sort((a, b) => a - b).join(',')
  emit('update:modelValue', ids)
  syncCheckAll()
}

// 勾选目录会影响后代菜单的勾选态，须对 data 及其后代中「有按钮的菜单」联动按钮勾选
const handleTreeCheck = (data: MenuTree, { checkedKeys }: { checkedKeys: (string | number)[] }) => {
  const affected: MenuTree[] = []
  const walk = (nodes: MenuTree[]) => {
    nodes.forEach((n) => {
      if (buttonsMap.value.has(n.id)) affected.push(n)
      if (n.children?.length) walk(n.children)
    })
  }
  walk([data])
  affected.forEach((menu) => {
    const btns = buttonsMap.value.get(menu.id) || []
    if (checkedKeys.includes(menu.id)) {
      btns.forEach((b) => checkedButtonIds.value.add(b.id))
    } else {
      btns.forEach((b) => checkedButtonIds.value.delete(b.id))
    }
  })
  emitValue()
}

const handleButtonToggle = (id: number, checked: boolean) => {
  if (checked) {
    checkedButtonIds.value.add(id)
    // 勾选按钮 ⇒ 所属菜单授权勾选（父子联动会推导父目录半选/全选）
    const menuId = findMenuIdByButton(id)
    if (menuId != null) {
      const node = treeRef.value?.getNode(menuId)
      if (node && !node.checked && !node.indeterminate) {
        const checkedKeys: number[] = treeRef.value?.getCheckedKeys(false) || []
        treeRef.value?.setCheckedKeys([...checkedKeys, menuId])
      }
    }
  } else {
    checkedButtonIds.value.delete(id)
  }
  emitValue()
}

const findMenuIdByButton = (buttonId: number): number | null => {
  for (const [menuId, btns] of buttonsMap.value) {
    if (btns.some((b) => b.id === buttonId)) return menuId
  }
  return null
}

const syncCheckAll = () => {
  const allIds = getAllTreeIds(skeletonTree.value)
  const treeChecked: number[] = treeRef.value?.getCheckedKeys(false) || []
  checkedMenuCount.value = treeChecked.length
  checkAll.value =
    allIds.length > 0 &&
    treeChecked.length === allIds.length &&
    checkedButtonIds.value.size === allButtonIds.value.length
}

const handleCheckAll = (val: boolean | string | number) => {
  if (val) {
    treeRef.value?.setCheckedKeys(getAllTreeIds(skeletonTree.value))
    checkedButtonIds.value = new Set(allButtonIds.value)
  } else {
    treeRef.value?.setCheckedKeys([])
    checkedButtonIds.value = new Set()
  }
  emitValue()
}

const handleExpandToggle = (expand: boolean) => {
  if (!treeRef.value) return
  expandAll.value = expand
  // Element Plus Tree 没有公开 expandAll API，通过 Node.expanded 属性直接控制
  const walk = (nodes: MenuTree[]) => {
    nodes.forEach((n) => {
      const node = treeRef.value?.getNode(n.id)
      if (node) node.expanded = expand
      if (n.children?.length) walk(n.children)
    })
  }
  walk(skeletonTree.value)
}

watch(
  () => props.data,
  async (tree) => {
    if (tree?.length) {
      transform(tree)
      // el-tree 在 data 变更后异步重建内部 node store，同 tick 调 setCheckedKeys 会静默失效
      await nextTick()
      applyValue(props.modelValue)
    }
  }
)

watch(
  () => props.modelValue,
  (ids) => {
    const incoming = [...ids].sort((a, b) => a - b).join(',')
    if (incoming === lastEmitted) return
    applyValue(ids)
  }
)

onMounted(async () => {
  if (props.data?.length) {
    transform(props.data)
    await nextTick()
    applyValue(props.modelValue)
    return
  }
  loading.value = true
  try {
    try {
      transform(await getMenuTree())
    } catch {
      transform([])
    }
    // el-tree 在 data 变更后异步重建内部 node store，须待渲染完成再回显勾选
    await nextTick()
    applyValue(props.modelValue)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="scss">
.menu-assign-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;

  .menu-assign-count {
    margin-left: auto;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.menu-assign-container {
  width: 100%;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px 12px;

  // 节点含按钮行时整体高度为两行，名称/勾选框/箭头统一按 26px 首行高度顶部对齐，
  // 恢复 el-tree 单行节点的默认视觉对齐
  :deep(.el-tree-node__content) {
    height: auto;
    min-height: 26px;
    align-items: flex-start;

    > .el-tree-node__expand-icon {
      height: 26px;
      display: inline-flex;
      align-items: center;
      // icon 字体字形在 em box 内视觉偏上，补偿使其与名称行文字对齐
      padding-top: 0px;
    }

    > .el-checkbox {
      height: 26px;
      display: inline-flex;
      align-items: center;
    }
  }
}

.menu-node {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.menu-node-label {
  height: 26px;
  line-height: 26px;
}

.menu-node-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0 8px;
  margin: 2px 0 2px 0;

  .menu-node-button {
    margin-right: 0;
    height: 22px;

    :deep(.el-checkbox__label) {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      padding-left: 6px;
    }
  }
}
</style>
