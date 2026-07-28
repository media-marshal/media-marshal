package com.mediamarshal.model.dto;

import java.util.List;

public record QueueBatchRecognitionRematchResponse(
        int updatedCount,
        int matchedCount,
        int emptyCount,
        int failedCount,
        QueueBatchRecognitionPreview preview,
        List<QueueBatchRecognitionRematchResult> results
) {
}
