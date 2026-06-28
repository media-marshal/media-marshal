package com.mediamarshal.model.dto;

import com.mediamarshal.model.entity.MediaTask;

public record TaskCorrectionApplyResponse(
        MediaTask originalTask,
        MediaTask correctedTask,
        TaskCorrectionPreview preview
) {
}
