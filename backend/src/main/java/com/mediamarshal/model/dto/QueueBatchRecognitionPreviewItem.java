package com.mediamarshal.model.dto;

import com.mediamarshal.model.entity.MediaTask;

import java.util.List;

public record QueueBatchRecognitionPreviewItem(
        Long taskId,
        String sourcePath,
        MediaTask.MediaType currentMediaType,
        MediaTask.MediaType effectiveMediaType,
        String currentTitle,
        String effectiveTitle,
        Integer currentYear,
        Integer effectiveYear,
        Integer currentSeason,
        Integer effectiveSeason,
        Integer currentEpisode,
        Integer currentEpisodeEnd,
        Integer effectiveEpisode,
        Integer effectiveEpisodeEnd,
        Integer sequenceIndex,
        List<String> blockers,
        List<String> warnings
) {
}
