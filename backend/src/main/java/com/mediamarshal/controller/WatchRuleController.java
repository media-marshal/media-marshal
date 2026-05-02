package com.mediamarshal.controller;

import com.mediamarshal.model.dto.ApiResponse;
import com.mediamarshal.model.dto.WatchRuleValidationResult;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.discovery.FileDiscoveryService;
import com.mediamarshal.service.watchrule.WatchRulePreflightService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 监控规则管理 REST API（ADR-002）
 *
 * GET    /api/watch-rules              查询所有规则
 * POST   /api/watch-rules              新建规则
 * PUT    /api/watch-rules/{id}         更新规则
 * DELETE /api/watch-rules/{id}         删除规则
 * PATCH  /api/watch-rules/{id}/toggle  启用/禁用规则
 * POST   /api/watch-rules/{id}/scan    触发规则源目录全量扫描
 *
 * 规则变更后自动触发 FileDiscoveryService 重新加载发现配置。
 */
@Slf4j
@RestController
@RequestMapping("/api/watch-rules")
@RequiredArgsConstructor
public class WatchRuleController {

    private final WatchRuleRepository watchRuleRepository;
    private final FileDiscoveryService fileDiscoveryService;
    private final WatchRulePreflightService watchRulePreflightService;

    @GetMapping
    public ApiResponse<List<WatchRule>> listRules() {
        return ApiResponse.ok(watchRuleRepository.findAll());
    }

    @PostMapping
    public ApiResponse<WatchRule> createRule(@Valid @RequestBody RuleRequest request) {
        WatchRule rule = buildRule(new WatchRule(), request);
        WatchRule saved = watchRuleRepository.save(Objects.requireNonNull(rule));
        log.info("WatchRule created: id={}, name={}, sourceDir={}", saved.getId(), saved.getName(), saved.getSourceDir());
        fileDiscoveryService.reload();
        return ApiResponse.ok(saved);
    }

    @PostMapping("/validate")
    public ApiResponse<WatchRuleValidationResult> validateRule(@Valid @RequestBody RuleRequest request) {
        return ApiResponse.ok(watchRulePreflightService.validate(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<WatchRule> updateRule(@PathVariable Long id, @Valid @RequestBody RuleRequest request) {
        WatchRule rule = watchRuleRepository.findById(Objects.requireNonNull(id))
                .orElse(null);
        if (rule == null) {
            return ApiResponse.fail("Rule not found: " + id);
        }
        buildRule(rule, request);
        WatchRule saved = watchRuleRepository.save(Objects.requireNonNull(rule));
        log.info("WatchRule updated: id={}, name={}", saved.getId(), saved.getName());
        fileDiscoveryService.reload();
        return ApiResponse.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        watchRuleRepository.deleteById(Objects.requireNonNull(id));
        log.info("WatchRule deleted: id={}", id);
        fileDiscoveryService.reload();
        return ApiResponse.ok();
    }

    @PatchMapping("/{id}/toggle")
    public ApiResponse<WatchRule> toggleRule(@PathVariable Long id) {
        WatchRule rule = watchRuleRepository.findById(Objects.requireNonNull(id)).orElse(null);
        if (rule == null) {
            return ApiResponse.fail("Rule not found: " + id);
        }
        rule.setEnabled(!rule.getEnabled());
        WatchRule saved = watchRuleRepository.save(rule);
        log.info("WatchRule toggled: id={}, enabled={}", id, saved.getEnabled());
        fileDiscoveryService.reload();
        return ApiResponse.ok(saved);
    }

    @PostMapping("/{id}/scan")
    public ApiResponse<Void> scanRule(@PathVariable Long id) {
        WatchRule rule = watchRuleRepository.findById(Objects.requireNonNull(id)).orElse(null);
        if (rule == null) {
            return ApiResponse.fail("Rule not found: " + id);
        }

        try {
            fileDiscoveryService.triggerFullScan(rule);
            log.info("WatchRule full scan requested: id={}, sourceDir={}", id, rule.getSourceDir());
            return ApiResponse.ok();
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("WatchRule full scan rejected: id={}, error={}", id, e.getMessage());
            return ApiResponse.fail(e.getMessage());
        }
    }

    private WatchRule buildRule(WatchRule rule, RuleRequest req) {
        rule.setName(req.getName());
        rule.setSourceDir(req.getSourceDir());
        rule.setTargetDir(req.getTargetDir());
        rule.setMediaType(req.getMediaType());
        rule.setMoviePathTemplate(req.getMoviePathTemplate());
        rule.setTvPathTemplate(req.getTvPathTemplate());
        rule.setOperation(req.getOperation());
        rule.setEnabled(req.getEnabled() != null ? req.getEnabled() : true);
        rule.setMoveAssociatedFiles(req.getMoveAssociatedFiles() != null ? req.getMoveAssociatedFiles() : true);
        rule.setCleanupEmptyDirs(req.getCleanupEmptyDirs() != null ? req.getCleanupEmptyDirs() : false);
        rule.setGenerateNfo(req.getGenerateNfo() != null ? req.getGenerateNfo() : false);
        rule.setIgnoredFilePatterns(req.getIgnoredFilePatterns());
        rule.setDiscoveryMode(req.getDiscoveryMode() != null ? req.getDiscoveryMode() : WatchRule.DiscoveryMode.HYBRID);
        rule.setScanIntervalMinutes(normalizeScanIntervalMinutes(req.getScanIntervalMinutes()));
        rule.setWebhookEnabled(req.getWebhookEnabled() != null ? req.getWebhookEnabled() : false);
        return rule;
    }

    private int normalizeScanIntervalMinutes(Integer value) {
        return Math.max(value != null ? value : 10, 5);
    }

    @Data
    public static class RuleRequest {
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
        private com.mediamarshal.service.rename.FileOperationStrategy.OperationType operation;

        private Boolean enabled;

        private Boolean moveAssociatedFiles;

        private Boolean cleanupEmptyDirs;

        private Boolean generateNfo;

        private List<String> ignoredFilePatterns;

        private WatchRule.DiscoveryMode discoveryMode;

        private Integer scanIntervalMinutes;

        private Boolean webhookEnabled;
    }
}
