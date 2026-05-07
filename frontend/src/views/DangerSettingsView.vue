<template>
  <div class="danger-settings-view">
    <h2>{{ t('settings.dangerTitle') }}</h2>

    <el-card shadow="never" class="settings-section danger-section">
      <template #header>{{ t('settings.dangerSection') }}</template>
      <div class="danger-action">
        <div>
          <div class="danger-title">{{ t('settings.resetSystem') }}</div>
          <div class="danger-description">{{ t('settings.resetSystemDescription') }}</div>
        </div>
        <el-button type="danger" :loading="resetting" @click="handleResetSystem">
          {{ t('settings.resetSystem') }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useSettingsStore } from '@/stores/settingsStore'
import { useMediaStore } from '@/stores/mediaStore'

const { t } = useI18n()
const router = useRouter()
const settingsStore = useSettingsStore()
const mediaStore = useMediaStore()
const resetting = ref(false)
const resetKeyword = 'RESET'

async function handleResetSystem() {
  let value = ''
  try {
    const result = await ElMessageBox.prompt(
      t('settings.resetSystemConfirmInput', { keyword: resetKeyword }),
      t('settings.resetSystemConfirmTitle'),
      {
        confirmButtonText: t('settings.resetSystem'),
        cancelButtonText: t('common.cancel'),
        inputErrorMessage: t('settings.resetSystemConfirmKeyword', { keyword: resetKeyword }),
        inputPattern: /^RESET$/,
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )
    value = result.value
  } catch {
    return
  }

  if (value !== resetKeyword) return

  resetting.value = true
  try {
    await settingsStore.resetSystem()
    mediaStore.clearTasks()
    ElMessage.success(t('settings.resetSystemSuccess'))
    await router.replace('/setup')
  } finally {
    resetting.value = false
  }
}
</script>

<style scoped>
.danger-settings-view {
  padding: 24px;
  max-width: 760px;
}

h2 {
  margin: 0 0 24px;
  color: #303133;
  font-size: 22px;
}

.settings-section {
  margin-bottom: 20px;
}

.danger-section {
  border-color: #fecdca;
  background: #fffafa;
}

.danger-section :deep(.el-card__header) {
  color: #c45656;
  font-weight: 600;
}

.danger-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.danger-title {
  margin-bottom: 6px;
  color: #303133;
  font-weight: 600;
}

.danger-description {
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}

@media (max-width: 640px) {
  .danger-action {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
