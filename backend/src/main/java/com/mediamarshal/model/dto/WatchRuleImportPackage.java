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
public class WatchRuleImportPackage {

    private String kind;
    private Integer schemaVersion;
    private String appVersion;
    private String exportedAt;

    @Builder.Default
    private List<WatchRuleImportRule> rules = new ArrayList<>();
}
