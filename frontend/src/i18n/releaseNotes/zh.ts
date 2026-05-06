export default {
  v0_2_4: {
    items: {
      versionReleaseNotes: '新增统一版本号与 ReleaseNote 展示，前后端共用根目录 VERSION，页面标题旁悬停即可查看更新说明。',
      mediaAssetSupport: '新增媒体资产识别能力，支持普通视频、ISO 镜像和蓝光原盘目录，并在仪表盘与待确认队列展示资产类型。',
      reviewQueueBatching: '待确认队列支持批量选择、批量搜索、批量确认、批量跳过，以及将搜索候选应用到多任务。',
      metadataMatching: '优化中文 / 双语标题多 Query 搜索、TMDB 缓存、in-flight 去重和多维置信度评分，提升自动匹配准确度。',
      pathTemplateWorkflow: '优化路径设置体验，支持文件发现模式、复制 / 硬链接 / 软链接策略、模板变量帮助、可选片段和自定义模板预览。',
      sourceMissingGuard: '修复源文件删除后的确认风险，待处理和待确认任务会在源文件缺失时自动失败并给出明确原因。',
    },
  },
}
