<template>
  <el-dialog
    v-model="visible"
    :title="title || '选择目录'"
    width="480px"
    :close-on-click-modal="false"
    @open="handleOpen"
  >
    <!-- 面包屑路径导航 -->
    <div class="breadcrumb-bar">
      <el-icon class="folder-icon"><FolderOpened /></el-icon>
      <el-breadcrumb separator="/">
        <el-breadcrumb-item
          v-for="(segment, idx) in breadcrumbs"
          :key="idx"
          class="breadcrumb-item"
          @click="navigateTo(segment.path)"
        >
          {{ segment.label || '/' }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 目录列表 -->
    <el-scrollbar height="320px" class="dir-list">
      <div v-if="loading" class="dir-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>

      <!-- 返回上级 -->
      <div
        v-if="!loading && parentPath !== null"
        class="dir-item dir-item--parent"
        @click="navigateTo(parentPath!)"
      >
        <el-icon><ArrowLeft /></el-icon>
        <span>返回上级</span>
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
        此目录下没有子目录
      </div>
    </el-scrollbar>

    <!-- 当前选中路径预览 -->
    <div class="selected-path">
      <span class="selected-label">当前路径：</span>
      <code class="selected-value">{{ currentPath }}</code>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleConfirm">
        选择此目录
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
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

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const currentPath = ref('/')
const parentPath = ref<string | null>(null)
const dirs = ref<DirEntry[]>([])
const loading = ref(false)

/** 将路径拆分为面包屑片段 */
const breadcrumbs = computed(() => {
  const path = currentPath.value
  // 统一斜杠
  const normalized = path.replace(/\\/g, '/')
  if (normalized === '/') return [{ label: '/', path: '/' }]

  const parts = normalized.split('/').filter(Boolean)
  const crumbs = [{ label: '/', path: '/' }]
  let accumulated = ''
  for (const part of parts) {
    accumulated += '/' + part
    crumbs.push({ label: part, path: accumulated })
  }
  return crumbs
})

async function navigateTo(path: string) {
  loading.value = true
  try {
    const res = await filesystemApi.browse(path)
    const data = res.data.data
    currentPath.value = data.currentPath
    parentPath.value = data.parentPath
    dirs.value = data.dirs
  } finally {
    loading.value = false
  }
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
.breadcrumb-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  margin-bottom: 12px;
  min-height: 36px;
}

.folder-icon {
  color: #909399;
  flex-shrink: 0;
}

.breadcrumb-item {
  cursor: pointer;
}

:deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  font-weight: 600;
  color: #303133;
}

:deep(.el-breadcrumb__inner) {
  cursor: pointer !important;
}

:deep(.el-breadcrumb__inner:hover) {
  color: var(--el-color-primary) !important;
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
