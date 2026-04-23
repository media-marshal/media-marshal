<template>
  <div class="settings-view">
    <h2>{{ t('settings.title') }}</h2>

    <el-form v-loading="loading" label-position="top" class="settings-form">
      <!-- TMDB -->
      <el-card shadow="never" class="settings-section">
        <template #header>TMDB 配置</template>
        <el-form-item :label="t('settings.tmdbApiKey')">
          <el-input
            v-model="form.tmdbApiKey"
            type="password"
            show-password
            placeholder="从 themoviedb.org 申请"
          />
        </el-form-item>
      </el-card>

      <!-- 文件监控 -->
      <el-card shadow="never" class="settings-section">
        <template #header>文件监控</template>
        <el-form-item :label="t('settings.watchDirs')">
          <el-input
            v-model="form.watchDirs"
            type="textarea"
            :rows="3"
            placeholder="/media/downloads,/nas/inbox"
          />
          <div class="form-hint">多个目录用英文逗号分隔</div>
        </el-form-item>
        <el-form-item :label="t('settings.confidenceThreshold')">
          <el-slider v-model="form.confidenceThreshold" :min="0" :max="100" :step="5" show-input />
          <div class="form-hint">低于此阈值的匹配将进入待确认队列</div>
        </el-form-item>
        <el-form-item :label="t('settings.operationStrategy')">
          <el-select v-model="form.operationStrategy">
            <el-option label="移动（推荐）" value="MOVE" />
            <el-option label="复制" value="COPY" />
            <el-option label="硬链接" value="HARD_LINK" />
            <el-option label="软链接" value="SYMBOLIC_LINK" />
          </el-select>
        </el-form-item>
      </el-card>

      <!-- 邮件通知 -->
      <el-card shadow="never" class="settings-section">
        <template #header>邮件通知</template>
        <el-form-item :label="t('settings.emailEnabled')">
          <el-switch v-model="form.emailEnabled" />
        </el-form-item>
        <el-form-item v-if="form.emailEnabled" :label="t('settings.emailRecipient')">
          <el-input v-model="form.emailRecipient" type="email" placeholder="you@example.com" />
        </el-form-item>
      </el-card>

      <el-button type="primary" @click="handleSave">{{ t('settings.save') }}</el-button>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSettingsStore } from '@/stores/settingsStore'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'

const { t } = useI18n()
const settingsStore = useSettingsStore()
const { loading } = storeToRefs(settingsStore)

const form = reactive({
  tmdbApiKey: '',
  watchDirs: '',
  confidenceThreshold: 80,
  operationStrategy: 'MOVE',
  emailEnabled: false,
  emailRecipient: '',
})

onMounted(async () => {
  await settingsStore.fetchSettings()
  form.tmdbApiKey = settingsStore.getSetting('tmdb.api-key')
  form.watchDirs = settingsStore.getSetting('watcher.watch-dirs')
  form.confidenceThreshold = Number(settingsStore.getSetting('watcher.confidence-threshold') || 80) * 100
  form.operationStrategy = settingsStore.getSetting('operation.strategy') || 'MOVE'
  form.emailEnabled = settingsStore.getSetting('notification.email.enabled') === 'true'
  form.emailRecipient = settingsStore.getSetting('notification.email.recipient')
})

async function handleSave() {
  await Promise.all([
    settingsStore.updateSetting('tmdb.api-key', form.tmdbApiKey, 'TMDB API Key', true),
    settingsStore.updateSetting('watcher.watch-dirs', form.watchDirs, '监控目录列表'),
    settingsStore.updateSetting('watcher.confidence-threshold', String(form.confidenceThreshold / 100), '匹配置信度阈值'),
    settingsStore.updateSetting('operation.strategy', form.operationStrategy, '文件操作策略'),
    settingsStore.updateSetting('notification.email.enabled', String(form.emailEnabled), '邮件通知开关'),
    settingsStore.updateSetting('notification.email.recipient', form.emailRecipient, '通知邮箱'),
  ])
  ElMessage.success(t('settings.saved'))
}
</script>

<style scoped>
.settings-view {
  padding: 24px;
  max-width: 800px;
}

h2 {
  margin: 0 0 24px;
  font-size: 22px;
}

.settings-section {
  margin-bottom: 20px;
}

.settings-form :deep(.el-card__header) {
  font-weight: 600;
  color: #303133;
}

.form-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
