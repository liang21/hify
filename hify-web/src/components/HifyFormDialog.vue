<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    :width="width"
    :destroy-on-close="true"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      :label-width="labelWidth"
      :label-position="labelPosition"
    >
      <el-form-item
        v-for="item in formItems"
        :key="item.prop"
        :prop="item.prop"
        :label="item.label"
      >
        <el-input
          v-if="item.type === 'input' || !item.type"
          v-model="formData[item.prop]"
          :placeholder="item.placeholder || `请输入${item.label}`"
          :disabled="item.disabled"
          :type="item.inputType || 'text'"
          :rows="item.rows"
          :maxlength="item.maxlength"
          :show-word-limit="item.showWordLimit"
        />

        <el-input-number
          v-else-if="item.type === 'number'"
          v-model="formData[item.prop]"
          :placeholder="item.placeholder || `请输入${item.label}`"
          :disabled="item.disabled"
          :min="item.min"
          :max="item.max"
          :step="item.step"
          :precision="item.precision"
          style="width: 100%"
        />

        <el-select
          v-else-if="item.type === 'select'"
          v-model="formData[item.prop]"
          :placeholder="item.placeholder || `请选择${item.label}`"
          :disabled="item.disabled"
          :multiple="item.multiple"
          :clearable="item.clearable !== false"
          style="width: 100%"
        >
          <el-option
            v-for="opt in item.options"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>

        <el-radio-group
          v-else-if="item.type === 'radio'"
          v-model="formData[item.prop]"
          :disabled="item.disabled"
        >
          <el-radio
            v-for="opt in item.options"
            :key="opt.value"
            :label="opt.value"
          >
            {{ opt.label }}
          </el-radio>
        </el-radio-group>

        <el-checkbox-group
          v-else-if="item.type === 'checkbox'"
          v-model="formData[item.prop]"
          :disabled="item.disabled"
        >
          <el-checkbox
            v-for="opt in item.options"
            :key="opt.value"
            :label="opt.value"
          >
            {{ opt.label }}
          </el-checkbox>
        </el-checkbox-group>

        <el-date-picker
          v-else-if="item.type === 'date'"
          v-model="formData[item.prop]"
          type="date"
          :placeholder="item.placeholder || `请选择${item.label}`"
          :disabled="item.disabled"
          :clearable="item.clearable !== false"
          style="width: 100%"
        />

        <el-date-picker
          v-else-if="item.type === 'datetime'"
          v-model="formData[item.prop]"
          type="datetime"
          :placeholder="item.placeholder || `请选择${item.label}`"
          :disabled="item.disabled"
          :clearable="item.clearable !== false"
          style="width: 100%"
        />

        <el-switch
          v-else-if="item.type === 'switch'"
          v-model="formData[item.prop]"
          :disabled="item.disabled"
        />

        <slot
          v-else-if="item.type === 'slot'"
          :name="item.prop"
          :model="formData"
          :item="item"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" generic="T = any">
import { ref, computed, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

export interface FormOption {
  label: string
  prop: keyof T | string
  type?: 'input' | 'number' | 'select' | 'radio' | 'checkbox' | 'date' | 'datetime' | 'switch' | 'slot' | 'textarea'
  placeholder?: string
  disabled?: boolean
  inputType?: 'text' | 'textarea' | 'password'
  rows?: number
  maxlength?: number
  showWordLimit?: boolean
  min?: number
  max?: number
  step?: number
  precision?: number
  multiple?: boolean
  clearable?: boolean
  options?: Array<{ label: string; value: any }>
  defaultValue?: any
}

interface Props<T = any> {
  modelValue: boolean
  title?: string
  formItems: FormOption[]
  formRules?: FormRules
  width?: string | number
  labelWidth?: string | number
  labelPosition?: 'left' | 'right' | 'top'
  mode?: 'add' | 'edit'
}

const props = withDefaults(defineProps<Props<T>>(), {
  title: '表单',
  width: '600px',
  labelWidth: '100px',
  labelPosition: 'right',
  mode: 'add'
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'submit': [data: T, mode: 'add' | 'edit']
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const formRef = ref<FormInstance>()
const formData = ref<Partial<T>>({})
const submitting = ref(false)
const currentMode = ref<'add' | 'edit'>(props.mode)

const dialogTitle = computed(() => {
  return props.title === '表单' ? (currentMode.value === 'add' ? '新增' : '编辑') : props.title
})

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      initForm()
    }
  }
)

function initForm() {
  const data: Partial<T> = {}
  props.formItems.forEach((item) => {
    data[item.prop as keyof T] = item.defaultValue ?? null
  })
  formData.value = data
  formRef.value?.clearValidate()
}

function open(data?: T) {
  visible.value = true
  currentMode.value = data ? 'edit' : 'add'
  if (data) {
    formData.value = { ...data }
  }
}

async function handleSubmit() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        emit('submit', formData.value as T, currentMode.value)
        handleClose()
      } finally {
        submitting.value = false
      }
    }
  })
}

function handleClose() {
  visible.value = false
  formRef.value?.resetFields()
}

defineExpose({
  open,
  close: handleClose,
  getFormData: () => formData.value,
  validate: () => formRef.value?.validate()
})
</script>

<style scoped>
:deep(.el-dialog__header) {
  padding: var(--spacing-lg) var(--spacing-xl);
  border-bottom: 1px solid var(--color-border);
}

:deep(.el-dialog__body) {
  padding: var(--spacing-xl);
}

:deep(.el-dialog__footer) {
  padding: var(--spacing-lg) var(--spacing-xl);
  border-top: 1px solid var(--color-border);
}

:deep(.el-form-item__label) {
  color: var(--color-text-2);
  font-weight: 500;
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, var(--color-primary-500) 0%, var(--color-primary-600) 100%);
  border: none;
}
</style>
