<template>
  <div class="paths-view">
    <div class="page-header">
      <div>
        <h2>{{ t('watchRule.title') }}</h2>
        <p class="page-desc">{{ t('watchRule.description') }}</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">
        {{ t('watchRule.addRule') }}
      </el-button>
    </div>

    <!-- 空状态 -->
    <el-empty v-if="!loading && rules.length === 0" :description="t('watchRule.empty')" />

    <!-- 卡片网格 -->
    <div v-else class="rule-grid" v-loading="loading">
      <el-card
        v-for="rule in rules"
        :key="rule.id"
        class="rule-card"
        :class="{ 'is-disabled': !rule.enabled }"
        shadow="never"
      >
        <!-- 卡片头：规则名 + 启用开关 -->
        <template #header>
          <div class="card-header">
            <span class="rule-name">{{ rule.name }}</span>
            <el-switch
              :model-value="rule.enabled"
              :loading="togglingId === rule.id"
              @change="handleToggle(rule)"
            />
          </div>
        </template>

        <!-- 路径信息 -->
        <div class="path-row">
          <div class="path-block">
            <div class="path-label">{{ t('watchRule.sourceDir') }}</div>
            <div class="path-value" :title="rule.sourceDir">{{ rule.sourceDir }}</div>
          </div>
          <el-icon class="path-arrow"><ArrowRight /></el-icon>
          <div class="path-block">
            <div class="path-label">{{ t('watchRule.targetDir') }}</div>
            <div class="path-value" :title="rule.targetDir">{{ rule.targetDir }}</div>
          </div>
        </div>

        <!-- 标签行 -->
        <div class="tag-row">
          <el-tag size="small" :type="mediaTypeTagType(rule.mediaType)">
            {{ t(`watchRule.mediaTypeOptions.${rule.mediaType}`) }}
          </el-tag>
          <el-tag size="small" type="info">
            {{ t('watchRule.operationOptions.MOVE') }}
          </el-tag>
          <el-tag v-if="!rule.enabled" size="small" type="warning">
            {{ t('watchRule.disabled') }}
          </el-tag>
          <el-tag size="small" type="success">
            {{ t(`watchRule.discoveryModeOptions.${effectiveDiscoveryMode(rule)}`) }}
          </el-tag>
        </div>

        <!-- 路径模板摘要 -->
        <div class="template-summary-list">
          <div
            v-for="summary in templateSummaries(rule)"
            :key="summary.kind"
            class="template-row"
            :class="{ 'template-row--default': !summary.value }"
          >
            <el-icon><Document /></el-icon>
            <el-tag size="small" :type="summary.kind === 'movie' ? 'primary' : 'success'">
              {{ summary.label }}
            </el-tag>
            <span
              class="template-text"
              :title="summary.value || t('watchRule.defaultTemplate')"
            >
              {{ summary.value || t('watchRule.defaultTemplate') }}
            </span>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="card-actions">
          <el-button
            link
            type="success"
            :icon="Search"
            :loading="scanningId === rule.id"
            @click="handleScan(rule)"
          >
            {{ t('watchRule.fullScan') }}
          </el-button>
          <div class="card-actions-right">
            <el-button link type="primary" :icon="Edit" @click="openDialog(rule)">
              {{ t('common.edit') }}
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(rule)">
              {{ t('common.delete') }}
            </el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingRule ? t('watchRule.editRule') : t('watchRule.addRule')"
      width="960px"
      align-center
      :close-on-click-modal="false"
      @closed="variableDrawerVisible = false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        @submit.prevent
      >
        <div class="rule-form-grid">
          <section class="form-panel">
            <div class="form-panel-title">{{ t('watchRule.basicSettings') }}</div>

            <el-form-item :label="t('watchRule.name')" prop="name">
              <el-input v-model="form.name" :placeholder="t('watchRule.namePlaceholder')" />
            </el-form-item>

            <el-form-item :label="t('watchRule.sourceDir')" prop="sourceDir">
              <el-input v-model="form.sourceDir" :placeholder="t('watchRule.sourceDirPlaceholder')">
                <template #append>
                  <el-button :icon="FolderOpened" @click="openDirBrowser('source')" :title="t('dirBrowser.title')" />
                </template>
              </el-input>
            </el-form-item>

            <el-form-item :label="t('watchRule.targetDir')" prop="targetDir">
              <el-input v-model="form.targetDir" :placeholder="t('watchRule.targetDirPlaceholder')">
                <template #append>
                  <el-button :icon="FolderOpened" @click="openDirBrowser('target')" :title="t('dirBrowser.title')" />
                </template>
              </el-input>
            </el-form-item>

            <el-form-item :label="t('watchRule.mediaType')" prop="mediaType">
              <el-select v-model="form.mediaType" style="width: 100%">
                <el-option
                  v-for="opt in mediaTypeOptions"
                  :key="opt.value"
                  :label="t(`watchRule.mediaTypeOptions.${opt.value}`)"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item :label="t('watchRule.operation')" prop="operation">
              <el-select v-model="form.operation" style="width: 100%">
                <el-option
                  v-for="opt in operationOptions"
                  :key="opt.value"
                  :label="t(`watchRule.operationOptions.${opt.value}`)"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item
              v-if="showMovieTemplate"
            >
              <template #label>
                <span class="template-label">
                  <span>{{ t('watchRule.moviePathTemplate') }}</span>
                  <a href="#" @click.prevent="toggleVariableDrawer">
                    {{ t('watchRule.templateVariablesLink') }}
                  </a>
                </span>
              </template>

              <div v-if="!templateStates.movie.customMode" class="template-input-row">
                <el-select
                  v-model="templateStates.movie.selected"
                  style="flex: 1"
                  :placeholder="t('watchRule.pathTemplatePlaceholder')"
                  clearable
                >
                  <el-option
                    v-for="tpl in templateOptions('movie')"
                    :key="tpl.value"
                    :label="t(tpl.labelKey)"
                    :value="tpl.value"
                  >
                    <div class="template-option">
                      <span class="template-option-label">{{ t(tpl.labelKey) }}</span>
                      <el-tooltip :content="tpl.hint" placement="top">
                        <el-tag size="small" class="template-example-tag">{{ t('watchRule.templateExample') }}</el-tag>
                      </el-tooltip>
                    </div>
                  </el-option>
                </el-select>
                <el-tooltip :content="t('watchRule.customTemplate')" placement="top">
                  <el-button :icon="EditPen" @click="switchToCustom('movie')" />
                </el-tooltip>
              </div>

              <div v-else class="template-input-row">
                <el-input
                  v-model="templateStates.movie.custom"
                  style="flex: 1"
                  :placeholder="t('watchRule.customTemplatePlaceholder')"
                />
                <el-tooltip :content="t('watchRule.backToPreset')" placement="top">
                  <el-button :icon="ArrowLeft" @click="switchToPreset('movie')" />
                </el-tooltip>
              </div>
            </el-form-item>

            <el-form-item
              v-if="showTvTemplate"
              :label="t('watchRule.tvPathTemplate')"
            >
              <div v-if="!templateStates.tv.customMode" class="template-input-row">
                <el-select
                  v-model="templateStates.tv.selected"
                  style="flex: 1"
                  :placeholder="t('watchRule.pathTemplatePlaceholder')"
                  clearable
                >
                  <el-option
                    v-for="tpl in templateOptions('tv')"
                    :key="tpl.value"
                    :label="t(tpl.labelKey)"
                    :value="tpl.value"
                  >
                    <div class="template-option">
                      <span class="template-option-label">{{ t(tpl.labelKey) }}</span>
                      <el-tooltip :content="tpl.hint" placement="top">
                        <el-tag size="small" class="template-example-tag">{{ t('watchRule.templateExample') }}</el-tag>
                      </el-tooltip>
                    </div>
                  </el-option>
                </el-select>
                <el-tooltip :content="t('watchRule.customTemplate')" placement="top">
                  <el-button :icon="EditPen" @click="switchToCustom('tv')" />
                </el-tooltip>
              </div>

              <div v-else class="template-input-row">
                <el-input
                  v-model="templateStates.tv.custom"
                  style="flex: 1"
                  :placeholder="t('watchRule.customTemplatePlaceholder')"
                />
                <el-tooltip :content="t('watchRule.backToPreset')" placement="top">
                  <el-button :icon="ArrowLeft" @click="switchToPreset('tv')" />
                </el-tooltip>
              </div>
            </el-form-item>
          </section>

          <section class="form-panel">
            <div class="form-panel-title">{{ t('watchRule.advancedSettings') }}</div>

            <el-form-item :label="t('watchRule.discoverySettings')">
              <div class="discovery-options">
                <el-select v-model="form.discoveryMode" style="width: 100%">
                  <el-option
                    v-for="opt in discoveryModeOptions"
                    :key="opt.value"
                    :label="t(`watchRule.discoveryModeOptions.${opt.value}`)"
                    :value="opt.value"
                  />
                </el-select>
                <div class="discovery-help">
                  {{ t(`watchRule.discoveryModeHelp.${form.discoveryMode}`) }}
                </div>
                <div v-if="showScanInterval" class="discovery-interval">
                  <span>{{ t('watchRule.scanIntervalMinutes') }}</span>
                  <el-input-number
                    v-model="form.scanIntervalMinutes"
                    :min="5"
                    :step="5"
                    controls-position="right"
                    style="width: 140px"
                  />
                </div>
              </div>
            </el-form-item>

            <el-form-item :label="t('watchRule.fileHandling')">
              <div class="file-handling-options">
                <div class="switch-row">
                  <div>
                    <div class="switch-title">{{ t('watchRule.moveAssociatedFiles') }}</div>
                    <div class="switch-desc">{{ t('watchRule.moveAssociatedFilesHelp') }}</div>
                  </div>
                  <el-switch v-model="form.moveAssociatedFiles" />
                </div>
                <div class="switch-row">
                  <div>
                    <div class="switch-title">{{ t('watchRule.generateNfo') }}</div>
                    <div class="switch-desc">{{ t('watchRule.generateNfoHelp') }}</div>
                  </div>
                  <el-switch v-model="form.generateNfo" />
                </div>
                <div class="switch-row">
                  <div>
                    <div class="switch-title">{{ t('watchRule.cleanupEmptyDirs') }}</div>
                    <div class="switch-desc">{{ t('watchRule.cleanupEmptyDirsHelp') }}</div>
                  </div>
                  <el-switch v-model="form.cleanupEmptyDirs" />
                </div>
                <div class="ignored-patterns">
                  <div class="ignored-header">
                    <div>
                      <div class="switch-title">{{ t('watchRule.ignoredFilePatterns') }}</div>
                      <div class="switch-desc">{{ t('watchRule.ignoredFilePatternsHelp') }}</div>
                    </div>
                    <div class="ignored-actions">
                      <el-tag v-if="form.ignoredFilePatterns === null" size="small" type="success">
                        {{ t('watchRule.usingDefaultIgnoredPatterns') }}
                      </el-tag>
                      <el-button size="small" link type="primary" @click="ignoredPatternsEditing = !ignoredPatternsEditing">
                        {{ ignoredPatternsEditing ? t('common.done') : t('common.edit') }}
                      </el-button>
                    </div>
                  </div>
                  <div class="ignored-tags">
                    <el-tag
                      v-for="pattern in effectiveIgnoredPatterns"
                      :key="pattern"
                      size="small"
                      type="info"
                      :closable="ignoredPatternsEditing"
                      @close="removeIgnoredPattern(pattern)"
                    >
                      {{ pattern }}
                    </el-tag>
                    <el-text v-if="effectiveIgnoredPatterns.length === 0" size="small" type="info">
                      {{ t('watchRule.noIgnoredPatterns') }}
                    </el-text>
                  </div>
                  <div v-if="ignoredPatternsEditing" class="ignored-editor">
                    <div class="ignored-input-row">
                      <el-input
                        v-model="ignoredPatternInput"
                        size="small"
                        :placeholder="t('watchRule.ignoredPatternPlaceholder')"
                        @keyup.enter="addIgnoredPattern"
                      />
                      <el-button size="small" type="primary" @click="addIgnoredPattern">
                        {{ t('common.add') }}
                      </el-button>
                    </div>
                    <el-button size="small" link type="warning" @click="restoreDefaultIgnoredPatterns">
                      {{ t('watchRule.restoreDefaultIgnoredPatterns') }}
                    </el-button>
                  </div>
                </div>
              </div>
            </el-form-item>
          </section>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <transition name="variable-panel-slide">
      <aside v-if="variableDrawerVisible" class="template-variable-panel">
        <div class="variable-panel-header">
          <div class="variable-panel-title">{{ t('templateVariables.title') }}</div>
          <button class="variable-panel-close" type="button" @click="variableDrawerVisible = false">×</button>
        </div>

        <div class="variable-help">
          <p class="variable-help-desc">{{ t('templateVariables.description') }}</p>

          <div v-loading="templateVariablesLoading" class="variable-category-list">
            <el-empty
              v-if="!templateVariablesLoading && templateVariableGroups.length === 0"
              :description="t('templateVariables.empty')"
            />

            <div
              v-for="group in templateVariableGroups"
              :key="group.category"
              class="variable-category"
            >
              <div class="variable-category-title">{{ group.categoryName }}</div>
              <div
                v-for="variable in group.variables"
                :key="variable.placeholder"
                class="variable-card"
              >
                <div class="variable-card-header">
                  <code class="variable-name">{{ variable.placeholder }}</code>
                  <el-tag size="small" :type="templateVariableStatusType(variable.status)">
                    {{ t(`templateVariables.status.${variable.status}`) }}
                  </el-tag>
                </div>
                <p class="variable-desc">{{ variable.description }}</p>
                <div class="variable-meta">
                  <span>{{ t('templateVariables.type') }}：{{ variable.type }}</span>
                  <span>{{ t('templateVariables.source') }}：{{ variable.source }}</span>
                </div>
                <div class="variable-media-types">
                  <el-tag
                    v-for="mediaType in variable.mediaTypes"
                    :key="mediaType"
                    size="small"
                    effect="plain"
                  >
                    {{ t(`task.mediaType.${mediaType}`) }}
                  </el-tag>
                </div>
                <div class="variable-example">
                  <span>{{ t('templateVariables.example') }}</span>
                  <code>{{ variable.example }}</code>
                </div>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </transition>

    <!-- 目录浏览弹窗 -->
    <DirBrowserDialog
      v-model="dirBrowserVisible"
      :title="dirBrowserTarget === 'source' ? t('dirBrowser.sourceTitle') : t('dirBrowser.targetTitle')"
      :initial-path="dirBrowserTarget === 'source' ? form.sourceDir || '/' : form.targetDir || '/'"
      @select="onDirSelected"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, FolderOpened, EditPen, ArrowRight, ArrowLeft, Document, Search } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { watchRuleApi, type DiscoveryMode, type WatchRule, type WatchRuleRequest } from '@/api/watchRule'
