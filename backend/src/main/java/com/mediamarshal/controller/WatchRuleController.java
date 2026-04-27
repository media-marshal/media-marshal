package com.mediamarshal.controller;

import com.mediamarshal.model.dto.ApiResponse;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.watcher.FileWatcherService;
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
 * 规则变更后自动触发 FileWatcherService 重新加载监控目录。
 */
@Slf4j
@RestController
@RequestMapping("/api/watch-rules")
@RequiredArgsConstructor
public class WatchRuleController {

    private final WatchRuleRepository watchRuleRepository;
    private final FileWatcherService fileWatcherService;

    @GetMapping
    public ApiResponse<List<WatchRule>> listRules() {
        return ApiResponse.ok(watchRuleRepository.findAll());
    }

    @PostMapping
    public ApiResponse<WatchRule> createRule(@Valid @RequestBody RuleRequest request) {
        WatchRule rule = buildRule(new WatchRule(), request);
        WatchRule saved = watchRuleRepository.save(Objects.requireNonNull(rule));
        log.info("WatchRule created: id={}, name={}, sourceDir={}", saved.getId(), saved.getName(), saved.getSourceDir());
        fileWatcherService.reload();
        return ApiResponse.ok(saved);
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
        fileWatcherService.reload();
        return ApiResponse.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        watchRuleRepository.deleteById(Objects.requireNonNull(id));
        log.info("WatchRule deleted: id={}", id);
        fileWatcherService.reload();
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
        fileWatcherService.reload();
        return ApiResponse.ok(saved);
    }

    @PostMapping("/{id}/scan")
    public ApiResponse<Void> scanRule(@PathVariable Long id) {
        WatchRule rule = watchRuleRepository.findById(Objects.requireNonNull(id)).orElse(null);
        if (rule == null) {
            return ApiResponse.fail("Rule not found: " + id);
        }

        try {
            fileWatcherService.triggerFullScan(rule);
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
        return rule;
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
    }
}
