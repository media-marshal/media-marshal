<template>
  <el-container class="layout-container">
    <!-- 侧边导航 -->
    <el-aside width="220px" class="layout-aside">
      <div class="logo">
        <span class="logo-text">Media Marshal</span>
      </div>
      <el-menu
        :default-active="activeRoute"
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
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>{{ t('nav.settings') }}</span>
        </el-menu-item>
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

const activeRoute = computed(() => route.path)
const queueCount = computed(() => mediaStore.queueTasks.length)

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
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid #2a2a4a;
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
}

.queue-badge {
  margin-left: auto;
}

.locale-switcher {
  padding: 16px;
  display: flex;
  justify-content: center;
  border-top: 1px solid #2a2a4a;
}

.layout-main {
  background-color: #f5f7fa;
  overflow-y: auto;
}
</style>
