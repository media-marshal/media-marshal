package com.mediamarshal.model.dto;

import com.mediamarshal.model.entity.MediaTask;

import java.util.List;

public record QueueBatchRecognitionSaveResponse(
        int updatedCount,
        List<MediaTask> tasks,
        QueueBatchRecognitionPreview preview
) {
}
