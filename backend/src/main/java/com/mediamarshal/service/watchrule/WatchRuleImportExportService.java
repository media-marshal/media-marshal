package com.mediamarshal.service.watchrule;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediamarshal.model.dto.WatchRuleImportPackage;
import com.mediamarshal.model.dto.WatchRuleImportPreview;
import com.mediamarshal.model.dto.WatchRuleImportPreviewItem;
import com.mediamarshal.model.dto.WatchRuleImportPreviewItem.Status;
import com.mediamarshal.model.dto.WatchRuleImportPreviewRequest;
import com.mediamarshal.model.dto.WatchRuleImportRequest;
import com.mediamarshal.model.dto.WatchRuleImportResult;
import com.mediamarshal.model.dto.WatchRuleImportRule;
import com.mediamarshal.model.dto.WatchRuleImportSummary;
import com.mediamarshal.model.dto.WatchRuleRequest;
import com.mediamarshal.model.dto.WatchRuleValidationResult;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.AppVersionProvider;
import com.mediamarshal.service.rename.FileOperationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * ADR-026 WatchRule 导入导出。
 *
 * 该服务只处理监控路径规则，不导入系统配置、任务、候选或缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchRuleImportExportService {

    public static final String PACKAGE_KIND = "media-marshal.watch-rules";
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final WatchRuleRepository watchRuleRepository;
    private final WatchRulePreflightService watchRulePreflightService;
    private final AppVersionProvider appVersionProvider;

    public WatchRuleImportPackage exportRules() {
        List<WatchRuleImportRule> rules = watchRuleRepository.findAll().stream()
                .sorted(Comparator.comparing(WatchRule::getId, Comparator.nullsLast(Long::compareTo)))
                .map(this::toImportRule)
                .toList();

        return WatchRuleImportPackage.builder()
                .kind(PACKAGE_KIND)
                .schemaVersion(SUPPORTED_SCHEMA_VERSION)
                .appVersion(appVersionProvider.getVersion())
                .exportedAt(OffsetDateTime.now().toString())
                .rules(rules)
                .build();
    }

    public WatchRuleImportPreview preview(WatchRuleImportPreviewRequest request) {
        boolean preserveEnabledState = request != null && request.shouldPreserveEnabledState();
        ParsedPackage parsedPackage = parsePackage(request != null ? request.getImportPackage() : null);
        if (!parsedPackage.valid()) {
            return fileInvalidPreview(parsedPackage, preserveEnabledState);
        }

        List<RuleDescriptor> existingRules = watchRuleRepository.findAll().stream()
                .map(rule -> new RuleDescriptor(
                        rule.getName(),
                        normalizePath(rule.getSourceDir()),
                        normalizePath(rule.getTargetDir()),
                        fingerprint(rule),
                        Boolean.TRUE.equals(rule.getEnabled())
                ))
                .toList();

        Set<String> internallyConflictingSources = findInternallyConflictingSources(parsedPackage.rules());
        Set<String> firstImportFingerprints = new HashSet<>();

        List<WatchRuleImportPreviewItem> items = new ArrayList<>();
        for (ParsedRule parsedRule : parsedPackage.rules()) {
            items.add(previewRule(
                    parsedRule,
                    existingRules,
                    parsedPackage.rules(),
                    internallyConflictingSources,
                    firstImportFingerprints,
                    preserveEnabledState
            ));
        }

        WatchRuleImportSummary summary = summarize(items);
        return WatchRuleImportPreview.builder()
                .kind(parsedPackage.kind())
                .schemaVersion(parsedPackage.schemaVersion())
                .appVersion(parsedPackage.appVersion())
                .exportedAt(parsedPackage.exportedAt())
                .ruleCount(parsedPackage.ruleCount())
                .fileValid(true)
                .hasBlockingIssues(summary.getConflicts() > 0 || summary.getInvalid() > 0)
                .preserveEnabledState(preserveEnabledState)
                .summary(summary)
                .items(items)
                .build();
    }

    @Transactional
    public WatchRuleImportResult importRules(WatchRuleImportRequest request) {
        WatchRuleImportPreview preview = preview(request);
        if (preview.isHasBlockingIssues()) {
            throw new IllegalArgumentException("导入文件存在冲突或无效规则，请先处理预览结果");
        }

        boolean preserveEnabledState = request != null && request.shouldPreserveEnabledState();
        List<WatchRule> createdRules = new ArrayList<>();
        for (WatchRuleImportPreviewItem item : preview.getItems()) {
            if (item.getStatus() != Status.READY && item.getStatus() != Status.WARNING) {
                continue;
            }

            WatchRuleRequest ruleRequest = toWatchRuleRequest(item.getRule());
            boolean enabled = preserveEnabledState && Boolean.TRUE.equals(item.getRule().getEnabled());
            if (enabled) {
                WatchRuleValidationResult validation = watchRulePreflightService.validate(ruleRequest);
                if (!validation.valid()) {
                    throw new IllegalStateException("启用规则预检失败: " + String.join("；", validation.details()));
                }
            }

            WatchRule rule = new WatchRule();
            apply(rule, ruleRequest, enabled);
            createdRules.add(watchRuleRepository.save(rule));
        }

        WatchRuleImportSummary summary = preview.getSummary();
        return WatchRuleImportResult.builder()
                .preserveEnabledState(preserveEnabledState)
                .reloadTriggered(false)
                .createdCount(createdRules.size())
                .skippedCount(summary.getSkipped())
                .conflictCount(summary.getConflicts())
                .invalidCount(summary.getInvalid())
                .warningCount(summary.getWarnings())
                .createdRules(createdRules)
                .items(preview.getItems())
                .build();
    }

    private WatchRuleImportPreviewItem previewRule(
            ParsedRule parsedRule,
            List<RuleDescriptor> existingRules,
            List<ParsedRule> importedRules,
            Set<String> internallyConflictingSources,
            Set<String> firstImportFingerprints,
            boolean preserveEnabledState
    ) {
        if (!parsedRule.valid()) {
            return WatchRuleImportPreviewItem.builder()
                    .index(parsedRule.index())
                    .status(Status.INVALID)
                    .message(String.join("；", parsedRule.errors()))
                    .rule(parsedRule.rule())
                    .build();
        }

        WatchRuleImportRule rule = parsedRule.rule();
        if (internallyConflictingSources.contains(parsedRule.normalizedSourceDir())) {
            return WatchRuleImportPreviewItem.builder()
                    .index(parsedRule.index())
                    .status(Status.CONFLICT)
                    .message("导入文件中存在相同源目录但整理行为不同的规则")
                    .rule(rule)
                    .build();
        }

        if (!firstImportFingerprints.add(parsedRule.fingerprint())) {
            return WatchRuleImportPreviewItem.builder()
                    .index(parsedRule.index())
                    .status(Status.SKIPPED_DUPLICATE)
                    .message("导入文件中已有相同规则，将跳过")
                    .rule(rule)
                    .build();
        }

        if (existingRules.stream().anyMatch(existing -> existing.fingerprint().equals(parsedRule.fingerprint()))) {
            return WatchRuleImportPreviewItem.builder()
                    .index(parsedRule.index())
                    .status(Status.SKIPPED_DUPLICATE)
                    .message("已有相同规则，将跳过")
                    .rule(rule)
                    .build();
        }

        if (existingRules.stream().anyMatch(existing ->
                existing.normalizedSourceDir().equals(parsedRule.normalizedSourceDir())
                        && !existing.fingerprint().equals(parsedRule.fingerprint()))) {
            return WatchRuleImportPreviewItem.builder()
                    .index(parsedRule.index())
                    .status(Status.CONFLICT)
                    .message("源目录与现有规则相同，但整理行为不同")
                    .rule(rule)
                    .build();
        }

        if (preserveEnabledState && Boolean.TRUE.equals(rule.getEnabled())
                && hasEnabledParentChildConflict(parsedRule, existingRules, importedRules)) {
            return WatchRuleImportPreviewItem.builder()
                    .index(parsedRule.index())
                    .status(Status.CONFLICT)
                    .message("保持启用状态后，源目录会与其它启用规则形成父子包含关系")
                    .rule(rule)
                    .build();
        }

        List<String> warnings = collectWarnings(parsedRule, existingRules, preserveEnabledState);
        Status status = warnings.isEmpty() ? Status.READY : Status.WARNING;
        return WatchRuleImportPreviewItem.builder()
                .index(parsedRule.index())
                .status(status)
                .message(status == Status.READY ? "可导入" : "可导入，但存在需要确认的风险")
                .warnings(warnings)
                .rule(rule)
                .build();
    }

    private List<String> collectWarnings(
            ParsedRule parsedRule,
            List<RuleDescriptor> existingRules,
            boolean preserveEnabledState
    ) {
        List<String> warnings = new ArrayList<>();
        WatchRuleImportRule rule = parsedRule.rule();
        boolean sameName = existingRules.stream().anyMatch(existing -> Objects.equals(existing.name(), rule.getName()));
        if (sameName) {
            warnings.add("规则名称与现有规则重复");
        }
        boolean sameTarget = existingRules.stream().anyMatch(existing -> existing.normalizedTargetDir().equals(parsedRule.normalizedTargetDir()));
        if (sameTarget) {
            warnings.add("目标目录与现有规则相同");
        }
        boolean parentChild = existingRules.stream().anyMatch(existing -> isParentChild(existing.normalizedSourceDir(), parsedRule.normalizedSourceDir()));
        if (parentChild) {
            warnings.add("源目录与现有规则存在父子目录包含关系");
        }
        warnings.addAll(collectPathAvailabilityWarnings(rule, preserveEnabledState));
        return warnings;
    }

    private List<String> collectPathAvailabilityWarnings(WatchRuleImportRule rule, boolean preserveEnabledState) {
        List<String> warnings = new ArrayList<>();
        if (preserveEnabledState && Boolean.TRUE.equals(rule.getEnabled())) {
            warnings.add("导入确认时会执行完整路径和文件操作预检");
            return warnings;
        }

        Path sourceDir = toPath(rule.getSourceDir());
        if (sourceDir == null) {
            warnings.add("源目录路径格式无法在当前系统识别");
        } else if (!Files.isDirectory(sourceDir)) {
            warnings.add("源目录当前不可用，导入后请检查挂载或路径");
        } else if (!Files.isReadable(sourceDir)) {
            warnings.add("源目录当前不可读，导入后请检查权限");
        }

        Path targetDir = toPath(rule.getTargetDir());
        if (targetDir == null) {
            warnings.add("目标目录路径格式无法在当前系统识别");
        } else if (!Files.exists(targetDir)) {
            warnings.add("目标目录当前不存在，启用规则前请确认路径");
        } else if (!Files.isDirectory(targetDir)) {
            warnings.add("目标路径当前不是目录");
        } else if (!Files.isWritable(targetDir)) {
            warnings.add("目标目录当前不可写，导入后请检查权限");
        }
        return warnings;
    }

    private boolean hasEnabledParentChildConflict(
            ParsedRule parsedRule,
            List<RuleDescriptor> existingRules,
            List<ParsedRule> importedRules
    ) {
        boolean conflictsWithExisting = existingRules.stream()
                .anyMatch(existing -> existing.enabled()
                        && isParentChild(existing.normalizedSourceDir(), parsedRule.normalizedSourceDir()));
        if (conflictsWithExisting) {
            return true;
        }

        return importedRules.stream()
                .filter(ParsedRule::valid)
                .filter(other -> other.index() != parsedRule.index())
                .filter(other -> Boolean.TRUE.equals(other.rule().getEnabled()))
                .anyMatch(other -> isParentChild(other.normalizedSourceDir(), parsedRule.normalizedSourceDir()));
    }

    private Set<String> findInternallyConflictingSources(List<ParsedRule> parsedRules) {
        Map<String, Set<String>> fingerprintsBySource = new LinkedHashMap<>();
        for (ParsedRule parsedRule : parsedRules) {
            if (!parsedRule.valid()) {
                continue;
            }
            fingerprintsBySource
                    .computeIfAbsent(parsedRule.normalizedSourceDir(), ignored -> new HashSet<>())
                    .add(parsedRule.fingerprint());
        }

        Set<String> conflicts = new HashSet<>();
        fingerprintsBySource.forEach((sourceDir, fingerprints) -> {
            if (fingerprints.size() > 1) {
                conflicts.add(sourceDir);
            }
        });
        return conflicts;
    }

    private WatchRuleImportSummary summarize(List<WatchRuleImportPreviewItem> items) {
        int ready = 0;
        int skipped = 0;
        int conflicts = 0;
        int invalid = 0;
        int warnings = 0;
        for (WatchRuleImportPreviewItem item : items) {
            switch (item.getStatus()) {
                case READY -> ready++;
                case SKIPPED_DUPLICATE -> skipped++;
                case CONFLICT -> conflicts++;
                case INVALID -> invalid++;
                case WARNING -> warnings++;
            }
        }

        return WatchRuleImportSummary.builder()
                .ready(ready)
                .warnings(warnings)
                .importable(ready + warnings)
                .skipped(skipped)
                .conflicts(conflicts)
                .invalid(invalid)
                .build();
    }

    private WatchRuleImportPreview fileInvalidPreview(ParsedPackage parsedPackage, boolean preserveEnabledState) {
        WatchRuleImportSummary summary = WatchRuleImportSummary.builder()
                .invalid(1)
                .build();
        return WatchRuleImportPreview.builder()
                .kind(parsedPackage.kind())
                .schemaVersion(parsedPackage.schemaVersion())
                .appVersion(parsedPackage.appVersion())
                .exportedAt(parsedPackage.exportedAt())
                .ruleCount(parsedPackage.ruleCount())
                .fileValid(false)
                .hasBlockingIssues(true)
                .fileMessage(parsedPackage.message())
                .preserveEnabledState(preserveEnabledState)
                .summary(summary)
                .items(List.of())
                .build();
    }

    private ParsedPackage parsePackage(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return ParsedPackage.invalid("导入内容缺少 package 对象");
        }
        if (!root.isObject()) {
            return ParsedPackage.invalid("package 必须是对象");
        }

        String kind = readOptionalText(root, "kind");
        Integer schemaVersion = readOptionalInteger(root, "schemaVersion");
        String appVersion = readOptionalText(root, "appVersion");
        String exportedAt = readOptionalText(root, "exportedAt");

        if (!PACKAGE_KIND.equals(kind)) {
            return ParsedPackage.invalid(kind, schemaVersion, appVersion, exportedAt, "文件类型不是 Media Marshal 监控路径配置包");
        }
        if (schemaVersion == null) {
            return ParsedPackage.invalid(kind, schemaVersion, appVersion, exportedAt, "缺少或无法识别 schemaVersion");
        }
        if (schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            return ParsedPackage.invalid(kind, schemaVersion, appVersion, exportedAt, "导入文件版本高于当前支持版本，请升级 Media Marshal 或重新导出兼容文件");
        }

        JsonNode rulesNode = root.get("rules");
        if (rulesNode == null || !rulesNode.isArray()) {
            return ParsedPackage.invalid(kind, schemaVersion, appVersion, exportedAt, "rules 必须是数组");
        }

        List<ParsedRule> rules = new ArrayList<>();
        int index = 0;
        for (JsonNode ruleNode : rulesNode) {
            rules.add(parseRule(index, ruleNode));
            index++;
        }

        return new ParsedPackage(true, null, kind, schemaVersion, appVersion, exportedAt, rules.size(), rules);
    }

    private ParsedRule parseRule(int index, JsonNode ruleNode) {
        List<String> errors = new ArrayList<>();
        if (ruleNode == null || !ruleNode.isObject()) {
            errors.add("规则必须是对象");
            return ParsedRule.invalid(index, null, errors);
        }

        WatchRuleImportRule rule = WatchRuleImportRule.builder()
                .name(readRequiredText(ruleNode, "name", errors))
                .sourceDir(readRequiredText(ruleNode, "sourceDir", errors))
                .targetDir(readRequiredText(ruleNode, "targetDir", errors))
                .mediaType(readRequiredText(ruleNode, "mediaType", errors))
                .moviePathTemplate(readNullableText(ruleNode, "moviePathTemplate", errors))
                .tvPathTemplate(readNullableText(ruleNode, "tvPathTemplate", errors))
                .operation(readRequiredText(ruleNode, "operation", errors))
                .enabled(readRequiredBoolean(ruleNode, "enabled", errors))
                .moveAssociatedFiles(readRequiredBoolean(ruleNode, "moveAssociatedFiles", errors))
                .cleanupEmptyDirs(readRequiredBoolean(ruleNode, "cleanupEmptyDirs", errors))
                .generateNfo(readRequiredBoolean(ruleNode, "generateNfo", errors))
                .ignoredFilePatterns(readNullableStringList(ruleNode, "ignoredFilePatterns", errors))
                .discoveryMode(readRequiredText(ruleNode, "discoveryMode", errors))
                .scanIntervalMinutes(readRequiredInteger(ruleNode, "scanIntervalMinutes", errors))
                .webhookEnabled(readRequiredBoolean(ruleNode, "webhookEnabled", errors))
                .build();

        WatchRuleRequest request = null;
        if (errors.isEmpty()) {
            request = toWatchRuleRequest(rule, errors);
        }
        if (!errors.isEmpty()) {
            return ParsedRule.invalid(index, rule, errors);
        }

        String fingerprint = fingerprint(rule);
        return new ParsedRule(
                index,
                true,
                rule,
                request,
                List.of(),
                normalizePath(rule.getSourceDir()),
                normalizePath(rule.getTargetDir()),
                fingerprint
        );
    }

    private WatchRuleRequest toWatchRuleRequest(WatchRuleImportRule rule) {
        return toWatchRuleRequest(rule, new ArrayList<>());
    }

    private WatchRuleRequest toWatchRuleRequest(WatchRuleImportRule rule, List<String> errors) {
        WatchRuleRequest request = new WatchRuleRequest();
        request.setName(trim(rule.getName()));
        request.setSourceDir(trim(rule.getSourceDir()));
        request.setTargetDir(trim(rule.getTargetDir()));
        request.setMediaType(parseEnum(WatchRule.RuleMediaType.class, rule.getMediaType(), "mediaType", errors));
        request.setMoviePathTemplate(rule.getMoviePathTemplate());
        request.setTvPathTemplate(rule.getTvPathTemplate());
        request.setOperation(parseEnum(FileOperationStrategy.OperationType.class, rule.getOperation(), "operation", errors));
        request.setEnabled(rule.getEnabled());
        request.setMoveAssociatedFiles(rule.getMoveAssociatedFiles());
        request.setCleanupEmptyDirs(rule.getCleanupEmptyDirs());
        request.setGenerateNfo(rule.getGenerateNfo());
        request.setIgnoredFilePatterns(rule.getIgnoredFilePatterns());
        request.setDiscoveryMode(parseEnum(WatchRule.DiscoveryMode.class, rule.getDiscoveryMode(), "discoveryMode", errors));
        request.setScanIntervalMinutes(rule.getScanIntervalMinutes());
        request.setWebhookEnabled(rule.getWebhookEnabled());
        return request;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, String field, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(field + " 不能为空");
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(field + " 不支持当前值: " + value);
            return null;
        }
    }

    private WatchRuleImportRule toImportRule(WatchRule rule) {
        return WatchRuleImportRule.builder()
                .name(rule.getName())
                .sourceDir(rule.getSourceDir())
                .targetDir(rule.getTargetDir())
                .mediaType(rule.getMediaType().name())
                .moviePathTemplate(rule.getMoviePathTemplate())
                .tvPathTemplate(rule.getTvPathTemplate())
                .operation(rule.getOperation().name())
                .enabled(rule.getEnabled())
                .moveAssociatedFiles(rule.getMoveAssociatedFiles())
                .cleanupEmptyDirs(rule.getCleanupEmptyDirs())
                .generateNfo(rule.getGenerateNfo())
                .ignoredFilePatterns(rule.getIgnoredFilePatterns())
                .discoveryMode(rule.getDiscoveryMode().name())
                .scanIntervalMinutes(rule.getScanIntervalMinutes())
                .webhookEnabled(rule.getWebhookEnabled())
                .build();
    }

    private void apply(WatchRule rule, WatchRuleRequest req, boolean enabled) {
        rule.setName(req.getName());
        rule.setSourceDir(req.getSourceDir());
        rule.setTargetDir(req.getTargetDir());
        rule.setMediaType(req.getMediaType());
        rule.setMoviePathTemplate(req.getMoviePathTemplate());
        rule.setTvPathTemplate(req.getTvPathTemplate());
        rule.setOperation(req.getOperation());
        rule.setEnabled(enabled);
        rule.setMoveAssociatedFiles(Boolean.TRUE.equals(req.getMoveAssociatedFiles()));
        rule.setCleanupEmptyDirs(Boolean.TRUE.equals(req.getCleanupEmptyDirs()));
        rule.setGenerateNfo(Boolean.TRUE.equals(req.getGenerateNfo()));
        rule.setIgnoredFilePatterns(req.getIgnoredFilePatterns());
        rule.setDiscoveryMode(req.getDiscoveryMode() != null ? req.getDiscoveryMode() : WatchRule.DiscoveryMode.HYBRID);
        rule.setScanIntervalMinutes(Math.max(req.getScanIntervalMinutes() != null ? req.getScanIntervalMinutes() : 10, 5));
        rule.setWebhookEnabled(Boolean.TRUE.equals(req.getWebhookEnabled()));
    }

    private String fingerprint(WatchRule rule) {
        return String.join("\u001f",
                normalizePath(rule.getSourceDir()),
                normalizePath(rule.getTargetDir()),
                enumName(rule.getMediaType()),
                nullable(rule.getMoviePathTemplate()),
                nullable(rule.getTvPathTemplate()),
                enumName(rule.getOperation()),
                bool(rule.getMoveAssociatedFiles()),
                bool(rule.getCleanupEmptyDirs()),
                bool(rule.getGenerateNfo()),
                normalizeIgnoredPatterns(rule.getIgnoredFilePatterns()),
                enumName(rule.getDiscoveryMode()),
                String.valueOf(rule.getScanIntervalMinutes()),
                bool(rule.getWebhookEnabled())
        );
    }

    private String fingerprint(WatchRuleImportRule rule) {
        return String.join("\u001f",
                normalizePath(rule.getSourceDir()),
                normalizePath(rule.getTargetDir()),
                normalizeEnumString(rule.getMediaType()),
                nullable(rule.getMoviePathTemplate()),
                nullable(rule.getTvPathTemplate()),
                normalizeEnumString(rule.getOperation()),
                bool(rule.getMoveAssociatedFiles()),
                bool(rule.getCleanupEmptyDirs()),
                bool(rule.getGenerateNfo()),
                normalizeIgnoredPatterns(rule.getIgnoredFilePatterns()),
                normalizeEnumString(rule.getDiscoveryMode()),
                String.valueOf(rule.getScanIntervalMinutes()),
                bool(rule.getWebhookEnabled())
        );
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.length() > 1 && normalized.endsWith("/") && !isWindowsRoot(normalized)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isWindowsRoot(String path) {
        return path.matches("^[A-Za-z]:/$");
    }

    private boolean isParentChild(String first, String second) {
        if (first == null || second == null || first.equals(second)) {
            return false;
        }
        String a = normalizePath(first);
        String b = normalizePath(second);
        return b.startsWith(a + "/") || a.startsWith(b + "/");
    }

    private String normalizeIgnoredPatterns(List<String> patterns) {
        if (patterns == null) {
            return "<DEFAULT>";
        }
        if (patterns.isEmpty()) {
            return "<EMPTY>";
        }
        return patterns.stream()
                .map(pattern -> pattern == null ? "" : pattern.replace("\r", "").replace("\n", "").strip())
                .filter(pattern -> !pattern.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("<EMPTY>");
    }

    private Path toPath(String path) {
        try {
            return Paths.get(path).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private String readRequiredText(JsonNode node, String field, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            errors.add(field + " 缺失或不是有效字符串");
            return null;
        }
        return value.asText().trim();
    }

    private String readNullableText(JsonNode node, String field, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            errors.add(field + " 必须是字符串或 null");
            return null;
        }
        return value.asText();
    }

    private Boolean readRequiredBoolean(JsonNode node, String field, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            errors.add(field + " 缺失或不是布尔值");
            return null;
        }
        return value.asBoolean();
    }

    private Integer readRequiredInteger(JsonNode node, String field, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || !value.isInt()) {
            errors.add(field + " 缺失或不是整数");
            return null;
        }
        return value.asInt();
    }

    private List<String> readNullableStringList(JsonNode node, String field, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isArray()) {
            errors.add(field + " 必须是字符串数组或 null");
            return null;
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                errors.add(field + " 只能包含字符串");
                return null;
            }
            result.add(item.asText());
        }
        return result;
    }

    private String readOptionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private Integer readOptionalInteger(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isInt() ? value.asInt() : null;
    }

    private String enumName(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private String bool(Boolean value) {
        return Boolean.TRUE.equals(value) ? "true" : "false";
    }

    private String nullable(String value) {
        return value == null ? "<NULL>" : value;
    }

    private String normalizeEnumString(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record RuleDescriptor(
            String name,
            String normalizedSourceDir,
            String normalizedTargetDir,
            String fingerprint,
            boolean enabled
    ) {
    }

    private record ParsedPackage(
            boolean valid,
            String message,
            String kind,
            Integer schemaVersion,
            String appVersion,
            String exportedAt,
            int ruleCount,
            List<ParsedRule> rules
    ) {
        private static ParsedPackage invalid(String message) {
            return invalid(null, null, null, null, message);
        }

        private static ParsedPackage invalid(
                String kind,
                Integer schemaVersion,
                String appVersion,
                String exportedAt,
                String message
        ) {
            return new ParsedPackage(false, message, kind, schemaVersion, appVersion, exportedAt, 0, List.of());
        }
    }

    private record ParsedRule(
            int index,
            boolean valid,
            WatchRuleImportRule rule,
            WatchRuleRequest request,
            List<String> errors,
            String normalizedSourceDir,
            String normalizedTargetDir,
            String fingerprint
    ) {
        private static ParsedRule invalid(int index, WatchRuleImportRule rule, List<String> errors) {
            return new ParsedRule(index, false, rule, null, errors, "", "", "");
        }
    }
}
