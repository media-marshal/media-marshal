export default {
  v0_2_9: {
    items: {
      enabledTemplateVariables: 'Path templates now support {codec}, {release_group}, and {original_title} for organizing media by codec, release group, and original title.',
      backendTemplatePreview: 'Added backend-powered template preview and validation so custom templates are checked with the real renderer before saving.',
      safeTemplateRendering: 'Added path safety checks that block absolute paths, Windows drive paths, UNC paths, and .. traversal before any file operation runs.',
      templateValueSanitizing: 'Template variable values now sanitize path-unsafe characters, keeping frontend previews aligned with actual backend organization.',
    },
  },
  v0_2_8: {
    items: {
      parentFolderContext: 'When the filename alone is not enough, matching can use localized and English titles from the release folder to reduce repeated review prompts for the same show.',
      bilingualAliasConfidence: 'Fixed low confidence when an English alias points to a localized TMDB title, so cases like The Heir and 家业 can corroborate each other more reliably.',
      parentFolderSafety: 'Parent-folder context is skipped when the filename is already confident, and parent-only candidates cannot auto-confirm, reducing the risk of incorrect organization.',
      dashboardFileSearch: 'Dashboard tasks can now be searched from the File and Matched Title column headers, making task records easier to locate.',
    },
  },
  v0_2_7: {
    items: {
      laterSeasonYearMatching: 'Season 2 and later now treat the file year as that season\'s air year, reducing unnecessary manual review caused by year differences.',
      laterSeasonYearSearch: 'Fixed later-season TV files missing the correct TMDB show, or lowering confidence for the right result, when the file year differs from the first-air year.',
    },
  },
  v0_2_6: {
    items: {
      multiEpisodeParsing: 'Added support for contiguous multi-episode parser results such as episode [16, 17] and [21, 22, ... 28].',
      templateAffixParameters: 'Template placeholders now support prefix, suffix, separator, repeatPrefix, and repeatSuffix parameters for reusable range rendering.',
      episodeRangeDisplay: 'Review Queue now preserves and displays parsed episode ranges, for example 16-17.',
      episodeArrayDecode: 'Fixed pipeline failures when guessit returns episode as a JSON array for multi-episode files.',
    },
  },
  v0_2_5: {
    items: {
      queueRecognitionEditor: 'Review Queue tasks now support editing the effective recognition info, including media type, parsed title, year, season, and episode.',
      queueRecognitionRematch: 'Added “Save and Rematch” to refresh TMDB candidates from corrected recognition info without auto-confirming the task.',
      reviewSearchContext: 'Manual search now picks up the edited media type and title as the default review context.',
      queueResolutionDisplay: 'Added resolution to the Review Queue task summary so source quality is easier to identify during confirmation.',
      releaseNoteFolding: 'Improved the Release Notes panel so each version shows up to 5 changes by default, with extra items available on demand.',
      recognitionValidation: 'Added recognition edit validation: title is required, TV tasks require season and episode, and movie tasks clear episode fields.',
    },
  },
  v0_2_4: {
    items: {
      versionReleaseNotes: 'Added unified version and ReleaseNote display. Frontend and backend now share the root VERSION file, and release notes appear on hover next to the page title.',
      firstRunSetup: 'Added first-run setup mode. Main features stay locked until a TMDB API Key is configured.',
      systemReset: 'Added a Danger Zone in System Settings to clear application database data and return to first-run setup.',
      mediaAssetSupport: 'Added media asset detection for video files, ISO images, and Blu-ray directories, with asset type display in Dashboard and Review Queue.',
      reviewQueueBatching: 'Review Queue now supports batch selection, batch search, batch confirmation, batch skip, and applying a search candidate to multiple tasks.',
      metadataMatching: 'Improved Chinese / bilingual multi-query title search, TMDB caching, in-flight deduplication, and multi-factor confidence scoring for better auto matching.',
      pathTemplateWorkflow: 'Improved Path Settings with discovery modes, copy / hard link / symbolic link strategies, template variable help, optional segments, and custom template preview.',
      dashboardFilters: 'Dashboard task list now supports combined filtering by status, asset type, and media type.',
      i18nPolish: 'Completed i18n coverage for user-facing frontend text and refined ReleaseNote panel width and tag alignment.',
      systemSettingsLayout: 'Improved System Settings layout so normal settings stay focused on system configuration, with consistent helper text placement.',
      dangerSettingsPage: 'Moved danger actions into a dedicated Danger Settings page and standardized helper text below form controls.',
      sourceMissingGuard: 'Fixed confirmation risks after source files are deleted. Pending and awaiting-review tasks now fail with a clear reason when the source file is missing.',
    },
  },
}
