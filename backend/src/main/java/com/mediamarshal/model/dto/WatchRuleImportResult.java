package com.mediamarshal.model.dto;

import com.mediamarshal.model.entity.WatchRule;
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
public class WatchRuleImportResult {

    private boolean preserveEnabledState;
    private boolean reloadTriggered;
    private int createdCount;
    private int skippedCount;
    private int conflictCount;
    private int invalidCount;
    private int warningCount;

    @Builder.Default
    private List<WatchRule> createdRules = new ArrayList<>();

    @Builder.Default
    private List<WatchRuleImportPreviewItem> items = new ArrayList<>();
}
