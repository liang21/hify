<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">模型管理</h1>
      <el-button type="primary" :icon="Plus">添加模型</el-button>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" class="search-form">
        <el-form-item label="提供商">
          <el-select placeholder="选择提供商" style="width: 160px">
            <el-option label="全部" value="" />
            <el-option label="OpenAI" value="openai" />
            <el-option label="Claude" value="claude" />
            <el-option label="Ollama" value="ollama" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">查询</el-button>
          <el-button>重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" stripe>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="provider" label="提供商">
          <template #default="{ row }">
            <el-tag :type="getProviderType(row.provider)" size="small">
              {{ row.provider }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="模型" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
              {{ row.status === 'active' ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default>
            <el-button link type="primary">编辑</el-button>
            <el-button link type="danger">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-divider"></div>

      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination-right"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { Plus } from '@element-plus/icons-vue'

const tableData = ref([
  { name: 'GPT-4', provider: 'OpenAI', model: 'gpt-4', status: 'active' },
  { name: 'Claude 3', provider: 'Claude', model: 'claude-3-opus', status: 'active' },
  { name: 'Llama 3', provider: 'Ollama', model: 'llama3', status: 'active' }
])

const pagination = reactive({
  current: 1,
  size: 20,
  total: 3
})

const getProviderType = (provider: string) => {
  const map: Record<string, any> = {
    OpenAI: '',
    Claude: 'success',
    Ollama: 'warning'
  }
  return map[provider] || 'info'
}
</script>

<style scoped>
.page-container {
  padding: var(--spacing-xl);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--color-text-1);
  margin: 0;
}

.search-card,
.table-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
}

:deep(.el-card__body) {
  padding: var(--spacing-lg);
}

:deep(.el-table) {
  font-size: var(--text-sm);
}

:deep(.el-table th) {
  background-color: var(--color-bg-2);
  color: var(--color-text-2);
  font-weight: 500;
}

:deep(.el-table__row:hover) {
  background-color: var(--color-bg-3);
}

.pagination-divider {
  height: 1px;
  background-color: var(--color-border);
  margin-top: var(--spacing-lg);
}

.pagination-right {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-lg);
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, var(--color-primary-500) 0%, var(--color-primary-600) 100%);
  border: none;
  box-shadow: var(--shadow-glow);
  transition: all var(--transition-fast);
}

:deep(.el-button--primary:hover) {
  transform: translateY(-1px);
  box-shadow: var(--shadow-lg), var(--shadow-glow);
}

:deep(.el-button--primary:active) {
  transform: translateY(0);
}

:deep(.el-tag) {
  border-radius: var(--radius-sm);
  font-weight: 500;
}

:deep(.el-button--primary.is-link) {
  background: transparent;
  border: none;
  box-shadow: none;
}

:deep(.el-button--primary.is-link:hover) {
  transform: none;
  box-shadow: none;
}

:deep(.el-button--danger.is-link) {
  background: transparent;
  border: none;
}
</style>
