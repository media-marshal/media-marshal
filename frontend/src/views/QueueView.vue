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
      <div class="global-search-panel">
        <div class="panel-header">
          <div>
            <h3>{{ t('queue.globalSearchTitle') }}</h3>
            <p>{{ currentCandidateSummary }}</p>
          </div>
          <el-button link type="primary" @click="searchPanelExpanded = !searchPanelExpanded">
            {{ searchPanelExpanded ? t('queue.collapseSearch') : t('queue.expandSearch') }}
          </el-button>
        </div>

        <div v-if="searchPanelExpanded" class="global-search-body">
          <div class="global-search-row">
            <el-select v-model="globalSearchMediaType" class="media-type-select">
              <el-option :label="t('task.mediaType.MOVIE')" value="MOVIE" />
              <el-option :label="t('task.mediaType.TV_SHOW')" value="TV_SHOW" />
            </el-select>
            <el-input
              v-model="globalSearchKeyword"
              :placeholder="t('queue.globalSearchPlaceholder')"
              clearable
              @keyup.enter="handleGlobalSearch"
            />
            <el-button
              type="primary"
              :loading="globalSearchLoading"
              :disabled="!globalSearchKeyword.trim()"
              @click="handleGlobalSearch"
            >
              {{ t('queue.search') }}
            </el-button>
          </div>

          <el-empty
            v-if="globalSearchPerformed && !globalSearchLoading && globalSearchResults.length === 0"
            :description="t('queue.noSearchResults')"
            :image-size="80"
          />

          <div v-if="globalSearchResults.length > 0" class="global-result-list">
            <button
              v-for="option in globalSearchResults"
              :key="option.key"
              class="global-result-card"
              :class="{ 'is-selected': currentCandidate?.key === option.key }"
              type="button"
              @click="setCurrentCandidate(option)"
            >
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
                <div class="candidate-title">{{ displayTitle(option) }}</div>
                <div class="candidate-meta">
                  <span>{{ option.year ?? t('queue.unknown') }}</span>
                  <span>{{ t(`task.mediaType.${option.mediaType}`) }}</span>
                  <span>{{ t('queue.confidence') }} {{ formatConfidence(option.confidence) }}</span>
                </div>
                <p class="overview">{{ option.overview || t('queue.noOverview') }}</p>
              </div>
            </button>
          </div>
        </div>
      </div>

      <div class="batch-toolbar">
        <div class="batch-actions">
          <el-button size="small" @click="selectCurrentPageTasks">
            {{ t('queue.selectCurrentPageTasks') }}
            <el-tooltip :content="t('queue.selectCurrentPageTasksHelp')" placement="top">
              <el-icon class="button-help-icon" @click.stop.prevent>
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
          </el-button>
          <el-button size="small" @click="clearCurrentPageSelection">
            {{ t('queue.clearPageSelection') }}
            <el-tooltip :content="t('queue.clearPageSelectionHelp')" placement="top">
              <el-icon class="button-help-icon" @click.stop.prevent>
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
          </el-button>
          <el-divider direction="vertical" class="batch-divider" />
          <el-button size="small" :disabled="!canApplyCurrentCandidate" @click="applyCurrentCandidateToSelectedTasks">
            {{ t('queue.applyCurrentCandidate') }}
            <el-tooltip :content="t('queue.applyCurrentCandidateHelp')" placement="top">
              <el-icon class="button-help-icon" @click.stop.prevent>
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
          </el-button>
          <el-button
            size="small"
            type="primary"
            :loading="batchConfirming"
            :disabled="currentPageBatchItems.length === 0"
            @click="handleBatchConfirm"
          >
            {{ t('queue.batchConfirm', { count: currentPageBatchItems.length }) }}
            <el-tooltip :content="t('queue.batchConfirmHelp')" placement="top">
              <el-icon class="button-help-icon" @click.stop.prevent>
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
          </el-button>
          <el-button
            size="small"
            type="warning"
            :loading="batchSkipping"
            :disabled="currentPageSelectedTaskIds.length === 0"
            @click="handleBatchSkip"
          >
            {{ t('queue.batchSkip', { count: currentPageSelectedTaskIds.length }) }}
            <el-tooltip :content="t('queue.batchSkipHelp')" placement="top">
              <el-icon class="button-help-icon" @click.stop.prevent>
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
          </el-button>
        </div>
        <el-text size="small" type="info">
          {{ t('queue.pageSelectionHint') }}
        </el-text>
      </div>

      <div v-if="visibleCandidateGroups.length > 0" class="candidate-groups">
        <el-alert
          v-for="group in visibleCandidateGroups"
          :key="group.key"
          type="info"
          show-icon
          :closable="false"
        >
          <template #title>
            <span>
              {{ t('queue.sameCandidateHint', { count: group.count, title: group.title }) }}
            </span>
            <el-button size="small" link type="primary" @click="quickSelectCandidateGroup(group.key)">
              {{ t('queue.quickSelect') }}
            </el-button>
          </template>
        </el-alert>
        <el-button
          v-if="candidateGroups.length > 3"
          size="small"
          link
          type="primary"
          @click="recommendationExpanded = !recommendationExpanded"
        >
          {{ recommendationExpanded
            ? t('queue.collapseRecommendations')
            : t('queue.expandRecommendations', { count: candidateGroups.length - 3 }) }}
        </el-button>
      </div>

      <el-card v-for="task in displayedTasks" :key="task.id" class="queue-card" shadow="never">
        <template #header>
          <div class="card-header">
            <el-checkbox
              :model-value="selectedTaskIds.has(task.id)"
              @change="(checked) => toggleTaskSelection(task.id, Boolean(checked))"
            />
            <div class="file-path" :title="task.sourcePath">{{ task.sourcePath }}</div>
            <div class="header-tags">
              <el-tag :type="selectedTaskIds.has(task.id) ? (selectedOptionByTask[task.id] ? 'success' : 'info') : 'info'" size="small">
                {{ taskSelectionStatus(task.id) }}
              </el-tag>
              <el-tag :type="assetTypeTagType(task.assetType)" size="small">
                {{ t(`task.assetType.${task.assetType || 'VIDEO_FILE'}`) }}
              </el-tag>
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

          <el-radio-group
            v-else
            v-model="selectedOptionByTask[task.id]"
            class="candidate-list"
            @change="markManualSelection(task.id)"
          >
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
          <el-text v-if="batchErrorByTask[task.id]" type="danger" size="small">
            {{ batchErrorByTask[task.id] }}
          </el-text>
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
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="sortedQueueTasks.length"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMediaStore } from '@/stores/mediaStore'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mediaApi } from '@/api/media'
import type { BatchConfirmItem, MatchResult, MediaAssetType, MediaTask, MediaType, TaskCandidate } from '@/types'

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
const candidateLoadingByTask = reactive<Record<number, boolean>>({})
const actionLoadingByTask = reactive<Record<number, boolean>>({})
const batchErrorByTask = reactive<Record<number, string>>({})
const manualSelectedTaskIds = reactive(new Set<number>())
const selectedTaskIds = reactive(new Set<number>())
const currentPage = ref(1)
const pageSize = ref(20)
const batchConfirming = ref(false)
const batchSkipping = ref(false)
const globalSearchKeyword = ref('')
const globalSearchMediaType = ref<MediaType>('TV_SHOW')
const globalSearchLoading = ref(false)
const globalSearchPerformed = ref(false)
const globalSearchResults = ref<QueueOption[]>([])
const currentCandidate = ref<QueueOption | null>(null)
const searchPanelExpanded = ref(true)
const recommendationExpanded = ref(false)

