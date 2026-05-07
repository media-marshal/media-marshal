<template>
  <el-config-provider :locale="elementLocale">
    <router-view v-if="bareLayout" />
    <AppLayout v-else />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import AppLayout from '@/components/AppLayout.vue'

const { locale } = useI18n()
const route = useRoute()

const elementLocale = computed(() => (locale.value === 'zh' ? zhCn : en))
const bareLayout = computed(() => route.meta.bareLayout === true)
</script>

<style>
*,
*::before,
*::after {
  box-sizing: border-box;
}

html,
body {
  margin: 0;
  padding: 0;
}
</style>
