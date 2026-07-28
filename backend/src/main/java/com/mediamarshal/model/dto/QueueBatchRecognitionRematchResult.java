package com.mediamarshal.model.dto;

import com.mediamarshal.model.entity.TaskCandidate;

import java.util.List;

public record QueueBatchRecognitionRematchResult(
        Long taskId,
        QueueBatchRecognitionRematchStatus status,
        String message,
        List<TaskCandidate> candidates
) {
}
