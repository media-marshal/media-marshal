import { defineStore } from 'pinia'
import { ref } from 'vue'
import { settingsApi } from '@/api/settings'
import type { AppSetting } from '@/types'

export const useSettingsStore = defineStore('settings', () => {
  const settings = ref<AppSetting[]>([])
  const loading = ref(false)

  async function fetchSettings() {
    loading.value = true
    try {
      const res = await settingsApi.getAll()
      settings.value = res.data.data
    } finally {
      loading.value = false
    }
  }

  async function updateSetting(key: string, value: string, description?: string, sensitive = false) {
    await settingsApi.update(key, value, description, sensitive)
    const existing = settings.value.find((s) => s.key === key)
    if (existing) {
      existing.value = sensitive ? value.substring(0, 4) + '****' : value
    }
  }

  function getSetting(key: string): string {
    return settings.value.find((s) => s.key === key)?.value ?? ''
  }

  return { settings, loading, fetchSettings, updateSetting, getSetting }
})
