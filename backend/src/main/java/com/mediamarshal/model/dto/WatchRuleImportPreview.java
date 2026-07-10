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
public class WatchRuleImportPreview {

    private String kind;
    private Integer schemaVersion;
    private String appVersion;
    private String exportedAt;
    private int ruleCount;
    private boolean fileValid;
    private boolean hasBlockingIssues;
    private String fileMessage;
    private boolean preserveEnabledState;
    private WatchRuleImportSummary summary;

    @Builder.Default
    private List<WatchRuleImportPreviewItem> items = new ArrayList<>();
}
