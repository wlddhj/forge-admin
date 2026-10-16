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
          <MenuAssignTree v-model="formData.menuIds" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  getTenantPackageDetail,
  addTenantPackage,
  updateTenantPackage,
  type TenantPackageEntity,
  type TenantPackageRequest
} from '@/api/system/tenant-package'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import MenuAssignTree from '@/components/MenuAssignTree.vue'

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

// 组件由父级 v-if 控制在打开时才挂载，modelValue 初始即 true，
// el-drawer 的 @open 不会触发（无 false→true 变化），故在 onMounted 初始化
onMounted(async () => {
  initializing.value = true
  try {
    if (props.row) {
      let detail: TenantPackageEntity
      try {
        detail = await getTenantPackageDetail(props.row.id)
      } catch {
        detail = props.row
      }
      Object.assign(formData, {
        id: detail.id,
        name: detail.name,
        code: detail.code,
        status: detail.status,
        remark: detail.remark || '',
        menuIds: detail.menuIds || []
      })
    }

    initialized.value = true
  } finally {
    initializing.value = false
  }
})

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
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
</script>
