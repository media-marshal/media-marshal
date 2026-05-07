<template>
  <main class="setup-view">
    <section class="setup-panel">
      <div class="setup-header">
        <div class="setup-title-row">
          <h1>{{ t('setup.title') }}</h1>
          <el-button-group>
            <el-button size="small" :type="locale === 'zh' ? 'primary' : ''" @click="switchLocale('zh')">
              {{ t('locale.zhShort') }}
            </el-button>
            <el-button size="small" :type="locale === 'en' ? 'primary' : ''" @click="switchLocale('en')">
              {{ t('locale.enShort') }}
            </el-button>
          </el-button-group>
        </div>
        <p>{{ t('setup.description') }}</p>
      </div>

      <el-form label-position="top" class="setup-form" @submit.prevent="handleSave">
        <el-form-item required>
          <template #label>
            <span class="field-label">
              {{ t('settings.tmdbApiKey') }}
              <el-popover placement="top-start" trigger="hover" :width="320">
                <template #reference>
                  <el-icon class="help-icon"><QuestionFilled /></el-icon>
                </template>
                <div class="api-key-help">
                  <p>{{ t('setup.tmdbApiKeyHelpPopover') }}</p>
                  <a
                    href="https://www.themoviedb.org/settings/api"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {{ t('setup.tmdbApiKeyLink') }}
                  </a>
                </div>
              </el-popover>
            </span>
          </template>
          <div class="field-stack">
            <el-input
              v-model="form.tmdbApiKey"
              type="password"
              show-password
              :placeholder="t('setup.tmdbApiKeyPlaceholder')"
              @keyup.enter="handleSave"
            />
            <div class="form-hint">{{ t('setup.tmdbApiKeyHelp') }}</div>
          </div>
        </el-form-item>

        <el-form-item :label="t('settings.tmdbLanguage')">
          <div class="field-stack">
            <el-select
              v-model="form.tmdbLanguage"
              filterable
              class="language-select"
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
          </div>
        </el-form-item>

        <el-button
          class="setup-submit"
          type="primary"
          size="large"
          :loading="saving"
          @click="handleSave"
        >
          {{ t('setup.saveAndContinue') }}
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useSettingsStore } from '@/stores/settingsStore'
import { setLocale } from '@/i18n'

const { t, locale } = useI18n()
const router = useRouter()
const settingsStore = useSettingsStore()
const saving = ref(false)

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
})

onMounted(() => {
  form.tmdbLanguage = settingsStore.getSetting('tmdb.language') || 'zh-CN'
})

function switchLocale(lang: 'zh' | 'en') {
  setLocale(lang)
}

async function handleSave() {
  const apiKey = form.tmdbApiKey.trim()
  if (!apiKey) {
    ElMessage.warning(t('setup.tmdbApiKeyRequired'))
    return
  }

  saving.value = true
  try {
    await Promise.all([
      settingsStore.updateSetting('tmdb.api-key', apiKey, undefined, true),
      settingsStore.updateSetting('tmdb.language', form.tmdbLanguage),
    ])
    ElMessage.success(t('setup.saveSuccess'))
    await router.replace('/settings/paths')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.setup-view {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background:
    linear-gradient(135deg, rgba(55, 79, 140, 0.16), transparent 38%),
    #f5f7fa;
}

.setup-panel {
  position: relative;
  width: min(620px, 100%);
  padding: 32px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 18px 48px rgba(31, 45, 61, 0.10);
}

.setup-header {
  margin-bottom: 24px;
}

.setup-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.setup-header h1 {
  margin: 0;
  color: #303133;
  font-size: 26px;
  line-height: 1.25;
}

.setup-header p {
  margin: 0;
  color: #606266;
  line-height: 1.6;
}

.setup-form {
  display: flex;
  flex-direction: column;
}

.field-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.help-icon {
  color: #909399;
  cursor: help;
  font-size: 15px;
}

.help-icon:hover {
  color: #409eff;
}

.api-key-help {
  display: flex;
  flex-direction: column;
  gap: 8px;
  line-height: 1.5;
}

.api-key-help p {
  margin: 0;
  color: #606266;
}

.language-select {
  width: 100%;
  max-width: 360px;
}

.field-stack {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  width: 100%;
}

.form-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.setup-submit {
  align-self: flex-start;
  margin-top: 4px;
}
</style>
