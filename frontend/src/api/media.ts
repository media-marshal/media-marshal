import http from './http'
import type { ApiResponse, MediaTask, TaskStatus } from '@/types'

export const mediaApi = {
  listTasks(status?: TaskStatus) {
    return http.get<ApiResponse<MediaTask[]>>('/api/tasks', {
      params: status ? { status } : undefined,
    })
  },

  getTask(id: number) {
    return http.get<ApiResponse<MediaTask>>(`/api/tasks/${id}`)
  },

  deleteTask(id: number) {
    return http.delete<ApiResponse<void>>(`/api/tasks/${id}`)
  },

  // 待确认队列
  getPendingQueue() {
    return http.get<ApiResponse<MediaTask[]>>('/api/queue')
  },

  confirmTask(id: number, tmdbId: number, mediaType: string) {
    return http.post<ApiResponse<void>>(`/api/queue/${id}/confirm`, {
      tmdbId,
      mediaType,
    })
  },

  skipTask(id: number) {
    return http.post<ApiResponse<void>>(`/api/queue/${id}/skip`)
  },
}