import { templateVariablesApi } from '@/api/templateVariables'
import type { TemplateVariableGroup, TemplateVariableStatus } from '@/types'
import DirBrowserDialog from '@/components/DirBrowserDialog.vue'

const { t } = useI18n()

// ─── 规则列表 ────────────────────────────────────────────────────
const rules = ref<WatchRule[]>([])
const loading = ref(false)
const togglingId = ref<number | null>(null)
const scanningId = ref<number | null>(null)

// ─── 对话框 ──────────────────────────────────────────────────────
const dialogVisible = ref(false)
const editingRule = ref<WatchRule | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const ignoredPatternsEditing = ref(false)
const ignoredPatternInput = ref('')
const variableDrawerVisible = ref(false)
const templateVariablesLoading = ref(false)
const templateVariableGroups = ref<TemplateVariableGroup[]>([])

// ─── 目录浏览器 ──────────────────────────────────────────────────
const dirBrowserVisible = ref(false)
const dirBrowserTarget = ref<'source' | 'target'>('source')

// ─── 预设模板 ────────────────────────────────────────────────────
// TODO ADR-007：后续从后端接口 GET /api/template-variables 获取，当前硬编码
type TemplateKind = 'movie' | 'tv'

const PRESET_TEMPLATES = [
  {
    kind: 'movie',
    labelKey: 'watchRule.presetTemplates.movieDefault',
    value: '{title} ({year})/{title} ({year}) - {resolution}{ext}',
    hint: 'The Dark Knight (2008)/The Dark Knight (2008) - 1080p.mkv',
  },
  {
    kind: 'movie',
    labelKey: 'watchRule.presetTemplates.movieByYear',
    value: '{year}/{title} ({year})/{title} ({year}) - {resolution}{ext}',
    hint: '2008/The Dark Knight (2008)/The Dark Knight (2008) - 1080p.mkv',
  },
  {
    kind: 'movie',
    labelKey: 'watchRule.presetTemplates.movieByTypeYear',
    value: '{media_type}/{year}/{title} ({year})/{title} ({year}) - {resolution}{ext}',
    hint: 'MOVIE/2008/The Dark Knight (2008)/The Dark Knight (2008) - 1080p.mkv',
  },
  {
    kind: 'movie',
    labelKey: 'watchRule.presetTemplates.movieByTypeInitial',
    value: '{media_type}/{title_initial}/{title} ({year})/{title} ({year}) - {resolution}{ext}',
    hint: 'MOVIE/T/The Dark Knight (2008)/The Dark Knight (2008) - 1080p.mkv',
  },
  {
    kind: 'tv',
    labelKey: 'watchRule.presetTemplates.tvDefault',
    value: '{title} ({year})/S{season:02d}/{title}.S{season:02d}E{episode:02d}.{year}{ext}',
    hint: 'Breaking Bad (2008)/S03/Breaking Bad.S03E07.2008.mkv',
  },
  {
    kind: 'tv',
    labelKey: 'watchRule.presetTemplates.tvByYear',
    value: '{year}/{title} ({year})/S{season:02d}/{title}.S{season:02d}E{episode:02d}.{year}{ext}',
    hint: '2008/Breaking Bad (2008)/S03/Breaking Bad.S03E07.2008.mkv',
  },
  {
    kind: 'tv',
    labelKey: 'watchRule.presetTemplates.tvByTypeYear',
    value: '{media_type}/{year}/{title} ({year})/S{season:02d}/{title}.S{season:02d}E{episode:02d}.{year}{ext}',
    hint: 'TV_SHOW/2008/Breaking Bad (2008)/S03/Breaking Bad.S03E07.2008.mkv',
  },
  {
    kind: 'tv',
    labelKey: 'watchRule.presetTemplates.tvByTypeInitial',
    value: '{media_type}/{title_initial}/{title} ({year})/S{season:02d}/{title}.S{season:02d}E{episode:02d}.{year}{ext}',
    hint: 'TV_SHOW/B/Breaking Bad (2008)/S03/Breaking Bad.S03E07.2008.mkv',
  },
] satisfies Array<{ kind: TemplateKind, labelKey: string, value: string, hint: string }>

