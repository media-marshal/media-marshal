<template>
  <el-popover
    placement="right-end"
    trigger="hover"
    :width="390"
    popper-class="app-version-popover"
  >
    <template #reference>
      <button class="version-trigger" type="button">
        {{ appVersion }}
      </button>
    </template>

    <div class="version-panel">
      <div class="version-heading">
        <span class="version-title">{{ t('appVersion.title') }}</span>
        <span class="version-current">{{ appVersion }}</span>
      </div>

      <div class="release-list">
        <section v-for="note in visibleReleaseNotes" :key="note.version" class="release-note">
          <div class="release-meta">
            <span class="release-version">{{ note.version }}</span>
            <span class="release-date">{{ note.date }}</span>
          </div>
          <ul class="release-items">
            <li v-for="item in note.items" :key="item.key" class="release-item">
              <el-tag size="small" effect="plain" :type="tagTypeMap[item.type]">
                {{ t(`appVersion.itemType.${item.type}`) }}
              </el-tag>
              <span>{{ t(item.key) }}</span>
            </li>
          </ul>
        </section>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TagProps } from 'element-plus'
import { appVersion } from '@/meta/appVersion'
import { releaseNotes } from '@/meta/releaseNotes'

const { t } = useI18n()

const visibleReleaseNotes = computed(() => releaseNotes.slice(0, 3))

const tagTypeMap: Record<string, TagProps['type']> = {
  feature: 'success',
  fix: 'danger',
  optimization: 'warning',
}
</script>

<style scoped>
.version-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  height: 20px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: #7f86a3;
  cursor: help;
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
}

.version-trigger:hover {
  border-color: #343454;
  background: #131325;
  color: #c4c8dc;
}

.version-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.version-heading,
.release-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.version-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.version-current,
.release-date {
  color: #909399;
  font-size: 12px;
}

.release-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.release-note {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.release-version {
  color: #303133;
  font-weight: 600;
}

.release-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.release-item {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: start;
  gap: 8px;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}
</style>
