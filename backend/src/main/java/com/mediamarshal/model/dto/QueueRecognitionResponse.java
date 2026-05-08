package com.mediamarshal.model.dto;

import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.TaskCandidate;

import java.util.List;

public record QueueRecognitionResponse(MediaTask task, List<TaskCandidate> candidates) {
}