// ─── 模板选择状态 ─────────────────────────────────────────────────
const templateStates = reactive<Record<TemplateKind, {
  customMode: boolean
  selected: string
  custom: string
}>>({
  movie: { customMode: false, selected: '', custom: '' },
  tv: { customMode: false, selected: '', custom: '' },
})

const showMovieTemplate = computed(() => form.mediaType === 'AUTO' || form.mediaType === 'MOVIE')
const showTvTemplate = computed(() => form.mediaType === 'AUTO' || form.mediaType === 'TV_SHOW')
const showScanInterval = computed(() => form.discoveryMode === 'PERIODIC_SCAN' || form.discoveryMode === 'HYBRID')

function templateOptions(kind: TemplateKind) {
  return PRESET_TEMPLATES.filter(tpl => tpl.kind === kind)
}

function effectiveTemplate(kind: TemplateKind) {
  const state = templateStates[kind]
  if (state.customMode) return state.custom.trim() || undefined
  return state.selected || undefined
}

function switchToCustom(kind: TemplateKind) {
  // 将当前预设值填入自定义输入框，方便用户在预设基础上修改
  templateStates[kind].custom = templateStates[kind].selected
  templateStates[kind].customMode = true
}

function switchToPreset(kind: TemplateKind) {
  templateStates[kind].customMode = false
  templateStates[kind].selected = ''
}

