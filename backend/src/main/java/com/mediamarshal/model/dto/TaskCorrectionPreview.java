package com.mediamarshal.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TaskCorrectionPreview {
    private String currentTargetPath;
    private String correctedTargetPath;
    private boolean sameTargetPath;
    private MatchResult selectedMatch;
    private List<TaskCorrectionOperation> operations = new ArrayList<>();
    private List<String> blockers = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private boolean canApply;
}
