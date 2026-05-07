export default {
  v0_2_4: {
    items: {
      versionReleaseNotes: '新增统一版本号与 ReleaseNote 展示，前后端共用根目录 VERSION，页面标题旁悬停即可查看更新说明。',
      firstRunSetup: '新增首次初始化模式，未配置 TMDB API Key 时会锁定主功能并引导用户先完成必要配置。',
      systemReset: '系统设置新增危险操作区域，支持清空数据库中的应用数据并回到首次初始化流程。',
      mediaAssetSupport: '新增媒体资产识别能力，支持普通视频、ISO 镜像和蓝光原盘目录，并在仪表盘与待确认队列展示资产类型。',
      reviewQueueBatching: '待确认队列支持批量选择、批量搜索、批量确认、批量跳过，以及将搜索候选应用到多任务。',
      metadataMatching: '优化中文 / 双语标题多 Query 搜索、TMDB 缓存、in-flight 去重和多维置信度评分，提升自动匹配准确度。',
      pathTemplateWorkflow: '优化路径设置体验，支持文件发现模式、复制 / 硬链接 / 软链接策略、模板变量帮助、可选片段和自定义模板预览。',
      dashboardFilters: '仪表盘任务列表支持按状态、资产类型和影片类型组合筛选。',
      i18nPolish: '补齐前端用户可见文案的国际化适配，并优化 ReleaseNote 面板宽度与标签对齐。',
      systemSettingsLayout: '优化系统设置页布局，让普通设置聚焦在系统配置本身，并统一表单说明文字的排版。',
      dangerSettingsPage: '将危险操作从系统设置中拆分为独立的危险设置页面，并统一表单说明文字显示在控件下方。',
      sourceMissingGuard: '修复源文件删除后的确认风险，待处理和待确认任务会在源文件缺失时自动失败并给出明确原因。',
    },
  },
}
