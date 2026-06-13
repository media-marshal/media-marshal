import type { ReleaseNote } from '@/types'

export const releaseNotes: ReleaseNote[] = [
  {
    version: 'v0.2.9',
    date: '2026-06-09',
    items: [
      { type: 'feature', key: 'releaseNotes.v0_2_9.items.enabledTemplateVariables' },
      { type: 'feature', key: 'releaseNotes.v0_2_9.items.tmdbTemplateVariables' },
      { type: 'feature', key: 'releaseNotes.v0_2_9.items.backendTemplatePreview' },
      { type: 'feature', key: 'releaseNotes.v0_2_9.items.watchRuleImportExport' },
      { type: 'fix', key: 'releaseNotes.v0_2_9.items.tmdbProxyComposeEnv' },
      { type: 'fix', key: 'releaseNotes.v0_2_9.items.debugLoggingSwitch' },
      { type: 'fix', key: 'releaseNotes.v0_2_9.items.safeTemplateRendering' },
      { type: 'optimization', key: 'releaseNotes.v0_2_9.items.tmdbProxyDiagnostics' },
      { type: 'optimization', key: 'releaseNotes.v0_2_9.items.scanDuplicateLogging' },
      { type: 'optimization', key: 'releaseNotes.v0_2_9.items.hideEmailSettingsPanel' },
      { type: 'optimization', key: 'releaseNotes.v0_2_9.items.templateValueSanitizing' },
    ],
  },
  {
    version: 'v0.2.8',
    date: '2026-05-31',
    items: [
      { type: 'fix', key: 'releaseNotes.v0_2_8.items.bilingualAliasConfidence' },
      { type: 'optimization', key: 'releaseNotes.v0_2_8.items.parentFolderContext' },
      { type: 'optimization', key: 'releaseNotes.v0_2_8.items.parentFolderSafety' },
      { type: 'optimization', key: 'releaseNotes.v0_2_8.items.dashboardFileSearch' },
    ],
  },
  {
    version: 'v0.2.7',
    date: '2026-05-30',
    items: [
      { type: 'fix', key: 'releaseNotes.v0_2_7.items.laterSeasonYearSearch' },
      { type: 'optimization', key: 'releaseNotes.v0_2_7.items.laterSeasonYearMatching' },
    ],
  },
  {
    version: 'v0.2.6',
    date: '2026-05-19',
    items: [
      { type: 'feature', key: 'releaseNotes.v0_2_6.items.multiEpisodeParsing' },
      { type: 'feature', key: 'releaseNotes.v0_2_6.items.templateAffixParameters' },
      { type: 'fix', key: 'releaseNotes.v0_2_6.items.episodeArrayDecode' },
      { type: 'optimization', key: 'releaseNotes.v0_2_6.items.episodeRangeDisplay' },
    ],
  },
  {
    version: 'v0.2.5',
    date: '2026-05-08',
    items: [
      { type: 'feature', key: 'releaseNotes.v0_2_5.items.queueRecognitionEditor' },
      { type: 'feature', key: 'releaseNotes.v0_2_5.items.queueRecognitionRematch' },
      { type: 'fix', key: 'releaseNotes.v0_2_5.items.recognitionValidation' },
      { type: 'optimization', key: 'releaseNotes.v0_2_5.items.reviewSearchContext' },
      { type: 'optimization', key: 'releaseNotes.v0_2_5.items.queueResolutionDisplay' },
      { type: 'optimization', key: 'releaseNotes.v0_2_5.items.releaseNoteFolding' },
    ],
  },
  {
    version: 'v0.2.4',
    date: '2026-05-06',
    items: [
      { type: 'feature', key: 'releaseNotes.v0_2_4.items.versionReleaseNotes' },
      { type: 'feature', key: 'releaseNotes.v0_2_4.items.firstRunSetup' },
      { type: 'feature', key: 'releaseNotes.v0_2_4.items.systemReset' },
      { type: 'feature', key: 'releaseNotes.v0_2_4.items.mediaAssetSupport' },
      { type: 'feature', key: 'releaseNotes.v0_2_4.items.reviewQueueBatching' },
      { type: 'fix', key: 'releaseNotes.v0_2_4.items.sourceMissingGuard' },
      { type: 'optimization', key: 'releaseNotes.v0_2_4.items.metadataMatching' },
      { type: 'optimization', key: 'releaseNotes.v0_2_4.items.pathTemplateWorkflow' },
      { type: 'optimization', key: 'releaseNotes.v0_2_4.items.dashboardFilters' },
      { type: 'optimization', key: 'releaseNotes.v0_2_4.items.i18nPolish' },
      { type: 'optimization', key: 'releaseNotes.v0_2_4.items.systemSettingsLayout' },
      { type: 'optimization', key: 'releaseNotes.v0_2_4.items.dangerSettingsPage' },
    ],
  },
]
