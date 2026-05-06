export default {
  v0_2_4: {
    items: {
      versionReleaseNotes: 'Added unified version and ReleaseNote display. Frontend and backend now share the root VERSION file, and release notes appear on hover next to the page title.',
      mediaAssetSupport: 'Added media asset detection for video files, ISO images, and Blu-ray directories, with asset type display in Dashboard and Review Queue.',
      reviewQueueBatching: 'Review Queue now supports batch selection, batch search, batch confirmation, batch skip, and applying a search candidate to multiple tasks.',
      metadataMatching: 'Improved Chinese / bilingual multi-query title search, TMDB caching, in-flight deduplication, and multi-factor confidence scoring for better auto matching.',
      pathTemplateWorkflow: 'Improved Path Settings with discovery modes, copy / hard link / symbolic link strategies, template variable help, optional segments, and custom template preview.',
      sourceMissingGuard: 'Fixed confirmation risks after source files are deleted. Pending and awaiting-review tasks now fail with a clear reason when the source file is missing.',
    },
  },
}
