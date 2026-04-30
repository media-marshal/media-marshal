// ─── API 通用响应 ────────────────────────────────────────────────
export interface ApiResponse<T> {
  success: boolean
  message?: string
  data: T
}

// ─── 媒体任务 ────────────────────────────────────────────────────
export type TaskStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'AWAITING_CONFIRMATION'
  | 'DONE'
  | 'FAILED'
  | 'SKIPPED'

export type MediaType = 'MOVIE' | 'TV_SHOW'
export type ConfirmationSource = 'AUTO_MATCH' | 'MANUAL_SINGLE' | 'MANUAL_BATCH'

export interface MediaTask {
  id: number
  sourcePath: string
  targetPath: string | null
  status: TaskStatus
  mediaType: MediaType | null
  parsedTitle: string | null
  parsedYear: number | null
  parsedSeason: number | null
  parsedEpisode: number | null
  parsedResolution: string | null
  tmdbId: number | null
  confirmedTitle: string | null
  confirmedYear: number | null
  confirmationSource: ConfirmationSource | null
  matchConfidence: number | null
  operationType: string | null
  errorMessage: string | null
  skipReason: string | null
  ruleId: number | null
  createdAt: string
  updatedAt: string
}

// ─── 配置项 ──────────────────────────────────────────────────────
export interface AppSetting {
  key: string
  value: string
  description?: string
  sensitive: boolean
}

// ─── 待确认队列候选项 ─────────────────────────────────────────────
export interface TaskCandidate {
  id: number
  taskId: number
  tmdbId: number
  title: string | null
  originalTitle: string | null
  year: number | null
  mediaType: MediaType
  confidence: number | null
  posterUrl: string | null
  overview: string | null
  rank: number
  selected: boolean
  createdAt: string
}

export interface MatchResult {
  source: 'tmdb'
  sourceId: string
  title: string | null
  originalTitle: string | null
  year: number | null
  mediaType: MediaType
  overview: string | null
  posterUrl: string | null
  confidence: number | null
}

export interface BatchConfirmItem {
  taskId: number
  tmdbId: number
  mediaType: MediaType
}

export interface BatchConfirmResult {
  taskId: number
  success: boolean
  message: string | null
}

export interface BatchConfirmResponse {
  results: BatchConfirmResult[]
}

// ─── 模板变量帮助 ──────────────────────────────────────────────────
export type TemplateVariableStatus = 'AVAILABLE' | 'RESERVED'

export interface TemplateVariableItem {
  name: string
  placeholder: string
  type: string
  source: string
  description: string
  example: string
  mediaTypes: MediaType[]
  status: TemplateVariableStatus
}

export interface TemplateVariableGroup {
  category: string
  categoryName: string
  variables: TemplateVariableItem[]
}

// ─── WebSocket 事件 ──────────────────────────────────────────────
export type WsEventType =
  | 'task.created'
  | 'task.processing'
  | 'task.confirm'
  | 'task.done'
  | 'task.failed'

export interface WsTaskEvent {
  type: WsEventType
  taskId: number
  status: TaskStatus
  sourcePath: string
}
