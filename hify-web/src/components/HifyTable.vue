<template>
  <div class="hify-table">
    <el-table
      v-loading="loading"
      :data="tableData"
      stripe
      @sort-change="handleSortChange"
    >
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :sortable="col.sortable ? 'custom' : false"
        :formatter="col.formatter"
      >
        <template v-if="col.slot" #default="{ row, column, $index }">
          <slot :name="col.slot" :row="row" :column="column" :index="$index" />
        </template>
      </el-table-column>

      <el-table-column
        v-if="showActions"
        label="操作"
        :width="actionWidth"
        fixed="right"
      >
        <template #default="{ row, $index }">
          <slot name="actions" :row="row" :index="$index" />
        </template>
      </el-table-column>
    </el-table>

    <div v-if="showPagination" class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts" generic="T = any">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'

export interface Column<T = any> {
  prop: keyof T | string
  label: string
  width?: number | string
  minWidth?: number | string
  sortable?: boolean
  formatter?: (row: T, column: any, cellValue: any, index: number) => string
  slot?: string
}

interface Pagination {
  current: number
  size: number
  total: number
}

interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

interface Props<T = any> {
  columns: Column<T>[]
  api: (params: any) => Promise<PageResult<T>>
  showActions?: boolean
  actionWidth?: number
  showPagination?: boolean
  defaultPageSize?: number
  autoFetch?: boolean
  params?: Record<string, any>
}

const props = withDefaults(defineProps<Props<T>>(), {
  showActions: true,
  actionWidth: 180,
  showPagination: true,
  defaultPageSize: 20,
  autoFetch: true,
  params: () => ({})
})

const loading = ref(false)
const tableData = ref<T[]>([])
const pagination = ref<Pagination>({
  current: 1,
  size: props.defaultPageSize,
  total: 0
})

const sortParams = ref<Record<string, 'asc' | 'desc'>>({})

defineExpose({
  refresh,
  getData: () => tableData.value,
  getPagination: () => pagination.value
})

async function fetchData() {
  loading.value = true
  try {
    const params = {
      current: pagination.value.current,
      size: pagination.value.size,
      ...props.params,
      ...sortParams.value
    }
    const res = await props.api(params)
    tableData.value = res.records
    pagination.value.total = res.total
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

function refresh() {
  pagination.value.current = 1
  fetchData()
}

function handlePageChange(page: number) {
  pagination.value.current = page
  fetchData()
}

function handleSizeChange(size: number) {
  pagination.value.size = size
  pagination.value.current = 1
  fetchData()
}

function handleSortChange({ prop, order }: { prop: string; order: string | null }) {
  if (order) {
    sortParams.value[prop] = order === 'ascending' ? 'asc' : 'desc'
  } else {
    delete sortParams.value[prop]
  }
  fetchData()
}

onMounted(() => {
  if (props.autoFetch) {
    fetchData()
  }
})

defineExpose({ refresh })
</script>

<style scoped>
.hify-table {
  background: var(--color-bg-1);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
}

:deep(.el-table) {
  font-size: var(--text-sm);
}

:deep(.el-table__row) {
  height: 52px;
}

:deep(.el-table td) {
  height: 52px;
}

:deep(.el-table th) {
  background-color: var(--color-bg-2);
  color: var(--color-text-2);
  font-weight: 500;
}

:deep(.el-table__row:hover) {
  background-color: var(--color-bg-3);
}

:deep(.el-table__empty-block) {
  background-color: var(--color-bg-1);
}

.pagination-wrapper {
  padding: var(--spacing-lg);
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--color-border);
}

:deep(.el-pagination) {
  font-size: var(--text-sm);
}

:deep(.el-pagination.is-background .el-pager li:not(.is-disabled).is-active) {
  background: linear-gradient(135deg, var(--color-primary-500) 0%, var(--color-primary-600) 100%);
}

:deep(.el-table__cell .el-button + .el-button) {
  margin-left: 8px;
}
</style>
