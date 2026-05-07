import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { settingsApi } from '@/api/settings'
import type { AppSetting } from '@/types'

export const useSettingsStore = defineStore('settings', () => {
  const settings = ref<AppSetting[]>([])
  const loading = ref(false)
  const loaded = ref(false)

  const requiresSetup = computed(() => !hasConfiguredValue('tmdb.api-key'))

  async function fetchSettings() {
    loading.value = true
    try {
      const res = await settingsApi.getAll()
      settings.value = res.data.data
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function updateSetting(key: string, value: string, description?: string, sensitive = false) {
    await settingsApi.update(key, value, description, sensitive)
    const existing = settings.value.find((s) => s.key === key)
    const displayValue = sensitive ? value.substring(0, 4) + '****' : value
    if (existing) {
      existing.value = displayValue
      existing.sensitive = sensitive
    } else {
      settings.value.push({
        key,
        value: displayValue,
        description,
        sensitive,
      })
    }
  }

  async function resetSystem() {
    await settingsApi.resetSystem()
    settings.value = []
    loaded.value = true
  }

  function getSetting(key: string): string {
    return settings.value.find((s) => s.key === key)?.value ?? ''
  }

  function hasConfiguredValue(key: string) {
    return getSetting(key).trim().length > 0
  }

  return {
    settings,
    loading,
    loaded,
    requiresSetup,
    fetchSettings,
    updateSetting,
    resetSystem,
    getSetting,
    hasConfiguredValue,
  }
})
