<template>
  <el-dialog v-model="dialogVisible" :title="currentId ? '编辑 Agent 团队' : '新增 Agent 团队'" width="880px"
    class="dialog-form-responsive" :close-on-click-modal="false">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="团队编码" prop="teamCode">
            <el-input v-model="formData.teamCode" placeholder="如 data-analysis" :disabled="!!currentId" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="团队名称" prop="teamName">
            <el-input v-model="formData.teamName" placeholder="请输入团队名称" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="团队描述">
        <el-input v-model="formData.description" placeholder="请输入团队描述（可选）" />
      </el-form-item>
      <el-form-item label="Supervisor 提示词" prop="supervisorPrompt">
        <el-input v-model="formData.supervisorPrompt" type="textarea" :rows="4"
          placeholder="主管调度规则：说明团队目标、分派原则（会自动注入成员清单/任务/专家汇报，只需写调度策略）" />
      </el-form-item>
      <el-form-item label="模型绑定">
        <el-input v-model="formData.modelBindings" type="textarea" :rows="3"
          placeholder="JSON：{&quot;supervisor&quot;:{&quot;modelType&quot;:&quot;text&quot;},&quot;summary&quot;:{&quot;modelType&quot;:&quot;text&quot;}}，modelId 优先；留空则报错提示绑定" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="调度轮次上限">
            <el-input-number v-model="formData.maxRounds" :min="1" :max="30" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-switch v-model="formData.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">团队成员（专家）</el-divider>
      <el-table :data="formData.members" size="small" border>
        <el-table-column label="专家 Agent" width="220">
          <template #default="{ row, $index }">
            <el-select v-model="row.agentId" placeholder="选择 Agent" filterable>
              <el-option v-for="opt in agentOptions" :key="opt.value" :value="opt.value" :label="opt.label"
                :disabled="isAgentTaken(opt.value, $index)" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="职责描述（注入 Supervisor）" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.memberRole" placeholder="如：负责订单数据 SQL 查询与统计" />
          </template>
        </el-table-column>
        <el-table-column label="工具白名单（空=全部）" width="230">
          <template #default="{ row }">
            <el-select v-model="row.allowedTools" multiple clearable placeholder="全部工具">
              <el-option v-for="t in AGENT_TOOLS" :key="t.value" :value="t.value" :label="t.label" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="步数上限" width="120">
          <template #default="{ row }">
            <el-input-number :min="1" :max="50" :model-value="row.maxSteps ?? undefined"
              placeholder="默认" @update:model-value="(v: number | undefined) => (row.maxSteps = v ?? null)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="removeMember($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button type="primary" plain size="small" style="margin-top: 8px" @click="addMember">+ 添加成员</el-button>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { agentTeamApi } from '@/api/ai/agentTeam'
import type { AgentTeam, TeamMember } from '@/api/ai/agentTeam'
import { AGENT_TOOLS } from '@/api/ai/agentTeam'

defineOptions({ name: 'TeamForm' })

defineProps<{ agentOptions: { value: number; label: string }[] }>()
const emit = defineEmits(['success'])

const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const currentId = ref<number>()

const emptyForm = () => ({
  teamCode: '',
  teamName: '',
  description: '',
  supervisorPrompt: '',
  modelBindings: '',
  maxRounds: 6,
  status: 1,
  remark: '',
  members: [] as TeamMember[]
})

const formData = reactive(emptyForm())

const formRules: FormRules = {
  teamCode: [{ required: true, message: '请输入团队编码', trigger: 'blur' }],
  teamName: [{ required: true, message: '请输入团队名称', trigger: 'blur' }],
  supervisorPrompt: [{ required: true, message: '请输入 Supervisor 提示词', trigger: 'blur' }]
}

/** 该 Agent 是否已被其他行选择（禁用重复选项） */
const isAgentTaken = (agentId: number, currentIndex: number) =>
  formData.members.some((m, i) => i !== currentIndex && m.agentId === agentId)

const addMember = () => {
  formData.members.push({ agentId: undefined as unknown as number, memberRole: '', allowedTools: [], maxSteps: null })
}

const removeMember = (index: number) => {
  formData.members.splice(index, 1)
}

const open = (row?: AgentTeam) => {
  Object.assign(formData, emptyForm())
  if (row) {
    currentId.value = row.id
    Object.assign(formData, {
      teamCode: row.teamCode,
      teamName: row.teamName,
      description: row.description || '',
      supervisorPrompt: row.supervisorPrompt || '',
      modelBindings: row.modelBindings || '',
      maxRounds: row.maxRounds,
      status: row.status,
      remark: row.remark || '',
      members: (row.members || []).map(m => ({ ...m, allowedTools: m.allowedTools || [] }))
    })
  } else {
    currentId.value = undefined
  }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  if (formData.members.length === 0) {
    ElMessage.error('团队至少需要一个成员')
    return
  }
  for (const m of formData.members) {
    if (!m.agentId) {
      ElMessage.error('存在未选择 Agent 的成员')
      return
    }
    if (!m.memberRole || !m.memberRole.trim()) {
      ElMessage.error('存在职责描述为空的成员')
      return
    }
  }
  const dup = formData.members.find((m, i) => formData.members.findIndex(x => x.agentId === m.agentId) !== i)
  if (dup) {
    ElMessage.error('同一 Agent 不允许重复加入团队')
    return
  }
  if (formData.modelBindings.trim()) {
    try {
      JSON.parse(formData.modelBindings)
    } catch {
      ElMessage.error('模型绑定必须是合法 JSON')
      return
    }
  }
  submitLoading.value = true
  try {
    const payload = { ...formData, members: formData.members.map((m, i) => ({ ...m, sortOrder: i })) }
    if (currentId.value) {
      await agentTeamApi.update(currentId.value, payload)
    } else {
      await agentTeamApi.add(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    emit('success')
  } finally {
    submitLoading.value = false
  }
}

defineExpose({ open })
</script>
