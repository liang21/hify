import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/providers',
    name: 'Providers',
    component: () => import('@/views/providers/index.vue')
  },
  {
    path: '/agents',
    name: 'Agents',
    component: () => import('@/views/agents/index.vue')
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/chat/index.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
