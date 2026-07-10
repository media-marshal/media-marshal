package com.mediamarshal.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchRuleImportPreviewItem {

    public enum Status {
        READY,
        SKIPPED_DUPLICATE,
        CONFLICT,
        INVALID,
        WARNING
    }

    private int index;
    private Status status;
    private String message;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    private WatchRuleImportRule rule;
}
