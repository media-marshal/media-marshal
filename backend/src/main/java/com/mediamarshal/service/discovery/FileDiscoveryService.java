package com.mediamarshal.service.discovery;

import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.discovery.asset.MediaAsset;
import com.mediamarshal.service.discovery.asset.MediaAssetDetectionService;
import com.mediamarshal.service.pipeline.MediaProcessPipeline;
import com.mediamarshal.service.settings.SettingsService;
import com.mediamarshal.websocket.EventPublisher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 文件发现服务（ADR-013）。
 *
 * 统一承接实时文件事件、周期补扫和手动全量扫描，并复用同一套路径规范化、
 * 忽略规则、文件分类、数据库查重与 pipeline 投递逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDiscoveryService {

    private static final int MIN_SCAN_INTERVAL_MINUTES = 5;

    private static final List<String> VIDEO_EXTENSIONS = List.of(
            ".mkv", ".mp4", ".avi", ".mov", ".wmv", ".flv", ".ts", ".m2ts", ".rmvb"
    );

    private static final List<String> ASSOCIATED_EXTENSIONS = List.of(
            ".srt", ".ass", ".ssa", ".sub", ".idx", ".nfo", ".jpg", ".jpeg", ".png", ".webp"
    );

    private static final List<String> GENERIC_COVER_NAMES = List.of(
            "poster.jpg", "folder.jpg", "cover.jpg"
    );

    private static final List<String> DEFAULT_IGNORED_PATTERNS = List.of(
            ".DS_Store",
            "Thumbs.db",
            "desktop.ini",
            "*.part",
            "*.tmp",
            "*.crdownload",
            "*.lock",
            "~$*",
            ".*",
            "__MACOSX/",
            "@eaDir/"
    );

    private final WatchRuleRepository watchRuleRepository;
    private final MediaTaskRepository mediaTaskRepository;
    private final MediaProcessPipeline pipeline;
    private final SettingsService settingsService;
    private final EventPublisher eventPublisher;
    private final MediaAssetDetectionService mediaAssetDetectionService;

    private WatchService watchService;
    private volatile boolean running = false;

    /** 被注册目录（规范化绝对路径）→ WatchRule，用于文件事件时查规则。 */
    private final Map<Path, WatchRule> dirRuleMap = new ConcurrentHashMap<>();

    /** 文件绝对路径 → 防抖任务；同路径新事件会取消旧任务并重新计时。 */
    private final Map<String, ScheduledFuture<?>> debounceMap = new ConcurrentHashMap<>();

    /** WatchRule ID → 周期扫描任务；reload 时整体重建。 */
    private final Map<Long, ScheduledFuture<?>> periodicScanFutures = new ConcurrentHashMap<>();

    /** WatchRule ID → 源文件缺失巡检任务；用于弥补删除事件漏发。 */
    private final Map<Long, ScheduledFuture<?>> missingSourceInspectionFutures = new ConcurrentHashMap<>();

    /** 正在扫描的规则集合，确保同一 WatchRule 同一时间只跑一个扫描。 */
    private final Set<String> activeScanKeys = ConcurrentHashMap.newKeySet();

    /** 执行防抖、稳定性检测和周期补扫。 */
    private final ScheduledExecutorService discoveryExecutor = Executors.newScheduledThreadPool(4);

    @PostConstruct
    public void init() {
        reload();
    }

    @PreDestroy
    public void shutdown() throws IOException {
        running = false;
        cancelDebounceTasks();
        cancelPeriodicScans();
        cancelMissingSourceInspections();
        discoveryExecutor.shutdownNow();
        if (watchService != null) {
            watchService.close();
        }
        log.info("FileDiscoveryService stopped");
    }

    /**
     * 热重载发现配置：
     * 1. 重建 WatchService 和目录映射
     * 2. 按 discoveryMode 注册实时监听或调度周期补扫
     * 3. 清理旧防抖任务与旧周期扫描任务
     */
    public synchronized void reload() {
        rebuildWatchService();
        cancelPeriodicScans();
        cancelMissingSourceInspections();

        List<WatchRule> rules = watchRuleRepository.findByEnabledTrue();
        if (rules.isEmpty()) {
            log.warn("No enabled watch rules found. FileDiscovery is idle.");
            return;
        }

        int watchRuleCount = 0;
        int periodicRuleCount = 0;
        for (WatchRule rule : rules) {
            Path root = Paths.get(rule.getSourceDir()).toAbsolutePath().normalize();
            if (usesWatchEvents(rule)) {
                registerDirectoryTree(root, rule);
                watchRuleCount++;
            }
            if (usesPeriodicScan(rule)) {
                schedulePeriodicScan(rule);
                periodicRuleCount++;
            }
            scheduleMissingSourceInspection(rule);
        }

        log.info("FileDiscovery reloaded: {} rules active, {} watch rules, {} periodic rules, {} directories registered",
                rules.size(), watchRuleCount, periodicRuleCount, dirRuleMap.size());

        if (!dirRuleMap.isEmpty()) {
            startWatchLoop();
        }
    }

    /**
     * 手动触发某条规则的历史文件全量扫描。
     *
     * 手动扫描是用户动作，不受规则 discoveryMode 限制。
     */
    public void triggerFullScan(WatchRule rule) {
        Path root = Paths.get(rule.getSourceDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("WatchRule sourceDir is not a directory: " + rule.getSourceDir());
        }
        startScan(rule, root, "manual full scan", true, true);
    }

    private void schedulePeriodicScan(WatchRule rule) {
        int intervalMinutes = normalizeScanIntervalMinutes(rule.getScanIntervalMinutes());
        ScheduledFuture<?> future = discoveryExecutor.scheduleWithFixedDelay(
                () -> runPeriodicScan(rule),
                intervalMinutes,
                intervalMinutes,
                TimeUnit.MINUTES
        );
        periodicScanFutures.put(rule.getId(), future);
        log.info("Periodic scan scheduled: ruleId={}, rule='{}', interval={}m",
                rule.getId(), rule.getName(), intervalMinutes);
    }

    private void scheduleMissingSourceInspection(WatchRule rule) {
        if (rule.getId() == null) {
            return;
        }
        long intervalSeconds = getMissingSourceCheckSeconds();
        ScheduledFuture<?> future = discoveryExecutor.scheduleWithFixedDelay(
                () -> runMissingSourceInspection(rule),
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
        missingSourceInspectionFutures.put(rule.getId(), future);
        log.info("Missing source inspection scheduled: ruleId={}, rule='{}', interval={}s",
                rule.getId(), rule.getName(), intervalSeconds);
    }

    private void runMissingSourceInspection(WatchRule rule) {
        try {
            ScanCounter counter = new ScanCounter();
            failMissingPendingTasks(rule, counter);
            if (counter.missingFailed > 0) {
                log.info("Missing source inspection completed: ruleId={}, missingFailed={}",
                        rule.getId(), counter.missingFailed);
            }
        } catch (Exception e) {
            log.warn("Missing source inspection failed: ruleId={}, rule='{}'",
                    rule.getId(), rule.getName(), e);
        }
    }

    private void runPeriodicScan(WatchRule rule) {
        Path root = Paths.get(rule.getSourceDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            log.warn("Periodic scan skipped because sourceDir is not a directory: ruleId={}, sourceDir={}",
                    rule.getId(), root);
            return;
        }
        startScan(rule, root, "periodic scan", false, true);
    }

    private void startScan(
            WatchRule rule,
            Path root,
            String label,
            boolean rejectWhenRunning,
            boolean inspectMissingSources
    ) {
        String scanKey = scanKey(rule);
        if (!activeScanKeys.add(scanKey)) {
            String message = "Scan is already running for this rule. Please try again later.";
            if (rejectWhenRunning) {
                throw new IllegalStateException(message);
            }
            log.debug("{} skipped: ruleId={}, reason={}", label, rule.getId(), message);
            return;
        }

        Thread.ofVirtual()
                .name("file-discovery-scan-" + rule.getId())
                .start(() -> runScan(root, rule, scanKey, label, inspectMissingSources));
    }

    private void runScan(Path root, WatchRule rule, String scanKey, String label, boolean inspectMissingSources) {
        log.info("{} started: ruleId={}, rule='{}', root={}",
                label, rule.getId(), rule.getName(), root);

        ScanCounter counter = new ScanCounter();
        try {
            discoverTree(root, rule, counter);
            if (inspectMissingSources) {
                failMissingPendingTasks(rule, counter);
            }
            log.info("{} completed: ruleId={}, scanned={}, queued={}, skipped={}, missingFailed={}",
                    label, rule.getId(), counter.scanned, counter.queued, counter.skipped, counter.missingFailed);
        } catch (IOException e) {
            log.error("{} failed: ruleId={}, root={}", label, rule.getId(), root, e);
        } finally {
            activeScanKeys.remove(scanKey);
        }
    }

    private void discoverTree(Path root, WatchRule rule, ScanCounter counter) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path normalized = dir.toAbsolutePath().normalize();
                if (!normalized.equals(normalizedRoot) && isIgnored(normalized, rule)) {
                    counter.skipped++;
                    log.debug("Scan ignored directory subtree: {}", normalized);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!normalized.equals(normalizedRoot)) {
                    counter.scanned++;
                    var asset = mediaAssetDetectionService.detect(normalized, rule);
                    if (asset.isPresent()) {
                        boolean queued = processAssetIfNotDuplicated(asset.get(), rule);
                        if (queued) {
                            counter.queued++;
                        } else {
                            counter.skipped++;
                        }
                        return asset.get().shouldPruneChildren()
                                ? FileVisitResult.SKIP_SUBTREE
                                : FileVisitResult.CONTINUE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                discoverPath(file.toAbsolutePath().normalize(), rule, counter);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void discoverPath(Path path, WatchRule rule, ScanCounter counter) {
        counter.scanned++;
        if (isIgnored(path, rule)) {
            counter.skipped++;
            return;
        }

        var asset = mediaAssetDetectionService.detect(path, rule);
        if (asset.isPresent()) {
            boolean queued = processAssetIfNotDuplicated(asset.get(), rule);
            if (queued) {
                counter.queued++;
            } else {
                counter.skipped++;
            }
            return;
        }

        FileCategory category = categorizeFile(path);
        if (category == FileCategory.ASSOCIATED) {
            counter.skipped++;
            return;
        }

        boolean queued = recordSkippedIfNotDuplicated(
                path,
                rule,
                MediaAssetType.VIDEO_FILE,
                "非视频文件，已跳过处理"
        );
        if (queued) {
            counter.queued++;
        } else {
            counter.skipped++;
        }
    }

    private void rebuildWatchService() {
        running = false;
        cancelDebounceTasks();
        dirRuleMap.clear();

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Failed to close old WatchService during reload: {}", e.getMessage());
            }
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create WatchService", e);
        }
    }

    private void cancelDebounceTasks() {
        debounceMap.values().forEach(future -> future.cancel(false));
        debounceMap.clear();
    }

    private void cancelPeriodicScans() {
        periodicScanFutures.values().forEach(future -> future.cancel(false));
        periodicScanFutures.clear();
    }

    private void cancelMissingSourceInspections() {
        missingSourceInspectionFutures.values().forEach(future -> future.cancel(false));
        missingSourceInspectionFutures.clear();
    }

    /**
     * 递归注册目录树。启动和 reload 时用于注册已存在目录；运行中发现新目录时也复用此方法。
     */
    private void registerDirectoryTree(Path root, WatchRule rule) {
        if (!Files.isDirectory(root)) {
            log.warn("Watch directory does not exist or is not a directory, skipping: {}", root);
            return;
        }

        Path normalizedRoot = root.toAbsolutePath().normalize();
        try {
            Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    Path normalized = dir.toAbsolutePath().normalize();
                    if (!normalized.equals(normalizedRoot) && isIgnored(normalized, rule)) {
                        log.debug("Ignored watch directory subtree, not registering: {}", normalized);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    registerDirectory(normalized, rule);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Failed to recursively register watch directory: {}", root, e);
        }
    }

    private void registerDirectory(Path dir, WatchRule rule) {
        if (dirRuleMap.containsKey(dir)) {
            log.debug("Directory already registered, skipping: {}", dir);
            return;
        }

        if (!Files.isDirectory(dir)) {
            log.warn("Watch directory does not exist or is not a directory, skipping: {}", dir);
            return;
        }

        try {
            dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            dirRuleMap.put(dir, rule);
            log.info("Watching: {} (rule='{}', discoveryMode={}, mediaType={}, operation={})",
                    dir, rule.getName(), rule.getDiscoveryMode(), rule.getMediaType(), rule.getOperation());
        } catch (IOException e) {
            log.error("Failed to register watch directory: {}", dir, e);
        }
    }

    private void startWatchLoop() {
        running = true;
        Thread.ofVirtual().name("file-discovery-watch-loop").start(() -> {
            log.info("FileDiscovery watch event loop started");
            while (running) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    log.info("FileDiscovery watch event loop stopped");
                    break;
                }

                handleWatchKey(key);
            }
        });
    }

    private void handleWatchKey(WatchKey key) {
        Path watchedDir = (Path) key.watchable();
        WatchRule rule = dirRuleMap.get(watchedDir);

        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Path filename = ((WatchEvent<Path>) event).context();
            Path fullPath = watchedDir.resolve(filename).toAbsolutePath().normalize();

            if (rule == null) {
                log.warn("No rule found for directory: {}, event ignored: {}", watchedDir, fullPath);
                continue;
            }

            handlePathEvent(event.kind(), fullPath, rule);
        }

        boolean valid = key.reset();
        if (!valid) {
            log.warn("Watch key invalidated for dir: {}", watchedDir);
            dirRuleMap.remove(watchedDir);
        }
    }

    private void handlePathEvent(WatchEvent.Kind<?> kind, Path fullPath, WatchRule rule) {
        boolean isDebug = Boolean.parseBoolean(settingsService.get("debug", "false"));
        if (isDebug) {
            log.debug("File event: kind={}, path={}, rule={}", kind.name(), fullPath, rule.getName());
        }

        if (!isInsideSourceDir(fullPath, rule)) {
            log.warn("Discovered path is outside sourceDir, ignored: path={}, sourceDir={}",
                    fullPath, rule.getSourceDir());
            return;
        }

        if (isIgnored(fullPath, rule)) {
            log.debug("Ignoring path by WatchRule ignoredFilePatterns: {}", fullPath);
            return;
        }

        if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath)) {
            log.info("New directory detected, registering recursively and scanning once: {} (rule='{}')",
                    fullPath, rule.getName());
            registerDirectoryTree(fullPath, rule);
            startScan(rule, fullPath, "new directory scan", false, false);
            return;
        }

        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            cancelDebouncedProcessing(fullPath);
            scheduleMissingSourceCheck(fullPath);
            return;
        }

        var asset = mediaAssetDetectionService.detect(fullPath, rule);
        if (asset.isPresent() && !MediaAssetType.VIDEO_FILE.equals(asset.get().type())) {
            processAssetIfNotDuplicated(asset.get(), rule);
            return;
        }

        if (Files.isDirectory(fullPath)) {
            return;
        }

        FileCategory category = categorizeFile(fullPath);
        if (category == FileCategory.ASSOCIATED) {
            log.debug("Ignoring associated file until main video completes: {}", fullPath);
            return;
        }

        if (category == FileCategory.MISC) {
            recordSkippedIfNotDuplicated(
                    fullPath,
                    rule,
                    MediaAssetType.VIDEO_FILE,
                    "非视频文件，已跳过处理"
            );
            return;
        }

        scheduleDebouncedProcessing(fullPath, rule);
    }

    /**
     * ADR-005 第一层防护：防抖。
     * 相同文件在防抖窗口内多次触发时，取消旧任务并重新计时。
     */
    private void scheduleDebouncedProcessing(Path file, WatchRule rule) {
        String key = file.toString();
        cancelDebouncedProcessing(file);

        long debounceSeconds = getDebounceSeconds();
        ScheduledFuture<?> future = discoveryExecutor.schedule(
                () -> processAfterStabilityCheck(file, rule),
                debounceSeconds,
                TimeUnit.SECONDS
        );
        debounceMap.put(key, future);

        log.debug("Debounced video file event: path={}, delay={}s", file, debounceSeconds);
    }

    private void cancelDebouncedProcessing(Path file) {
        ScheduledFuture<?> oldFuture = debounceMap.remove(file.toString());
        if (oldFuture != null) {
            oldFuture.cancel(false);
        }
    }

    private void scheduleMissingSourceCheck(Path file) {
        discoveryExecutor.schedule(
                () -> failTaskIfSourceMissing(file),
                getDebounceSeconds(),
                TimeUnit.SECONDS
        );
    }

    /**
     * ADR-005 第一层防护：文件大小稳定检测。
     * 防抖结束后读取 size1，等待 1 秒读取 size2；两者一致才认为文件已写完。
     */
    private void processAfterStabilityCheck(Path file, WatchRule rule) {
        String key = file.toString();
        try {
            if (!Files.exists(file)) {
                log.debug("File disappeared before stability check, skipping: {}", file);
                return;
            }

            long size1 = Files.size(file);
            TimeUnit.SECONDS.sleep(1);

            if (!Files.exists(file)) {
                log.debug("File disappeared during stability check, skipping: {}", file);
                return;
            }

            long size2 = Files.size(file);
            if (size1 != size2) {
                log.info("File is still changing, waiting for next event: path={}, size1={}, size2={}",
                        file, size1, size2);
                return;
            }

            processDetectedPathIfNotDuplicated(file, rule);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Stability check interrupted: {}", file);
        } catch (IOException e) {
            log.warn("Failed to check file stability, skipping: path={}, error={}", file, e.getMessage());
        } finally {
            debounceMap.remove(key);
        }
    }

    /**
     * ADR-005 第二层防护：数据库查重。
     * 同一路径存在非 FAILED 任务时跳过，避免重复入库。
     */
    private boolean processDetectedPathIfNotDuplicated(Path path, WatchRule rule) {
        var asset = mediaAssetDetectionService.detect(path, rule);
        if (asset.isEmpty()) {
            log.debug("No media asset detected after stability check, skipping: {}", path);
            return false;
        }
        return processAssetIfNotDuplicated(asset.get(), rule);
    }

    private boolean processAssetIfNotDuplicated(MediaAsset asset, WatchRule rule) {
        MediaAsset effectiveAsset = applyRuleSupportScope(asset, rule);
        String sourcePath = effectiveAsset.rootPath().toString();
        boolean exists = mediaTaskRepository.existsBySourcePathAndStatusNot(
                sourcePath,
                MediaTask.TaskStatus.FAILED
        );

        if (exists) {
            log.warn("Duplicate media task skipped: sourcePath={}", sourcePath);
            return false;
        }

        if (effectiveAsset.shouldSkip()) {
            return recordSkippedIfNotDuplicated(
                    effectiveAsset.rootPath(),
                    rule,
                    effectiveAsset.type(),
                    effectiveAsset.skipReason()
            );
        }

        try {
            log.info("Stable media asset discovered: path={}, type={}, rule='{}'",
                    effectiveAsset.rootPath(), effectiveAsset.type(), rule.getName());
            pipeline.process(effectiveAsset, rule);
            return true;
        } catch (Exception e) {
            log.error("Pipeline execution failed for media asset: {}", effectiveAsset.rootPath(), e);
            return false;
        }
    }

    private MediaAsset applyRuleSupportScope(MediaAsset asset, WatchRule rule) {
        if (MediaAssetType.BLURAY_DIRECTORY.equals(asset.type())
                && WatchRule.RuleMediaType.TV_SHOW.equals(rule.getMediaType())
                && !asset.shouldSkip()) {
            return new MediaAsset(
                    asset.rootPath(),
                    asset.type(),
                    asset.displayName(),
                    asset.shouldPruneChildren(),
                    "当前版本暂不支持剧集蓝光原盘"
            );
        }
        return asset;
    }

    private boolean recordSkippedIfNotDuplicated(
            Path path,
            WatchRule rule,
            MediaAssetType assetType,
            String skipReason
    ) {
        String sourcePath = path.toString();
        boolean exists = mediaTaskRepository.existsBySourcePathAndStatusNot(
                sourcePath,
                MediaTask.TaskStatus.FAILED
        );

        if (exists) {
            log.debug("Duplicate skipped task ignored: sourcePath={}", sourcePath);
            return false;
        }

        MediaTask task = new MediaTask();
        task.setSourcePath(sourcePath);
        task.setAssetType(assetType);
        task.setRuleId(rule.getId());
        task.setOperationType(rule.getOperation().name());
        task.setStatus(MediaTask.TaskStatus.SKIPPED);
        task.setSkipReason(skipReason);
        mediaTaskRepository.save(task);
        log.info("Media asset skipped: path={}, type={}, rule='{}', reason={}",
                path, assetType, rule.getName(), skipReason);
        return true;
    }

    private void failMissingPendingTasks(WatchRule rule, ScanCounter counter) {
        if (rule.getId() == null) {
            return;
        }
        List<MediaTask> pendingTasks = mediaTaskRepository.findByRuleIdAndStatusIn(
                rule.getId(),
                List.of(MediaTask.TaskStatus.PENDING, MediaTask.TaskStatus.AWAITING_CONFIRMATION)
        );
        for (MediaTask task : pendingTasks) {
            Path sourcePath = Paths.get(task.getSourcePath()).toAbsolutePath().normalize();
            if (!Files.exists(sourcePath)) {
                markMissingSourceFailed(task);
                counter.missingFailed++;
            }
        }
    }

    private void failTaskIfSourceMissing(Path sourcePath) {
        String normalizedSourcePath = sourcePath.toAbsolutePath().normalize().toString();
        mediaTaskRepository.findBySourcePath(normalizedSourcePath).ifPresent(task -> {
            if (isMissingSourceFailureCandidate(task)) {
                markMissingSourceFailed(task);
            }
        });
    }

    private boolean isMissingSourceFailureCandidate(MediaTask task) {
        return MediaTask.TaskStatus.PENDING.equals(task.getStatus())
                || MediaTask.TaskStatus.AWAITING_CONFIRMATION.equals(task.getStatus());
    }

    private void markMissingSourceFailed(MediaTask task) {
        task.setStatus(MediaTask.TaskStatus.FAILED);
        task.setErrorMessage(MediaProcessPipeline.SOURCE_MISSING_ERROR_MESSAGE);
        mediaTaskRepository.save(task);
        eventPublisher.publishTaskFailed(task);
        log.info("Task failed because source file is missing: taskId={}, sourcePath={}",
                task.getId(), task.getSourcePath());
    }

    private long getDebounceSeconds() {
        String value = settingsService.get("watcher.debounce-seconds", "3");
        try {
            long seconds = Long.parseLong(value);
            return Math.max(seconds, 1);
        } catch (NumberFormatException e) {
            log.warn("Invalid watcher.debounce-seconds='{}', fallback to 3", value);
            return 3;
        }
    }

    private long getMissingSourceCheckSeconds() {
        String value = settingsService.get("watcher.missing-source-check-seconds", "60");
        try {
            long seconds = Long.parseLong(value);
            return Math.max(seconds, 10);
        } catch (NumberFormatException e) {
            log.warn("Invalid watcher.missing-source-check-seconds='{}', fallback to 60", value);
            return 60;
        }
    }

    private int normalizeScanIntervalMinutes(Integer value) {
        return Math.max(value != null ? value : 10, MIN_SCAN_INTERVAL_MINUTES);
    }

    private boolean usesWatchEvents(WatchRule rule) {
        WatchRule.DiscoveryMode mode = rule.getDiscoveryMode() != null
                ? rule.getDiscoveryMode()
                : WatchRule.DiscoveryMode.HYBRID;
        return mode == WatchRule.DiscoveryMode.WATCH_EVENT || mode == WatchRule.DiscoveryMode.HYBRID;
    }

    private boolean usesPeriodicScan(WatchRule rule) {
        WatchRule.DiscoveryMode mode = rule.getDiscoveryMode() != null
                ? rule.getDiscoveryMode()
                : WatchRule.DiscoveryMode.HYBRID;
        return mode == WatchRule.DiscoveryMode.PERIODIC_SCAN || mode == WatchRule.DiscoveryMode.HYBRID;
    }

    private String scanKey(WatchRule rule) {
        return rule.getId() != null ? "rule:" + rule.getId() : "source:" + rule.getSourceDir();
    }

    private boolean isInsideSourceDir(Path path, WatchRule rule) {
        Path root = Paths.get(rule.getSourceDir()).toAbsolutePath().normalize();
        return path.toAbsolutePath().normalize().startsWith(root);
    }

    private boolean isVideoFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return VIDEO_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private FileCategory categorizeFile(Path path) {
        String name = path.getFileName().toString();
        String lowerName = name.toLowerCase();

        if (isVideoFile(path)) {
            return FileCategory.VIDEO;
        }

        if (GENERIC_COVER_NAMES.contains(lowerName) || isAssociatedWithSiblingVideo(path, lowerName)) {
            return FileCategory.ASSOCIATED;
        }

        return FileCategory.MISC;
    }

    private boolean isAssociatedWithSiblingVideo(Path path, String lowerName) {
        if (ASSOCIATED_EXTENSIONS.stream().noneMatch(lowerName::endsWith)) {
            return false;
        }

        Path parent = path.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return false;
        }

        try (Stream<Path> siblings = Files.list(parent)) {
            return siblings
                    .filter(Files::isRegularFile)
                    .filter(this::isVideoFile)
                    .map(video -> basename(video.getFileName().toString()).toLowerCase())
                    .anyMatch(videoBase -> lowerName.startsWith(videoBase + "."));
        } catch (IOException e) {
            log.debug("Failed to inspect sibling videos for associated file: {}", path, e);
            return false;
        }
    }

    private String basename(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private boolean isIgnored(Path path, WatchRule rule) {
        List<String> patterns = effectiveIgnoredPatterns(rule);
        if (patterns.isEmpty()) {
            return false;
        }

        Path root = Paths.get(rule.getSourceDir()).toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        Path relative = root.equals(normalized) || !normalized.startsWith(root)
                ? normalized.getFileName()
                : root.relativize(normalized);

        if (relative == null) {
            return false;
        }

        for (String pattern : patterns) {
            if (matchesIgnorePattern(relative, pattern)) {
                return true;
            }
        }
        return false;
    }

    private List<String> effectiveIgnoredPatterns(WatchRule rule) {
        List<String> patterns = rule.getIgnoredFilePatterns();
        if (patterns == null) {
            return DEFAULT_IGNORED_PATTERNS;
        }
        return patterns.stream()
                .map(String::trim)
                .filter(pattern -> !pattern.isBlank())
                .toList();
    }

    private boolean matchesIgnorePattern(Path relativePath, String pattern) {
        String normalizedPattern = pattern.replace("\\", "/").trim();
        if (normalizedPattern.isBlank()) {
            return false;
        }

        if (normalizedPattern.endsWith("/")) {
            String directoryPattern = normalizedPattern.substring(0, normalizedPattern.length() - 1);
            return pathSegments(relativePath).stream()
                    .anyMatch(segment -> matchesNamePattern(segment, directoryPattern));
        }

        Path fileName = relativePath.getFileName();
        return fileName != null && matchesNamePattern(fileName.toString(), normalizedPattern);
    }

    private List<String> pathSegments(Path relativePath) {
        List<String> segments = new ArrayList<>();
        for (Path segment : relativePath) {
            segments.add(segment.toString());
        }
        return segments;
    }

    private boolean matchesNamePattern(String name, String pattern) {
        String normalizedName = name.toLowerCase();
        String normalizedPattern = pattern.toLowerCase();
        if (normalizedPattern.equals(normalizedName)) {
            return true;
        }
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);
            return matcher.matches(Paths.get(normalizedName));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ignored file pattern skipped: pattern='{}', error={}", pattern, e.getMessage());
            return false;
        }
    }

    private static class ScanCounter {
        private int scanned;
        private int queued;
        private int skipped;
        private int missingFailed;
    }

    private enum FileCategory {
        VIDEO,
        ASSOCIATED,
        MISC
    }
}
