package com.mediamarshal.service.watcher;

import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.pipeline.MediaProcessPipeline;
import com.mediamarshal.service.settings.SettingsService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 文件监控服务（ADR-002 + ADR-005）
 *
 * 职责：
 *  1. 从 watch_rule 表读取启用规则
 *  2. 递归监听每条规则的 sourceDir 及全部已存在子目录
 *  3. 运行期间发现新目录时立即递归注册
 *  4. 对视频文件事件执行防抖 + 文件大小稳定检测
 *  5. 通过数据库查重避免同一路径重复生成任务
 *
 * 关键约束：
 *  - WatchService 原生不支持递归监听，必须业务层逐目录注册
 *  - reload() 必须重建 WatchService，避免禁用/删除规则后旧 WatchKey 继续触发
 *  - 正在拷贝的大文件必须等待稳定后才进入 pipeline
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileWatcherService {

    private static final List<String> VIDEO_EXTENSIONS = List.of(
            ".mkv", ".mp4", ".avi", ".mov", ".wmv", ".flv", ".ts", ".m2ts", ".rmvb"
    );

    private final WatchRuleRepository watchRuleRepository;
    private final MediaTaskRepository mediaTaskRepository;
    private final MediaProcessPipeline pipeline;
    private final SettingsService settingsService;

    private WatchService watchService;
    private volatile boolean running = false;

    /** 被注册目录（规范化绝对路径）→ WatchRule，用于文件事件时查规则。 */
    private final Map<Path, WatchRule> dirRuleMap = new ConcurrentHashMap<>();

    /** 文件绝对路径 → 防抖任务；同路径新事件会取消旧任务并重新计时。 */
    private final Map<String, ScheduledFuture<?>> debounceMap = new ConcurrentHashMap<>();

    /** 执行防抖延迟任务和文件大小稳定检测。 */
    private final ScheduledExecutorService debounceExecutor = Executors.newScheduledThreadPool(2);

    @PostConstruct
    public void init() {
        reload();
    }

    @PreDestroy
    public void shutdown() throws IOException {
        running = false;
        cancelDebounceTasks();
        debounceExecutor.shutdownNow();
        if (watchService != null) {
            watchService.close();
        }
        log.info("FileWatcherService stopped");
    }

    /**
     * 热重载：
     *  1. 重建 WatchService 和目录映射
     *  2. 递归注册所有 enabled=true 的 WatchRule
     *  3. 启动新的事件循环
     */
    public synchronized void reload() {
        rebuildWatchService();

        List<WatchRule> rules = watchRuleRepository.findByEnabledTrue();
        if (rules.isEmpty()) {
            log.warn("No enabled watch rules found. FileWatcher is idle.");
            return;
        }

        for (WatchRule rule : rules) {
            Path root = Paths.get(rule.getSourceDir()).toAbsolutePath().normalize();
            registerDirectoryTree(root, rule);
        }

        log.info("FileWatcher reloaded: {} rules active, {} directories registered",
                rules.size(), dirRuleMap.size());

        startWatchLoop();
    }

    /**
     * reload 时重建 WatchService，确保旧目录不再继续触发事件。
     */
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

    /**
     * 递归注册目录树。启动和 reload 时用于注册已存在目录；运行中发现新目录时也复用此方法。
     */
    private void registerDirectoryTree(Path root, WatchRule rule) {
        if (!Files.isDirectory(root)) {
            log.warn("Watch directory does not exist or is not a directory, skipping: {}", root);
            return;
        }

        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isDirectory)
                    .forEach(dir -> registerDirectory(dir.toAbsolutePath().normalize(), rule));
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
                    StandardWatchEventKinds.ENTRY_MODIFY);
            dirRuleMap.put(dir, rule);
            log.info("Watching: {} (rule='{}', mediaType={}, operation={})",
                    dir, rule.getName(), rule.getMediaType(), rule.getOperation());
        } catch (IOException e) {
            log.error("Failed to register watch directory: {}", dir, e);
        }
    }

    private void startWatchLoop() {
        running = true;
        Thread.ofVirtual().name("file-watcher-loop").start(() -> {
            log.info("FileWatcher event loop started");
            while (running) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    log.info("FileWatcher event loop stopped");
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

        // 运行期间若收到新目录创建事件，立即递归注册，确保后续子文件可被检测。
        if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath)) {
            log.info("New directory detected, registering recursively: {} (rule='{}')",
                    fullPath, rule.getName());
            registerDirectoryTree(fullPath, rule);
            return;
        }

        // 目录事件只用于递归注册，不进入 pipeline。
        if (Files.isDirectory(fullPath)) {
            return;
        }

        if (!isVideoFile(fullPath)) {
            log.debug("Ignoring non-video file: {}", fullPath);
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
        ScheduledFuture<?> oldFuture = debounceMap.remove(key);
        if (oldFuture != null) {
            oldFuture.cancel(false);
        }

        long debounceSeconds = getDebounceSeconds();
        ScheduledFuture<?> future = debounceExecutor.schedule(
                () -> processAfterStabilityCheck(file, rule),
                debounceSeconds,
                TimeUnit.SECONDS
        );
        debounceMap.put(key, future);

        log.debug("Debounced video file event: path={}, delay={}s", file, debounceSeconds);
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

            processIfNotDuplicated(file, rule);
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
    private void processIfNotDuplicated(Path file, WatchRule rule) {
        String sourcePath = file.toString();
        boolean exists = mediaTaskRepository.existsBySourcePathAndStatusNot(
                sourcePath,
                MediaTask.TaskStatus.FAILED
        );

        if (exists) {
            log.warn("Duplicate media task skipped: sourcePath={}", sourcePath);
            return;
        }

        try {
            log.info("Stable video file detected: {} (rule='{}')", file, rule.getName());
            pipeline.process(file, rule);
        } catch (Exception e) {
            // Pipeline 仍有未实现步骤，不能让异常杀死防抖线程或 WatchService。
            log.error("Pipeline execution failed for file: {}", file, e);
        }
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

    private boolean isVideoFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return VIDEO_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
