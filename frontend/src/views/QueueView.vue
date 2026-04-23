<template>
  <div class="queue-view">
    <h2>{{ t('queue.title') }}</h2>

    <el-empty v-if="!loading && queueTasks.length === 0" :description="t('queue.empty')" />

    <div v-for="task in queueTasks" :key="task.id" class="queue-item">
      <el-card shadow="never">
        <template #header>
          <div class="item-header">
            <span class="file-path" :title="task.sourcePath">{{ task.sourcePath }}</span>
            <el-tag type="warning" size="small">
              {{ t('queue.confidence') }}: {{ ((task.matchConfidence ?? 0) * 100).toFixed(0) }}%
            </el-tag>
          </div>
        </template>

        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item :label="t('queue.parsedTitle')">
            {{ task.parsedTitle }} {{ task.parsedYear ? `(${task.parsedYear})` : '' }}
          </el-descriptions-item>
          <el-descriptions-item label="媒体类型">
            {{ task.mediaType ? t(`task.mediaType.${task.mediaType}`) : '未知' }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- TODO: 搜索候选列表组件 -->
        <div class="actions">
          <el-button type="primary" size="small" @click="handleConfirm(task.id)">
            {{ t('queue.confirm') }}
          </el-button>
          <el-button size="small" @click="handleSkip(task.id)">
            {{ t('queue.skip') }}
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMediaStore } from '@/stores/mediaStore'
import { storeToRefs } from 'pinia'
import { ElMessageBox } from 'element-plus'

const { t } = useI18n()
const mediaStore = useMediaStore()
const { queueTasks, loading } = storeToRefs(mediaStore)

onMounted(() => mediaStore.fetchTasks())

async function handleConfirm(taskId: number) {
  // TODO: 弹出 TMDB 搜索候选对话框，让用户选择后确认
  ElMessageBox.alert('TMDB 搜索确认对话框 - 待实现', '选择匹配项')
}

async function handleSkip(taskId: number) {
  await ElMessageBox.confirm('确认跳过此任务？文件将不会被处理。', '跳过任务', {
    confirmButtonText: '跳过',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await mediaStore.skipTask(taskId)
}
</script>

<style scoped>
.queue-view {
  padding: 24px;
}

h2 {
  margin: 0 0 24px;
  font-size: 22px;
}

.queue-item {
  margin-bottom: 16px;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.file-path {
  font-family: monospace;
  font-size: 13px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}
</style>
