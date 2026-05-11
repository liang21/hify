<script setup lang="ts">
import { ref } from 'vue'

const collapsed = ref(false)

const menuItems = [
  { path: '/providers', icon: 'Connection', title: '模型管理' },
  { path: '/agents', icon: 'Avatar', title: 'Agent 管理' },
  { path: '/chat', icon: 'ChatDotRound', title: '对话' }
]
</script>

<template>
  <el-container class="layout-container">
    <el-aside :width="collapsed ? '64px' : '200px'" class="sidebar-dark">
      <div class="logo">
        <span v-if="!collapsed">Hify</span>
        <span v-else>H</span>
      </div>
      <el-menu
        :default-active="$route.path"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="sidebar-menu"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout-container {
  height: 100vh;
}

.el-aside {
  background-color: var(--color-bg-dark-1);
  transition: width var(--transition-base);
  border-right: 1px solid var(--color-border-dark);
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-dark-1);
  font-size: 20px;
  font-weight: 600;
  border-bottom: 1px solid var(--color-border-dark);
  background: linear-gradient(135deg, var(--color-primary-600) 0%, var(--color-primary-700) 100%);
}

.el-main {
  padding: 0;
  background-color: var(--color-bg-2);
}

:deep(.el-menu) {
  border-right: none;
  background-color: transparent;
}

:deep(.el-menu-item) {
  color: var(--color-text-dark-3);
  transition: all var(--transition-fast);
  margin: 4px 8px;
  border-radius: var(--radius-md);
}

:deep(.el-menu-item:hover) {
  background-color: var(--color-bg-dark-hover);
  color: var(--color-text-dark-1);
}

:deep(.el-menu-item.is-active) {
  color: var(--color-text-dark-1);
  background: linear-gradient(135deg, var(--color-primary-500) 0%, var(--color-primary-600) 100%);
  box-shadow: var(--shadow-glow);
}

:deep(.el-menu-item .el-icon) {
  font-size: 18px;
}
</style>
