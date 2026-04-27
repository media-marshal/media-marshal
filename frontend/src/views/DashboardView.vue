<template>
  <div class="dashboard">
    <h2>{{ t('dashboard.title') }}</h2>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic :title="t('dashboard.totalTasks')" :value="tasks.length" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic :title="t('dashboard.doneTasks')" :value="doneTasks.length">
            <template #suffix><span style="color: #67c23a">✓</span></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic :title="t('dashboard.awaitingTasks')" :value="queueTasks.length">
            <template #suffix><span style="color: #e6a23c">!</span></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic :title="t('dashboard.failedTasks')" :value="failedTasks.length">
            <template #suffix><span style="color: #f56c6c">✗</span></template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近任务列表 -->
    <el-card shadow="never" class="task-table-card">
      <template #header>
        <div class="card-header">
          <span>最近任务</span>
          <div class="header-actions">
            <el-select v-model="statusFilter" size="small" class="status-filter">
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
            <el-button size="small" @click="fetchTasks()">刷新</el-button>
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
        <el-table-column prop="sourcePath" label="文件" min-width="300" show-overflow-tooltip />
        <el-table-column prop="mediaType" label="类型" width="80">
          <template #default="{ row }">
            {{ row.mediaType ? t(`task.mediaType.${row.mediaType}`) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
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
        <el-table-column prop="confirmedTitle" label="匹配标题" width="200" show-overflow-tooltip />
        <el-table-column :label="t('dashboard.details')" width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ taskDetails(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="matchConfidence" label="置信度" width="90">
          <template #default="{ row }">
            {{ row.matchConfidence != null ? (row.matchConfidence * 100).toFixed(0) + '%' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="130">
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
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMediaStore } from '@/stores/mediaStore'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { MediaTask, TaskStatus } from '@/types'

const { t, te } = useI18n()
const mediaStore = useMediaStore()
const { tasks, loading, queueTasks, doneTasks, failedTasks } = storeToRefs(mediaStore)
const { fetchTasks, deleteTask } = mediaStore
const deletingId = ref<number | null>(null)
const selectedTasks = ref<MediaTask[]>([])
const batchDeleting = ref(false)
const statusOptions: TaskStatus[] = ['PENDING', 'PROCESSING', 'AWAITING_CONFIRMATION', 'DONE', 'FAILED', 'SKIPPED']
const statusFilter = ref<TaskStatus | 'ALL'>('ALL')
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

const displayedTasks = computed(() => {
  const filtered = statusFilter.value === 'ALL'
    ? tasks.value
    : tasks.value.filter((task) => task.status === statusFilter.value)
  return filtered.slice(0, 20)
})

onMounted(() => fetchTasks())

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

.status-filter {
  width: 150px;
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
</style>
