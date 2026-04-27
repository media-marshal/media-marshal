package com.mediamarshal.service.pipeline;

import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.TaskCandidate;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.notification.EmailNotificationService;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.repository.TaskCandidateRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
 *   Step 4: TMDB 搜索匹配并保存候选
 *             - Step 4a: 调用 TMDB 搜索，获得候选列表
 *             - Step 4b: 将候选列表按置信度降序写入 task_candidate 表，记录 rank
 *   Step 5: 置信度判断
 *             - 高置信度：直接继续
 *             - 低置信度：状态改为 AWAITING_CONFIRMATION，推送 WebSocket + 邮件通知
 *             - 前端收到通知后，通过 Queue API 拉取 Step 4b 已保存的候选列表
 *   Step 6: 重命名文件（调用 RenameService，使用规则 targetDir + 对应媒体类型路径模板）
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
    private final TaskCandidateRepository candidateRepository;
    private final SettingsService settingsService;
    private final EventPublisher eventPublisher;
    private final EmailNotificationService emailNotificationService;

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
        task.setOperationType(rule.getOperation().name());
        task.setStatus(MediaTask.TaskStatus.PENDING);
        taskRepository.save(task);
        eventPublisher.publishTaskCreated(task);

        if (isDebug) {
            log.debug("Task created: id={}, ruleId={}, sourceDir={}", task.getId(), rule.getId(), file);
        }

        try {
            task.setStatus(MediaTask.TaskStatus.PROCESSING);
            taskRepository.save(task);
            eventPublisher.publishTaskProcessing(task);

            // Step 2: guessit 解析文件名
            ParseResult parseResult = parserClient.parse(file.getFileName().toString());
            fillParsedFields(task, parseResult);

            // Step 3: 确定媒体类型
            Optional<MediaTask.MediaType> mediaType = resolveMediaType(rule, parseResult);
            if (mediaType.isEmpty()) {
                moveToAwaitingConfirmation(task, "Unable to detect media type from filename");
                return;
            }
            task.setMediaType(mediaType.get());
            applyMediaTypeToParseResult(parseResult, mediaType.get());
            taskRepository.save(task);

            // Step 4: TMDB 搜索并保存候选
            List<MatchResult> candidates = metadataMatcher.search(parseResult);
            List<TaskCandidate> savedCandidates = saveCandidates(task, candidates);
            if (savedCandidates.isEmpty()) {
                moveToAwaitingConfirmation(task, "No TMDB candidates found");
                return;
            }

            // Step 5: 置信度判断
            TaskCandidate topCandidate = savedCandidates.getFirst();
            task.setMatchConfidence(topCandidate.getConfidence());
            if (topCandidate.getConfidence() == null || topCandidate.getConfidence() < getConfidenceThreshold()) {
                moveToAwaitingConfirmation(task, "Low confidence match");
                return;
            }

            // 高置信度自动采纳 rank=1 候选，继续 Step 6-8
            topCandidate.setSelected(true);
            candidateRepository.save(topCandidate);
            MatchResult topMatch = toMatchResult(topCandidate);
            applyMatchToTask(task, topMatch);
            continueAfterMetadataConfirmed(task, topMatch);

        } catch (Exception e) {
            log.error("Pipeline failed: file={}, taskId={}", file, task.getId(), e);
            task.setStatus(MediaTask.TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            taskRepository.save(task);
            eventPublisher.publishTaskFailed(task);
            emailNotificationService.notifyTaskFailed(task);
        }
    }

    /**
     * 人工确认低置信度任务（由 QueueController 调用）
     *
     * ADR-006 确认流程：
     *   1. 加载任务，校验状态必须为 AWAITING_CONFIRMATION
     *   2. 判断用户选择的 tmdbId 是否已存在于 task_candidate：
     *      - 若存在：将该候选 selected=true
     *      - 若不存在：说明来自用户重新搜索，新增一条 task_candidate 并 selected=true
     *   3. 调用 MetadataMatcher.getById(tmdbId, mediaType) 获取完整元数据
     *   4. 更新 MediaTask 的 tmdbId、mediaType、confirmedTitle、confirmedYear 等字段
     *   5. 继续执行 Step 6-8：重命名 -> 生成 NFO -> DONE + WebSocket 通知
     *
     * @param taskId    任务 ID
     * @param tmdbId    用户选择的 TMDB ID
     * @param mediaType 用户确认的媒体类型
     */
    @SuppressWarnings("null")
    public void confirm(Long taskId, Long tmdbId, String mediaType) {
        MediaTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (!MediaTask.TaskStatus.AWAITING_CONFIRMATION.equals(task.getStatus())) {
            throw new IllegalStateException("Task is not awaiting confirmation: " + taskId);
        }

        MediaTask.MediaType confirmedType = MediaTask.MediaType.valueOf(mediaType);
        TaskCandidate selectedCandidate = candidateRepository
                .findByTask_IdAndTmdbIdAndMediaType(taskId, tmdbId, confirmedType)
                .orElseGet(() -> createManualCandidate(task, tmdbId, confirmedType));

        markCandidateSelected(taskId, selectedCandidate);

        MatchResult match = metadataMatcher.getById(String.valueOf(tmdbId), confirmedType.name());
        applyMatchToTask(task, match);
        continueAfterMetadataConfirmed(task, match);
    }

    private void fillParsedFields(MediaTask task, ParseResult parseResult) {
        task.setParsedTitle(parseResult.getTitle());
        task.setParsedYear(parseResult.getYear());
        task.setParsedSeason(parseResult.getSeason());
        task.setParsedEpisode(parseResult.getEpisode());
        taskRepository.save(task);
    }

    private Optional<MediaTask.MediaType> resolveMediaType(WatchRule rule, ParseResult parseResult) {
        if (WatchRule.RuleMediaType.MOVIE.equals(rule.getMediaType())) {
            return Optional.of(MediaTask.MediaType.MOVIE);
        }
        if (WatchRule.RuleMediaType.TV_SHOW.equals(rule.getMediaType())) {
            return Optional.of(MediaTask.MediaType.TV_SHOW);
        }

        if ("movie".equalsIgnoreCase(parseResult.getType())) {
            return Optional.of(MediaTask.MediaType.MOVIE);
        }
        if ("episode".equalsIgnoreCase(parseResult.getType())
                || parseResult.getSeason() != null
                || parseResult.getEpisode() != null) {
            return Optional.of(MediaTask.MediaType.TV_SHOW);
        }
        return Optional.empty();
    }

    private void applyMediaTypeToParseResult(ParseResult parseResult, MediaTask.MediaType mediaType) {
        parseResult.setType(MediaTask.MediaType.TV_SHOW.equals(mediaType) ? "episode" : "movie");
    }

    private List<TaskCandidate> saveCandidates(MediaTask task, List<MatchResult> matches) {
        List<MatchResult> sorted = matches.stream()
                .sorted(Comparator.comparing(
                        MatchResult::getConfidence,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();

        int rank = 1;
        for (MatchResult match : sorted) {
            TaskCandidate candidate = new TaskCandidate();
            candidate.setTask(task);
            candidate.setTmdbId(Long.valueOf(match.getSourceId()));
            candidate.setTitle(match.getTitle());
            candidate.setOriginalTitle(match.getOriginalTitle());
            candidate.setYear(match.getYear());
            candidate.setMediaType(MediaTask.MediaType.valueOf(match.getMediaType()));
            candidate.setConfidence(match.getConfidence());
            candidate.setPosterUrl(match.getPosterUrl());
            candidate.setOverview(match.getOverview());
            candidate.setRank(rank++);
            candidate.setSelected(false);
            candidateRepository.save(candidate);
        }

        return candidateRepository.findByTask_IdOrderByRankAsc(task.getId());
    }

    private void moveToAwaitingConfirmation(MediaTask task, String reason) {
        log.info("Task awaiting confirmation: taskId={}, reason={}", task.getId(), reason);
        task.setStatus(MediaTask.TaskStatus.AWAITING_CONFIRMATION);
        task.setErrorMessage(reason);
        taskRepository.save(task);
        eventPublisher.publishAwaitingConfirmation(task);
        emailNotificationService.notifyAwaitingConfirmation(task);
    }

    private double getConfidenceThreshold() {
        String value = settingsService.get("watcher.confidence-threshold", "0.8");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid watcher.confidence-threshold='{}', fallback to 0.8", value);
            return 0.8;
        }
    }

    private void applyMatchToTask(MediaTask task, MatchResult match) {
        task.setTmdbId(Long.valueOf(match.getSourceId()));
        task.setMediaType(MediaTask.MediaType.valueOf(match.getMediaType()));
        task.setConfirmedTitle(match.getTitle());
        task.setConfirmedYear(match.getYear());
        task.setMatchConfidence(match.getConfidence());
        taskRepository.save(task);
    }

    private void continueAfterMetadataConfirmed(MediaTask task, MatchResult match) {
        try {
            task.setStatus(MediaTask.TaskStatus.PROCESSING);
            taskRepository.save(task);
            eventPublisher.publishTaskProcessing(task);

            Path target = renameService.rename(task);
            task.setTargetPath(target.toString());
            taskRepository.save(task);

            nfoGeneratorService.generate(task, match, target);

            task.setStatus(MediaTask.TaskStatus.DONE);
            task.setErrorMessage(null);
            taskRepository.save(task);
            eventPublisher.publishTaskDone(task);
            log.info("Pipeline completed: taskId={}, target={}", task.getId(), target);
        } catch (Exception e) {
            log.error("Pipeline finalization failed: taskId={}", task.getId(), e);
            task.setStatus(MediaTask.TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            taskRepository.save(task);
            eventPublisher.publishTaskFailed(task);
            emailNotificationService.notifyTaskFailed(task);
        }
    }

    private TaskCandidate createManualCandidate(MediaTask task, Long tmdbId, MediaTask.MediaType mediaType) {
        MatchResult match = metadataMatcher.getById(String.valueOf(tmdbId), mediaType.name());
        int nextRank = candidateRepository.findByTask_IdOrderByRankAsc(task.getId()).size() + 1;

        TaskCandidate candidate = new TaskCandidate();
        candidate.setTask(task);
        candidate.setTmdbId(tmdbId);
        candidate.setTitle(match.getTitle());
        candidate.setOriginalTitle(match.getOriginalTitle());
        candidate.setYear(match.getYear());
        candidate.setMediaType(mediaType);
        candidate.setConfidence(match.getConfidence());
        candidate.setPosterUrl(match.getPosterUrl());
        candidate.setOverview(match.getOverview());
        candidate.setRank(nextRank);
        candidate.setSelected(true);
        return candidateRepository.save(candidate);
    }

    @SuppressWarnings("null")
    private void markCandidateSelected(Long taskId, TaskCandidate selectedCandidate) {
        List<TaskCandidate> candidates = candidateRepository.findByTask_IdOrderByRankAsc(taskId);
        for (TaskCandidate candidate : candidates) {
            candidate.setSelected(candidate.getId().equals(selectedCandidate.getId()));
        }
        candidateRepository.saveAll(candidates);
    }

    private MatchResult toMatchResult(TaskCandidate candidate) {
        MatchResult result = new MatchResult();
        result.setSource("tmdb");
        result.setSourceId(String.valueOf(candidate.getTmdbId()));
        result.setTitle(candidate.getTitle());
        result.setOriginalTitle(candidate.getOriginalTitle());
        result.setYear(candidate.getYear());
        result.setMediaType(candidate.getMediaType().name());
        result.setOverview(candidate.getOverview());
        result.setPosterUrl(candidate.getPosterUrl());
        result.setConfidence(candidate.getConfidence());
        return result;
    }
}
