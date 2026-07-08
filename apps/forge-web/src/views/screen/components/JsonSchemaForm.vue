<script setup lang="ts">
import { computed } from 'vue'
import type { JSONSchema7 } from 'json-schema'

const props = defineProps<{
  schema: JSONSchema7
  modelValue: Record<string, unknown>
}>()
const emit = defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()

const properties = computed(() => {
  const props = props.schema.properties ?? {}
  return Object.entries(props).map(([key, def]) => ({ key, def: def as JSONSchema7 }))
})

const update = (key: string, val: unknown) => {
  emit('update:modelValue', { ...props.modelValue, [key]: val })
}

const inputType = (def: JSONSchema7): string => {
  if (def.type === 'number' || def.type === 'integer') return 'number'
  if (def.type === 'boolean') return 'checkbox'
  return 'text'
}
</script>

<template>
  <el-form label-width="100px" size="small">
    <el-form-item v-for="p in properties" :key="p.key" :label="p.def.title || p.key">
      <el-input
        v-if="inputType(p.def) === 'text'"
        :model-value="String(modelValue[p.key] ?? p.def.default ?? '')"
        @update:model-value="v => update(p.key, v)"
      />
      <el-input-number
        v-else-if="inputType(p.def) === 'number'"
        :model-value="Number(modelValue[p.key] ?? p.def.default ?? 0)"
        :min="p.def.minimum" :max="p.def.maximum"
        @update:model-value="v => update(p.key, v)"
      />
      <el-switch
        v-else-if="inputType(p.def) === 'checkbox'"
        :model-value="Boolean(modelValue[p.key] ?? p.def.default ?? false)"
        @update:model-value="v => update(p.key, v)"
      />
      <el-select
        v-else-if="p.def.enum"
        :model-value="modelValue[p.key] ?? p.def.default"
        @update:model-value="v => update(p.key, v)"
      >
        <el-option v-for="e in p.def.enum" :key="String(e)" :label="String(e)" :value="e" />
      </el-select>
    </el-form-item>
  </el-form>
</template>