function resetTemplateState(kind: TemplateKind, value?: string | null) {
  const state = templateStates[kind]
  state.customMode = false
  state.selected = ''
  state.custom = ''

  if (!value) return

  const preset = templateOptions(kind).find(tpl => tpl.value === value)
  if (preset) {
    state.selected = preset.value
  } else {
    state.customMode = true
    state.custom = value
  }
}

function templateSummaries(rule: WatchRule) {
  const summaries: Array<{ kind: TemplateKind, label: string, value?: string | null }> = []
  if (rule.mediaType === 'AUTO' || rule.mediaType === 'MOVIE') {
    summaries.push({
      kind: 'movie',
      label: t('watchRule.templateType.movie'),
      value: rule.moviePathTemplate,
    })
  }
  if (rule.mediaType === 'AUTO' || rule.mediaType === 'TV_SHOW') {
    summaries.push({
      kind: 'tv',
      label: t('watchRule.templateType.tv'),
      value: rule.tvPathTemplate,
    })
  }
  return summaries
}

// ─── 表单 ────────────────────────────────────────────────────────
const defaultForm = (): WatchRuleRequest => ({
  name: '',
  sourceDir: '',
  targetDir: '',
  mediaType: 'AUTO',
  moviePathTemplate: undefined,
  tvPathTemplate: undefined,
  operation: 'MOVE',
  enabled: false,   // 新规则默认不启用（在卡片上手动开启）
  moveAssociatedFiles: true,
  cleanupEmptyDirs: false,
  generateNfo: false,
  ignoredFilePatterns: null,
  discoveryMode: 'HYBRID',
  scanIntervalMinutes: 10,
  webhookEnabled: false,
})

