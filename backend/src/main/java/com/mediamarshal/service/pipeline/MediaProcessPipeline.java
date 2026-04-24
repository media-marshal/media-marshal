package com.mediamarshal.service.pipeline;

import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.WatchRule;
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

/**
 * 媒体处理流水线（核心业务协调层）
 *
 * 编排各服务，按以下步骤处理一个新发现的媒体文件：
 *
 *   Step 1: 入库（创建 MediaTask，记录触发规则 ruleId，状态 PENDING）
 *   Step 2: 解析文件名（调用 GuessitParserClient）
 *   Step 3: 判断媒体类型
 *             - 规则 mediaType = MOVIE/TV_SHOW：直接使用规则指定类型
 *             - 规则 mediaType = AUTO：使用 guessit 解析结果
 *             - AUTO 且 guessit 无法判断：进 AWAITING_CONFIRMATION 队列
 *   Step 4: TMDB 搜索匹配
 *   Step 5: 置信度判断
 *             - 高置信度：直接继续
 *             - 低置信度：状态改为 AWAITING_CONFIRMATION，推送 WebSocket + 邮件通知
 *   Step 6: 重命名文件（调用 RenameService，使用规则的 targetDir + pathTemplate）
 *   Step 7: 生成 NFO（调用 NfoGeneratorService）
 *   Step 8: 状态改为 DONE，推送 WebSocket 完成通知
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
     *
     * @param file 媒体文件绝对路径
     * @param rule 触发此次处理的监控规则
     */
    public void process(Path file, WatchRule rule) {
        log.info("Pipeline started: file={}, rule='{}'", file, rule.getName());

        boolean isDebug = Boolean.parseBoolean(settingsService.get("debug", "false"));

        // Step 1: 入库，记录触发规则
        MediaTask task = new MediaTask();
        task.setSourcePath(file.toAbsolutePath().toString());
        task.setRuleId(rule.getId());
        task.setStatus(MediaTask.TaskStatus.PENDING);
        taskRepository.save(task);
        eventPublisher.publishTaskCreated(task);

        if (isDebug) {
            log.debug("Task created: id={}, ruleId={}, sourceDir={}", task.getId(), rule.getId(), file);
        }

        try {
            task.setStatus(MediaTask.TaskStatus.PROCESSING);
            taskRepository.save(task);

            // TODO Step 2: guessit 解析文件名
            // ParseResult parseResult = parserClient.parse(file.getFileName().toString());

            // TODO Step 3: 确定媒体类型
            // 规则 mediaType != AUTO → 直接用；AUTO → 用 guessit 结果；无法判断 → 进队列

            // TODO Step 4: TMDB 搜索
            // List<MatchResult> candidates = metadataMatcher.search(parseResult);

            // TODO Step 5: 置信度判断
            // double threshold = Double.parseDouble(settingsService.get("watcher.confidence-threshold", "0.8"));

            // TODO Step 6: 重命名
            // Path target = renameService.rename(task);

            // TODO Step 7: 生成 NFO
            // nfoGeneratorService.generate(task, topMatch, target);

            // TODO Step 8: 完成
            // task.setStatus(MediaTask.TaskStatus.DONE);
            // taskRepository.save(task);
            // eventPublisher.publishTaskDone(task);

            throw new UnsupportedOperationException("Pipeline steps 2-8 not yet implemented");

        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Pipeline failed: file={}, taskId={}", file, task.getId(), e);
            task.setStatus(MediaTask.TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            taskRepository.save(task);
            eventPublisher.publishTaskFailed(task);
        }
    }

    /**
     * 人工确认低置信度任务（由 QueueController 调用）
     *
     * @param taskId    任务 ID
     * @param tmdbId    用户选择的 TMDB ID
     * @param mediaType 用户确认的媒体类型
     */
    public void confirm(Long taskId, Long tmdbId, String mediaType) {
        // TODO: 加载任务 → 更新 tmdbId 和 mediaType → 继续 Step 6-8
        throw new UnsupportedOperationException("Confirm flow not yet implemented");
    }
}
