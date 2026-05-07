import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'
import { useSettingsStore } from '@/stores/settingsStore'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/dashboard',
    },
    {
      path: '/setup',
      name: 'setup',
      component: () => import('@/views/SetupView.vue'),
      meta: { bareLayout: true },
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
    {
      path: '/settings/danger',
      name: 'settings-danger',
      component: () => import('@/views/DangerSettingsView.vue'),
    },
    // 高级设置：路由预留，v1 菜单中不显示
    {
      path: '/settings/advanced',
      name: 'settings-advanced',
      component: () => import('@/views/SystemSettingsView.vue'), // placeholder，v2 替换
    },
  ],
})

router.beforeEach(async (to) => {
  const settingsStore = useSettingsStore()
  if (!settingsStore.loaded) {
    await settingsStore.fetchSettings()
  }

  if (settingsStore.requiresSetup && to.name !== 'setup') {
    return {
      name: 'setup',
    }
  }

  if (!settingsStore.requiresSetup && to.name === 'setup') {
    return '/settings/paths'
  }

  return true
})

export default router
