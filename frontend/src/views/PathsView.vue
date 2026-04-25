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
            <div class="path-label">监控源目录</div>
            <div class="path-value" :title="rule.sourceDir">{{ rule.sourceDir }}</div>
          </div>
          <el-icon class="path-arrow"><ArrowRight /></el-icon>
          <div class="path-block">
            <div class="path-label">目标根目录</div>
            <div class="path-value" :title="rule.targetDir">{{ rule.targetDir }}</div>
          </div>
        </div>

        <!-- 标签行 -->
        <div class="tag-row">
          <el-tag size="small" :type="mediaTypeTagType(rule.mediaType)">
            {{ t(`watchRule.mediaTypeOptions.${rule.mediaType}`) }}
          </el-tag>
          <el-tag size="small" type="info">
            {{ t(`watchRule.operationOptions.${rule.operation}`) }}
          </el-tag>
          <el-tag v-if="!rule.enabled" size="small" type="warning">已停用</el-tag>
        </div>

        <!-- 路径模板（有值时才显示） -->
        <div v-if="rule.pathTemplate" class="template-row">
          <el-icon><Document /></el-icon>
          <span class="template-text" :title="rule.pathTemplate">{{ rule.pathTemplate }}</span>
        </div>
        <div v-else class="template-row template-row--default">
          <el-icon><Document /></el-icon>
          <span>使用全局默认模板</span>
        </div>

        <!-- 操作按钮 -->
        <div class="card-actions">
          <el-button link type="primary" :icon="Edit" @click="openDialog(rule)">
            编辑
          </el-button>
          <el-button link type="danger" :icon="Delete" @click="handleDelete(rule)">
            删除
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingRule ? t('watchRule.editRule') : t('watchRule.addRule')"
      width="580px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        @submit.prevent
      >
        <!-- 规则名称 -->
        <el-form-item :label="t('watchRule.name')" prop="name">
          <el-input v-model="form.name" placeholder="如：电影库、剧集收录" />
        </el-form-item>

        <!-- 源目录（带浏览按钮） -->
        <el-form-item :label="t('watchRule.sourceDir')" prop="sourceDir">
          <el-input v-model="form.sourceDir" placeholder="/media/incoming（容器内绝对路径）">
            <template #append>
              <el-button :icon="FolderOpened" @click="openDirBrowser('source')" title="浏览目录" />
            </template>
          </el-input>
        </el-form-item>

        <!-- 目标目录（带浏览按钮） -->
        <el-form-item :label="t('watchRule.targetDir')" prop="targetDir">
          <el-input v-model="form.targetDir" placeholder="/media/movies（容器内绝对路径）">
            <template #append>
              <el-button :icon="FolderOpened" @click="openDirBrowser('target')" title="浏览目录" />
            </template>
          </el-input>
        </el-form-item>

        <!-- 媒体类型 + 文件操作（同行） -->
        <el-row :gutter="16">
          <el-col :span="12">
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
          </el-col>
          <el-col :span="12">
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
          </el-col>
        </el-row>

        <!-- 路径模板：下拉预设 + 自定义切换 -->
        <el-form-item :label="t('watchRule.pathTemplate')">
          <!-- 预设模式 -->
          <div v-if="!templateCustomMode" class="template-input-row">
            <el-select
              v-model="selectedTemplateValue"
              style="flex: 1"
              placeholder="使用全局默认模板"
              clearable
            >
              <el-option
                v-for="tpl in PRESET_TEMPLATES"
                :key="tpl.value"
                :label="tpl.label"
                :value="tpl.value"
              >
                <div class="template-option">
                  <span class="template-option-label">{{ tpl.label }}</span>
                  <span class="template-option-hint">{{ tpl.hint }}</span>
                </div>
              </el-option>
            </el-select>
            <el-tooltip content="自定义模板" placement="top">
              <el-button :icon="EditPen" @click="switchToCustom" />
            </el-tooltip>
          </div>

          <!-- 自定义模式 -->
          <div v-else class="template-input-row">
            <el-input
              v-model="customTemplateValue"
              style="flex: 1"
              placeholder="输入自定义路径模板，如：{title} ({year}){ext}"
            />
            <el-tooltip content="返回选择预设" placement="top">
              <el-button :icon="ArrowLeft" @click="switchToPreset" />
            </el-tooltip>
          </div>
          <div class="form-hint">
            可用变量：{title} &nbsp;{year} &nbsp;{season:02d} &nbsp;{episode:02d} &nbsp;{original_title} &nbsp;{ext}
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 目录浏览弹窗 -->
    <DirBrowserDialog
      v-model="dirBrowserVisible"
      :title="dirBrowserTarget === 'source' ? '选择监控源目录' : '选择目标根目录'"
      :initial-path="dirBrowserTarget === 'source' ? form.sourceDir || '/' : form.targetDir || '/'"
      @select="onDirSelected"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, FolderOpened, EditPen, ArrowRight, ArrowLeft, Document } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { watchRuleApi, type WatchRule, type WatchRuleRequest } from '@/api/watchRule'
