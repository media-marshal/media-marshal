import http from './http'
import type { ApiResponse, BatchConfirmItem, BatchConfirmResponse, MatchResult, MediaTask, MediaType, QueueRecognitionRequest, QueueRecognitionResponse, TaskCandidate, TaskStatus } from '@/types'

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

  searchMetadata(keyword: string, mediaType: MediaType) {
    return http.get<ApiResponse<MatchResult[]>>('/api/metadata/search', {
      params: { q: keyword, mediaType },
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

  updateTaskRecognition(id: number, request: QueueRecognitionRequest) {
    return http.put<ApiResponse<QueueRecognitionResponse>>(`/api/queue/${id}/recognition`, request)
  },

  rematchTaskRecognition(id: number, request: QueueRecognitionRequest) {
    return http.post<ApiResponse<QueueRecognitionResponse>>(`/api/queue/${id}/recognition/rematch`, request)
  },

  skipTask(id: number) {
    return http.post<ApiResponse<void>>(`/api/queue/${id}/skip`)
  },
}
