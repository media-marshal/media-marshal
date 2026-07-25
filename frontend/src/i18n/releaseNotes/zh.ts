export default {
  v0_2_10: {
    items: {
      completedTaskCorrection: '已完成任务支持修正匹配，并同步移动或重命名媒体库文件。',
      queueBatchRecognition: '待确认剧集可批量修正识别信息，按文件顺序生成集号，并稳定等待重新匹配结果。',
      sampleScanPruning: '默认忽略 Sample 目录，减少无效识别和 TMDB 请求。',
    },
  },
  v0_2_9: {
    items: {
      enabledTemplateVariables: '路径模板可使用编码、发布组和原语种标题，整理结果更贴近你的命名习惯。',
      tmdbTemplateVariables: '路径模板可按 TMDB 分类、国家和剧集单集标题组织文件。',
      backendTemplatePreview: '保存自定义模板前会先预览并提示问题，减少保存后才发现模板不可用的情况。',
      watchRuleImportExport: '路径设置支持导入导出监控规则，升级或迁移服务器前可以备份和恢复整理策略。',
      tmdbProxyComposeEnv: '使用 Docker Compose 配置 TMDB 代理时，代理开关和地址会正确生效。',
      debugLoggingSwitch: '开启调试模式后，后端会正确输出调试日志，排查网络和匹配问题更直接。',
      safeTemplateRendering: '危险路径模板会被拦截，避免文件被整理到目标目录之外。',
      tmdbProxyDiagnostics: '调试日志会显示 TMDB 请求是否通过代理访问，代理配置是否生效更容易确认。',
      scanDuplicateLogging: '重复扫描已有任务时不再刷屏，扫描完成日志会汇总重复数量。',
      hideEmailSettingsPanel: '系统设置暂时隐藏未开放的邮件通知入口，页面更聚焦已可用配置。',
      templateValueSanitizing: '标题、发布组等内容里的非法路径字符会自动处理，预览和实际整理结果更一致。',
    },
  },
  v0_2_8: {
    items: {
      parentFolderContext: '文件名信息不够时，会参考父级目录名称，减少正确影片反复进入待确认。',
      bilingualAliasConfidence: '双语片名匹配更稳定，英文名和中文名指向同一条目时更容易正确识别。',
      parentFolderSafety: '文件名已足够明确时不会额外参考父目录，降低误匹配风险。',
      dashboardFileSearch: '仪表盘可按文件名或匹配标题搜索任务，查找记录更快。',
    },
  },
  v0_2_7: {
    items: {
      laterSeasonYearMatching: '多季剧集会更好理解后续季年份，减少因年份不同进入待确认。',
      laterSeasonYearSearch: '修复后续季可能搜不到正确剧集或置信度偏低的问题。',
    },
  },
  v0_2_6: {
    items: {
      multiEpisodeParsing: '支持连续多集文件，整理合集或连播剧集更省心。',
      templateAffixParameters: '路径模板可更灵活地展示季集范围，适合不同命名风格。',
      episodeRangeDisplay: '待确认队列会展示多集范围，确认任务时更直观。',
      episodeArrayDecode: '修复部分多集文件解析后处理失败的问题。',
    },
  },
  v0_2_5: {
    items: {
      queueRecognitionEditor: '待确认任务可直接编辑识别信息，修正标题、年份、季号和集号更方便。',
      queueRecognitionRematch: '修正识别信息后可重新匹配候选，确认前有更多调整空间。',
      reviewSearchContext: '手动搜索会沿用你刚编辑的信息，减少重复输入。',
      queueResolutionDisplay: '待确认列表会显示分辨率，确认片源规格更快。',
      releaseNoteFolding: '版本更新面板默认更简洁，更多内容可按需展开。',
      recognitionValidation: '识别信息编辑会提示必填项，减少保存无效信息。',
    },
  },
  v0_2_4: {
    items: {
      versionReleaseNotes: '页面中可直接查看当前版本和更新内容。',
      firstRunSetup: '首次使用会引导完成必要配置，避免未配置就开始整理。',
      systemReset: '需要重新初始化时，可在设置中清空应用数据并回到首次配置流程。',
      mediaAssetSupport: '任务列表会区分普通视频、ISO 镜像和蓝光原盘目录，资产类型更清楚。',
      reviewQueueBatching: '待确认队列支持批量操作，处理大量任务更高效。',
      metadataMatching: '中文和双语标题识别更准确，自动匹配更稳定。',
      pathTemplateWorkflow: '路径设置更完整，可按偏好的目录结构预览整理结果。',
      dashboardFilters: '仪表盘支持多条件筛选，定位任务更方便。',
      i18nPolish: '界面文案和更新面板显示更统一。',
      systemSettingsLayout: '系统设置页更清晰，常用配置更容易找到。',
      dangerSettingsPage: '危险操作集中到独立页面，降低误操作风险。',
      sourceMissingGuard: '源文件缺失时会给出明确提示，避免继续确认无效任务。',
    },
  },
}
