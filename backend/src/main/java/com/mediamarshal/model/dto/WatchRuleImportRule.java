package com.mediamarshal.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchRuleImportRule {

    private String name;
    private String sourceDir;
    private String targetDir;
    private String mediaType;
    private String moviePathTemplate;
    private String tvPathTemplate;
    private String operation;
    private Boolean enabled;
    private Boolean moveAssociatedFiles;
    private Boolean cleanupEmptyDirs;
    private Boolean generateNfo;
    private List<String> ignoredFilePatterns;
    private String discoveryMode;
    private Integer scanIntervalMinutes;
    private Boolean webhookEnabled;
}