const form = reactive<WatchRuleRequest>(defaultForm())

const formRules: FormRules = {
  name: [{ required: true, message: t('watchRule.validation.nameRequired'), trigger: 'blur' }],
  sourceDir: [{ required: true, message: t('watchRule.validation.sourceDirRequired'), trigger: 'blur' }],
  targetDir: [{ required: true, message: t('watchRule.validation.targetDirRequired'), trigger: 'blur' }],
  mediaType: [{ required: true }],
  operation: [{ required: true }],
}

const mediaTypeOptions = [{ value: 'AUTO' }, { value: 'MOVIE' }, { value: 'TV_SHOW' }]
const operationOptions = [{ value: 'MOVE' }]
const discoveryModeOptions: Array<{ value: DiscoveryMode }> = [
  { value: 'WATCH_EVENT' },
  { value: 'PERIODIC_SCAN' },
  { value: 'HYBRID' },
]
const defaultIgnoredPatterns = ['.DS_Store', 'Thumbs.db', 'desktop.ini', '*.part', '*.tmp', '*.crdownload', '*.lock', '~$*', '.*', '__MACOSX/', '@eaDir/']

const effectiveIgnoredPatterns = computed(() => form.ignoredFilePatterns ?? defaultIgnoredPatterns)

// ─── 生命周期 ────────────────────────────────────────────────────
onMounted(fetchRules)

