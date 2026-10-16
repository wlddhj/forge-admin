<template>
  <el-drawer
    :model-value="modelValue"
    :title="drawerTitle"
    size="720px"
    destroy-on-close
    @update:model-value="(val: boolean) => emit('update:modelValue', val)"
    @closed="emit('closed')"
  >
    <div v-loading="initializing">
      <el-form
        v-if="initialized"
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-row>
          <el-col :span="12">
            <el-form-item label="套餐名称" prop="name">
              <el-input v-model="formData.name" placeholder="请输入套餐名称" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="套餐编码" prop="code">
              <el-input
                v-model="formData.code"
                placeholder="请输入套餐编码（如 standard）"
                maxlength="50"
                :disabled="!!formData.id"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="formData.status">
              <el-radio
                v-for="item in statusOptions"
                :key="item.dictValue"
                :value="Number(item.dictValue)"
              >
                {{ item.dictLabel }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
          </el-col>
          <el-col :span="24">
        <el-form-item label="备注">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入备注"
            maxlength="200"
          />
        </el-form-item>
          </el-col>
          <el-col :span="24">
        <el-form-item label="关联菜单">
          <div class="menu-tree-toolbar">
            <el-checkbox v-model="menuCheckAll" @change="handleMenuCheckAll">全选</el-checkbox>
            <el-button link type="primary" @click="handleMenuExpandToggle(!menuExpandAll)">
              {{ menuExpandAll ? '折叠全部' : '展开全部' }}
            </el-button>
          </div>
          <div class="menu-tree-container">
            <el-tree
              ref="treeRef"
              :data="menuTree"
              :props="{ label: 'menuName', children: 'children' }"
              node-key="id"
              show-checkbox
              :default-expanded-keys="defaultExpandedKeys"
              :default-checked-keys="defaultCheckedKeys"
              @check="handleMenuCheck"
            />
          </div>
        </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  getTenantPackageDetail,
  addTenantPackage,
  updateTenantPackage,
  type TenantPackageEntity,
  type TenantPackageRequest
} from '@/api/system/tenant-package'
import { getMenuTree } from '@/api/system'
import type { MenuTree } from '@/types/system'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'

const props = defineProps<{
  modelValue: boolean
  /** 编辑时传行数据，新增时传 null */
  row: TenantPackageEntity | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
  closed: []
}>()

const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_NORMAL_DISABLE)

const drawerTitle = computed(() => (props.row ? '编辑套餐' : '新增套餐'))

const initializing = ref(false)
const initialized = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

// 菜单树
const menuTree = ref<MenuTree[]>([])
const treeRef = ref<any>(null)
const menuExpandAll = ref(false)
const menuCheckAll = ref(false)
// el-tree 仅在初始化时读 default-*-keys，须在树渲染前就绪（initialized 控制）
const defaultExpandedKeys = ref<number[]>([])
const defaultCheckedKeys = ref<number[]>([])

const formData = reactive<TenantPackageRequest>({
  id: undefined,
  name: '',
  code: '',
  status: 1,
  remark: '',
  menuIds: []
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入套餐编码', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_-]{1,49}$/,
      message: '编码需以字母开头，仅允许字母、数字、下划线、短横线',
      trigger: 'blur'
    }
  ]
}

const getAllMenuIds = (tree: MenuTree[]): number[] => {
  const ids: number[] = []
  const walk = (nodes: MenuTree[]) => {
    nodes.forEach((n) => {
      ids.push(n.id)
      if (n.children?.length) walk(n.children)
    })
  }
  walk(tree)
  return ids
}

// 组件由父级 v-if 控制在打开时才挂载，modelValue 初始即 true，
// el-drawer 的 @open 不会触发（无 false→true 变化），故在 onMounted 初始化
onMounted(async () => {
  initializing.value = true
  try {
    try {
      menuTree.value = await getMenuTree()
    } catch {
      menuTree.value = []
    }

    let ids: number[] = []
    if (props.row) {
      let detail: TenantPackageEntity
      try {
        detail = await getTenantPackageDetail(props.row.id)
      } catch {
        detail = props.row
      }
      ids = detail.menuIds || []
      Object.assign(formData, {
        id: detail.id,
        name: detail.name,
        code: detail.code,
        status: detail.status,
        remark: detail.remark || '',
        menuIds: ids
      })
    }
    // 默认展开包含选中节点的父链 + 选中节点本身；展开全部由用户点击切换
    defaultExpandedKeys.value = ids
    defaultCheckedKeys.value = ids
    menuExpandAll.value = false

    initialized.value = true
    await nextTick()
    // setCheckedKeys 用于精确控制半选状态（父子联动）
    treeRef.value?.setCheckedKeys(ids, false)
    handleMenuCheck()
  } finally {
    initializing.value = false
  }
})

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    const checkedKeys = treeRef.value?.getCheckedKeys(false) || []
    const halfCheckedKeys = treeRef.value?.getHalfCheckedKeys() || []
    formData.menuIds = [...checkedKeys, ...halfCheckedKeys]

    if (formData.id) {
      await updateTenantPackage(formData)
      ElMessage.success('更新成功')
    } else {
      await addTenantPackage(formData)
      ElMessage.success('新增成功')
    }
    emit('update:modelValue', false)
    emit('success')
  } finally {
    submitLoading.value = false
  }
}

const handleMenuCheck = () => {
  const allIds = getAllMenuIds(menuTree.value)
  const checked = treeRef.value?.getCheckedKeys(false) || []
  menuCheckAll.value = checked.length === allIds.length && allIds.length > 0
}

const handleMenuCheckAll = (val: any) => {
  if (val) {
    treeRef.value?.setCheckedKeys(getAllMenuIds(menuTree.value))
  } else {
    treeRef.value?.setCheckedKeys([])
  }
}

const handleMenuExpandToggle = (expand: boolean) => {
  if (!treeRef.value) return
  menuExpandAll.value = expand
  // Element Plus Tree 没有公开 expandAll API，通过 Node.expanded 属性直接控制
  const walk = (nodes: MenuTree[]) => {
    nodes.forEach((n) => {
      const node = treeRef.value?.getNode(n.id)
      if (node) node.expanded = expand
      if (n.children?.length) walk(n.children)
    })
  }
  walk(menuTree.value)
}
</script>

<style scoped lang="scss">
.menu-tree-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.menu-tree-container {
  width: 100%;
  // 抽屉 header/表单字段/footer 约占 480px，剩余空间给菜单树；小屏保底 320px
  max-height: max(320px, calc(100vh - 350px));
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px 12px;
}
</style>
