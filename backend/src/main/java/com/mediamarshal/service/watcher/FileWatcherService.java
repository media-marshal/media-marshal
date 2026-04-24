package com.mediamarshal.service.watcher;

import com.mediamarshal.service.pipeline.MediaProcessPipeline;
import com.mediamarshal.service.settings.SettingsService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;

/**
 * 文件监控服务（Java NIO WatchService）
 *
 * 监控用户配置的目录列表，发现新文件时触发媒体处理流水线。
 *
 * 配置项：media-marshal.watcher.watch-dirs（逗号分隔的绝对路径列表）
 *
 * 注意事项：
 *  - WatchService 仅监控直接子文件，不递归；需手动递归注册子目录
 *  - 大文件拷贝时可能触发 ENTRY_CREATE 但文件尚未写完，需要等待文件大小稳定
 *  - TODO: 实现文件稳定性检测（等待 N 秒文件大小不变视为写入完毕）
 *
 * TODO:
 *  - 实现 startWatching() 的 WatchService 循环
 *  - 实现递归目录注册
 *  - 实现文件过滤（仅处理视频文件扩展名）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileWatcherService {

    private static final List<String> VIDEO_EXTENSIONS = List.of(
            ".mkv", ".mp4", ".avi", ".mov", ".wmv", ".flv", ".ts", ".m2ts", ".rmvb"
    );

    private final MediaProcessPipeline pipeline;
    private final SettingsService settingsService;

    private WatchService watchService;
    private volatile boolean running = false;

    @PostConstruct
    public void init() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        String watchDirs = settingsService.get("watcher.watch-dirs", "");
        if (watchDirs.isBlank()) {
            log.warn("No watch directories configured. File watcher is idle.");
            return;
        }
        List<String> dirs = Arrays.asList(watchDirs.split(","));
        dirs.forEach(dir -> registerDirectory(Paths.get(dir.trim())));
        startWatching();
    }

    @PreDestroy
    public void shutdown() throws IOException {
        running = false;
        if (watchService != null) {
            watchService.close();
        }
    }

    private void registerDirectory(Path dir) {
        try {
            dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            log.info("Watching directory: {}", dir);
        } catch (IOException e) {
            log.error("Failed to watch directory: {}", dir, e);
        }
    }

    private void startWatching() {
        running = true;
        Thread.ofVirtual().name("file-watcher").start(() -> {
            while (running) {
                // TODO: implement WatchKey polling and event dispatching
            }
        });
    }

    boolean isVideoFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return VIDEO_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
