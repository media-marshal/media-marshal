<template>
  <el-popover
    placement="right-end"
    trigger="hover"
    :width="520"
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
            <li v-for="item in displayedItems(note)" :key="item.key" class="release-item">
              <el-tag class="release-tag" size="small" effect="plain" :type="tagTypeMap[item.type]">
                {{ t(`appVersion.itemType.${item.type}`) }}
              </el-tag>
              <span>{{ t(item.key) }}</span>
            </li>
          </ul>
          <el-button
            v-if="hasFoldedItems(note)"
            class="release-toggle"
            type="primary"
            link
            @click="toggleReleaseNote(note.version)"
          >
            {{
              isExpanded(note.version)
                ? t('appVersion.collapse')
                : t('appVersion.expand', { count: foldedItemCount(note) })
            }}
          </el-button>
        </section>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TagProps } from 'element-plus'
import { appVersion } from '@/meta/appVersion'
import { releaseNotes } from '@/meta/releaseNotes'
import type { ReleaseNote, ReleaseNoteItem } from '@/types'

const { t } = useI18n()

const MAX_ITEMS_PER_VERSION = 5
const visibleReleaseNotes = computed(() => releaseNotes.slice(0, 3))
const expandedVersions = ref<Set<string>>(new Set())

const tagTypeMap: Record<string, TagProps['type']> = {
  feature: 'success',
  fix: 'danger',
  optimization: 'warning',
}

function isExpanded(version: string) {
  return expandedVersions.value.has(version)
}

function hasFoldedItems(note: ReleaseNote) {
  return note.items.length > MAX_ITEMS_PER_VERSION
}

function foldedItemCount(note: ReleaseNote) {
  return Math.max(note.items.length - MAX_ITEMS_PER_VERSION, 0)
}

function displayedItems(note: ReleaseNote): ReleaseNoteItem[] {
  if (!hasFoldedItems(note) || isExpanded(note.version)) {
    return note.items
  }
  return note.items.slice(0, MAX_ITEMS_PER_VERSION)
}

function toggleReleaseNote(version: string) {
  const next = new Set(expandedVersions.value)
  if (next.has(version)) {
    next.delete(version)
  } else {
    next.add(version)
  }
  expandedVersions.value = next
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
  max-height: 50vh;
  overflow-y: auto;
  padding-right: 4px;
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
  grid-template-columns: 88px 1fr;
  align-items: start;
  gap: 8px;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}

.release-tag {
  width: 88px;
  justify-content: center;
}

.release-toggle {
  align-self: center;
  height: 22px;
  padding: 0;
  font-size: 12px;
}
</style>
