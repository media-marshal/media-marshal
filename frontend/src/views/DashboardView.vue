<template>
  <div class="dashboard">
    <h2>{{ t('dashboard.title') }}</h2>

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <el-card shadow="never">
        <el-statistic :title="t('dashboard.totalTasks')" :value="tasks.length" />
      </el-card>
      <el-card shadow="never">
        <el-statistic :title="t('dashboard.doneTasks')" :value="doneTasks.length">
          <template #suffix><span style="color: #67c23a">✓</span></template>
        </el-statistic>
      </el-card>
      <el-card shadow="never">
        <el-statistic :title="t('dashboard.awaitingTasks')" :value="queueTasks.length">
          <template #suffix><span style="color: #e6a23c">!</span></template>
        </el-statistic>
      </el-card>
      <el-card shadow="never">
        <el-statistic :title="t('dashboard.skippedTasks')" :value="skippedTasks.length">
          <template #suffix><span style="color: #909399">-</span></template>
        </el-statistic>
      </el-card>
      <el-card shadow="never">
        <el-statistic :title="t('dashboard.failedTasks')" :value="failedTasks.length">
          <template #suffix><span style="color: #f56c6c">✗</span></template>
        </el-statistic>
      </el-card>
    </div>

    <!-- 最近任务列表 -->
    <el-card shadow="never" class="task-table-card">
      <template #header>
        <div class="card-header">
          <span>{{ t('dashboard.recentTasks') }}</span>
          <div class="header-actions">
            <el-select v-model="assetTypeFilter" size="small" class="dashboard-filter">
              <el-option :label="t('dashboard.allAssetTypes')" value="ALL" />
              <el-option
                v-for="assetType in assetTypeOptions"
                :key="assetType"
                :label="t(`task.assetType.${assetType}`)"
                :value="assetType"
              />
            </el-select>
            <el-select v-model="mediaTypeFilter" size="small" class="dashboard-filter">
              <el-option :label="t('dashboard.allMediaTypes')" value="ALL" />
              <el-option
                v-for="mediaType in mediaTypeOptions"
                :key="mediaType"
                :label="t(`task.mediaType.${mediaType}`)"
                :value="mediaType"
              />
            </el-select>
            <el-select v-model="statusFilter" size="small" class="dashboard-filter">
              <el-option :label="t('dashboard.allStatuses')" value="ALL" />
              <el-option
                v-for="status in statusOptions"
                :key="status"
                :label="t(`task.status.${status}`)"
                :value="status"
              />
            </el-select>
            <el-button
              size="small"
              type="danger"
              :disabled="selectedTasks.length === 0"
              :loading="batchDeleting"
              @click="handleBatchDelete"
            >
              {{ t('dashboard.batchDelete') }}
            </el-button>
            <el-button size="small" @click="fetchTasks()">{{ t('common.refresh') }}</el-button>
          </div>
        </div>
      </template>
      <el-table
        :data="displayedTasks"
        v-loading="loading"
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="42" />
        <el-table-column prop="sourcePath" :label="t('dashboard.file')" min-width="300" show-overflow-tooltip>
          <template #header>
            <div class="searchable-column-header">
              <el-input
                v-if="fileNameSearchActive"
                ref="fileNameSearchInputRef"
                v-model="fileNameKeyword"
                size="small"
                clearable
                class="table-header-search-input"
                :placeholder="t('dashboard.fileSearchPlaceholder')"
                @input="handleFileNameSearchInput"
                @clear="resetFileNameSearch"
              />
              <template v-else>
                <span>{{ t('dashboard.file') }}</span>
                <el-tooltip :content="t('dashboard.fileSearch')" placement="top">
                  <el-button
                    link
                    size="small"
                    class="table-header-search-button"
                    :icon="Search"
                    :aria-label="t('dashboard.fileSearch')"
                    @click.stop="activateFileNameSearch"
                  />
                </el-tooltip>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="assetType" :label="t('dashboard.assetType')" width="110">
          <template #default="{ row }">
            <el-tag :type="assetTypeTagType(row.assetType)" size="small">
              {{ t(`task.assetType.${row.assetType || 'VIDEO_FILE'}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="mediaType" :label="t('dashboard.mediaType')" width="80">
          <template #default="{ row }">
            {{ row.mediaType ? t(`task.mediaType.${row.mediaType}`) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('dashboard.status')" width="120">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.status === 'DONE' && row.targetPath"
              :content="row.targetPath"
              placement="top"
            >
              <el-tag :type="statusTagType(row.status)" size="small">
                {{ t(`task.status.${row.status}`) }}
              </el-tag>
            </el-tooltip>
            <el-tag v-else :type="statusTagType(row.status)" size="small">
              {{ t(`task.status.${row.status}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="confirmedTitle" :label="t('dashboard.matchedTitle')" width="200" show-overflow-tooltip>
          <template #header>
            <div class="searchable-column-header">
              <el-input
                v-if="matchedTitleSearchActive"
                ref="matchedTitleSearchInputRef"
                v-model="matchedTitleKeyword"
                size="small"
                clearable
                class="table-header-search-input"
                :placeholder="t('dashboard.matchedTitleSearchPlaceholder')"
                @input="handleMatchedTitleSearchInput"
                @clear="resetMatchedTitleSearch"
              />
              <template v-else>
                <span>{{ t('dashboard.matchedTitle') }}</span>
                <el-tooltip :content="t('dashboard.matchedTitleSearch')" placement="top">
                  <el-button
                    link
                    size="small"
                    class="table-header-search-button"
                    :icon="Search"
                    :aria-label="t('dashboard.matchedTitleSearch')"
                    @click.stop="activateMatchedTitleSearch"
                  />
                </el-tooltip>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('dashboard.details')" width="280" show-overflow-tooltip>
          <template #default="{ row }">
            <div v-if="row.status === 'FAILED'" class="failure-detail">
              <el-tag type="danger" size="small" effect="plain">
                {{ t('dashboard.failureCount', { count: normalizedFailureCount(row) }) }}
              </el-tag>
              <span class="detail-message">{{ taskDetails(row) }}</span>
            </div>
            <span v-else>{{ taskDetails(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="matchConfidence" :label="t('dashboard.confidence')" width="90">
          <template #default="{ row }">
            {{ row.matchConfidence != null ? (row.matchConfidence * 100).toFixed(0) + '%' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('dashboard.createdAt')" width="130">
          <template #default="{ row }">
            <div class="created-time">
              <span>{{ formatCreatedAt(row.createdAt).date }}</span>
              <span>{{ formatCreatedAt(row.createdAt).time }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="150" fixed="right">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.status === 'DONE' && row.assetType === 'ISO_IMAGE'"
              :content="t('dashboard.correction.isoUnsupported')"
              placement="top"
            >
              <span>
                <el-button link type="warning" size="small" :icon="EditPen" disabled>
                  {{ t('dashboard.correction.action') }}
                </el-button>
              </span>
            </el-tooltip>
            <el-button
              v-else-if="row.status === 'DONE'"
              link
              type="warning"
              size="small"
              :icon="EditPen"
              @click="openCorrectionDialog(row)"
            >
              {{ t('dashboard.correction.action') }}
            </el-button>
            <el-button
              link
              type="danger"
              size="small"
              :loading="deletingId === row.id"
              @click="handleDeleteTask(row)"
            >
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filteredTasks.length"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="correctionDialogVisible"
      :title="t('dashboard.correction.title')"
      width="min(1180px, calc(100vw - 64px))"
      align-center
      destroy-on-close
      class="correction-dialog"
    >
      <div v-if="correctionTask" class="correction-layout">
        <section class="correction-section current-info-section">
          <h3>{{ t('dashboard.correction.currentInfo') }}</h3>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item :label="t('dashboard.correction.sourcePath')" :span="2">
              <span class="muted-path">{{ correctionTask.sourcePath }}</span>
            </el-descriptions-item>
            <el-descriptions-item :label="t('dashboard.correction.currentTargetPath')" :span="2">
              <span class="muted-path">{{ correctionTask.targetPath || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item :label="t('dashboard.assetType')">
              {{ t(`task.assetType.${correctionTask.assetType || 'VIDEO_FILE'}`) }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('dashboard.mediaType')">
              {{ correctionTask.mediaType ? t(`task.mediaType.${correctionTask.mediaType}`) : '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('dashboard.correction.currentTmdbId')">
              {{ correctionTask.tmdbId || '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('dashboard.correction.confirmationSource')">
              {{ correctionTask.confirmationSource ? t(`task.confirmationSource.${correctionTask.confirmationSource}`) : '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <div class="correction-main-grid">
          <div class="correction-left-column">
            <section class="correction-section">
              <h3>{{ t('dashboard.correction.editRecognition') }}</h3>
              <el-form label-position="top" class="correction-form">
                <el-row :gutter="12">
                  <el-col :span="8">
                    <el-form-item :label="t('queue.mediaType')">
                      <el-select v-model="correctionForm.mediaType" @change="invalidateCorrectionPreview">
                        <el-option :label="t('task.mediaType.MOVIE')" value="MOVIE" />
                        <el-option
                          :label="t('task.mediaType.TV_SHOW')"
                          value="TV_SHOW"
                          :disabled="correctionTask.assetType === 'BLURAY_DIRECTORY'"
                        />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="10">
                    <el-form-item :label="t('queue.parsedTitle')">
                      <el-input v-model="correctionForm.parsedTitle" @input="invalidateCorrectionPreview" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="6">
                    <el-form-item :label="t('queue.parsedYear')">
                      <el-input-number
                        v-model="correctionForm.parsedYear"
                        :min="1888"
                        :max="2100"
                        controls-position="right"
                        @change="invalidateCorrectionPreview"
                      />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row v-if="correctionForm.mediaType === 'TV_SHOW'" :gutter="12">
                  <el-col :span="8">
                    <el-form-item :label="t('queue.parsedSeason')">
                      <el-input-number v-model="correctionForm.parsedSeason" :min="0" controls-position="right" @change="invalidateCorrectionPreview" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item :label="t('queue.parsedEpisode')">
                      <el-input-number v-model="correctionForm.parsedEpisode" :min="0" controls-position="right" @change="invalidateCorrectionPreview" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item :label="t('dashboard.correction.parsedEpisodeEnd')">
                      <el-input-number v-model="correctionForm.parsedEpisodeEnd" :min="0" controls-position="right" @change="invalidateCorrectionPreview" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <div class="correction-form-actions">
                  <el-button native-type="button" size="small" :icon="Refresh" :loading="correctionRematching" @click="rematchCorrection">
                    {{ t('dashboard.correction.rematch') }}
                  </el-button>
                  <el-checkbox v-model="correctionForm.regenerateNfo" @change="handleRegenerateNfoChange">
                    {{ t('dashboard.correction.regenerateNfo') }}
                  </el-checkbox>
                </div>
              </el-form>
            </section>

            <section class="correction-section">
              <div class="section-title-row">
                <h3>{{ t('dashboard.correction.candidates') }}</h3>
              </div>
              <el-empty v-if="correctionCandidates.length === 0" :description="t('dashboard.correction.noCandidates')" :image-size="56" />
              <div v-else class="candidate-list">
                <button
                  v-for="candidate in correctionCandidates"
                  :key="candidateKey(candidate)"
                  class="candidate-row"
                  :class="{ selected: isCorrectionCandidateSelected(candidate) }"
                  type="button"
                  @click="selectCorrectionCandidate(candidate)"
                >
                  <span class="candidate-title">{{ candidateTitle(candidate) }}</span>
                  <span class="candidate-meta">
                    {{ candidate.year || '-' }} · {{ t(`task.mediaType.${candidate.mediaType}`) }} · {{ formatConfidence(candidate.confidence) }}
                  </span>
                </button>
              </div>
            </section>
          </div>

          <section class="correction-section preview-section" v-loading="correctionPreviewing">
            <div class="section-title-row">
              <h3>{{ t('dashboard.correction.preview') }}</h3>
            </div>
            <div v-if="correctionPreview" class="preview-panel">
              <el-descriptions :column="1" border size="small" class="preview-path-descriptions">
                <el-descriptions-item :label="t('dashboard.correction.currentTargetPath')">
                  <span class="muted-path">{{ correctionPreview.currentTargetPath || '-' }}</span>
                </el-descriptions-item>
                <el-descriptions-item :label="t('dashboard.correction.correctedTargetPath')">
                  <span class="muted-path">{{ correctionPreview.correctedTargetPath || '-' }}</span>
                </el-descriptions-item>
              </el-descriptions>
              <div v-if="correctionPreview.blockers.length" class="message-stack">
                <el-alert
                  v-for="blocker in correctionPreview.blockers"
                  :key="blocker"
                  type="error"
                  :title="blocker"
                  show-icon
                  :closable="false"
                />
              </div>
              <div v-if="correctionPreview.warnings.length" class="message-stack">
                <el-alert
                  v-for="warning in correctionPreview.warnings"
                  :key="warning"
                  type="warning"
                  :title="warning"
                  show-icon
                  :closable="false"
                />
              </div>
              <el-table :data="correctionPreview.operations" size="small" border class="operation-table">
                <el-table-column :label="t('dashboard.correction.operationType')" width="136">
                  <template #default="{ row }">
                    {{ t(`dashboard.correction.operation.${row.type}`) }}
                  </template>
                </el-table-column>
                <el-table-column prop="sourcePath" :label="t('dashboard.correction.operationSource')" min-width="180" show-overflow-tooltip />
                <el-table-column prop="targetPath" :label="t('dashboard.correction.operationTarget')" min-width="180" show-overflow-tooltip />
              </el-table>
            </div>
            <el-empty v-else :description="t('dashboard.correction.previewEmpty')" :image-size="72" />
          </section>
        </div>
      </div>
      <template #footer>
        <el-button @click="correctionDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :icon="Check"
          :loading="correctionApplying"
          :disabled="!correctionPreview?.canApply"
          @click="applyCorrection"
        >
          {{ t('dashboard.correction.apply') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMediaStore } from '@/stores/mediaStore'
import { mediaApi } from '@/api/media'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, EditPen, Refresh, Search } from '@element-plus/icons-vue'
import type { MatchResult, MediaAssetType, MediaTask, MediaType, TaskCorrectionPreview, TaskCorrectionRequest, TaskStatus } from '@/types'

const { t, te } = useI18n()
const mediaStore = useMediaStore()
const { tasks, loading, queueTasks, doneTasks, skippedTasks, failedTasks } = storeToRefs(mediaStore)
const { fetchTasks, deleteTask } = mediaStore
const deletingId = ref<number | null>(null)
const selectedTasks = ref<MediaTask[]>([])
const batchDeleting = ref(false)
const statusOptions: TaskStatus[] = ['PENDING', 'PROCESSING', 'AWAITING_CONFIRMATION', 'DONE', 'FAILED', 'SKIPPED', 'CORRECTED']
const assetTypeOptions: MediaAssetType[] = ['VIDEO_FILE', 'ISO_IMAGE', 'BLURAY_DIRECTORY']
const mediaTypeOptions: MediaType[] = ['MOVIE', 'TV_SHOW']
const statusFilter = ref<TaskStatus | 'ALL'>('ALL')
const assetTypeFilter = ref<MediaAssetType | 'ALL'>('ALL')
const mediaTypeFilter = ref<MediaType | 'ALL'>('ALL')
const fileNameSearchActive = ref(false)
const fileNameKeyword = ref('')
const fileNameSearchInputRef = ref<{ focus: () => void } | null>(null)
const matchedTitleSearchActive = ref(false)
const matchedTitleKeyword = ref('')
const matchedTitleSearchInputRef = ref<{ focus: () => void } | null>(null)
const currentPage = ref(1)
const pageSize = ref(20)
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'
const correctionDialogVisible = ref(false)
const correctionTask = ref<MediaTask | null>(null)
const correctionForm = reactive({
  mediaType: 'MOVIE' as MediaType,
  parsedTitle: '',
  parsedYear: null as number | null,
  parsedSeason: null as number | null,
  parsedEpisode: null as number | null,
  parsedEpisodeEnd: null as number | null,
  regenerateNfo: false,
})
const correctionCandidates = ref<MatchResult[]>([])
const selectedCorrectionCandidate = ref<MatchResult | null>(null)
const correctionPreview = ref<TaskCorrectionPreview | null>(null)
const correctionRematching = ref(false)
const correctionPreviewing = ref(false)
const correctionApplying = ref(false)
let correctionPreviewRequestSeq = 0

const normalizedFileNameKeyword = computed(() => fileNameKeyword.value.trim().toLocaleLowerCase())
const normalizedMatchedTitleKeyword = computed(() => matchedTitleKeyword.value.trim().toLocaleLowerCase())

const filteredTasks = computed(() => {
  return tasks.value.filter((task) => {
    const taskAssetType = task.assetType || 'VIDEO_FILE'
    const matchesStatus = statusFilter.value === 'ALL' || task.status === statusFilter.value
    const matchesAssetType = assetTypeFilter.value === 'ALL' || taskAssetType === assetTypeFilter.value
    const matchesMediaType = mediaTypeFilter.value === 'ALL' || task.mediaType === mediaTypeFilter.value
    const matchesFileName =
      !normalizedFileNameKeyword.value ||
      sourceFileName(task.sourcePath).toLocaleLowerCase().includes(normalizedFileNameKeyword.value)
    const matchesMatchedTitle =
      !normalizedMatchedTitleKeyword.value ||
      (task.confirmedTitle || '').toLocaleLowerCase().includes(normalizedMatchedTitleKeyword.value)
    return matchesStatus && matchesAssetType && matchesMediaType && matchesFileName && matchesMatchedTitle
  })
})

const sortedTasks = computed(() => {
  return [...filteredTasks.value].sort((a, b) => taskTime(b) - taskTime(a))
})

const displayedTasks = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return sortedTasks.value.slice(start, start + pageSize.value)
})

onMounted(() => fetchTasks())

watch([statusFilter, assetTypeFilter, mediaTypeFilter, normalizedFileNameKeyword, normalizedMatchedTitleKeyword, pageSize], () => {
  selectedTasks.value = []
  currentPage.value = 1
})

watch(currentPage, () => {
  selectedTasks.value = []
})

watch(filteredTasks, () => {
  const maxPage = Math.max(1, Math.ceil(filteredTasks.value.length / pageSize.value))
  if (currentPage.value > maxPage) {
    currentPage.value = maxPage
  }
})

function taskTime(task: MediaTask) {
  const value = task.updatedAt || task.createdAt
  const timestamp = new Date(value).getTime()
  return Number.isNaN(timestamp) ? 0 : timestamp
}

async function activateFileNameSearch() {
  fileNameSearchActive.value = true
  await nextTick()
  fileNameSearchInputRef.value?.focus()
}

function handleFileNameSearchInput(value: string) {
  if (isBlankSearch(value)) {
    resetFileNameSearch()
  }
}

function resetFileNameSearch() {
  fileNameKeyword.value = ''
  fileNameSearchActive.value = false
}

function sourceFileName(sourcePath: string) {
  return sourcePath.split(/[\\/]/).pop() || sourcePath
}

async function activateMatchedTitleSearch() {
  matchedTitleSearchActive.value = true
  await nextTick()
  matchedTitleSearchInputRef.value?.focus()
}

function handleMatchedTitleSearchInput(value: string) {
  if (isBlankSearch(value)) {
    resetMatchedTitleSearch()
  }
}

function resetMatchedTitleSearch() {
  matchedTitleKeyword.value = ''
  matchedTitleSearchActive.value = false
}

function isBlankSearch(value: string) {
  return value.length > 0 && value.trim() === ''
}

function statusTagType(status: TaskStatus): TagType | undefined {
  const map: Record<TaskStatus, TagType | undefined> = {
    PENDING: 'info',
    PROCESSING: undefined,
    AWAITING_CONFIRMATION: 'warning',
    DONE: 'success',
    FAILED: 'danger',
    SKIPPED: 'info',
    CORRECTED: 'info',
  }
  return map[status] ?? 'info'
}

function assetTypeTagType(assetType: MediaAssetType | null | undefined): TagType | undefined {
  const map: Record<MediaAssetType, TagType | undefined> = {
    VIDEO_FILE: 'info',
    ISO_IMAGE: 'warning',
    BLURAY_DIRECTORY: 'primary',
  }
  return map[assetType || 'VIDEO_FILE'] ?? 'info'
}

function formatCreatedAt(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return { date: '-', time: '-' }
  }

  const pad = (num: number) => num.toString().padStart(2, '0')
  return {
    date: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
    time: `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`,
  }
}

function taskDetails(task: MediaTask) {
  if (task.status === 'FAILED') {
    return localizedTaskDetail(task.errorMessage)
  }
  if (task.status === 'SKIPPED') {
    return localizedTaskDetail(task.skipReason)
  }
  if (task.status === 'CORRECTED') {
    return task.correctedToTaskId
      ? t('dashboard.correction.correctedTo', { id: task.correctedToTaskId })
      : t('dashboard.correction.correctedHistory')
  }
  return '-'
}

function normalizedFailureCount(task: MediaTask) {
  return task.failureCount && task.failureCount > 0 ? task.failureCount : 1
}

function localizedTaskDetail(detail: string | null) {
  if (!detail) return '-'

  const key = detail.trim()
  if (te(`task.details.${key}`)) {
    return t(`task.details.${key}`)
  }

  return detail
}

async function handleDeleteTask(task: MediaTask) {
  await ElMessageBox.confirm(
    t('dashboard.deleteTaskConfirm'),
    t('common.delete'),
    {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    },
  )

  deletingId.value = task.id
  try {
    await deleteTask(task.id)
    const maxPage = Math.max(1, Math.ceil(filteredTasks.value.length / pageSize.value))
    currentPage.value = Math.min(currentPage.value, maxPage)
    ElMessage.success(t('dashboard.deleteTaskSuccess'))
  } finally {
    deletingId.value = null
  }
}

function openCorrectionDialog(task: MediaTask) {
  if (task.assetType === 'ISO_IMAGE') {
    ElMessage.warning(t('dashboard.correction.isoUnsupported'))
    return
  }
  correctionTask.value = task
  correctionForm.mediaType = task.mediaType || 'MOVIE'
  correctionForm.parsedTitle = task.parsedTitle || task.confirmedTitle || sourceFileName(task.sourcePath)
  correctionForm.parsedYear = task.parsedYear || task.confirmedYear
  correctionForm.parsedSeason = task.parsedSeason
  correctionForm.parsedEpisode = task.parsedEpisode
  correctionForm.parsedEpisodeEnd = task.parsedEpisodeEnd
  correctionForm.regenerateNfo = false
  invalidateCorrectionPreview()
  selectedCorrectionCandidate.value = task.tmdbId ? taskToMatchResult(task) : null
  correctionCandidates.value = selectedCorrectionCandidate.value ? [selectedCorrectionCandidate.value] : []
  correctionDialogVisible.value = true
  if (selectedCorrectionCandidate.value) {
    void nextTick().then(() => refreshCorrectionPreview())
  }
}

function taskToMatchResult(task: MediaTask): MatchResult {
  return {
    source: 'tmdb',
    sourceId: String(task.tmdbId || ''),
    title: task.confirmedTitle,
    originalTitle: task.confirmedOriginalTitle,
    year: task.confirmedYear,
    mediaType: task.mediaType || 'MOVIE',
    overview: null,
    posterUrl: null,
    genres: [task.confirmedGenre1, task.confirmedGenre2, task.confirmedGenre3, task.confirmedGenre4].filter((value): value is string => Boolean(value)),
    country: task.confirmedCountry,
    episodeTitle: task.confirmedEpisodeTitle,
    confidence: task.matchConfidence,
  }
}

function buildCorrectionRequest(): TaskCorrectionRequest | null {
  if (!correctionTask.value) return null
  return {
    mediaType: correctionForm.mediaType,
    parsedTitle: correctionForm.parsedTitle.trim(),
    parsedYear: correctionForm.parsedYear,
    parsedSeason: correctionForm.mediaType === 'TV_SHOW' ? correctionForm.parsedSeason : null,
    parsedEpisode: correctionForm.mediaType === 'TV_SHOW' ? correctionForm.parsedEpisode : null,
    parsedEpisodeEnd: correctionForm.mediaType === 'TV_SHOW' ? correctionForm.parsedEpisodeEnd : null,
    tmdbId: selectedCorrectionCandidate.value ? Number(selectedCorrectionCandidate.value.sourceId) : null,
    regenerateNfo: correctionForm.regenerateNfo,
  }
}

function validateCorrectionForm() {
  if (!correctionForm.parsedTitle.trim()) {
    ElMessage.warning(t('queue.validation.titleRequired'))
    return false
  }
  if (correctionForm.mediaType === 'TV_SHOW' && (correctionForm.parsedSeason == null || correctionForm.parsedEpisode == null)) {
    ElMessage.warning(t('queue.validation.seasonEpisodeRequired'))
    return false
  }
  return true
}

function invalidateCorrectionPreview() {
  correctionPreviewRequestSeq += 1
  correctionPreview.value = null
  correctionPreviewing.value = false
}

function handleRegenerateNfoChange() {
  invalidateCorrectionPreview()
  if (selectedCorrectionCandidate.value) {
    void refreshCorrectionPreview()
  }
}

async function rematchCorrection() {
  if (!correctionTask.value || !validateCorrectionForm()) return
  const request = buildCorrectionRequest()
  if (!request) return
  correctionRematching.value = true
  try {
    const res = await mediaApi.rematchTaskCorrection(correctionTask.value.id, request)
    correctionCandidates.value = res.data.data.candidates
    selectedCorrectionCandidate.value = correctionCandidates.value[0] || null
    invalidateCorrectionPreview()
    if (correctionCandidates.value.length === 0) {
      ElMessage.info(t('dashboard.correction.noCandidates'))
    } else {
      await refreshCorrectionPreview()
    }
  } finally {
    correctionRematching.value = false
  }
}

async function selectCorrectionCandidate(candidate: MatchResult) {
  selectedCorrectionCandidate.value = candidate
  invalidateCorrectionPreview()
  await refreshCorrectionPreview()
}

function isCorrectionCandidateSelected(candidate: MatchResult) {
  return selectedCorrectionCandidate.value
    ? candidateKey(selectedCorrectionCandidate.value) === candidateKey(candidate)
    : false
}

function candidateKey(candidate: MatchResult) {
  return `${candidate.mediaType}:${candidate.sourceId}`
}

function candidateTitle(candidate: MatchResult) {
  return candidate.title || candidate.originalTitle || candidate.sourceId
}

function formatConfidence(confidence: number | null) {
  return confidence != null ? `${Math.round(confidence * 100)}%` : '-'
}

async function refreshCorrectionPreview() {
  if (!correctionTask.value || !selectedCorrectionCandidate.value || !validateCorrectionForm()) return
  const request = buildCorrectionRequest()
  if (!request) return
  const requestSeq = ++correctionPreviewRequestSeq
  correctionPreviewing.value = true
  try {
    const res = await mediaApi.previewTaskCorrection(correctionTask.value.id, request)
    if (requestSeq === correctionPreviewRequestSeq) {
      correctionPreview.value = res.data.data
    }
  } finally {
    if (requestSeq === correctionPreviewRequestSeq) {
      correctionPreviewing.value = false
    }
  }
}

async function applyCorrection() {
  if (!correctionTask.value || !correctionPreview.value?.canApply || !selectedCorrectionCandidate.value) return
  const request = buildCorrectionRequest()
  if (!request) return

  await ElMessageBox.confirm(
    t('dashboard.correction.applyConfirmMessage'),
    t('dashboard.correction.applyConfirmTitle'),
    {
      confirmButtonText: t('dashboard.correction.apply'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    },
  )

  correctionApplying.value = true
  try {
    await mediaApi.applyTaskCorrection(correctionTask.value.id, request)
    ElMessage.success(t('dashboard.correction.applySuccess'))
    correctionDialogVisible.value = false
    await fetchTasks()
  } finally {
    correctionApplying.value = false
  }
}

function handleSelectionChange(selection: MediaTask[]) {
  selectedTasks.value = selection
}

async function handleBatchDelete() {
  const count = selectedTasks.value.length
  if (count === 0) return

  await ElMessageBox.confirm(
    t('dashboard.batchDeleteConfirm', { count }),
    t('dashboard.batchDelete'),
    {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    },
  )

  batchDeleting.value = true
  try {
    for (const task of selectedTasks.value) {
      await deleteTask(task.id)
    }
    selectedTasks.value = []
    const maxPage = Math.max(1, Math.ceil(filteredTasks.value.length / pageSize.value))
    currentPage.value = Math.min(currentPage.value, maxPage)
    ElMessage.success(t('dashboard.batchDeleteSuccess'))
  } finally {
    batchDeleting.value = false
  }
}
</script>

<style scoped>
.dashboard {
  padding: 24px;
}

h2 {
  margin: 0 0 24px;
  font-size: 22px;
  color: #303133;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.task-table-card {
  margin-top: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.dashboard-filter {
  width: 138px;
}

.searchable-column-header {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  width: 100%;
}

.table-header-search-input {
  width: 100%;
  max-width: 240px;
}

.table-header-search-button {
  min-height: 24px;
  min-width: 24px;
  padding: 0;
}

.failure-detail {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.detail-message {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.created-time {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.35;
}

.created-time span:last-child {
  color: #909399;
  font-size: 12px;
}

.correction-dialog :deep(.el-dialog__body) {
  padding-top: 10px;
  padding-bottom: 10px;
}

.correction-dialog :deep(.el-dialog__footer) {
  padding-top: 10px;
}

.correction-layout {
  display: grid;
  gap: 14px;
}

.correction-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  gap: 16px;
  align-items: start;
}

.correction-left-column {
  display: grid;
  gap: 12px;
  padding: 14px;
  border-radius: 6px;
  background: #fcfdff;
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.16), 0 8px 18px rgba(31, 45, 61, 0.05);
}

.correction-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.correction-section h3 {
  margin: 0;
  font-size: 14px;
  color: #303133;
}

.current-info-section {
  padding: 14px;
  border-radius: 6px;
  background: #f7fbff;
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.14), 0 8px 18px rgba(31, 45, 61, 0.06);
}

.current-info-section :deep(.el-descriptions__label) {
  width: 132px;
}

.current-info-section :deep(.el-descriptions__content) {
  max-width: 0;
}

.preview-path-descriptions :deep(.el-descriptions__label) {
  width: 154px;
  white-space: nowrap;
}

.preview-path-descriptions :deep(.el-descriptions__content) {
  max-width: 0;
}

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.correction-form :deep(.el-input-number) {
  width: 100%;
}

.correction-form :deep(.el-form-item) {
  margin-bottom: 10px;
}

.correction-form-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 32px;
}

.muted-path {
  color: #606266;
  word-break: break-all;
  line-height: 1.35;
}

.candidate-list {
  display: grid;
  gap: 6px;
}

.candidate-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  width: 100%;
  min-height: 34px;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  color: #303133;
  cursor: pointer;
  text-align: left;
}

.candidate-row:hover,
.candidate-row.selected {
  border-color: #409eff;
  background: #ecf5ff;
}

.candidate-title,
.candidate-meta {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.candidate-meta {
  color: #909399;
  font-size: 12px;
}

.preview-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message-stack {
  display: grid;
  gap: 6px;
}

.operation-table {
  width: 100%;
}

.preview-section {
  min-width: 0;
  padding: 14px;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.12), 0 8px 20px rgba(31, 45, 61, 0.07);
}

@media (max-width: 1200px) {
  .stat-cards {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .correction-main-grid {
    grid-template-columns: 1fr;
  }

  .correction-left-column {
    padding: 12px;
  }
}

@media (max-width: 720px) {
  .stat-cards {
    grid-template-columns: 1fr;
  }

  .card-header,
  .header-actions,
  .section-title-row {
    align-items: stretch;
    flex-direction: column;
  }

  .dashboard-filter {
    width: 100%;
  }

  .candidate-row {
    grid-template-columns: 1fr;
  }
}
</style>
