<template>
  <el-dialog
    v-model="visible"
    :title="title || t('dirBrowser.title')"
    width="480px"
    :close-on-click-modal="false"
    @open="handleOpen"
  >
    <div class="path-toolbar">
      <el-input
        v-model="pathInput"
        size="small"
        :placeholder="t('dirBrowser.pathPlaceholder')"
        @keyup.enter="navigateToInput"
      />
      <el-button size="small" @click="navigateTo('/')">
        {{ t('dirBrowser.root') }}
      </el-button>
    </div>

    <!-- 目录列表 -->
    <el-scrollbar height="320px" class="dir-list">
      <div v-if="loading" class="dir-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ t('common.loading') }}</span>
      </div>

      <!-- 返回上级 -->
      <div
        v-if="!loading && parentPath !== null"
        class="dir-item dir-item--parent"
        @click="navigateTo(parentPath!)"
      >
        <el-icon><ArrowLeft /></el-icon>
        <span>{{ t('dirBrowser.parent') }}</span>
      </div>

      <div
        v-for="dir in dirs"
        :key="dir.path"
        class="dir-item"
        :class="{ 'is-selected': dir.path === currentPath }"
        @click="navigateTo(dir.path)"
      >
        <el-icon><Folder /></el-icon>
        <span class="dir-name">{{ dir.name }}</span>
        <el-icon class="dir-arrow"><ArrowRight /></el-icon>
      </div>

      <div v-if="!loading && dirs.length === 0 && parentPath !== null" class="dir-empty">
        {{ t('dirBrowser.empty') }}
      </div>
    </el-scrollbar>

    <!-- 当前选中路径预览 -->
    <div class="selected-path">
      <span class="selected-label">{{ t('dirBrowser.currentPath') }}</span>
      <code class="selected-value">{{ currentPath }}</code>
    </div>

    <template #footer>
      <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="handleConfirm">
        {{ t('dirBrowser.selectCurrent') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { filesystemApi } from '@/api/filesystem'
import type { DirEntry } from '@/api/filesystem'

interface Props {
  modelValue: boolean
  title?: string
  initialPath?: string
}

const props = withDefaults(defineProps<Props>(), {
  initialPath: '/',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'select': [path: string]
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const currentPath = ref('/')
const pathInput = ref('/')
const parentPath = ref<string | null>(null)
const dirs = ref<DirEntry[]>([])
const loading = ref(false)

async function navigateTo(path: string) {
  loading.value = true
  try {
    const res = await filesystemApi.browse(path)
    const data = res.data.data
    currentPath.value = data.currentPath
    pathInput.value = data.currentPath
    parentPath.value = data.parentPath
    dirs.value = data.dirs
  } finally {
    loading.value = false
  }
}

function navigateToInput() {
  const path = pathInput.value.trim()
  navigateTo(path || '/')
}

function handleOpen() {
  navigateTo(props.initialPath || '/')
}

function handleConfirm() {
  emit('select', currentPath.value)
  visible.value = false
}
</script>

<style scoped>
.path-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.path-toolbar :deep(.el-input__inner),
.path-toolbar :deep(.el-button) {
  font-size: 13px;
}

.dir-list {
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.dir-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 80px;
  color: #909399;
}

.dir-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.15s;
  border-bottom: 1px solid #f0f0f0;
  user-select: none;
}

.dir-item:last-child {
  border-bottom: none;
}

.dir-item:hover {
  background: #f5f7fa;
}

.dir-item.is-selected {
  background: #ecf5ff;
  color: var(--el-color-primary);
}

.dir-item--parent {
  color: #909399;
  font-size: 13px;
}

.dir-name {
  flex: 1;
  font-size: 14px;
}

.dir-arrow {
  color: #c0c4cc;
  flex-shrink: 0;
}

.dir-empty {
  padding: 24px 16px;
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
}

.selected-path {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.selected-label {
  color: #606266;
  flex-shrink: 0;
}

.selected-value {
  background: #f5f7fa;
  padding: 2px 8px;
  border-radius: 4px;
  font-family: monospace;
  color: #303133;
  word-break: break-all;
}
</style>
