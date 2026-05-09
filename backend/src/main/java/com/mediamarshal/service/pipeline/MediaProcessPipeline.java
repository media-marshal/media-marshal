package com.mediamarshal.service.pipeline;

import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.model.dto.QueueRecognitionRequest;
import com.mediamarshal.model.dto.QueueRecognitionResponse;
import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.TaskCandidate;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.model.exception.MediaTaskFailureException;
import com.mediamarshal.notification.EmailNotificationService;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.repository.TaskCandidateRepository;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.discovery.asset.MediaAsset;
import com.mediamarshal.service.matcher.MetadataMatcher;
import com.mediamarshal.service.nfo.NfoGeneratorService;
import com.mediamarshal.service.parser.GuessitParserClient;
import com.mediamarshal.service.rename.AssetOrganizerService;
import com.mediamarshal.service.rename.FileOperationStrategy;
import com.mediamarshal.service.settings.SettingsService;
import com.mediamarshal.websocket.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    public static final String SOURCE_MISSING_ERROR_MESSAGE = "源文件不存在，无法继续处理";

    private final GuessitParserClient parserClient;
    private final MetadataMatcher metadataMatcher;
    private final AssetOrganizerService assetOrganizerService;
    private final NfoGeneratorService nfoGeneratorService;
    private final MediaTaskRepository taskRepository;
    private final TaskCandidateRepository candidateRepository;
    private final WatchRuleRepository watchRuleRepository;
    private final SettingsService settingsService;
    private final EventPublisher eventPublisher;
    private final EmailNotificationService emailNotificationService;
    private final Map<String, FileOperationStrategy> fileOperationStrategies;
    private final ExecutorService manualConfirmationExecutor = Executors.newFixedThreadPool(
            4,
            Thread.ofVirtual().name("manual-confirm-", 0).factory()
    );

    @PreDestroy
    public void shutdownManualConfirmationExecutor() {
        manualConfirmationExecutor.shutdown();
    }

    /**
     * 处理一个新发现的媒体文件（由 FileDiscoveryService 调用）
     *
     * @param file 媒体文件绝对路径
     * @param rule 触发此次处理的监控规则
     */
    public void process(Path file, WatchRule rule) {
        process(new MediaAsset(
                file.toAbsolutePath().normalize(),
                MediaAssetType.VIDEO_FILE,
                file.getFileName().toString(),
                false
        ), rule);
    }

    /**
     * 处理一个新发现的媒体资产（由 FileDiscoveryService 调用）。
     */
    public void process(MediaAsset asset, WatchRule rule) {
        log.info("Pipeline started: asset={}, type={}, rule='{}'",
                asset.rootPath(), asset.type(), rule.getName());

        boolean isDebug = Boolean.parseBoolean(settingsService.get("debug", "false"));

        // Step 1: 入库，记录触发规则
        MediaTask task = new MediaTask();
        task.setSourcePath(asset.rootPath().toAbsolutePath().normalize().toString());
        task.setAssetType(asset.type());
        task.setRuleId(rule.getId());
        task.setOperationType(rule.getOperation().name());
        task.setStatus(MediaTask.TaskStatus.PENDING);
        taskRepository.save(task);
        eventPublisher.publishTaskCreated(task);

        if (isDebug) {
            log.debug("Task created: id={}, ruleId={}, sourcePath={}, assetType={}",
                    task.getId(), rule.getId(), task.getSourcePath(), task.getAssetType());
        }

        try {
            task.setStatus(MediaTask.TaskStatus.PROCESSING);
            taskRepository.save(task);
            eventPublisher.publishTaskProcessing(task);

            // Step 2: guessit 解析文件名
            ParseResult parseResult = parserClient.parse(asset.displayName());
            fillParsedFields(task, parseResult);

            // Step 3: 确定媒体类型
            Optional<MediaTask.MediaType> mediaType = resolveMediaType(rule, parseResult, task.getAssetType());
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
            task.setConfirmationSource(MediaTask.ConfirmationSource.AUTO_MATCH);
            taskRepository.save(task);
            continueAfterMetadataConfirmed(task, topMatch);

        } catch (Exception e) {
            log.error("Pipeline failed: asset={}, taskId={}", asset.rootPath(), task.getId(), e);
            recordFailure(task, e);
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
     *   3. 优先使用已保存的 TaskCandidate 快照作为确认元数据
     *      - 只有用户确认的 TMDB ID 不在候选表中时，才调用 MetadataMatcher.getById() 创建手动候选
     *   4. 更新 MediaTask 的 tmdbId、mediaType、confirmedTitle、confirmedYear 等字段
     *   5. 继续执行 Step 6-8：重命名 -> 生成 NFO -> DONE + WebSocket 通知
     *
     * @param taskId    任务 ID
     * @param tmdbId    用户选择的 TMDB ID
     * @param mediaType 用户确认的媒体类型
     */
    @SuppressWarnings("null")
    public void confirm(Long taskId, Long tmdbId, String mediaType) {
        confirm(taskId, tmdbId, mediaType, MediaTask.ConfirmationSource.MANUAL_SINGLE);
    }

    @SuppressWarnings("null")
    public void confirm(Long taskId, Long tmdbId, String mediaType, MediaTask.ConfirmationSource confirmationSource) {
        MediaTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (!MediaTask.TaskStatus.AWAITING_CONFIRMATION.equals(task.getStatus())) {
            throw new IllegalStateException("Task is not awaiting confirmation: " + taskId);
        }
        ensureSourceFileExists(task);

        MediaTask.MediaType confirmedType = MediaTask.MediaType.valueOf(mediaType);
        TaskCandidate selectedCandidate = candidateRepository
                .findByTask_IdAndTmdbIdAndMediaType(taskId, tmdbId, confirmedType)
                .orElseGet(() -> createManualCandidate(task, tmdbId, confirmedType));

        markCandidateSelected(taskId, selectedCandidate);

        MatchResult match = toMatchResult(selectedCandidate);
        applyMatchToTask(task, match);
        task.setConfirmationSource(confirmationSource);
        taskRepository.save(task);
        continueAfterMetadataConfirmed(task, match);
    }

    /**
     * 批量确认使用异步整理，避免一个 HTTP 请求等待多条任务的 TMDB 详情、文件操作和 NFO 生成。
     * 该方法只负责接收确认并把任务移出待确认队列；后续完成/失败通过任务状态和 WebSocket 体现。
     */
    @SuppressWarnings("null")
    public void submitConfirm(Long taskId, Long tmdbId, String mediaType, MediaTask.ConfirmationSource confirmationSource) {
        MediaTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (!MediaTask.TaskStatus.AWAITING_CONFIRMATION.equals(task.getStatus())) {
            throw new IllegalStateException("Task is not awaiting confirmation: " + taskId);
        }
        ensureSourceFileExists(task);

        MediaTask.MediaType.valueOf(mediaType);
        task.setStatus(MediaTask.TaskStatus.PROCESSING);
        task.setConfirmationSource(confirmationSource);
        taskRepository.save(task);
        eventPublisher.publishTaskProcessing(task);

        manualConfirmationExecutor.submit(() -> completeSubmittedConfirm(taskId, tmdbId, mediaType, confirmationSource));
    }

    public QueueRecognitionResponse updateRecognition(Long taskId, QueueRecognitionRequest request) {
        MediaTask task = updateRecognitionFields(taskId, request);
        return new QueueRecognitionResponse(task, candidateRepository.findByTask_IdOrderByRankAsc(taskId));
    }

    public QueueRecognitionResponse updateRecognitionAndRematch(Long taskId, QueueRecognitionRequest request) {
        MediaTask task = updateRecognitionFields(taskId, request);
        ParseResult parseResult = toParseResult(task);
        List<TaskCandidate> candidates = saveCandidates(task, metadataMatcher.search(parseResult));
        task.setMatchConfidence(candidates.isEmpty() ? null : candidates.getFirst().getConfidence());
        taskRepository.save(task);
        return new QueueRecognitionResponse(task, candidates);
    }

    private MediaTask updateRecognitionFields(Long taskId, QueueRecognitionRequest request) {
        MediaTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (!MediaTask.TaskStatus.AWAITING_CONFIRMATION.equals(task.getStatus())) {
            throw new IllegalStateException("Only awaiting confirmation tasks can be edited: " + taskId);
        }

        if (request == null) {
            throw new IllegalArgumentException("Recognition request is required");
        }

        String parsedTitle = request.getParsedTitle() == null ? "" : request.getParsedTitle().trim();
        if (parsedTitle.isBlank()) {
            throw new IllegalArgumentException("Parsed title is required");
        }

        MediaTask.MediaType mediaType = parseRecognitionMediaType(request.getMediaType());
        task.setMediaType(mediaType);
        task.setParsedTitle(parsedTitle);
        task.setParsedYear(request.getParsedYear());
        if (MediaTask.MediaType.TV_SHOW.equals(mediaType)) {
            if (request.getParsedSeason() == null || request.getParsedEpisode() == null) {
                throw new IllegalArgumentException("Season and episode are required for TV show tasks");
            }
            task.setParsedSeason(request.getParsedSeason());
            task.setParsedEpisode(request.getParsedEpisode());
        } else {
            task.setParsedSeason(null);
            task.setParsedEpisode(null);
        }
        return taskRepository.save(task);
    }

    private MediaTask.MediaType parseRecognitionMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("Media type is required");
        }
        try {
            return MediaTask.MediaType.valueOf(mediaType.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported media type: " + mediaType, e);
        }
    }

    private ParseResult toParseResult(MediaTask task) {
        ParseResult parseResult = new ParseResult();
        parseResult.setTitle(task.getParsedTitle());
        parseResult.setYear(task.getParsedYear());
        parseResult.setSeason(task.getParsedSeason());
        parseResult.setEpisode(task.getParsedEpisode());
        parseResult.setScreenSize(task.getParsedResolution());
        applyMediaTypeToParseResult(parseResult, task.getMediaType());
        return parseResult;
    }

    @SuppressWarnings("null")
    private void completeSubmittedConfirm(
            Long taskId,
            Long tmdbId,
            String mediaType,
            MediaTask.ConfirmationSource confirmationSource
    ) {
        try {
            MediaTask task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
            ensureSourceFileExists(task);
            MediaTask.MediaType confirmedType = MediaTask.MediaType.valueOf(mediaType);
            TaskCandidate selectedCandidate = candidateRepository
                    .findByTask_IdAndTmdbIdAndMediaType(taskId, tmdbId, confirmedType)
                    .orElseGet(() -> createManualCandidate(task, tmdbId, confirmedType));

            markCandidateSelected(taskId, selectedCandidate);

            MatchResult match = toMatchResult(selectedCandidate);
            applyMatchToTask(task, match);
            task.setConfirmationSource(confirmationSource);
            taskRepository.save(task);
            continueAfterMetadataConfirmed(task, match);
        } catch (Exception e) {
            log.error("Submitted manual confirmation failed: taskId={}", taskId, e);
            taskRepository.findById(taskId).ifPresent(task -> recordFailure(task, e));
        }
    }

    private void ensureSourceFileExists(MediaTask task) {
        Path sourcePath = Paths.get(task.getSourcePath()).toAbsolutePath().normalize();
        if (Files.exists(sourcePath)) {
            return;
        }

        failTaskBecauseSourceMissing(task);
        throw new IllegalStateException(SOURCE_MISSING_ERROR_MESSAGE);
    }

    private void failTaskBecauseSourceMissing(MediaTask task) {
        recordFailure(
                task,
                new MediaTaskFailureException(
                        MediaTask.TaskErrorCode.SOURCE_MISSING,
                        SOURCE_MISSING_ERROR_MESSAGE
                )
        );
        log.info("Task failed because source file is missing: taskId={}, sourcePath={}",
                task.getId(), task.getSourcePath());
    }

    void recordFailure(MediaTask task, Exception error) {
        MediaTask.TaskErrorCode errorCode = resolveErrorCode(error);
        String errorMessage = resolveErrorMessage(error);
        LocalDateTime now = LocalDateTime.now();

        Optional<MediaTask> existingFailure = taskRepository
                .findFirstBySourcePathAndRuleIdAndStatusAndErrorCodeOrderByUpdatedAtDesc(
                        task.getSourcePath(),
                        task.getRuleId(),
                        MediaTask.TaskStatus.FAILED,
                        errorCode
                );

        if (existingFailure.isPresent() && !Objects.equals(existingFailure.get().getId(), task.getId())) {
            MediaTask existing = existingFailure.get();
            existing.setFailureCount(nextFailureCount(existing.getFailureCount()));
            existing.setLastFailedAt(now);
            existing.setErrorCode(errorCode);
            existing.setErrorMessage(errorMessage);
            taskRepository.save(existing);

            if (task.getId() != null) {
                candidateRepository.deleteAll(candidateRepository.findByTask_IdOrderByRankAsc(task.getId()));
                taskRepository.delete(task);
            }

            eventPublisher.publishTaskFailed(existing);
            log.info("Repeated failure merged: sourcePath={}, errorCode={}, existingTaskId={}, failureCount={}",
                    existing.getSourcePath(), errorCode, existing.getId(), existing.getFailureCount());
            return;
        }

        task.setStatus(MediaTask.TaskStatus.FAILED);
        task.setErrorCode(errorCode);
        task.setFailureCount(firstFailureCount(task.getFailureCount()));
        task.setLastFailedAt(now);
        task.setErrorMessage(errorMessage);
        taskRepository.save(task);
        eventPublisher.publishTaskFailed(task);
        emailNotificationService.notifyTaskFailed(task);
    }

    private MediaTask.TaskErrorCode resolveErrorCode(Exception error) {
        if (error instanceof MediaTaskFailureException failureException) {
            return failureException.getErrorCode();
        }
        return MediaTask.TaskErrorCode.PIPELINE_FAILED;
    }

    private String resolveErrorMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "任务处理失败" : message;
    }

    private int firstFailureCount(Integer current) {
        return current == null || current < 1 ? 1 : current;
    }

    private int nextFailureCount(Integer current) {
        return current == null || current < 1 ? 2 : current + 1;
    }

    private void fillParsedFields(MediaTask task, ParseResult parseResult) {
        task.setParsedTitle(parseResult.getTitle());
        task.setParsedYear(parseResult.getYear());
        task.setParsedSeason(parseResult.getSeason());
        task.setParsedEpisode(parseResult.getEpisode());
        task.setParsedResolution(parseResult.getScreenSize());
        taskRepository.save(task);
    }

    private Optional<MediaTask.MediaType> resolveMediaType(
            WatchRule rule,
            ParseResult parseResult,
            MediaAssetType assetType
    ) {
        if (MediaAssetType.BLURAY_DIRECTORY.equals(assetType)) {
            if (WatchRule.RuleMediaType.TV_SHOW.equals(rule.getMediaType())) {
                throw new IllegalStateException("当前版本暂不支持剧集蓝光原盘");
            }
            return Optional.of(MediaTask.MediaType.MOVIE);
        }

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
        // Re-running a task should replace its candidate snapshot rather than append duplicates.
        candidateRepository.deleteAll(Objects.requireNonNull(candidateRepository.findByTask_IdOrderByRankAsc(task.getId())));

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

            WatchRule rule = loadRule(task);
            Path sourceFile = Paths.get(task.getSourcePath()).toAbsolutePath().normalize();
            Path sourceParent = sourceFile.getParent();
            Path target = assetOrganizerService.organize(task);
            task.setTargetPath(target.toString());
            taskRepository.save(task);

            boolean hasUserNfo = moveAssociatedFiles(task, rule, target);
            if (Boolean.TRUE.equals(rule.getGenerateNfo()) && !hasUserNfo) {
                nfoGeneratorService.generate(task, match, target);
            } else {
                log.debug("NFO generation skipped: taskId={}, generateNfo={}, hasUserNfo={}",
                        task.getId(), rule.getGenerateNfo(), hasUserNfo);
            }

            task.setStatus(MediaTask.TaskStatus.DONE);
            task.setErrorMessage(null);
            task.setErrorCode(null);
            task.setFailureCount(0);
            task.setLastFailedAt(null);
            taskRepository.save(task);
            eventPublisher.publishTaskDone(task);
            cleanupEmptySourceDirs(rule, sourceParent);
            log.info("Pipeline completed: taskId={}, target={}", task.getId(), target);
        } catch (Exception e) {
            log.error("Pipeline finalization failed: taskId={}", task.getId(), e);
            recordFailure(task, e);
        }
    }

    private WatchRule loadRule(MediaTask task) {
        Long ruleId = Objects.requireNonNull(task.getRuleId(), "MediaTask.ruleId is required");
        return watchRuleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("WatchRule not found: " + ruleId));
    }

    private boolean moveAssociatedFiles(MediaTask task, WatchRule rule, Path targetFile) {
        if (MediaAssetType.BLURAY_DIRECTORY.equals(task.getAssetType())) {
            return false;
        }
        if (!Boolean.TRUE.equals(rule.getMoveAssociatedFiles())) {
            return false;
        }

        Path sourceFile = Paths.get(task.getSourcePath()).toAbsolutePath().normalize();
        Path sourceDir = sourceFile.getParent();
        Path targetDir = targetFile.getParent();
        if (sourceDir == null || targetDir == null || !Files.isDirectory(sourceDir)) {
            return false;
        }

        String sourceBase = basename(sourceFile.getFileName().toString());
        String targetBase = basename(targetFile.getFileName().toString());
        boolean userNfoFound = false;

        try (var stream = Files.list(sourceDir)) {
            for (Path associated : stream.filter(Files::isRegularFile).toList()) {
                String filename = associated.getFileName().toString();
                String lower = filename.toLowerCase();
                Path destination = null;

                if (isSameBaseAssociated(filename, sourceBase)) {
                    String suffix = filename.substring(sourceBase.length());
                    destination = targetDir.resolve(targetBase + suffix);
                    if (lower.endsWith(".nfo")) {
                        userNfoFound = true;
                    }
                } else if (isGenericCover(lower)) {
                    destination = targetDir.resolve(filename);
                }

                if (destination != null) {
                    operateAssociatedFile(rule, associated, destination);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to inspect associated files: taskId={}, sourceDir={}, error={}",
                    task.getId(), sourceDir, e.getMessage());
        }

        return userNfoFound;
    }

    private void operateAssociatedFile(WatchRule rule, Path source, Path destination) {
        try {
            if (Files.exists(destination)) {
                log.warn("Associated file target already exists, skipping: source={}, target={}", source, destination);
                return;
            }
            FileOperationStrategy strategy = resolveStrategy(rule.getOperation());
            strategy.execute(source, destination);
            log.info("Associated file processed: operation={}, source={} -> target={}",
                    rule.getOperation(), source, destination);
        } catch (IOException e) {
            log.warn("Failed to process associated file: operation={}, source={}, target={}, error={}",
                    rule.getOperation(), source, destination, e.getMessage());
        }
    }

    private FileOperationStrategy resolveStrategy(FileOperationStrategy.OperationType operation) {
        return fileOperationStrategies.values().stream()
                .filter(strategy -> strategy.getType() == operation)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown file operation strategy: " + operation));
    }

    private void cleanupEmptySourceDirs(WatchRule rule, Path startDir) {
        if (!Boolean.TRUE.equals(rule.getCleanupEmptyDirs())
                || !com.mediamarshal.service.rename.FileOperationStrategy.OperationType.MOVE.equals(rule.getOperation())
                || startDir == null) {
            return;
        }

        Path sourceRoot = Paths.get(rule.getSourceDir()).toAbsolutePath().normalize();
        Path current = startDir.toAbsolutePath().normalize();
        while (current.startsWith(sourceRoot) && !current.equals(sourceRoot)) {
            try (var stream = Files.list(current)) {
                if (stream.findAny().isPresent()) {
                    return;
                }
            } catch (IOException e) {
                log.warn("Failed to inspect empty source directory: dir={}, error={}", current, e.getMessage());
                return;
            }

            try {
                Files.delete(current);
                log.info("Empty source directory deleted: {}", current);
            } catch (IOException e) {
                log.warn("Failed to delete empty source directory: dir={}, error={}", current, e.getMessage());
                return;
            }
            current = current.getParent();
            if (current == null) {
                return;
            }
        }
    }

    private boolean isSameBaseAssociated(String filename, String sourceBase) {
        String lower = filename.toLowerCase();
        return filename.startsWith(sourceBase + ".")
                && (lower.endsWith(".srt")
                || lower.endsWith(".ass")
                || lower.endsWith(".ssa")
                || lower.endsWith(".sub")
                || lower.endsWith(".idx")
                || lower.endsWith(".nfo")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp"));
    }

    private boolean isGenericCover(String lowerFilename) {
        return lowerFilename.equals("poster.jpg")
                || lowerFilename.equals("folder.jpg")
                || lowerFilename.equals("cover.jpg");
    }

    private String basename(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private TaskCandidate createManualCandidate(MediaTask task, Long tmdbId, MediaTask.MediaType mediaType) {
        MatchResult match = getByIdWithRetry(tmdbId, mediaType);
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

    private MatchResult getByIdWithRetry(Long tmdbId, MediaTask.MediaType mediaType) {
        int attempts = getConfirmMetadataRetryAttempts();
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return metadataMatcher.getById(String.valueOf(tmdbId), mediaType.name());
            } catch (RuntimeException e) {
                lastError = e;
                if (attempt >= attempts) {
                    break;
                }
                log.warn("TMDB detail lookup failed, will retry: tmdbId={}, mediaType={}, attempt={}/{}, error={}",
                        tmdbId, mediaType, attempt, attempts, e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }
        throw lastError != null ? lastError : new IllegalStateException("TMDB detail lookup failed: " + tmdbId);
    }

    private int getConfirmMetadataRetryAttempts() {
        String value = settingsService.get("tmdb.confirm-retry-attempts", "3");
        try {
            return Math.max(Integer.parseInt(value), 1);
        } catch (NumberFormatException e) {
            log.warn("Invalid tmdb.confirm-retry-attempts='{}', fallback to 3", value);
            return 3;
        }
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            TimeUnit.MILLISECONDS.sleep(300L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry TMDB detail lookup", e);
        }
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
