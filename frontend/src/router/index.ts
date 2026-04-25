import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/dashboard',
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: DashboardView,
    },
    {
      path: '/queue',
      name: 'queue',
      component: () => import('@/views/QueueView.vue'),
    },
    // /settings 重定向到路径设置（ADR-003）
    {
      path: '/settings',
      redirect: '/settings/paths',
    },
    {
      path: '/settings/paths',
      name: 'settings-paths',
      component: () => import('@/views/PathsView.vue'),
    },
    {
      path: '/settings/system',
      name: 'settings-system',
      component: () => import('@/views/SystemSettingsView.vue'),
    },
    // 高级设置：路由预留，v1 菜单中不显示
    {
      path: '/settings/advanced',
      name: 'settings-advanced',
      component: () => import('@/views/SystemSettingsView.vue'), // placeholder，v2 替换
    },
  ],
})

export default router
