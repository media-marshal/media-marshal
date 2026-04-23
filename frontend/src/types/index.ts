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

export type MediaType = 'MOVIE' | 'TV_SHOW'

export interface MediaTask {
  id: number
  sourcePath: string
  targetPath?: string
  status: TaskStatus
  mediaType?: MediaType
  parsedTitle?: string
  parsedYear?: number
  parsedSeason?: number
  parsedEpisode?: number
  tmdbId?: number
  confirmedTitle?: string
  confirmedYear?: number
  matchConfidence?: number
  operationType?: string
  errorMessage?: string
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

// ─── TMDB 搜索候选项 ─────────────────────────────────────────────
export interface MatchCandidate {
  source: string
  sourceId: string
  title: string
  originalTitle?: string
  year?: number
  mediaType: string
  overview?: string
  posterUrl?: string
  confidence: number
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
