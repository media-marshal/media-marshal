import type { ReleaseNote } from '@/types'

export const releaseNotes: ReleaseNote[] = [
  {
    version: 'v0.2.7',
    date: '2026-05-30',
    items: [
      { type: 'optimization', key: 'releaseNotes.v0_2_7.items.laterSeasonYearMatching' },
      { type: 'fix', key: 'releaseNotes.v0_2_7.items.laterSeasonYearSearch' },
    ],
  },
  {
    version: 'v0.2.6',
    date: '2026-05-19',
    items: [
      { type: 'feature', key: 'releaseNotes.v0_2_6.items.multiEpisodeParsing' },
      { type: 'feature', key: 'releaseNotes.v0_2_6.items.templateAffixParameters' },
      { type: 'optimization', key: 'releaseNotes.v0_2_6.items.episodeRangeDisplay' },
      { type: 'fix', key: 'releaseNotes.v0_2_6.items.episodeArrayDecode' },
    ],
  },
  {
    version: 'v0.2.5',
    date: '2026-05-08',
    items: [
      { type: 'feature', key: 'releaseNotes.v0_2_5.items.queueRecognitionEditor' },
      { type: 'feature', key: 'releaseNotes.v0_2_5.items.queueRecognitionRematch' },
      { type: 'optimization', key: 'releaseNotes.v0_2_5.items.reviewSearchContext' },
      { type: 'optimization', key: 'releaseNotes.v0_2_5.items.queueResolutionDisplay' },
      { type: 'optimization', key: 'releaseNotes.v0_2_5.items.releaseNoteFolding' },
      { type: 'fix', key: 'releaseNotes.v0_2_5.items.recognitionValidation' },
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
      { type: 'optimization', key: 'releaseNotes.v0_2_4.items.metadataMatching' },
      { type: 'optimization', key: 'releaseNotes.v0_2_4.items.pathTemplateWorkflow' },
      { type: 'optimization', key: 'releaseNotes.v0_2_4.items.dashboardFilters' },
      { type: 'optimization', key: 'releaseNotes.v0_2_4.items.i18nPolish' },
      { type: 'optimization', key: 'releaseNotes.v0_2_4.items.systemSettingsLayout' },
      { type: 'optimization', key: 'releaseNotes.v0_2_4.items.dangerSettingsPage' },
      { type: 'fix', key: 'releaseNotes.v0_2_4.items.sourceMissingGuard' },
    ],
  },
]
