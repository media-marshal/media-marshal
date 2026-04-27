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
          <el-button size="small" @click="fetchTasks()">刷新</el-button>
        </div>
      </template>
      <el-table :data="tasks.slice(0, 20)" v-loading="loading" stripe>
        <el-table-column prop="sourcePath" label="文件" min-width="300" show-overflow-tooltip />
        <el-table-column prop="mediaType" label="类型" width="80">
          <template #default="{ row }">
            {{ row.mediaType ? t(`task.mediaType.${row.mediaType}`) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ t(`task.status.${row.status}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="confirmedTitle" label="匹配标题" width="200" show-overflow-tooltip />
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
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMediaStore } from '@/stores/mediaStore'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { MediaTask, TaskStatus } from '@/types'

const { t } = useI18n()
const mediaStore = useMediaStore()
const { tasks, loading, queueTasks, doneTasks, failedTasks } = storeToRefs(mediaStore)
const { fetchTasks, deleteTask } = mediaStore
const deletingId = ref<number | null>(null)

onMounted(() => fetchTasks())

function statusTagType(status: TaskStatus) {
  const map: Record<TaskStatus, string> = {
    PENDING: 'info',
    PROCESSING: '',
    AWAITING_CONFIRMATION: 'warning',
    DONE: 'success',
    FAILED: 'danger',
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
