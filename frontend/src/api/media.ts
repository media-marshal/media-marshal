import http from './http'
import type { ApiResponse, BatchConfirmItem, BatchConfirmResponse, MatchResult, MediaTask, MediaType, TaskCandidate, TaskStatus } from '@/types'

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

  getTaskCandidates(id: number) {
    return http.get<ApiResponse<TaskCandidate[]>>(`/api/queue/${id}/candidates`)
  },

  searchQueue(id: number, keyword: string) {
    return http.get<ApiResponse<MatchResult[]>>(`/api/queue/${id}/search`, {
      params: { q: keyword },
    })
  },

  confirmTask(id: number, tmdbId: number, mediaType: MediaType) {
    return http.post<ApiResponse<void>>(`/api/queue/${id}/confirm`, {
      tmdbId,
      mediaType,
    })
  },

  batchConfirm(items: BatchConfirmItem[]) {
    return http.post<ApiResponse<BatchConfirmResponse>>('/api/queue/batch-confirm', {
      items,
    })
  },

  skipTask(id: number) {
    return http.post<ApiResponse<void>>(`/api/queue/${id}/skip`)
  },
}
