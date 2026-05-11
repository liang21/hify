<template>
  <div class="chat-container">
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <el-button type="primary" :icon="Plus" size="small">新建对话</el-button>
      </div>
      <div class="conversation-list">
        <div v-for="conv in conversations" :key="conv.id" class="conv-item" :class="{ active: conv.id === activeId }">
          <div class="conv-title">{{ conv.title }}</div>
          <div class="conv-time">{{ conv.time }}</div>
        </div>
      </div>
    </div>

    <div class="chat-main">
      <div class="chat-messages">
        <div v-for="msg in messages" :key="msg.id" class="message" :class="msg.role">
          <div class="message-avatar">
            <el-icon v-if="msg.role === 'user'"><User /></el-icon>
            <el-icon v-else><Avatar /></el-icon>
          </div>
          <div class="message-content">
            <div class="message-text">{{ msg.content }}</div>
          </div>
        </div>
      </div>

      <div class="chat-input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="3"
          placeholder="输入消息... (Enter 发送, Shift + Enter 换行)"
          class="chat-input"
        />
        <div class="input-actions">
          <el-button type="primary" :icon="Promotion" @click="sendMessage">发送</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus, User, Avatar, Promotion } from '@element-plus/icons-vue'

const activeId = ref(1)
const inputText = ref('')

const conversations = ref([
  { id: 1, title: '代码调试助手', time: '今天' },
  { id: 2, title: 'API 文档查询', time: '昨天' },
  { id: 3, title: '数据分析脚本', time: '3天前' }
])

const messages = ref([
  { id: 1, role: 'user', content: '帮我写一个 Python 函数来解析 JSON 文件' },
  { id: 2, role: 'assistant', content: '当然，这里是一个使用 json 模块的函数：\n\n```python\nimport json\n\ndef parse_json(file_path):\n    with open(file_path, \"r\", encoding=\"utf-8\") as f:\n        return json.load(f)\n```' },
  { id: 3, role: 'user', content: '如果文件很大怎么办？' }
])

const sendMessage = () => {
  if (!inputText.value.trim()) return
  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: inputText.value
  })
  inputText.value = ''
}
</script>

<style scoped>
.chat-container {
  display: flex;
  height: 100%;
  background-color: var(--color-bg-1);
}

.chat-sidebar {
  width: 280px;
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  background-color: var(--color-bg-2);
}

.sidebar-header {
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--color-border);
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-sm);
}

.conv-item {
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-bottom: var(--spacing-xs);
}

.conv-item:hover {
  background-color: var(--color-bg-3);
}

.conv-item.active {
  background: linear-gradient(135deg, var(--color-primary-50) 0%, var(--color-accent-50) 100%);
  border-left: 3px solid var(--color-primary-500);
}

.conv-title {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text-1);
  margin-bottom: var(--spacing-xs);
}

.conv-time {
  font-size: var(--text-xs);
  color: var(--color-text-4);
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-xl);
}

.message {
  display: flex;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-xl);
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message.user .message-avatar {
  background: linear-gradient(135deg, var(--color-primary-500) 0%, var(--color-primary-600) 100%);
  color: white;
}

.message.assistant .message-avatar {
  background: linear-gradient(135deg, var(--color-accent-400) 0%, var(--color-accent-500) 100%);
  color: white;
}

.message-content {
  max-width: 70%;
}

.message-text {
  padding: var(--spacing-md) var(--spacing-lg);
  border-radius: var(--radius-lg);
  font-size: var(--text-sm);
  line-height: 1.6;
}

.message.user .message-text {
  background: linear-gradient(135deg, var(--color-primary-500) 0%, var(--color-primary-600) 100%);
  color: white;
  border-bottom-right-radius: var(--radius-xs);
}

.message.assistant .message-text {
  background-color: var(--color-bg-3);
  color: var(--color-text-1);
  border: 1px solid var(--color-border);
  border-bottom-left-radius: var(--radius-xs);
}

.chat-input-area {
  padding: var(--spacing-lg);
  border-top: 1px solid var(--color-border);
  background-color: var(--color-bg-1);
}

.chat-input {
  margin-bottom: var(--spacing-md);
}

.input-actions {
  display: flex;
  justify-content: flex-end;
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, var(--color-primary-500) 0%, var(--color-primary-600) 100%);
  border: none;
  box-shadow: var(--shadow-glow);
}
</style>