const sortedQueueTasks = computed(() => {
  return [...queueTasks.value].sort((a, b) => taskTime(b) - taskTime(a))
})

const displayedTasks = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return sortedQueueTasks.value.slice(start, start + pageSize.value)
})

const currentPageBatchItems = computed<BatchConfirmItem[]>(() => {
  return displayedTasks.value
    .map((task) => {
      if (!selectedTaskIds.has(task.id)) return null
      const selected = getSelectedOption(task.id)
      return selected
        ? { taskId: task.id, tmdbId: selected.tmdbId, mediaType: selected.mediaType }
        : null
    })
    .filter((item): item is BatchConfirmItem => item !== null)
})

const currentPageSelectedTaskIds = computed(() => {
  return displayedTasks.value
    .filter((task) => selectedTaskIds.has(task.id))
    .map((task) => task.id)
})

const canApplyCurrentCandidate = computed(() => {
  return Boolean(currentCandidate.value) && displayedTasks.value.some((task) => selectedTaskIds.has(task.id))
})

const candidateGroups = computed(() => {
  const groups = new Map<string, { key: string, title: string, count: number, totalConfidence: number, option: QueueOption, firstIndex: number }>()
  let index = 0
  for (const task of displayedTasks.value) {
    const seenInTask = new Set<string>()
    for (const option of candidateOptionsByTask[task.id] ?? []) {
      if (seenInTask.has(option.key)) continue
      seenInTask.add(option.key)

      const group = groups.get(option.key)
      if (group) {
        group.count++
        group.totalConfidence += option.confidence ?? 0
      } else {
        groups.set(option.key, {
          key: option.key,
          title: displayTitle(option),
          count: 1,
          totalConfidence: option.confidence ?? 0,
          option,
          firstIndex: index++,
        })
      }
    }
  }

  return [...groups.values()]
    .filter((group) => group.count > 1)
    .sort((a, b) => {
      if (b.count !== a.count) return b.count - a.count
      const confidenceDiff = (b.totalConfidence / b.count) - (a.totalConfidence / a.count)
      if (confidenceDiff !== 0) return confidenceDiff
      return a.firstIndex - b.firstIndex
    })
})

