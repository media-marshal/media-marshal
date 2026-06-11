export default {
  v0_2_9: {
    items: {
      enabledTemplateVariables: 'Path templates can now use codec, release group, and original title for names that better match your library style.',
      tmdbTemplateVariables: 'Path templates can now organize files by TMDB genre, country, and TV episode title.',
      backendTemplatePreview: 'Custom templates are previewed and checked before saving, so problems are easier to catch early.',
      safeTemplateRendering: 'Unsafe templates are blocked before they can place files outside the target folder.',
      templateValueSanitizing: 'Unsafe path characters in titles and release groups are handled automatically, keeping previews closer to the final result.',
    },
  },
  v0_2_8: {
    items: {
      parentFolderContext: 'When a filename is unclear, the parent folder can help reduce repeated review prompts.',
      bilingualAliasConfidence: 'Bilingual title matching is more stable when English and localized names point to the same item.',
      parentFolderSafety: 'Parent-folder hints are skipped when the filename is already clear, reducing mismatch risk.',
      dashboardFileSearch: 'Dashboard tasks can be searched by file name or matched title.',
    },
  },
  v0_2_7: {
    items: {
      laterSeasonYearMatching: 'Later seasons handle season-year differences better, reducing unnecessary review.',
      laterSeasonYearSearch: 'Fixed cases where later seasons could miss the right show or score it too low.',
    },
  },
  v0_2_6: {
    items: {
      multiEpisodeParsing: 'Contiguous multi-episode files are now easier to organize.',
      templateAffixParameters: 'Path templates can show episode ranges in more naming styles.',
      episodeRangeDisplay: 'Review Queue now shows episode ranges more clearly.',
      episodeArrayDecode: 'Fixed processing failures for some multi-episode files.',
    },
  },
  v0_2_5: {
    items: {
      queueRecognitionEditor: 'Review Queue tasks can be edited directly when the detected title, year, season, or episode is wrong.',
      queueRecognitionRematch: 'After editing recognition info, you can refresh candidates before confirming.',
      reviewSearchContext: 'Manual search now reuses your edited title and media type.',
      queueResolutionDisplay: 'Review Queue shows resolution so source quality is easier to identify.',
      releaseNoteFolding: 'Release notes are more compact, with extra items available on demand.',
      recognitionValidation: 'Recognition edits now show clearer required-field prompts.',
    },
  },
  v0_2_4: {
    items: {
      versionReleaseNotes: 'The app now shows the current version and recent changes in the UI.',
      firstRunSetup: 'First-time setup guides you through required configuration before organizing starts.',
      systemReset: 'You can reset application data and return to first-time setup when needed.',
      mediaAssetSupport: 'Task lists now distinguish video files, ISO images, and Blu-ray folders.',
      reviewQueueBatching: 'Review Queue supports batch actions for faster cleanup of large queues.',
      metadataMatching: 'Chinese and bilingual title matching is more accurate.',
      pathTemplateWorkflow: 'Path settings are easier to preview and adapt to your preferred folder structure.',
      dashboardFilters: 'Dashboard filters make task lists easier to narrow down.',
      i18nPolish: 'Interface text and release note display are more consistent.',
      systemSettingsLayout: 'System settings are clearer and easier to scan.',
      dangerSettingsPage: 'Dangerous actions are grouped on a separate page to reduce mistakes.',
      sourceMissingGuard: 'Missing source files now produce clear task messages instead of continuing invalid confirmations.',
    },
  },
}
