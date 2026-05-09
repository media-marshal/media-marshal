package com.mediamarshal.service.rename;

import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.model.exception.MediaTaskFailureException;
import com.mediamarshal.repository.WatchRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class BluRayDirectoryAssetOrganizer implements AssetOrganizerStrategy {

    private static final String BLURAY_FALLBACK_TEMPLATE = "{title} ({year})";

    private final RenameService renameService;
    private final WatchRuleRepository watchRuleRepository;
    private final TemplateRenderer templateRenderer;
    private final Map<String, FileOperationStrategy> fileOperationStrategies;

    @Override
    public boolean supports(MediaAssetType assetType) {
        return MediaAssetType.BLURAY_DIRECTORY.equals(assetType);
    }

    @Override
    public Path organize(MediaTask task) throws IOException {
        WatchRule rule = loadRule(task);
        Path sourceRoot = Paths.get(task.getSourcePath()).toAbsolutePath().normalize();
        Path targetRoot = buildTargetRoot(task, rule);

        log.info("Blu-ray organize plan: operation={}, source='{}' -> target='{}'",
                rule.getOperation(), sourceRoot, targetRoot);

        if (Files.exists(targetRoot)) {
            throw new MediaTaskFailureException(
                    MediaTask.TaskErrorCode.TARGET_CONFLICT,
                    "目标文件已存在，文件冲突"
            );
        }

        switch (rule.getOperation()) {
            case MOVE, SYMBOLIC_LINK -> resolveFileStrategy(rule.getOperation()).execute(sourceRoot, targetRoot);
            case COPY -> copyDirectory(sourceRoot, targetRoot);
            case HARD_LINK -> hardLinkDirectory(sourceRoot, targetRoot);
            default -> throw new IllegalArgumentException("Unknown file operation strategy: " + rule.getOperation());
        }

        log.info("Blu-ray organize completed: {}", targetRoot);
        return targetRoot;
    }

    private Path buildTargetRoot(MediaTask task, WatchRule rule) {
        String relativePath = renameService.renderRelativePath(task, rule, "");
        Path relative = Paths.get(relativePath).normalize();
        Path parent = relative.getParent();
        if (parent == null || parent.toString().isBlank()) {
            String fallback = templateRenderer.render(
                    BLURAY_FALLBACK_TEMPLATE,
                    renameService.buildVariables(task, "")
            );
            return Paths.get(rule.getTargetDir()).resolve(fallback).normalize();
        }
        return Paths.get(rule.getTargetDir()).resolve(parent).normalize();
    }

    private void copyDirectory(Path sourceRoot, Path targetRoot) throws IOException {
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(resolveTarget(sourceRoot, targetRoot, dir));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!Files.isRegularFile(file)) {
                    throw new IOException("蓝光原盘包含非普通文件，无法复制: " + file);
                }
                Files.copy(file, resolveTarget(sourceRoot, targetRoot, file));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void hardLinkDirectory(Path sourceRoot, Path targetRoot) throws IOException {
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(resolveTarget(sourceRoot, targetRoot, dir));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!Files.isRegularFile(file)) {
                    throw new IOException("蓝光原盘包含非普通文件，无法创建硬链接: " + file);
                }
                Files.createLink(resolveTarget(sourceRoot, targetRoot, file), file);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Path resolveTarget(Path sourceRoot, Path targetRoot, Path source) {
        return targetRoot.resolve(sourceRoot.relativize(source)).normalize();
    }

    private FileOperationStrategy resolveFileStrategy(FileOperationStrategy.OperationType operation) {
        return fileOperationStrategies.values().stream()
                .filter(strategy -> strategy.getType() == operation)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown file operation strategy: " + operation));
    }

    private WatchRule loadRule(MediaTask task) {
        Long ruleId = Objects.requireNonNull(task.getRuleId(), "MediaTask.ruleId is required");
        return watchRuleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("WatchRule not found: " + ruleId));
    }
}