const visibleCandidateGroups = computed(() => {
  return recommendationExpanded.value ? candidateGroups.value : candidateGroups.value.slice(0, 3)
})

const currentCandidateSummary = computed(() => {
  if (!currentCandidate.value) {
    return t('queue.noCurrentCandidate')
  }
  return t('queue.currentCandidateSummary', {
    title: displayTitle(currentCandidate.value),
    year: currentCandidate.value.year ?? t('queue.unknown'),
    mediaType: t(`task.mediaType.${currentCandidate.value.mediaType}`),
  })
})

onMounted(loadQueue)

watch([currentPage, pageSize], async () => {
  await loadCurrentPageCandidates()
})

watch(pageSize, () => {
  currentPage.value = 1
})

watch(sortedQueueTasks, () => {
  const maxPage = Math.max(1, Math.ceil(sortedQueueTasks.value.length / pageSize.value))
  if (currentPage.value > maxPage) {
    currentPage.value = maxPage
  }
})

async function loadQueue() {
  await mediaStore.fetchQueue()
  await loadCurrentPageCandidates()
}

async function loadCurrentPageCandidates() {
  await Promise.all(displayedTasks.value.map((task) => loadCandidates(task.id)))
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

async function handleGlobalSearch() {
  const keyword = globalSearchKeyword.value.trim()
  if (!keyword) return

  globalSearchLoading.value = true
  globalSearchPerformed.value = true
  try {
    const res = await mediaApi.searchMetadata(keyword, globalSearchMediaType.value)
    globalSearchResults.value = res.data.data
      .map(mapSearchResultToOption)
      .filter((option): option is QueueOption => option !== null)
    if (globalSearchResults.value.length > 0) {
      setCurrentCandidate(globalSearchResults.value[0])
    }
  } catch {
    ElMessage.error(t('queue.searchFailed'))
  } finally {
    globalSearchLoading.value = false
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

function selectCurrentPageTasks() {
  for (const task of displayedTasks.value) {
    selectedTaskIds.add(task.id)
  }
}

function quickSelectCandidateGroup(groupKey: string) {
  for (const task of displayedTasks.value) {
    const option = (candidateOptionsByTask[task.id] ?? []).find((item) => item.key === groupKey)
    if (option) {
      selectedTaskIds.add(task.id)
      selectedOptionByTask[task.id] = option.key
      manualSelectedTaskIds.add(task.id)
      delete batchErrorByTask[task.id]
    }
  }
}

function setCurrentCandidate(option: QueueOption) {
  currentCandidate.value = option
}

async function applyCurrentCandidateToSelectedTasks() {
  const candidate = currentCandidate.value
  if (!candidate) {
    ElMessage.warning(t('queue.selectCandidateFirst'))
    return
  }

  const selectedTasks = displayedTasks.value.filter((task) => selectedTaskIds.has(task.id))
  if (selectedTasks.length === 0) {
    ElMessage.warning(t('queue.selectTasksFirst'))
    return
  }

  const mismatched = selectedTasks.filter((task) => task.mediaType && task.mediaType !== candidate.mediaType)
  if (mismatched.length > 0) {
    ElMessage.warning(t('queue.mediaTypeMismatch'))
    return
  }

  const overwriteCount = selectedTasks.filter((task) => Boolean(selectedOptionByTask[task.id])).length
  if (overwriteCount > 0) {
    try {
      await ElMessageBox.confirm(
        t('queue.overwriteCandidateConfirm', { count: overwriteCount }),
        t('queue.overwriteCandidateTitle'),
        {
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
          type: 'warning',
        },
      )
    } catch {
      return
    }
  }

  for (const task of selectedTasks) {
    ensureSearchOptionForTask(task.id, candidate)
    selectedOptionByTask[task.id] = candidate.key
    manualSelectedTaskIds.add(task.id)
    delete batchErrorByTask[task.id]
  }
}

function clearCurrentPageSelection() {
  for (const task of displayedTasks.value) {
    selectedTaskIds.delete(task.id)
    selectedOptionByTask[task.id] = ''
    manualSelectedTaskIds.delete(task.id)
  }
}

function markManualSelection(taskId: number) {
  selectedTaskIds.add(taskId)
  manualSelectedTaskIds.add(taskId)
  delete batchErrorByTask[taskId]
}

function toggleTaskSelection(taskId: number, checked: boolean) {
  if (checked) {
    selectedTaskIds.add(taskId)
  } else {
    selectedTaskIds.delete(taskId)
  }
}

async function handleBatchConfirm() {
  const items = currentPageBatchItems.value
  if (items.length === 0) return

  const lowConfidenceCount = displayedTasks.value.filter((task) => {
    const selected = getSelectedOption(task.id)
    return selected && (selected.confidence == null || selected.confidence < 0.6)
  }).length

  try {
    await ElMessageBox.confirm(
      t('queue.batchConfirmMessage', { count: items.length, lowConfidenceCount }),
      t('queue.batchConfirmTitle'),
      {
        confirmButtonText: t('queue.batchConfirmAction'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      },
    )
  } catch {
    return
  }

  batchConfirming.value = true
  try {
    for (const item of items) {
      delete batchErrorByTask[item.taskId]
    }

    const res = await mediaApi.batchConfirm(items)
    const results = res.data.data.results
    const successCount = results.filter((result) => result.success).length
    const failedResults = results.filter((result) => !result.success)
    for (const result of failedResults) {
      batchErrorByTask[result.taskId] = result.message || t('queue.batchConfirmUnknownError')
    }

    ElMessage.success(t('queue.batchConfirmResult', { success: successCount, failed: failedResults.length }))
    await loadQueue()
  } finally {
    batchConfirming.value = false
  }
}

async function handleBatchSkip() {
  const taskIds = currentPageSelectedTaskIds.value
  if (taskIds.length === 0) return

  try {
    await ElMessageBox.confirm(
      t('queue.batchSkipConfirm', { count: taskIds.length }),
      t('queue.batchSkipTitle'),
      {
        confirmButtonText: t('queue.batchSkipAction'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      },
    )
  } catch {
    return
  }

  batchSkipping.value = true
  try {
    let successCount = 0
    let failedCount = 0
    for (const taskId of taskIds) {
      try {
        await mediaStore.skipTask(taskId)
        selectedTaskIds.delete(taskId)
        selectedOptionByTask[taskId] = ''
        manualSelectedTaskIds.delete(taskId)
        successCount++
      } catch (error) {
        failedCount++
        batchErrorByTask[taskId] = error instanceof Error ? error.message : t('queue.batchSkipUnknownError')
      }
    }

    ElMessage.success(t('queue.batchSkipResult', { success: successCount, failed: failedCount }))
    await loadQueue()
  } finally {
    batchSkipping.value = false
  }
}

function getTaskOptions(taskId: number) {
  const candidates = candidateOptionsByTask[taskId] ?? []
  const searches = searchOptionsByTask[taskId] ?? []
  const seen = new Set(candidates.map((option) => option.key))
  return [...candidates, ...searches.filter((option) => !seen.has(option.key))]
}

function getSelectedOption(taskId: number) {
  return getTaskOptions(taskId).find((option) => option.key === selectedOptionByTask[taskId])
}

function ensureSearchOptionForTask(taskId: number, option: QueueOption) {
  const existing = getTaskOptions(taskId).some((item) => item.key === option.key)
  if (existing) return

  const taskSearchOptions = searchOptionsByTask[taskId] ?? []
  searchOptionsByTask[taskId] = [
    ...taskSearchOptions,
    {
      ...option,
      origin: 'search',
      rank: null,
    },
  ]
}

function taskSelectionStatus(taskId: number) {
  if (!selectedTaskIds.has(taskId)) {
    return t('queue.taskNotSelected')
  }
  if (selectedOptionByTask[taskId]) {
    return t('queue.taskSelectedWithCandidate')
  }
  return t('queue.taskSelectedWithoutCandidate')
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

function assetTypeTagType(assetType: MediaAssetType | null | undefined) {
  const map: Record<MediaAssetType, 'primary' | 'warning' | 'info'> = {
    VIDEO_FILE: 'info',
    ISO_IMAGE: 'warning',
    BLURAY_DIRECTORY: 'primary',
  }
  return map[assetType || 'VIDEO_FILE']
}

function taskTime(task: MediaTask) {
  const timestamp = new Date(task.createdAt).getTime()
  return Number.isNaN(timestamp) ? 0 : timestamp
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

.global-search-panel {
  padding: 16px;
  border: 1px solid #d9ecff;
  border-radius: 14px;
  background: #f8fbff;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.panel-header h3 {
  margin: 0 0 6px;
  color: #303133;
  font-size: 16px;
}

.panel-header p {
  margin: 0;
  color: #606266;
  font-size: 13px;
}

.global-search-body {
  margin-top: 14px;
}

.global-search-row {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr) auto;
  gap: 10px;
  margin-bottom: 14px;
}

.media-type-select {
  width: 160px;
}

.global-result-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.global-result-card {
  display: flex;
  gap: 12px;
  min-width: 0;
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  text-align: left;
}

.global-result-card.is-selected {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.14);
}

.batch-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  background: #fafbfc;
}

.batch-actions,
.candidate-groups {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.batch-actions :deep(.el-button + .el-button),
.candidate-groups :deep(.el-button + .el-button) {
  margin-left: 0;
}

.batch-divider {
  height: 24px;
  margin: 0 4px;
}

.button-help-icon {
  margin-left: 6px;
  font-size: 14px;
}

.candidate-groups {
  flex-direction: column;
}

.candidate-groups :deep(.el-alert__title span) {
  margin-right: 8px;
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
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
  gap: 14px;
  min-width: 0;
  padding: 14px;
}

.poster {
  width: 78px;
  height: 116px;
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
  line-clamp: 3;
}

.actions {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

@media (max-width: 760px) {
  .page-header,
  .card-header,
  .global-search-row,
  .batch-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .global-search-row {
    display: flex;
  }

  .media-type-select {
    width: 100%;
  }

  .header-tags {
    flex-wrap: wrap;
  }
}

@media (max-width: 1100px) {
  .global-result-list,
  .candidate-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .global-result-list,
  .candidate-list {
    grid-template-columns: 1fr;
  }
}
</style>
