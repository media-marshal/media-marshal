package com.mediamarshal.service.watchrule;

import com.mediamarshal.controller.WatchRuleController;
import com.mediamarshal.model.dto.WatchRuleValidationResult;
import com.mediamarshal.service.rename.FileOperationStrategy;
import com.mediamarshal.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * WatchRule 保存前校验。
 *
 * 校验会真实创建临时文件 / 链接来确认目标目录和链接能力，避免保存后才发现策略不可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchRulePreflightService {

    private final SettingsService settingsService;

    public WatchRuleValidationResult validate(WatchRuleController.RuleRequest request) {
        if (!isPreflightEnabled()) {
            log.warn("WatchRule preflight validation is disabled by configuration");
            return WatchRuleValidationResult.ok();
        }

        List<String> details = new ArrayList<>();
        Path sourceDir = Paths.get(request.getSourceDir()).toAbsolutePath().normalize();
        Path targetDir = Paths.get(request.getTargetDir()).toAbsolutePath().normalize();
        FileOperationStrategy.OperationType operation = request.getOperation();

        validateSourceDirectory(sourceDir, operation, details);
        validateTargetDirectory(targetDir, details);

        if (details.isEmpty() && operation == FileOperationStrategy.OperationType.HARD_LINK) {
            validateHardLink(sourceDir, targetDir, details);
        }
        if (details.isEmpty() && operation == FileOperationStrategy.OperationType.SYMBOLIC_LINK) {
            validateSymbolicLink(sourceDir, targetDir, details);
        }

        if (details.isEmpty()) {
            return WatchRuleValidationResult.ok();
        }
        return WatchRuleValidationResult.fail("WatchRule 校验失败", details);
    }

    private boolean isPreflightEnabled() {
        return Boolean.parseBoolean(settingsService.get("watch-rule.preflight.enabled", "true"));
    }

    private void validateSourceDirectory(Path sourceDir, FileOperationStrategy.OperationType operation, List<String> details) {
        if (!Files.isDirectory(sourceDir)) {
            details.add("源目录不存在或不是目录: " + sourceDir);
            return;
        }
        if (!canReadDirectory(sourceDir)) {
            details.add("源目录不可读: " + sourceDir);
        }
        if (operation == FileOperationStrategy.OperationType.MOVE && !canWriteProbe(sourceDir, "source-write-test")) {
            details.add("移动模式要求源目录可写: " + sourceDir);
        }
    }

    private void validateTargetDirectory(Path targetDir, List<String> details) {
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            details.add("目标目录无法创建: " + targetDir + "，原因: " + e.getMessage());
            return;
        }
        if (!Files.isDirectory(targetDir)) {
            details.add("目标路径不是目录: " + targetDir);
            return;
        }

        if (!canWriteProbe(targetDir, "target-write-test")) {
            details.add("目标目录不可写: " + targetDir);
        }
    }

    private boolean canReadDirectory(Path dir) {
        try (var ignored = Files.list(dir)) {
            return true;
        } catch (IOException | SecurityException e) {
            log.debug("Directory read probe failed: dir={}, error={}", dir, e.getMessage());
            return false;
        }
    }

    private boolean canWriteProbe(Path dir, String probeName) {
        Path probe = dir.resolve(".media-marshal-" + probeName + "-" + UUID.randomUUID());
        try {
            Files.writeString(probe, "ok");
            return true;
        } catch (IOException e) {
            log.debug("Directory write probe failed: dir={}, error={}", dir, e.getMessage());
            return false;
        } finally {
            deleteIfExists(probe);
        }
    }

    private void validateHardLink(Path sourceDir, Path targetDir, List<String> details) {
        Path sourceProbe = sourceDir.resolve(".media-marshal-hardlink-source-" + UUID.randomUUID());
        Path targetProbe = targetDir.resolve(".media-marshal-hardlink-target-" + UUID.randomUUID());
        try {
            Files.writeString(sourceProbe, "hard-link-test");
            Files.createLink(targetProbe, sourceProbe);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            details.add("硬链接校验失败，请确认源目录和目标目录位于同一文件系统且支持硬链接。原因: " + e.getMessage());
        } finally {
            deleteIfExists(targetProbe);
            deleteIfExists(sourceProbe);
        }
    }

    private void validateSymbolicLink(Path sourceDir, Path targetDir, List<String> details) {
        Path sourceProbe = sourceDir.resolve(".media-marshal-symlink-source-" + UUID.randomUUID());
        Path targetProbe = targetDir.resolve(".media-marshal-symlink-target-" + UUID.randomUUID());
        try {
            Files.writeString(sourceProbe, "symbolic-link-test");
            Files.createSymbolicLink(targetProbe, sourceProbe.toAbsolutePath().normalize());
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            details.add("符号链接校验失败，请确认系统或容器权限允许创建符号链接。原因: " + e.getMessage());
        } finally {
            deleteIfExists(targetProbe);
            deleteIfExists(sourceProbe);
        }
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to clean preflight temp path: path={}, error={}", path, e.getMessage());
        }
    }
}
