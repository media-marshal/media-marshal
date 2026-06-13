import http from './http'
import type { ApiResponse } from '@/types'

export type DiscoveryMode = 'WATCH_EVENT' | 'PERIODIC_SCAN' | 'HYBRID'
export type FileOperationType = 'MOVE' | 'COPY' | 'HARD_LINK' | 'SYMBOLIC_LINK'
export type WatchRuleImportPreviewStatus = 'READY' | 'SKIPPED_DUPLICATE' | 'CONFLICT' | 'INVALID' | 'WARNING'

export interface WatchRule {
  id?: number
  name: string
  sourceDir: string
  targetDir: string
  mediaType: 'AUTO' | 'MOVIE' | 'TV_SHOW'
  moviePathTemplate?: string | null
  tvPathTemplate?: string | null
  operation: FileOperationType
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

export interface WatchRuleValidationResult {
  valid: boolean
  message: string | null
  details?: string[]
}

export interface WatchRuleImportRule {
  name: string
  sourceDir: string
  targetDir: string
  mediaType: 'AUTO' | 'MOVIE' | 'TV_SHOW' | string
  moviePathTemplate?: string | null
  tvPathTemplate?: string | null
  operation: FileOperationType | string
  enabled: boolean
  moveAssociatedFiles: boolean
  cleanupEmptyDirs: boolean
  generateNfo: boolean
  ignoredFilePatterns: string[] | null
  discoveryMode: DiscoveryMode | string
  scanIntervalMinutes: number
  webhookEnabled: boolean
}

export interface WatchRuleImportPackage {
  kind: string
  schemaVersion: number
  appVersion?: string | null
  exportedAt?: string | null
  rules: WatchRuleImportRule[]
}

export interface WatchRuleImportRequest {
  package: WatchRuleImportPackage
  preserveEnabledState: boolean
}

export interface WatchRuleImportSummary {
  importable: number
  ready: number
  skipped: number
  conflicts: number
  invalid: number
  warnings: number
}

export interface WatchRuleImportPreviewItem {
  index: number
  status: WatchRuleImportPreviewStatus
  message: string
  warnings: string[]
  rule: WatchRuleImportRule | null
}

export interface WatchRuleImportPreview {
  kind: string | null
  schemaVersion: number | null
  appVersion: string | null
  exportedAt: string | null
  ruleCount: number
  fileValid: boolean
  hasBlockingIssues: boolean
  fileMessage: string | null
  preserveEnabledState: boolean
  summary: WatchRuleImportSummary
  items: WatchRuleImportPreviewItem[]
}

export interface WatchRuleImportResult {
  preserveEnabledState: boolean
  reloadTriggered: boolean
  createdCount: number
  skippedCount: number
  conflictCount: number
  invalidCount: number
  warningCount: number
  createdRules: WatchRule[]
  items: WatchRuleImportPreviewItem[]
}

export const watchRuleApi = {
  listRules() {
    return http.get<ApiResponse<WatchRule[]>>('/api/watch-rules')
  },

  exportRules() {
    return http.get<ApiResponse<WatchRuleImportPackage>>('/api/watch-rules/export')
  },

  previewImport(data: WatchRuleImportRequest) {
    return http.post<ApiResponse<WatchRuleImportPreview>>('/api/watch-rules/import/preview', data)
  },

  importRules(data: WatchRuleImportRequest) {
    return http.post<ApiResponse<WatchRuleImportResult>>('/api/watch-rules/import', data)
  },

  createRule(data: WatchRuleRequest) {
    return http.post<ApiResponse<WatchRule>>('/api/watch-rules', data)
  },

  validateRule(data: WatchRuleRequest) {
    return http.post<ApiResponse<WatchRuleValidationResult>>('/api/watch-rules/validate', data)
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