// ─── 方法 ────────────────────────────────────────────────────────
async function fetchRules() {
  loading.value = true
  try {
    const res = await watchRuleApi.listRules()
    rules.value = res.data.data
  } finally {
    loading.value = false
  }
}

async function fetchTemplateVariables() {
  if (templateVariableGroups.value.length > 0) return

  templateVariablesLoading.value = true
  try {
    const res = await templateVariablesApi.listVariables()
    templateVariableGroups.value = res.data.data
  } finally {
    templateVariablesLoading.value = false
  }
}

async function toggleVariableDrawer() {
  variableDrawerVisible.value = !variableDrawerVisible.value
  if (variableDrawerVisible.value) {
    await fetchTemplateVariables()
  }
}

function openDialog(rule?: WatchRule) {
  editingRule.value = rule ?? null
  Object.assign(form, defaultForm(), rule ? { ...rule } : {})
  form.operation = 'MOVE'
  form.discoveryMode = form.discoveryMode || 'HYBRID'
  form.scanIntervalMinutes = Math.max(form.scanIntervalMinutes || 10, 5)
  form.webhookEnabled = false

  // 初始化模板状态
  resetTemplateState('movie', rule?.moviePathTemplate)
  resetTemplateState('tv', rule?.tvPathTemplate)
  ignoredPatternsEditing.value = false
  ignoredPatternInput.value = ''

  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    form.scanIntervalMinutes = Math.max(form.scanIntervalMinutes || 10, 5)
    const payload: WatchRuleRequest = {
      ...form,
      moviePathTemplate: showMovieTemplate.value ? effectiveTemplate('movie') : undefined,
      tvPathTemplate: showTvTemplate.value ? effectiveTemplate('tv') : undefined,
    }

    if (editingRule.value?.id) {
      await watchRuleApi.updateRule(editingRule.value.id, payload)
    } else {
      await watchRuleApi.createRule(payload)
    }

    ElMessage.success(t('watchRule.saveSuccess'))
    dialogVisible.value = false
    await fetchRules()
  } finally {
    saving.value = false
  }
}

