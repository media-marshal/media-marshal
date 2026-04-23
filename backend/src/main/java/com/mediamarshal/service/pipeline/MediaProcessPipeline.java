package com.mediamarshal.service.pipeline;

import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.service.matcher.MetadataMatcher;
import com.mediamarshal.service.nfo.NfoGeneratorService;
import com.mediamarshal.service.parser.GuessitParserClient;
import com.mediamarshal.service.rename.RenameService;
import com.mediamarshal.service.settings.SettingsService;
import com.mediamarshal.websocket.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * 媒体处理流水线（核心业务协调层）
 *
 * 编排各服务，按以下步骤处理一个新发现的媒体文件：
 *
 *   Step 1: 入库（创建 MediaTask，状态 PENDING）
 *   Step 2: 解析文件名（调用 GuessitParserClient）
 *   Step 3: 判断媒体类型（MOVIE / TV_SHOW）
 *   Step 4: TMDB 搜索匹配
 *   Step 5: 置信度判断
 *     - 高置信度：直接继续
 *     - 低置信度：状态改为 AWAITING_CONFIRMATION，推送 WebSocket 通知，等待人工确认
 *   Step 6: 重命名文件（调用 RenameService）
 *   Step 7: 生成 NFO（调用 NfoGeneratorService）
 *   Step 8: 状态改为 DONE，推送 WebSocket 完成通知
 *
 * TODO: 实现各步骤逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaProcessPipeline {

    private final GuessitParserClient parserClient;
    private final MetadataMatcher metadataMatcher;
    private final RenameService renameService;
    private final NfoGeneratorService nfoGeneratorService;
    private final MediaTaskRepository taskRepository;
    private final SettingsService settingsService;
    private final EventPublisher eventPublisher;

    /**
     * 处理一个新发现的媒体文件（由 FileWatcherService 调用）
     */
    public void process(Path file) {
        log.info("Processing new file: {}", file);

        // Step 1: 入库
        MediaTask task = new MediaTask();
        task.setSourcePath(file.toAbsolutePath().toString());
        task.setStatus(MediaTask.TaskStatus.PENDING);
        taskRepository.save(task);

        try {
            // Step 2-8: TODO
            task.setStatus(MediaTask.TaskStatus.PROCESSING);
            taskRepository.save(task);

            // TODO: implement full pipeline
            throw new UnsupportedOperationException("Pipeline not yet implemented");

        } catch (Exception e) {
            log.error("Pipeline failed for file: {}", file, e);
            task.setStatus(MediaTask.TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            taskRepository.save(task);
            eventPublisher.publishTaskFailed(task);
        }
    }

    /**
     * 人工确认低置信度任务（由 QueueController 调用）
     *
     * @param taskId   任务 ID
     * @param tmdbId   用户选择的 TMDB ID
     * @param mediaType 媒体类型
     */
    public void confirm(Long taskId, Long tmdbId, String mediaType) {
        // TODO: 加载任务 -> 更新 tmdbId 和 mediaType -> 继续 Step 6-8
        throw new UnsupportedOperationException("Confirm flow not yet implemented");
    }
}
