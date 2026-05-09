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
        <el-table-column prop="sourcePath" :label="t('dashboard.file')" min-width="300" show-overflow-tooltip />
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
        <el-table-column prop="confirmedTitle" :label="t('dashboard.matchedTitle')" width="200" show-overflow-tooltip />
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
        <el-table-column :label="t('common.actions')" width="90" fixed="right">
          <template #default="{ row }">
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMediaStore } from '@/stores/mediaStore'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { MediaAssetType, MediaTask, MediaType, TaskStatus } from '@/types'

const { t, te } = useI18n()
const mediaStore = useMediaStore()
const { tasks, loading, queueTasks, doneTasks, skippedTasks, failedTasks } = storeToRefs(mediaStore)
const { fetchTasks, deleteTask } = mediaStore
const deletingId = ref<number | null>(null)
const selectedTasks = ref<MediaTask[]>([])
const batchDeleting = ref(false)
const statusOptions: TaskStatus[] = ['PENDING', 'PROCESSING', 'AWAITING_CONFIRMATION', 'DONE', 'FAILED', 'SKIPPED']
const assetTypeOptions: MediaAssetType[] = ['VIDEO_FILE', 'ISO_IMAGE', 'BLURAY_DIRECTORY']
const mediaTypeOptions: MediaType[] = ['MOVIE', 'TV_SHOW']
const statusFilter = ref<TaskStatus | 'ALL'>('ALL')
const assetTypeFilter = ref<MediaAssetType | 'ALL'>('ALL')
const mediaTypeFilter = ref<MediaType | 'ALL'>('ALL')
const currentPage = ref(1)
const pageSize = ref(20)
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

const filteredTasks = computed(() => {
  return tasks.value.filter((task) => {
    const taskAssetType = task.assetType || 'VIDEO_FILE'
    const matchesStatus = statusFilter.value === 'ALL' || task.status === statusFilter.value
    const matchesAssetType = assetTypeFilter.value === 'ALL' || taskAssetType === assetTypeFilter.value
    const matchesMediaType = mediaTypeFilter.value === 'ALL' || task.mediaType === mediaTypeFilter.value
    return matchesStatus && matchesAssetType && matchesMediaType
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

watch([statusFilter, assetTypeFilter, mediaTypeFilter, pageSize], () => {
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

function statusTagType(status: TaskStatus): TagType | undefined {
  const map: Record<TaskStatus, TagType | undefined> = {
    PENDING: 'info',
    PROCESSING: undefined,
    AWAITING_CONFIRMATION: 'warning',
    DONE: 'success',
    FAILED: 'danger',
    SKIPPED: 'info',
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

@media (max-width: 1200px) {
  .stat-cards {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .stat-cards {
    grid-template-columns: 1fr;
  }
}
</style>
