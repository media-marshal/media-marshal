<template>
  <div class="queue-view">
    <div class="page-header">
      <div>
        <h2>{{ t('queue.title') }}</h2>
        <p class="page-desc">{{ t('queue.description') }}</p>
      </div>
      <el-button :loading="loading" @click="loadQueue">
        {{ t('common.refresh') }}
      </el-button>
    </div>

    <el-empty v-if="!loading && queueTasks.length === 0" :description="t('queue.empty')" />

    <div v-else class="queue-list" v-loading="loading">
      <el-card v-for="task in queueTasks" :key="task.id" class="queue-card" shadow="never">
        <template #header>
          <div class="card-header">
            <div class="file-path" :title="task.sourcePath">{{ task.sourcePath }}</div>
            <div class="header-tags">
              <el-tag type="warning" size="small">
                {{ t('queue.confidence') }}: {{ formatConfidence(task.matchConfidence) }}
              </el-tag>
              <el-tag size="small">{{ t(`task.status.${task.status}`) }}</el-tag>
            </div>
          </div>
        </template>

        <el-descriptions :column="3" size="small" border class="task-meta">
          <el-descriptions-item :label="t('queue.parsedTitle')">
            {{ formatParsedTitle(task) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('queue.mediaType')">
            {{ task.mediaType ? t(`task.mediaType.${task.mediaType}`) : t('queue.unknown') }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('queue.episode')">
            {{ formatSeasonEpisode(task) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="search-row">
          <el-input
            v-model="searchKeywordByTask[task.id]"
            :placeholder="t('queue.searchPlaceholder')"
            clearable
            @keyup.enter="handleSearch(task.id)"
          />
          <el-button
            type="primary"
            :loading="searchLoadingByTask[task.id]"
            :disabled="!hasSearchKeyword(task.id)"
            @click="handleSearch(task.id)"
          >
            {{ t('queue.search') }}
          </el-button>
        </div>

        <div class="candidate-section">
          <div class="section-title">
            <span>{{ t('queue.candidates') }}</span>
            <el-button link type="primary" :loading="candidateLoadingByTask[task.id]" @click="loadCandidates(task.id)">
              {{ t('common.refresh') }}
            </el-button>
          </div>

          <el-empty
            v-if="!candidateLoadingByTask[task.id] && getTaskOptions(task.id).length === 0"
            :description="t('queue.noCandidates')"
            :image-size="80"
          />

          <el-radio-group v-else v-model="selectedOptionByTask[task.id]" class="candidate-list">
            <el-radio
              v-for="option in getTaskOptions(task.id)"
              :key="option.key"
              class="candidate-option"
              :label="option.key"
              border
            >
              <div class="candidate-content">
                <el-image
                  v-if="option.posterUrl"
                  class="poster"
                  :src="option.posterUrl"
                  :alt="displayTitle(option)"
                  fit="cover"
                  lazy
                />
                <div v-else class="poster poster--empty">
                  {{ t('queue.noPoster') }}
                </div>

                <div class="candidate-info">
                  <div class="candidate-title-row">
                    <span class="candidate-title">{{ displayTitle(option) }}</span>
                  </div>
                  <div class="candidate-tag-row">
                    <el-tag v-if="option.rank != null" size="small" type="warning">
                      {{ t('queue.rank', { rank: option.rank }) }}
                    </el-tag>
                    <el-tag size="small" type="info">
                      {{ option.origin === 'candidate' ? t('queue.systemCandidate') : t('queue.searchCandidate') }}
                    </el-tag>
                  </div>
                  <div class="candidate-meta">
                    <span>{{ option.year ?? t('queue.unknown') }}</span>
                    <span>{{ t(`task.mediaType.${option.mediaType}`) }}</span>
                    <span>{{ t('queue.confidence') }} {{ formatConfidence(option.confidence) }}</span>
                  </div>
                  <p class="overview">{{ option.overview || t('queue.noOverview') }}</p>
                </div>
              </div>
            </el-radio>
          </el-radio-group>
        </div>

        <div class="actions">
          <el-button
            type="primary"
            :loading="actionLoadingByTask[task.id]"
            :disabled="!selectedOptionByTask[task.id]"
            @click="handleConfirm(task.id)"
          >
            {{ t('queue.confirm') }}
          </el-button>
          <el-button :loading="actionLoadingByTask[task.id]" @click="handleSkip(task.id)">
            {{ t('queue.skip') }}
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMediaStore } from '@/stores/mediaStore'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mediaApi } from '@/api/media'
import type { MatchResult, MediaTask, MediaType, TaskCandidate } from '@/types'

const { t } = useI18n()
const mediaStore = useMediaStore()
const { queueTasks, loading } = storeToRefs(mediaStore)

type OptionOrigin = 'candidate' | 'search'

interface QueueOption {
  key: string
  origin: OptionOrigin
  tmdbId: number
  title: string | null
  originalTitle: string | null
  year: number | null
  mediaType: MediaType
  overview: string | null
  posterUrl: string | null
  confidence: number | null
  rank: number | null
}

const candidateOptionsByTask = reactive<Record<number, QueueOption[]>>({})
const searchOptionsByTask = reactive<Record<number, QueueOption[]>>({})
const selectedOptionByTask = reactive<Record<number, string>>({})
const searchKeywordByTask = reactive<Record<number, string>>({})
const candidateLoadingByTask = reactive<Record<number, boolean>>({})
const searchLoadingByTask = reactive<Record<number, boolean>>({})
const actionLoadingByTask = reactive<Record<number, boolean>>({})

onMounted(loadQueue)

async function loadQueue() {
  await mediaStore.fetchQueue()
  await Promise.all(queueTasks.value.map((task) => loadCandidates(task.id)))
}

async function loadCandidates(taskId: number) {
  candidateLoadingByTask[taskId] = true
  try {
    const res = await mediaApi.getTaskCandidates(taskId)
    const options = res.data.data.map(mapCandidateToOption)
    candidateOptionsByTask[taskId] = options

    const selected = options.find((option) => option.key === selectedOptionByTask[taskId])
    const backendSelected = options.find((option) =>
      res.data.data.some((candidate) => candidate.selected && option.tmdbId === candidate.tmdbId && option.mediaType === candidate.mediaType),
    )
    selectedOptionByTask[taskId] = selected?.key ?? backendSelected?.key ?? ''
  } finally {
    candidateLoadingByTask[taskId] = false
  }
}

async function handleSearch(taskId: number) {
  const keyword = searchKeywordByTask[taskId]?.trim()
  if (!keyword) return

  searchLoadingByTask[taskId] = true
  try {
    const res = await mediaApi.searchQueue(taskId, keyword)
    searchOptionsByTask[taskId] = res.data.data
      .map(mapSearchResultToOption)
      .filter((option): option is QueueOption => option !== null)
  } finally {
    searchLoadingByTask[taskId] = false
  }
}

async function handleConfirm(taskId: number) {
  const selected = getTaskOptions(taskId).find((option) => option.key === selectedOptionByTask[taskId])
  if (!selected) return

  actionLoadingByTask[taskId] = true
  try {
    await mediaStore.confirmTask(taskId, selected.tmdbId, selected.mediaType)
    ElMessage.success(t('queue.confirmSuccess'))
  } finally {
    actionLoadingByTask[taskId] = false
  }
}

async function handleSkip(taskId: number) {
  try {
    await ElMessageBox.confirm(t('queue.skipConfirm'), t('queue.skipTitle'), {
      confirmButtonText: t('queue.skip'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
  } catch {
    return
  }

  actionLoadingByTask[taskId] = true
  try {
    await mediaStore.skipTask(taskId)
    ElMessage.success(t('queue.skipSuccess'))
  } finally {
    actionLoadingByTask[taskId] = false
  }
}

function getTaskOptions(taskId: number) {
  const candidates = candidateOptionsByTask[taskId] ?? []
  const searches = searchOptionsByTask[taskId] ?? []
  const seen = new Set(candidates.map((option) => option.key))
  return [...candidates, ...searches.filter((option) => !seen.has(option.key))]
}

function hasSearchKeyword(taskId: number) {
  return Boolean(searchKeywordByTask[taskId]?.trim())
}

function mapCandidateToOption(candidate: TaskCandidate): QueueOption {
  return {
    key: buildOptionKey(candidate.tmdbId, candidate.mediaType),
    origin: 'candidate',
    tmdbId: candidate.tmdbId,
    title: candidate.title,
    originalTitle: candidate.originalTitle,
    year: candidate.year,
    mediaType: candidate.mediaType,
    overview: candidate.overview,
    posterUrl: candidate.posterUrl,
    confidence: candidate.confidence,
    rank: candidate.rank,
  }
}

function mapSearchResultToOption(result: MatchResult): QueueOption | null {
  const tmdbId = Number.parseInt(result.sourceId, 10)
  if (Number.isNaN(tmdbId)) return null

  return {
    key: buildOptionKey(tmdbId, result.mediaType),
    origin: 'search',
    tmdbId,
    title: result.title,
    originalTitle: result.originalTitle,
    year: result.year,
    mediaType: result.mediaType,
    overview: result.overview,
    posterUrl: result.posterUrl,
    confidence: result.confidence,
    rank: null,
  }
}

function buildOptionKey(tmdbId: number, mediaType: MediaType) {
  return `${mediaType}:${tmdbId}`
}

function displayTitle(option: QueueOption) {
  return option.title || option.originalTitle || t('queue.unknownTitle')
}

function formatParsedTitle(task: MediaTask) {
  const title = task.parsedTitle || t('queue.unknownTitle')
  return task.parsedYear ? `${title} (${task.parsedYear})` : title
}

function formatSeasonEpisode(task: MediaTask) {
  if (task.parsedSeason == null && task.parsedEpisode == null) return t('queue.notApplicable')
  const parts: string[] = []
  if (task.parsedSeason != null) parts.push(t('queue.season', { season: task.parsedSeason }))
  if (task.parsedEpisode != null) parts.push(t('queue.episodeNumber', { episode: task.parsedEpisode }))
  return parts.join(' / ')
}

function formatConfidence(confidence: number | null) {
  return confidence == null ? t('queue.unknown') : `${(confidence * 100).toFixed(0)}%`
}
</script>

<style scoped>
.queue-view {
  padding: 24px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

h2 {
  margin: 0;
  font-size: 22px;
  color: #303133;
}

.page-desc {
  margin: 8px 0 0;
  color: #909399;
  font-size: 14px;
}

.queue-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18px;
  min-height: 160px;
}

.queue-card {
  border: 1px solid #e4e7ed;
  border-radius: 14px;
  transition:
    box-shadow 0.18s ease,
    transform 0.18s ease,
    border-color 0.18s ease;
}

.queue-card:hover {
  border-color: #c6e2ff;
  box-shadow: 0 10px 26px rgba(64, 158, 255, 0.1);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.file-path {
  flex: 1;
  min-width: 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f5f7fa;
  color: #606266;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-tags {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.task-meta {
  margin-bottom: 18px;
}

.search-row {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}

.candidate-section {
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  background: #fbfcff;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  color: #303133;
  font-weight: 600;
}

.candidate-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
  width: 100%;
}

.candidate-option {
  height: auto;
  margin: 0;
  padding: 0;
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
}

.candidate-option :deep(.el-radio__input) {
  align-self: flex-start;
  padding: 16px 0 0 14px;
}

.candidate-option :deep(.el-radio__label) {
  width: 100%;
  padding: 0;
}

.candidate-content {
  display: flex;
  gap: 12px;
  min-width: 0;
  padding: 12px;
}

.poster {
  width: 72px;
  height: 108px;
  flex-shrink: 0;
  border-radius: 8px;
  background: #eef0f4;
}

.poster--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 8px;
  color: #a8abb2;
  font-size: 12px;
  text-align: center;
}

.candidate-info {
  min-width: 0;
  flex: 1;
  overflow: hidden;
}

.candidate-title-row {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
  min-width: 0;
}

.candidate-title {
  display: block;
  width: 100%;
  overflow: hidden;
  color: #303133;
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.candidate-tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
  min-width: 0;
}

.candidate-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
  color: #909399;
  font-size: 12px;
}

.overview {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: #606266;
  font-size: 13px;
  line-height: 1.55;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 760px) {
  .page-header,
  .card-header,
  .search-row {
    flex-direction: column;
    align-items: stretch;
  }

  .header-tags {
    flex-wrap: wrap;
  }
}
</style>
