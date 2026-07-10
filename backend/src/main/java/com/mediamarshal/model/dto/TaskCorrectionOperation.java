package com.mediamarshal.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskCorrectionOperation {
    private OperationType type;
    private String sourcePath;
    private String targetPath;
    private String description;

    public enum OperationType {
        MOVE_MAIN_ASSET,
        MOVE_ASSOCIATED_FILE,
        GENERATE_NFO,
        CLEAN_EMPTY_DIR,
        CREATE_CORRECTION_TASK,
        MARK_ORIGINAL_CORRECTED
    }
}
