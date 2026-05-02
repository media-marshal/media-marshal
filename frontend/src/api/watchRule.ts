import http from './http'
import type { ApiResponse } from '@/types'

export type DiscoveryMode = 'WATCH_EVENT' | 'PERIODIC_SCAN' | 'HYBRID'

export interface WatchRule {
  id?: number
  name: string
  sourceDir: string
  targetDir: string
  mediaType: 'AUTO' | 'MOVIE' | 'TV_SHOW'
  moviePathTemplate?: string | null
  tvPathTemplate?: string | null
  operation: 'MOVE' | 'COPY' | 'HARD_LINK' | 'SYMBOLIC_LINK'
  enabled: boolean
  moveAssociatedFiles: boolean
  cleanupEmptyDirs: boolean
  generateNfo: boolean
  ignoredFilePatterns: string[] | null
  discoveryMode: DiscoveryMode
  scanIntervalMinutes: number
  webhookEnabled: boolean
  userId?: number
  createdAt?: string
  updatedAt?: string
}

export type WatchRuleRequest = Omit<WatchRule, 'id' | 'userId' | 'createdAt' | 'updatedAt'>

export const watchRuleApi = {
  listRules() {
    return http.get<ApiResponse<WatchRule[]>>('/api/watch-rules')
  },

  createRule(data: WatchRuleRequest) {
    return http.post<ApiResponse<WatchRule>>('/api/watch-rules', data)
  },

  updateRule(id: number, data: WatchRuleRequest) {
    return http.put<ApiResponse<WatchRule>>(`/api/watch-rules/${id}`, data)
  },

  deleteRule(id: number) {
    return http.delete<ApiResponse<void>>(`/api/watch-rules/${id}`)
  },

  toggleRule(id: number) {
    return http.patch<ApiResponse<WatchRule>>(`/api/watch-rules/${id}/toggle`)
  },

  scanRule(id: number) {
    return http.post<ApiResponse<void>>(`/api/watch-rules/${id}/scan`)
  },
}