async function handleDelete(rule: WatchRule) {
  await ElMessageBox.confirm(
    t('watchRule.deleteConfirm', { name: rule.name }),
    t('common.delete'),
    { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' },
  )
  await watchRuleApi.deleteRule(rule.id!)
  ElMessage.success(t('watchRule.deleteSuccess'))
  await fetchRules()
}

async function handleToggle(rule: WatchRule) {
  togglingId.value = rule.id!
  try {
    await watchRuleApi.toggleRule(rule.id!)
    rule.enabled = !rule.enabled
    ElMessage.success(t('watchRule.toggleSuccess'))
  } finally {
    togglingId.value = null
  }
}

async function handleScan(rule: WatchRule) {
  await ElMessageBox.confirm(
    t('watchRule.fullScanConfirm', { name: rule.name }),
    t('watchRule.fullScan'),
    { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' },
  )

  scanningId.value = rule.id!
  try {
    await watchRuleApi.scanRule(rule.id!)
    ElMessage.success(t('watchRule.fullScanStarted'))
  } finally {
    scanningId.value = null
  }
}

function openDirBrowser(target: 'source' | 'target') {
  dirBrowserTarget.value = target
  dirBrowserVisible.value = true
}

function onDirSelected(path: string) {
  if (dirBrowserTarget.value === 'source') {
    form.sourceDir = path
  } else {
    form.targetDir = path
  }
}

function addIgnoredPattern() {
  const pattern = ignoredPatternInput.value.trim()
  if (!pattern) return

  const current = form.ignoredFilePatterns === null
    ? [...defaultIgnoredPatterns]
    : [...form.ignoredFilePatterns]
  if (!current.includes(pattern)) {
    current.push(pattern)
  }
  form.ignoredFilePatterns = current
  ignoredPatternInput.value = ''
}

function removeIgnoredPattern(pattern: string) {
  if (form.ignoredFilePatterns === null) {
    ElMessage.info(t('watchRule.removeDefaultIgnoredPatternTip'))
    form.ignoredFilePatterns = defaultIgnoredPatterns.filter(item => item !== pattern)
    return
  }
  form.ignoredFilePatterns = form.ignoredFilePatterns.filter(item => item !== pattern)
}

function restoreDefaultIgnoredPatterns() {
  form.ignoredFilePatterns = null
  ignoredPatternInput.value = ''
}

function mediaTypeTagType(type: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' | undefined {
  return type === 'AUTO' ? 'info' : type === 'MOVIE' ? 'primary' : 'success'
}

function effectiveDiscoveryMode(rule: WatchRule): DiscoveryMode {
  return rule.discoveryMode || 'HYBRID'
}

function templateVariableStatusType(status: TemplateVariableStatus): 'success' | 'info' {
  return status === 'AVAILABLE' ? 'success' : 'info'
}
</script>

<style scoped>
.paths-view {
  padding: 28px 32px;
}

/* ─── 页头 ─────────────────────────── */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
}

h2 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
  color: #1d2129;
}

.page-desc {
  margin: 0;
  font-size: 13px;
  color: #86909c;
}

/* ─── 卡片网格 ─────────────────────── */
.rule-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(460px, 1fr));
  gap: 24px;
}

.rule-card {
  border: 1px solid #e5e8ef !important;
  border-radius: 12px !important;
  transition: box-shadow 0.25s, opacity 0.2s, transform 0.2s;
  overflow: hidden;
}

.rule-card:hover {
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.09) !important;
  transform: translateY(-2px);
}

.rule-card.is-disabled {
  opacity: 0.55;
}

/* 卡片头部区 */
:deep(.el-card__header) {
  padding: 16px 22px;
  background: #fafbfc;
  border-bottom: 1px solid #f0f2f5;
}

:deep(.el-card__body) {
  padding: 20px 22px 14px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rule-name {
  font-weight: 600;
  font-size: 16px;
  color: #1d2129;
  letter-spacing: 0.2px;
}

/* ─── 路径行 ───────────────────────── */
.path-row {
  display: flex;
  align-items: stretch;
  gap: 0;
  margin-bottom: 16px;
  background: #f7f8fa;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  overflow: hidden;
}

.path-block {
  flex: 1;
  min-width: 0;
  padding: 10px 14px;
}

.path-block:first-child {
  border-right: 1px solid #e8eaed;
}

.path-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  color: #86909c;
  margin-bottom: 5px;
}

.path-value {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  color: #1d2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.5;
}

.path-arrow {
  color: #c9cdd4;
  flex-shrink: 0;
  align-self: center;
  padding: 0 8px;
  font-size: 16px;
}

/* ─── 标签行 ───────────────────────── */
.tag-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

/* ─── 模板行 ───────────────────────── */
.template-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #4e5969;
  margin-bottom: 16px;
  overflow: hidden;
  background: #f2f3f5;
  border-radius: 6px;
  padding: 8px 12px;
}

