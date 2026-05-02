<template>
  <div class="system-settings-view">
    <h2>{{ t('settings.title') }}</h2>

    <el-form v-loading="loading" label-position="top" class="settings-form">

      <!-- TMDB 配置 -->
      <el-card shadow="never" class="settings-section">
        <template #header>{{ t('settings.tmdbSection') }}</template>
        <el-form-item :label="t('settings.tmdbApiKey')">
          <el-input
            v-model="form.tmdbApiKey"
            type="password"
            show-password
            :placeholder="t('settings.tmdbApiKeyHelp')"
          />
        </el-form-item>
        <el-form-item :label="t('settings.tmdbLanguage')">
          <el-select
            v-model="form.tmdbLanguage"
            filterable
            style="width: 100%; max-width: 360px"
            :placeholder="t('settings.tmdbLanguagePlaceholder')"
          >
            <el-option
              v-for="option in TMDB_LANGUAGE_OPTIONS"
              :key="option.value"
              :label="t(option.labelKey)"
              :value="option.value"
            />
          </el-select>
          <div class="form-hint">{{ t('settings.tmdbLanguageHelp') }}</div>
        </el-form-item>
      </el-card>

      <!-- 识别配置 -->
      <el-card shadow="never" class="settings-section">
        <template #header>{{ t('settings.matchingSection') }}</template>
        <el-form-item :label="t('settings.confidenceThreshold')">
          <el-slider
            v-model="form.confidenceThreshold"
            :min="0"
            :max="100"
            :step="5"
            show-input
            style="max-width: 480px"
          />
          <div class="form-hint">{{ t('settings.confidenceThresholdHelp') }}</div>
        </el-form-item>
      </el-card>

      <!-- 邮件通知 -->
      <el-card shadow="never" class="settings-section">
        <template #header>{{ t('settings.emailSection') }}</template>
        <el-form-item :label="t('settings.emailEnabled')">
          <div class="email-toggle-row">
            <el-switch v-model="form.emailEnabled" disabled />
            <span class="email-disabled-hint">{{ t('settings.emailComingSoon') }}</span>
          </div>
        </el-form-item>
        <el-form-item v-if="form.emailEnabled" :label="t('settings.emailRecipient')">
          <el-input v-model="form.emailRecipient" type="email" placeholder="you@example.com" disabled />
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

const TMDB_LANGUAGE_OPTIONS = [
  { value: 'zh-CN', labelKey: 'settings.tmdbLanguageOptions.zhCN' },
  { value: 'zh-TW', labelKey: 'settings.tmdbLanguageOptions.zhTW' },
  { value: 'en-US', labelKey: 'settings.tmdbLanguageOptions.enUS' },
  { value: 'ja-JP', labelKey: 'settings.tmdbLanguageOptions.jaJP' },
  { value: 'ko-KR', labelKey: 'settings.tmdbLanguageOptions.koKR' },
  { value: 'fr-FR', labelKey: 'settings.tmdbLanguageOptions.frFR' },
  { value: 'de-DE', labelKey: 'settings.tmdbLanguageOptions.deDE' },
  { value: 'es-ES', labelKey: 'settings.tmdbLanguageOptions.esES' },
  { value: 'it-IT', labelKey: 'settings.tmdbLanguageOptions.itIT' },
  { value: 'pt-BR', labelKey: 'settings.tmdbLanguageOptions.ptBR' },
  { value: 'ru-RU', labelKey: 'settings.tmdbLanguageOptions.ruRU' },
  { value: 'th-TH', labelKey: 'settings.tmdbLanguageOptions.thTH' },
  { value: 'vi-VN', labelKey: 'settings.tmdbLanguageOptions.viVN' },
  { value: 'id-ID', labelKey: 'settings.tmdbLanguageOptions.idID' },
  { value: 'hi-IN', labelKey: 'settings.tmdbLanguageOptions.hiIN' },
  { value: 'ar-SA', labelKey: 'settings.tmdbLanguageOptions.arSA' },
] as const

const form = reactive({
  tmdbApiKey: '',
  tmdbLanguage: 'zh-CN',
  confidenceThreshold: 80,
  emailEnabled: false,
  emailRecipient: '',
})

onMounted(async () => {
  await settingsStore.fetchSettings()
  form.tmdbApiKey = settingsStore.getSetting('tmdb.api-key')
  form.tmdbLanguage = settingsStore.getSetting('tmdb.language') || 'zh-CN'
  form.confidenceThreshold = Math.round(
    Number(settingsStore.getSetting('watcher.confidence-threshold') || 0.8) * 100,
  )
  form.emailEnabled = settingsStore.getSetting('notification.email.enabled') === 'true'
  form.emailRecipient = settingsStore.getSetting('notification.email.recipient')
})

async function handleSave() {
  await Promise.all([
    settingsStore.updateSetting('tmdb.api-key', form.tmdbApiKey, 'TMDB API Key', true),
    settingsStore.updateSetting('tmdb.language', form.tmdbLanguage, 'TMDB 查询语言'),
    settingsStore.updateSetting(
      'watcher.confidence-threshold',
      String(form.confidenceThreshold / 100),
      '匹配置信度阈值',
    ),
  ])
  ElMessage.success(t('settings.saved'))
}
</script>

<style scoped>
.system-settings-view {
  padding: 24px;
  max-width: 760px;
}

h2 {
  margin: 0 0 24px;
  font-size: 22px;
  color: #303133;
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

.email-toggle-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.email-disabled-hint {
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}
</style>
