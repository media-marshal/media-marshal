<template>
  <el-container class="layout-container">
    <!-- 侧边导航 -->
    <el-aside width="220px" class="layout-aside">
      <div class="logo">
        <span class="logo-text">Media Marshal</span>
      </div>

      <el-menu
        :default-active="route.path"
        :default-openeds="openedMenus"
        router
        background-color="#1a1a2e"
        text-color="#ccc"
        active-text-color="#fff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Monitor /></el-icon>
          <span>{{ t('nav.dashboard') }}</span>
        </el-menu-item>

        <el-menu-item index="/queue">
          <el-icon><Bell /></el-icon>
          <span>{{ t('nav.queue') }}</span>
          <el-badge v-if="queueCount > 0" :value="queueCount" class="queue-badge" />
        </el-menu-item>

        <!-- 设置：父菜单 + 子菜单（ADR-003） -->
        <el-sub-menu index="/settings">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>{{ t('nav.settings') }}</span>
          </template>
          <el-menu-item index="/settings/paths">
            <el-icon><FolderOpened /></el-icon>
            <span>{{ t('nav.settingsPaths') }}</span>
          </el-menu-item>
          <el-menu-item index="/settings/system">
            <el-icon><Tools /></el-icon>
            <span>{{ t('nav.settingsSystem') }}</span>
          </el-menu-item>
          <!-- 高级设置：路由预留，v1 不在菜单中显示，v2 有内容时露出 -->
        </el-sub-menu>
      </el-menu>

      <!-- 语言切换 -->
      <div class="locale-switcher">
        <el-button-group>
          <el-button size="small" :type="locale === 'zh' ? 'primary' : ''" @click="switchLocale('zh')">中</el-button>
          <el-button size="small" :type="locale === 'en' ? 'primary' : ''" @click="switchLocale('en')">EN</el-button>
        </el-button-group>
      </div>
    </el-aside>

    <!-- 主内容区 -->
    <el-main class="layout-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useMediaStore } from '@/stores/mediaStore'
import { setLocale } from '@/i18n'

const { t, locale } = useI18n()
const route = useRoute()
const mediaStore = useMediaStore()

const queueCount = computed(() => mediaStore.queueTasks.length)

// 当前路径在 /settings 下时，保持设置子菜单展开
const openedMenus = computed(() =>
  route.path.startsWith('/settings') ? ['/settings'] : [],
)

function switchLocale(lang: 'zh' | 'en') {
  setLocale(lang)
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-aside {
  background-color: #1a1a2e;
  display: flex;
  flex-direction: column;
  user-select: none;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid #2a2a4a;
  flex-shrink: 0;
}

.logo-text {
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.el-menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
}

/* 子菜单背景色与侧边栏保持一致 */
:deep(.el-sub-menu__title) {
  background-color: #1a1a2e !important;
  color: #ccc;
}

:deep(.el-sub-menu__title:hover) {
  background-color: #2a2a4a !important;
}

:deep(.el-menu--inline) {
  background-color: #131325 !important;
}

:deep(.el-menu--inline .el-menu-item) {
  background-color: #131325 !important;
  padding-left: 48px !important;
}

:deep(.el-menu--inline .el-menu-item:hover) {
  background-color: #1f1f3a !important;
}

:deep(.el-menu--inline .el-menu-item.is-active) {
  background-color: #2a2a5a !important;
  color: #fff !important;
}

.queue-badge {
  margin-left: auto;
}

.locale-switcher {
  padding: 16px;
  display: flex;
  justify-content: center;
  border-top: 1px solid #2a2a4a;
  flex-shrink: 0;
}

.layout-main {
  background-color: #f5f7fa;
  overflow-y: auto;
}
</style>
