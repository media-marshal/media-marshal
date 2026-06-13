package com.mediamarshal.model.dto;

import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.service.rename.FileOperationStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class WatchRuleRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String sourceDir;

    @NotBlank
    private String targetDir;

    @NotNull
    private WatchRule.RuleMediaType mediaType;

    /** null 表示使用全局电影默认模板 */
    private String moviePathTemplate;

    /** null 表示使用全局剧集默认模板 */
    private String tvPathTemplate;

    @NotNull
    private FileOperationStrategy.OperationType operation;

    private Boolean enabled;

    private Boolean moveAssociatedFiles;

    private Boolean cleanupEmptyDirs;

    private Boolean generateNfo;

    private List<String> ignoredFilePatterns;

    private WatchRule.DiscoveryMode discoveryMode;

    private Integer scanIntervalMinutes;

    private Boolean webhookEnabled;
}
