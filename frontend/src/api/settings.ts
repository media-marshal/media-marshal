import http from './http'
import type { ApiResponse, AppSetting } from '@/types'

export const settingsApi = {
  getAll() {
    return http.get<ApiResponse<AppSetting[]>>('/api/settings')
  },

  update(key: string, value: string, description?: string, sensitive = false) {
    return http.put<ApiResponse<void>>(`/api/settings/${key}`, {
      value,
      description,
      sensitive,
    })
  },
}
