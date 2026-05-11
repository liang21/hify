<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">Agent 管理</h1>
      <el-button type="primary" :icon="Plus">创建 Agent</el-button>
    </div>

    <el-card class="stats-card" shadow="never">
      <div class="stats-grid">
        <div class="stat-item">
          <div class="stat-value">12</div>
          <div class="stat-label">总 Agent 数</div>
        </div>
        <div class="stat-item">
          <div class="stat-value stat-accent">8</div>
          <div class="stat-label">活跃中</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">3,240</div>
          <div class="stat-label">今日调用</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">98.5%</div>
          <div class="stat-label">成功率</div>
        </div>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="agents" stripe>
        <el-table-column prop="name" label="名称" width="200">
          <template #default="{ row }">
            <div class="agent-name">
              <el-icon class="agent-icon"><Avatar /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="model" label="绑定模型" width="180" />
        <el-table-column prop="calls" label="调用量" width="120">
          <template #default="{ row }">
            <span class="metric-value">{{ row.calls.toLocaleString() }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default>
            <el-button link type="primary" :icon="Edit">编辑</el-button>
            <el-button link type="primary" :icon="ChatDotRound">对话</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus, Edit, ChatDotRound, Avatar } from '@element-plus/icons-vue'

const agents = ref([
  { name: '代码助手', description: '辅助代码编写和审查', model: 'GPT-4', calls: 1520, updatedAt: '2025-01-10 14:30' },
  { name: '数据分析', description: '数据查询和分析', model: 'Claude 3', calls: 890, updatedAt: '2025-01-10 12:15' },
  { name: '文档问答', description: '基于知识库的文档问答', model: 'GPT-3.5', calls: 830, updatedAt: '2025-01-09 18:45' }
])
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

.stats-card {
  background: linear-gradient(135deg, var(--color-bg-1) 0%, var(--color-bg-2) 100%);
  border: 1px solid var(--color-border);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-xl);
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: var(--text-3xl);
  font-weight: 700;
  color: var(--color-primary-600);
  margin-bottom: var(--spacing-xs);
}

.stat-accent {
  background: linear-gradient(135deg, var(--color-primary-500) 0%, var(--color-accent-500) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.stat-label {
  font-size: var(--text-sm);
  color: var(--color-text-3);
}

.table-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
}

.agent-name {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.agent-icon {
  color: var(--color-primary-500);
}

.metric-value {
  font-family: var(--font-mono);
  font-weight: 500;
  color: var(--color-text-1);
}

:deep(.el-card__body) {
  padding: var(--spacing-lg);
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, var(--color-primary-500) 0%, var(--color-primary-600) 100%);
  border: none;
  box-shadow: var(--shadow-glow);
}
</style>