import DirBrowserDialog from '@/components/DirBrowserDialog.vue'

const { t } = useI18n()

// ─── 规则列表 ────────────────────────────────────────────────────
const rules = ref<WatchRule[]>([])
const loading = ref(false)
const togglingId = ref<number | null>(null)

// ─── 对话框 ──────────────────────────────────────────────────────
const dialogVisible = ref(false)
const editingRule = ref<WatchRule | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

// ─── 目录浏览器 ──────────────────────────────────────────────────
const dirBrowserVisible = ref(false)
const dirBrowserTarget = ref<'source' | 'target'>('source')

// ─── 预设模板 ────────────────────────────────────────────────────
// TODO ADR-007：后续从后端接口 GET /api/template-variables 获取，当前硬编码
const PRESET_TEMPLATES = [
  {
    label: '电影默认',
    value: '{title} ({year})/{title} ({year}){ext}',
    hint: 'The Dark Knight (2008)/The Dark Knight (2008).mkv',
  },
  {
    label: '剧集默认',
    value: '{title}/Season {season:02d}/{title} - S{season:02d}E{episode:02d}{ext}',
    hint: 'Breaking Bad/Season 03/Breaking Bad - S03E07.mkv',
  },
]

// ─── 模板选择状态 ─────────────────────────────────────────────────
const templateCustomMode = ref(false)
const selectedTemplateValue = ref('')   // 预设模式选中的值
const customTemplateValue = ref('')     // 自定义模式输入的值

/** 最终提交的 pathTemplate 值 */
const effectiveTemplate = computed(() => {
  if (templateCustomMode.value) return customTemplateValue.value.trim() || undefined
  return selectedTemplateValue.value || undefined
})

function switchToCustom() {
  // 将当前预设值填入自定义输入框，方便用户在预设基础上修改
  customTemplateValue.value = selectedTemplateValue.value
  templateCustomMode.value = true
}

function switchToPreset() {
  templateCustomMode.value = false
  selectedTemplateValue.value = ''
}

// ─── 表单 ────────────────────────────────────────────────────────
const defaultForm = (): WatchRuleRequest => ({
  name: '',
  sourceDir: '',
  targetDir: '',
  mediaType: 'AUTO',
  pathTemplate: undefined,
  operation: 'MOVE',
  enabled: false,   // 新规则默认不启用（在卡片上手动开启）
})

const form = reactive<WatchRuleRequest>(defaultForm())

const formRules: FormRules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  sourceDir: [{ required: true, message: '请输入监控源目录', trigger: 'blur' }],
  targetDir: [{ required: true, message: '请输入目标根目录', trigger: 'blur' }],
  mediaType: [{ required: true }],
  operation: [{ required: true }],
}

const mediaTypeOptions = [{ value: 'AUTO' }, { value: 'MOVIE' }, { value: 'TV_SHOW' }]
const operationOptions = [
  { value: 'MOVE' }, { value: 'COPY' }, { value: 'HARD_LINK' }, { value: 'SYMBOLIC_LINK' },
]

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

function openDialog(rule?: WatchRule) {
  editingRule.value = rule ?? null
  Object.assign(form, rule ? { ...rule } : defaultForm())

  // 初始化模板状态
  templateCustomMode.value = false
  customTemplateValue.value = ''
  if (rule?.pathTemplate) {
    const preset = PRESET_TEMPLATES.find(t => t.value === rule.pathTemplate)
    if (preset) {
      selectedTemplateValue.value = preset.value
    } else {
      // 已有自定义模板，直接进入自定义模式
      templateCustomMode.value = true
      customTemplateValue.value = rule.pathTemplate
    }
  } else {
    selectedTemplateValue.value = ''
  }

  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload: WatchRuleRequest = {
      ...form,
      pathTemplate: effectiveTemplate.value,
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

function mediaTypeTagType(type: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' | undefined {
  return type === 'AUTO' ? 'info' : type === 'MOVIE' ? 'primary' : 'success'
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
  justify-content: flex-end;
  gap: 4px;
  border-top: 1px solid #f0f2f5;
  padding-top: 12px;
  margin-top: 4px;
}

/* ─── 表单内的模板选择 ──────────────── */
.template-input-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.template-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.template-option-label {
  font-weight: 500;
}

.template-option-hint {
  font-size: 11px;
  color: #909399;
  font-family: monospace;
}

.form-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  line-height: 1.6;
  font-family: monospace;
}
</style>
