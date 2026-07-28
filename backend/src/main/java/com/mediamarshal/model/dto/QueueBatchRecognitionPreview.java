package com.mediamarshal.model.dto;

import java.util.List;

public record QueueBatchRecognitionPreview(
        int totalCount,
        int editableCount,
        int sequentialCount,
        int blockerCount,
        int warningCount,
        boolean canApply,
        List<String> blockers,
        List<String> warnings,
        List<QueueBatchRecognitionPreviewItem> items
) {
}
