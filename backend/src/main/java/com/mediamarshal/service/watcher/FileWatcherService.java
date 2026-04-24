package com.mediamarshal.service.watcher;

import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.pipeline.MediaProcessPipeline;
import com.mediamarshal.service.settings.SettingsService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件监控服务（ADR-002 改造版）
 *
 * 改造要点：
 *   - 原来从 watcher.watch-dirs 配置项读取目录列表，现在改为从 watch_rule 表读取
 *   - 维护 sourceDir → WatchRule 的映射，文件事件触发时能找到对应规则
 *   - 支持运行时热重载（规则增删改后调用 reload()，无需重启服务）
 *
 * 注意事项：
 *   - WatchService 仅监控直接子文件，不递归子目录
 *   - 大文件拷贝时可能在写入完成前触发 ENTRY_CREATE，需等文件稳定后再处理
 *
 * TODO:
 *   - 实现 WatchKey 事件轮询主循环
 *   - 实现文件稳定性检测（等待文件大小不再增长）
 *   - 实现视频文件过滤逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileWatcherService {

    private static final List<String> VIDEO_EXTENSIONS = List.of(
            ".mkv", ".mp4", ".avi", ".mov", ".wmv", ".flv", ".ts", ".m2ts", ".rmvb"
    );

    private final WatchRuleRepository watchRuleRepository;
    private final MediaProcessPipeline pipeline;
    private final SettingsService settingsService;

    private WatchService watchService;
    private volatile boolean running = false;

    /** sourceDir（规范化绝对路径）→ WatchRule，用于文件事件时查规则 */
    private final Map<Path, WatchRule> dirRuleMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        reload();
    }

    @PreDestroy
    public void shutdown() throws IOException {
        running = false;
        if (watchService != null) {
            watchService.close();
        }
        log.info("FileWatcherService stopped");
    }

    /**
     * 热重载：重新从 DB 读取 watch_rule 表，更新监控目录列表。
     * 由 WatchRuleController 在规则增删改后调用。
     */
    public synchronized void reload() {
        List<WatchRule> rules = watchRuleRepository.findByEnabledTrue();
        dirRuleMap.clear();

        if (rules.isEmpty()) {
            log.warn("No enabled watch rules found. FileWatcher is idle.");
            return;
        }

        for (WatchRule rule : rules) {
            Path dir = Paths.get(rule.getSourceDir()).toAbsolutePath().normalize();
            registerDirectory(dir, rule);
        }

        log.info("FileWatcher reloaded: {} rules active, dirs={}",
                rules.size(), dirRuleMap.keySet());

        // 首次 reload 时启动监控线程
        if (!running) {
            startWatchLoop();
        }
    }

    private void registerDirectory(Path dir, WatchRule rule) {
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

    /**
     * 启动 WatchService 事件轮询主循环（虚拟线程，低资源占用）
     */
    private void startWatchLoop() {
        running = true;
        Thread.ofVirtual().name("file-watcher-loop").start(() -> {
            log.info("FileWatcher event loop started");
            while (running) {
                WatchKey key;
                try {
                    key = watchService.take();   // 阻塞等待事件
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    log.info("FileWatcher event loop stopped");
                    break;
                }

                Path watchedDir = (Path) key.watchable();
                WatchRule rule = dirRuleMap.get(watchedDir);

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    Path filename = ((WatchEvent<Path>) event).context();
                    Path fullPath = watchedDir.resolve(filename);

                    if (!isVideoFile(fullPath)) {
                        log.debug("Ignoring non-video file: {}", fullPath);
                        continue;
                    }

                    boolean isDebug = Boolean.parseBoolean(settingsService.get("debug", "false"));
                    if (isDebug) {
                        log.debug("File event: kind={}, path={}, rule={}",
                                event.kind().name(), fullPath, rule != null ? rule.getName() : "UNKNOWN");
                    }

                    if (rule != null) {
                        log.info("New video file detected: {} (rule='{}')", fullPath, rule.getName());
                        // TODO: 增加文件稳定性检测，确认写入完成后再触发 pipeline
                        pipeline.process(fullPath, rule);
                    } else {
                        log.warn("No rule found for directory: {}, file ignored: {}", watchedDir, fullPath);
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    log.warn("Watch key invalidated for dir: {}", watchedDir);
                    dirRuleMap.remove(watchedDir);
                }
            }
        });
    }

    private boolean isVideoFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return VIDEO_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