.template-summary-list {
  margin-bottom: 16px;
}

.template-summary-list .template-row {
  margin-bottom: 8px;
}

.template-summary-list .template-row:last-child {
  margin-bottom: 0;
}

.template-row--default {
  color: #c9cdd4;
  background: transparent;
  border: 1px dashed #e5e8ef;
}

.template-text {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ─── 卡片操作 ─────────────────────── */
.card-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 4px;
  border-top: 1px solid #f0f2f5;
  padding-top: 12px;
  margin-top: 4px;
}

.card-actions-right {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}

/* ─── 表单内的模板选择 ──────────────── */
.rule-form-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  gap: 20px;
  align-items: start;
}

.form-panel {
  min-width: 0;
  padding: 16px 18px 4px;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  background: #fafbfc;
}

.form-panel-title {
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid #edf0f5;
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}

.template-input-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.template-label {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.template-label a {
  color: #409eff;
  font-size: 12px;
  font-weight: 400;
  text-decoration: none;
}

.template-label a:hover {
  text-decoration: underline;
}

.template-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.template-option-label {
  min-width: 0;
  overflow: hidden;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-example-tag {
  flex-shrink: 0;
  border-color: #d1edc4;
  border-radius: 999px;
  background: linear-gradient(135deg, #f0f9eb 0%, #f7fcf4 100%);
  color: #529b2e;
  font-weight: 500;
  cursor: help;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.template-example-tag:hover {
  border-color: #b3e19d;
  box-shadow: 0 2px 8px rgba(103, 194, 58, 0.18);
  transform: translateY(-1px);
}

.discovery-options {
  width: 100%;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
  background: #fafbfc;
}

.discovery-help {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}

.discovery-interval {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}

.file-handling-options {
  width: 100%;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
  background: #fafbfc;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f2f5;
}

.switch-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.switch-desc {
  margin-top: 3px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.ignored-patterns {
  padding-top: 10px;
}

.ignored-header,
.ignored-actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.ignored-actions {
  flex-shrink: 0;
  align-items: center;
}

.ignored-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.ignored-editor {
  margin-top: 10px;
}

.template-variable-panel {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 3000;
  width: 380px;
  max-width: min(420px, 92vw);
  padding: 22px;
  overflow-y: auto;
  border-left: 1px solid #e4e7ed;
  background: #fff;
  box-shadow: -12px 0 36px rgba(31, 45, 61, 0.16);
}

.variable-panel-slide-enter-active,
.variable-panel-slide-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.variable-panel-slide-enter-from,
.variable-panel-slide-leave-to {
  opacity: 0;
  transform: translateX(100%);
}

.variable-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: -4px -4px 18px;
  padding: 4px 4px 16px;
  border-bottom: 1px solid #edf0f5;
}

.variable-panel-title {
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.variable-panel-close {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: #f5f7fa;
  color: #909399;
  cursor: pointer;
  font-size: 20px;
  line-height: 1;
  transition:
    background 0.18s ease,
    color 0.18s ease;
}

.variable-panel-close:hover {
  background: #ecf5ff;
  color: #409eff;
}

.variable-help {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.variable-help-desc {
  margin: 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
}

.variable-category-list {
  min-height: 120px;
}

.variable-category {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.variable-category + .variable-category {
  margin-top: 18px;
}

.variable-category-title {
  color: #303133;
  font-size: 14px;
  font-weight: 600;
}

.variable-card {
  padding: 14px;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #fafcff 100%);
  box-shadow: 0 8px 24px rgba(31, 45, 61, 0.06);
}

.variable-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.variable-name {
  padding: 4px 8px;
  border-radius: 8px;
  background: #f0f9eb;
  color: #529b2e;
  font-size: 13px;
  font-weight: 600;
}

.variable-desc {
  margin: 0 0 12px;
  color: #4e5969;
  font-size: 13px;
  line-height: 1.6;
}

.variable-meta {
  display: grid;
  grid-template-columns: 1fr;
  gap: 4px;
  margin-bottom: 10px;
  color: #909399;
  font-size: 12px;
}

.variable-media-types {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.variable-example {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f5f7fa;
  color: #909399;
  font-size: 12px;
}

.variable-example code {
  color: #303133;
  font-family: monospace;
}

.ignored-input-row {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}

@media (max-width: 980px) {
  .rule-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
