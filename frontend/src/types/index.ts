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
  | 'CORRECTED'

export type MediaType = 'MOVIE' | 'TV_SHOW'
export type MediaAssetType = 'VIDEO_FILE' | 'ISO_IMAGE' | 'BLURAY_DIRECTORY'
export type ConfirmationSource = 'AUTO_MATCH' | 'MANUAL_SINGLE' | 'MANUAL_BATCH' | 'MANUAL_CORRECTION'
export type TaskErrorCode = 'TARGET_CONFLICT' | 'UNSAFE_TARGET_PATH' | 'SOURCE_MISSING' | 'PIPELINE_FAILED'
export type DiscoveryMode = 'WATCH_EVENT' | 'PERIODIC_SCAN' | 'HYBRID'

export interface MediaTask {
  id: number
  sourcePath: string
  targetPath: string | null
  assetType: MediaAssetType
  status: TaskStatus
  mediaType: MediaType | null
  parsedTitle: string | null
  parsedYear: number | null
  parsedSeason: number | null
  parsedEpisode: number | null
  parsedEpisodeEnd: number | null
  parsedResolution: string | null
  parsedCodec: string | null
  parsedReleaseGroup: string | null
  tmdbId: number | null
  confirmedTitle: string | null
  confirmedOriginalTitle: string | null
  confirmedYear: number | null
  confirmedGenre1: string | null
  confirmedGenre2: string | null
  confirmedGenre3: string | null
  confirmedGenre4: string | null
  confirmedCountry: string | null
  confirmedEpisodeTitle: string | null
  confirmationSource: ConfirmationSource | null
  matchConfidence: number | null
  errorCode: TaskErrorCode | null
  failureCount: number | null
  lastFailedAt: string | null
  operationType: string | null
  errorMessage: string | null
  skipReason: string | null
  correctedFromTaskId: number | null
  correctedToTaskId: number | null
  correctedAt: string | null
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

export interface EffectiveSetting {
  key: string
  value: string
  source: string
  overriddenByDatabase: boolean
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
  genre1: string | null
  genre2: string | null
  genre3: string | null
  genre4: string | null
  country: string | null
  episodeTitle: string | null
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
  genres: string[] | null
  country: string | null
  episodeTitle: string | null
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

export interface QueueRecognitionRequest {
  mediaType: MediaType
  parsedTitle: string
  parsedYear: number | null
  parsedSeason: number | null
  parsedEpisode: number | null
  parsedEpisodeEnd: number | null
}

export interface QueueRecognitionResponse {
  task: MediaTask
  candidates: TaskCandidate[]
}

export interface TaskCorrectionRequest {
  mediaType: MediaType
  parsedTitle: string
  parsedYear: number | null
  parsedSeason: number | null
  parsedEpisode: number | null
  parsedEpisodeEnd: number | null
  tmdbId?: number | null
  regenerateNfo?: boolean
}

export type TaskCorrectionOperationType =
  | 'MOVE_MAIN_ASSET'
  | 'MOVE_ASSOCIATED_FILE'
  | 'GENERATE_NFO'
  | 'CLEAN_EMPTY_DIR'
  | 'CREATE_CORRECTION_TASK'
  | 'MARK_ORIGINAL_CORRECTED'

export interface TaskCorrectionOperation {
  type: TaskCorrectionOperationType
  sourcePath: string | null
  targetPath: string | null
  description: string | null
}

export interface TaskCorrectionPreview {
  currentTargetPath: string | null
  correctedTargetPath: string | null
  sameTargetPath: boolean
  selectedMatch: MatchResult | null
  operations: TaskCorrectionOperation[]
  blockers: string[]
  warnings: string[]
  canApply: boolean
}

export interface TaskCorrectionRematchResponse {
  candidates: MatchResult[]
}

export interface TaskCorrectionApplyResponse {
  originalTask: MediaTask
  correctedTask: MediaTask
  preview: TaskCorrectionPreview
}

// ─── 模板变量帮助 ──────────────────────────────────────────────────
export type TemplateVariableStatus = 'AVAILABLE' | 'RESERVED' | 'DEPRECATED' | 'UNAVAILABLE'

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

export interface TemplatePreviewRequest {
  template: string
  mediaType: MediaType
  targetDir?: string | null
  context?: Record<string, unknown>
}

export interface TemplatePreviewResponse {
  output: string
  warnings: string[]
  errors: string[]
  usedVariables: string[]
  unknownVariables: string[]
  reservedVariables: string[]
  unsafePath: boolean
}

// ─── 发布说明 ────────────────────────────────────────────────────
export type ReleaseNoteItemType = 'feature' | 'fix' | 'optimization'

export interface ReleaseNoteItem {
  type: ReleaseNoteItemType
  key: string
}

export interface ReleaseNote {
  version: string
  date: string
  items: ReleaseNoteItem[]
}

// ─── WebSocket 事件 ──────────────────────────────────────────────
export type WsEventType =
  | 'task.created'
  | 'task.processing'
  | 'task.confirm'
  | 'task.done'
  | 'task.failed'
  | 'task.corrected'

export interface WsTaskEvent {
  type: WsEventType
  taskId: number
  status: TaskStatus
  sourcePath: string
}
