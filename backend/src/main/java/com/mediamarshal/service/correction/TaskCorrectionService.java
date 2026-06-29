package com.mediamarshal.service.correction;

import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.model.dto.TaskCorrectionApplyResponse;
import com.mediamarshal.model.dto.TaskCorrectionOperation;
import com.mediamarshal.model.dto.TaskCorrectionPreview;
import com.mediamarshal.model.dto.TaskCorrectionRematchResponse;
import com.mediamarshal.model.dto.TaskCorrectionRequest;
import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.TaskCandidate;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.repository.TaskCandidateRepository;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.matcher.MetadataMatcher;
import com.mediamarshal.service.nfo.NfoGeneratorService;
import com.mediamarshal.service.rename.RenameService;
import com.mediamarshal.service.rename.TemplateRenderer;
import com.mediamarshal.service.settings.SettingsService;
import com.mediamarshal.websocket.EventPublisher;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCorrectionService {

    private static final String BLURAY_FALLBACK_TEMPLATE = "{title} ({year})";

    private static final List<String> ASSOCIATED_EXTENSIONS = List.of(
            ".srt", ".ass", ".ssa", ".sub", ".idx", ".nfo", ".jpg", ".jpeg", ".png", ".webp", ".md5"
    );

    private static final List<String> GENERIC_COVER_NAMES = List.of(
            "poster.jpg", "folder.jpg", "cover.jpg"
    );

    private final MediaTaskRepository taskRepository;
    private final TaskCandidateRepository candidateRepository;
    private final WatchRuleRepository watchRuleRepository;
    private final MetadataMatcher metadataMatcher;
    private final RenameService renameService;
    private final TemplateRenderer templateRenderer;
    private final NfoGeneratorService nfoGeneratorService;
    private final SettingsService settingsService;
    private final EventPublisher eventPublisher;

    public TaskCorrectionRematchResponse rematch(Long taskId, TaskCorrectionRequest request) {
        MediaTask task = loadTask(taskId);
        ensureRematchSupported(task);
        RecognitionInput input = parseRecognition(request);
        ParseResult parseResult = toParseResult(task, input);
        List<MatchResult> candidates = metadataMatcher.search(parseResult).stream()
                .sorted(Comparator.comparing(
                        MatchResult::getConfidence,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
        return new TaskCorrectionRematchResponse(candidates);
    }

    public TaskCorrectionPreview preview(Long taskId, TaskCorrectionRequest request) {
        return buildPlan(taskId, request).preview();
    }

    @Transactional
    public TaskCorrectionApplyResponse apply(Long taskId, TaskCorrectionRequest request) {
        CorrectionPlan plan = buildPlan(taskId, request);
        if (!plan.preview().isCanApply()) {
            throw new IllegalStateException(String.join("；", plan.preview().getBlockers()));
        }

        List<FileMove> executedMoves = new ArrayList<>();
        try {
            for (FileMove move : plan.moves()) {
                movePath(move.source(), move.target());
                executedMoves.add(move);
            }
            if (plan.regenerateNfo()) {
                nfoGeneratorService.generate(plan.correctedTask(), plan.selectedMatch(), plan.correctedTarget());
            }
        } catch (Exception e) {
            rollbackMoves(executedMoves);
            throw new IllegalStateException("修正文件操作失败，数据库记录未更新：" + e.getMessage(), e);
        }

        MediaTask correctedTask = taskRepository.saveAndFlush(plan.correctedTask());
        saveSelectedCandidate(correctedTask, plan.selectedMatch());

        MediaTask originalTask = plan.originalTask();
        originalTask.setStatus(MediaTask.TaskStatus.CORRECTED);
        originalTask.setCorrectedToTaskId(correctedTask.getId());
        originalTask.setCorrectedAt(LocalDateTime.now());
        taskRepository.saveAndFlush(originalTask);

        correctedTask.setUpdatedAt(LocalDateTime.now());
        correctedTask = taskRepository.saveAndFlush(correctedTask);

        cleanupEmptyTargetDirs(plan.cleanupStartDir(), plan.cleanupStopDir(), plan.preview().getWarnings());

        eventPublisher.publishTaskCorrected(originalTask);
        eventPublisher.publishTaskDone(correctedTask);
        log.info("Task correction applied: originalTaskId={}, correctedTaskId={}, target={}",
                originalTask.getId(), correctedTask.getId(), correctedTask.getTargetPath());
        return new TaskCorrectionApplyResponse(originalTask, correctedTask, plan.preview());
    }

    private CorrectionPlan buildPlan(Long taskId, TaskCorrectionRequest request) {
        MediaTask originalTask = loadTask(taskId);
        TaskCorrectionPreview preview = new TaskCorrectionPreview();
        preview.setCurrentTargetPath(originalTask.getTargetPath());

        RecognitionInput recognition = parseRecognitionOrBlock(request, preview);
        Long tmdbId = request == null ? null : request.getTmdbId();
        boolean regenerateNfo = request != null && Boolean.TRUE.equals(request.getRegenerateNfo());

        Path currentTarget = parseCurrentTarget(originalTask, preview);
        WatchRule rule = loadRuleOrBlock(originalTask, preview);
        validateTaskEligibility(originalTask, currentTarget, recognition, preview);
        warnIfSourceMissing(originalTask, preview);

        MatchResult selectedMatch = null;
        MediaTask correctedTask = null;
        Path correctedTarget = null;
        List<FileMove> moves = new ArrayList<>();
        Path cleanupStartDir = null;
        Path cleanupStopDir = null;

        if (tmdbId == null) {
            preview.getBlockers().add("请选择 TMDB 候选");
        }

        if (preview.getBlockers().isEmpty()) {
            try {
                selectedMatch = getByIdWithRetry(tmdbId, recognition.mediaType());
                correctedTask = buildCorrectedTask(originalTask, recognition, selectedMatch);
                fillEpisodeTitleIfNeeded(correctedTask, selectedMatch);
                applyMatchToTask(correctedTask, selectedMatch);
                correctedTarget = buildCorrectedTarget(originalTask, correctedTask, rule, currentTarget);
                correctedTask.setTargetPath(correctedTarget.toString());
                preview.setCorrectedTargetPath(correctedTarget.toString());
                preview.setSelectedMatch(selectedMatch);
                preview.setSameTargetPath(isSamePath(currentTarget, correctedTarget));
                collectTargetBlockers(originalTask, currentTarget, correctedTarget, preview);
                collectOperations(originalTask, rule, currentTarget, correctedTarget, regenerateNfo, preview, moves);
                cleanupStartDir = cleanupStartDir(originalTask, currentTarget);
                cleanupStopDir = ruleTargetRoot(rule);
            } catch (Exception e) {
                preview.getBlockers().add(e.getMessage() == null ? "修正预览计算失败" : e.getMessage());
            }
        }

        preview.getOperations().add(TaskCorrectionOperation.builder()
                .type(TaskCorrectionOperation.OperationType.CREATE_CORRECTION_TASK)
                .description("创建新的已完成修正任务记录")
                .build());
        preview.getOperations().add(TaskCorrectionOperation.builder()
                .type(TaskCorrectionOperation.OperationType.MARK_ORIGINAL_CORRECTED)
                .description("将原任务标记为已修正并保留历史")
                .build());
        preview.setCanApply(preview.getBlockers().isEmpty());
        return new CorrectionPlan(
                originalTask,
                correctedTask,
                selectedMatch,
                correctedTarget,
                moves,
                cleanupStartDir,
                cleanupStopDir,
                regenerateNfo,
                preview
        );
    }

    private MediaTask loadTask(Long taskId) {
        return taskRepository.findById(Objects.requireNonNull(taskId, "taskId is required"))
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    private void ensureRematchSupported(MediaTask task) {
        if (MediaTask.TaskStatus.CORRECTED.equals(task.getStatus())) {
            throw new IllegalStateException("已修正历史记录不能再次修正");
        }
        if (!MediaTask.TaskStatus.DONE.equals(task.getStatus())) {
            throw new IllegalStateException("只有已完成任务可以修正");
        }
        if (MediaAssetType.ISO_IMAGE.equals(task.getAssetType())) {
            throw new IllegalStateException("ISO 镜像修正将在 ISO 整理能力启用后支持");
        }
    }

    private void validateTaskEligibility(
            MediaTask task,
            Path currentTarget,
            RecognitionInput recognition,
            TaskCorrectionPreview preview
    ) {
        if (MediaTask.TaskStatus.CORRECTED.equals(task.getStatus())) {
            preview.getBlockers().add("已修正历史记录不能再次修正");
        } else if (!MediaTask.TaskStatus.DONE.equals(task.getStatus())) {
            preview.getBlockers().add("只有已完成任务可以修正");
        }

        MediaAssetType assetType = task.getAssetType() == null ? MediaAssetType.VIDEO_FILE : task.getAssetType();
        if (MediaAssetType.ISO_IMAGE.equals(assetType)) {
            preview.getBlockers().add("ISO 镜像修正将在 ISO 整理能力启用后支持");
        }
        if (MediaAssetType.BLURAY_DIRECTORY.equals(assetType)
                && recognition != null
                && MediaTask.MediaType.TV_SHOW.equals(recognition.mediaType())) {
            preview.getBlockers().add("第一版暂不支持剧集蓝光原盘修正");
        }

        if (currentTarget == null) {
            return;
        }
        if (!Files.exists(currentTarget, LinkOption.NOFOLLOW_LINKS)) {
            preview.getBlockers().add("当前目标路径不存在");
            return;
        }
        if (MediaAssetType.BLURAY_DIRECTORY.equals(assetType)) {
            if (!Files.isDirectory(currentTarget)) {
                preview.getBlockers().add("当前目标路径不是蓝光根目录");
            }
        } else if (!Files.isRegularFile(currentTarget)) {
            preview.getBlockers().add("当前目标路径不是普通视频文件");
        }
    }

    private Path parseCurrentTarget(MediaTask task, TaskCorrectionPreview preview) {
        String targetPath = task.getTargetPath();
        if (targetPath == null || targetPath.isBlank()) {
            preview.getBlockers().add("当前任务缺少目标路径");
            return null;
        }
        try {
            return Paths.get(targetPath).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            preview.getBlockers().add("当前目标路径格式无效");
            return null;
        }
    }

    private WatchRule loadRuleOrBlock(MediaTask task, TaskCorrectionPreview preview) {
        Long ruleId = task.getRuleId();
        if (ruleId == null) {
            preview.getBlockers().add("任务缺少监控规则信息，无法计算修正目标路径");
            return null;
        }
        return watchRuleRepository.findById(ruleId).orElseGet(() -> {
            preview.getBlockers().add("任务所属监控规则不存在，无法计算修正目标路径");
            return null;
        });
    }

    private void warnIfSourceMissing(MediaTask task, TaskCorrectionPreview preview) {
        String sourcePath = task.getSourcePath();
        if (sourcePath == null || sourcePath.isBlank()) {
            preview.getWarnings().add("源路径为空，本次修正不依赖源文件");
            return;
        }
        try {
            if (!Files.exists(Paths.get(sourcePath).toAbsolutePath().normalize())) {
                preview.getWarnings().add("源路径不存在，本次修正不依赖源文件");
            }
        } catch (RuntimeException e) {
            preview.getWarnings().add("源路径格式无效，本次修正不依赖源文件");
        }
    }

    private RecognitionInput parseRecognitionOrBlock(TaskCorrectionRequest request, TaskCorrectionPreview preview) {
        try {
            return parseRecognition(request);
        } catch (RuntimeException e) {
            preview.getBlockers().add(e.getMessage());
            return null;
        }
    }

    private RecognitionInput parseRecognition(TaskCorrectionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("修正请求不能为空");
        }
        String parsedTitle = request.getParsedTitle() == null ? "" : request.getParsedTitle().trim();
        if (parsedTitle.isBlank()) {
            throw new IllegalArgumentException("解析标题不能为空");
        }

        MediaTask.MediaType mediaType = parseMediaType(request.getMediaType());
        Integer season = null;
        Integer episode = null;
        Integer episodeEnd = null;
        if (MediaTask.MediaType.TV_SHOW.equals(mediaType)) {
            if (request.getParsedSeason() == null || request.getParsedEpisode() == null) {
                throw new IllegalArgumentException("剧集任务必须填写季号和集号");
            }
            season = request.getParsedSeason();
            episode = request.getParsedEpisode();
            episodeEnd = normalizeEpisodeEnd(episode, request.getParsedEpisodeEnd());
        }
        return new RecognitionInput(
                mediaType,
                parsedTitle,
                request.getParsedYear(),
                season,
                episode,
                episodeEnd
        );
    }

    private MediaTask.MediaType parseMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("媒体类型不能为空");
        }
        try {
            return MediaTask.MediaType.valueOf(mediaType.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的媒体类型：" + mediaType, e);
        }
    }

    private Integer normalizeEpisodeEnd(Integer episodeStart, Integer episodeEnd) {
        if (episodeStart == null || episodeEnd == null || episodeEnd.equals(episodeStart)) {
            return null;
        }
        if (episodeEnd < episodeStart) {
            throw new IllegalArgumentException("集结束号必须大于起始集号");
        }
        return episodeEnd;
    }

    private ParseResult toParseResult(MediaTask task, RecognitionInput input) {
        ParseResult parseResult = new ParseResult();
        parseResult.setTitle(input.parsedTitle());
        parseResult.setYear(input.parsedYear());
        parseResult.setSeason(input.parsedSeason());
        parseResult.setEpisode(input.parsedEpisode());
        parseResult.setEpisodeEnd(input.parsedEpisodeEnd());
        parseResult.setScreenSize(task.getParsedResolution());
        parseResult.setVideoCodec(task.getParsedCodec());
        parseResult.setReleaseGroup(task.getParsedReleaseGroup());
        parseResult.setType(MediaTask.MediaType.TV_SHOW.equals(input.mediaType()) ? "episode" : "movie");
        return parseResult;
    }

    private MediaTask buildCorrectedTask(MediaTask originalTask, RecognitionInput input, MatchResult selectedMatch) {
        MediaTask corrected = new MediaTask();
        corrected.setSourcePath(originalTask.getSourcePath());
        corrected.setAssetType(originalTask.getAssetType() == null ? MediaAssetType.VIDEO_FILE : originalTask.getAssetType());
        corrected.setStatus(MediaTask.TaskStatus.DONE);
        corrected.setMediaType(input.mediaType());
        corrected.setParsedTitle(input.parsedTitle());
        corrected.setParsedYear(input.parsedYear());
        corrected.setParsedSeason(input.parsedSeason());
        corrected.setParsedEpisode(input.parsedEpisode());
        corrected.setParsedEpisodeEnd(input.parsedEpisodeEnd());
        corrected.setParsedResolution(originalTask.getParsedResolution());
        corrected.setParsedCodec(originalTask.getParsedCodec());
        corrected.setParsedReleaseGroup(originalTask.getParsedReleaseGroup());
        corrected.setRuleId(originalTask.getRuleId());
        corrected.setOperationType("MOVE");
        corrected.setConfirmationSource(MediaTask.ConfirmationSource.MANUAL_CORRECTION);
        corrected.setCorrectedFromTaskId(originalTask.getId());
        applyMatchToTask(corrected, selectedMatch);
        return corrected;
    }

    private void applyMatchToTask(MediaTask task, MatchResult match) {
        task.setTmdbId(Long.valueOf(match.getSourceId()));
        task.setMediaType(MediaTask.MediaType.valueOf(match.getMediaType()));
        task.setConfirmedTitle(match.getTitle());
        task.setConfirmedOriginalTitle(match.getOriginalTitle());
        task.setConfirmedYear(match.getYear());
        task.setConfirmedGenre1(genreAt(match, 0));
        task.setConfirmedGenre2(genreAt(match, 1));
        task.setConfirmedGenre3(genreAt(match, 2));
        task.setConfirmedGenre4(genreAt(match, 3));
        task.setConfirmedCountry(match.getCountry());
        task.setConfirmedEpisodeTitle(match.getEpisodeTitle());
        task.setMatchConfidence(match.getConfidence());
    }

    private Path buildCorrectedTarget(
            MediaTask originalTask,
            MediaTask correctedTask,
            WatchRule rule,
            Path currentTarget
    ) {
        if (rule == null) {
            throw new IllegalStateException("任务所属监控规则不存在，无法计算修正目标路径");
        }
        if (MediaAssetType.BLURAY_DIRECTORY.equals(originalTask.getAssetType())) {
            return buildBlurayTargetRoot(correctedTask, rule);
        }
        String relativePath = renameService.renderRelativePath(correctedTask, rule, extension(currentTarget));
        return renameService.resolveSafeTargetPath(rule.getTargetDir(), relativePath);
    }

    private Path buildBlurayTargetRoot(MediaTask task, WatchRule rule) {
        String relativePath = renameService.renderRelativePath(task, rule, "");
        renameService.resolveSafeTargetPath(rule.getTargetDir(), relativePath);

        Path relative = Paths.get(relativePath).normalize();
        Path parent = relative.getParent();
        if (parent == null || parent.toString().isBlank()) {
            String fallback = templateRenderer.render(
                    BLURAY_FALLBACK_TEMPLATE,
                    renameService.buildVariables(task, "")
            );
            return renameService.resolveSafeTargetPath(rule.getTargetDir(), fallback);
        }
        return renameService.resolveSafeTargetPath(rule.getTargetDir(), parent.toString());
    }

    private void collectTargetBlockers(
            MediaTask originalTask,
            Path currentTarget,
            Path correctedTarget,
            TaskCorrectionPreview preview
    ) {
        if (currentTarget == null || correctedTarget == null) {
            return;
        }
        if (MediaAssetType.BLURAY_DIRECTORY.equals(originalTask.getAssetType())
                && correctedTarget.startsWith(currentTarget)
                && !isSamePath(currentTarget, correctedTarget)) {
            preview.getBlockers().add("蓝光原盘不能移动到自身子目录");
        }
        if (Files.exists(correctedTarget, LinkOption.NOFOLLOW_LINKS) && !isSamePath(currentTarget, correctedTarget)) {
            preview.getBlockers().add("新目标路径已存在");
        }
    }

    private void collectOperations(
            MediaTask originalTask,
            WatchRule rule,
            Path currentTarget,
            Path correctedTarget,
            boolean regenerateNfo,
            TaskCorrectionPreview preview,
            List<FileMove> moves
    ) throws IOException {
        if (currentTarget == null || correctedTarget == null) {
            return;
        }
        if (!isSamePath(currentTarget, correctedTarget)) {
            preview.getOperations().add(TaskCorrectionOperation.builder()
                    .type(TaskCorrectionOperation.OperationType.MOVE_MAIN_ASSET)
                    .sourcePath(currentTarget.toString())
                    .targetPath(correctedTarget.toString())
                    .description(MediaAssetType.BLURAY_DIRECTORY.equals(originalTask.getAssetType())
                            ? "移动 / 重命名蓝光根目录"
                            : "移动 / 重命名主视频文件")
                    .build());
            moves.add(new FileMove(currentTarget, correctedTarget));
        }

        if (!MediaAssetType.BLURAY_DIRECTORY.equals(originalTask.getAssetType())) {
            collectAssociatedFileOperations(currentTarget, correctedTarget, preview, moves);
        }

        if (regenerateNfo) {
            Path nfoTarget = MediaAssetType.BLURAY_DIRECTORY.equals(originalTask.getAssetType())
                    ? correctedTarget.resolve("movie.nfo")
                    : replaceExtension(correctedTarget, ".nfo");
            preview.getOperations().add(TaskCorrectionOperation.builder()
                    .type(TaskCorrectionOperation.OperationType.GENERATE_NFO)
                    .targetPath(nfoTarget.toString())
                    .description("按修正后的 TMDB 信息重新生成 NFO")
                    .build());
            preview.getWarnings().add("重新生成 NFO 可能覆盖目标位置已有 NFO");
        }

        Path cleanupStart = cleanupStartDir(originalTask, currentTarget);
        Path cleanupStop = ruleTargetRoot(rule);
        if (cleanupStart != null && cleanupStop != null && !isSamePath(currentTarget, correctedTarget)) {
            preview.getOperations().add(TaskCorrectionOperation.builder()
                    .type(TaskCorrectionOperation.OperationType.CLEAN_EMPTY_DIR)
                    .sourcePath(cleanupStart.toString())
                    .targetPath(cleanupStop.toString())
                    .description("修正成功后清理旧目标空目录")
                    .build());
        }
    }

    private void collectAssociatedFileOperations(
            Path currentTarget,
            Path correctedTarget,
            TaskCorrectionPreview preview,
            List<FileMove> moves
    ) throws IOException {
        Path currentDir = currentTarget.getParent();
        Path correctedDir = correctedTarget.getParent();
        if (currentDir == null || correctedDir == null || !Files.isDirectory(currentDir)) {
            return;
        }

        String currentBase = basename(currentTarget.getFileName().toString());
        String correctedBase = basename(correctedTarget.getFileName().toString());
        boolean md5Found = false;
        boolean genericCoverFound = false;

        try (var stream = Files.list(currentDir)) {
            for (Path associated : stream.filter(Files::isRegularFile).toList()) {
                if (isSamePath(associated, currentTarget)) {
                    continue;
                }
                String filename = associated.getFileName().toString();
                String lower = filename.toLowerCase(Locale.ROOT);
                if (GENERIC_COVER_NAMES.contains(lower)) {
                    genericCoverFound = true;
                    continue;
                }
                if (!isSameBaseAssociated(filename, currentBase)) {
                    continue;
                }
                String suffix = filename.substring(currentBase.length());
                Path destination = correctedDir.resolve(correctedBase + suffix).toAbsolutePath().normalize();
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !isSamePath(associated, destination)) {
                    preview.getBlockers().add("附属文件目标已存在：" + destination);
                    continue;
                }
                if (!isSamePath(associated, destination)) {
                    preview.getOperations().add(TaskCorrectionOperation.builder()
                            .type(TaskCorrectionOperation.OperationType.MOVE_ASSOCIATED_FILE)
                            .sourcePath(associated.toString())
                            .targetPath(destination.toString())
                            .description("移动 / 重命名同名附属文件")
                            .build());
                    moves.add(new FileMove(associated.toAbsolutePath().normalize(), destination));
                }
                if (lower.endsWith(".md5")) {
                    md5Found = true;
                }
            }
        }

        if (md5Found) {
            preview.getWarnings().add(".md5 文件会随文件名移动 / 改名，但不会修改 md5 文件内容");
        }
        if (genericCoverFound) {
            preview.getWarnings().add("poster.jpg / folder.jpg / cover.jpg 通用封面本版本不会自动移动");
        }
    }

    private boolean isSameBaseAssociated(String filename, String sourceBase) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return filename.startsWith(sourceBase + ".")
                && ASSOCIATED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private Path cleanupStartDir(MediaTask task, Path currentTarget) {
        if (currentTarget == null) {
            return null;
        }
        if (MediaAssetType.BLURAY_DIRECTORY.equals(task.getAssetType())) {
            return currentTarget.getParent();
        }
        return currentTarget.getParent();
    }

    private Path ruleTargetRoot(WatchRule rule) {
        if (rule == null || rule.getTargetDir() == null || rule.getTargetDir().isBlank()) {
            return null;
        }
        return Paths.get(rule.getTargetDir()).toAbsolutePath().normalize();
    }

    private void movePath(Path source, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(source, target);
        }
    }

    private void rollbackMoves(List<FileMove> executedMoves) {
        for (int i = executedMoves.size() - 1; i >= 0; i--) {
            FileMove move = executedMoves.get(i);
            try {
                if (Files.exists(move.target(), LinkOption.NOFOLLOW_LINKS)
                        && !Files.exists(move.source(), LinkOption.NOFOLLOW_LINKS)) {
                    movePath(move.target(), move.source());
                }
            } catch (IOException rollbackError) {
                log.warn("Failed to rollback correction move: target={}, source={}, error={}",
                        move.target(), move.source(), rollbackError.getMessage());
            }
        }
    }

    private void cleanupEmptyTargetDirs(Path startDir, Path stopDir, List<String> warnings) {
        if (startDir == null || stopDir == null) {
            return;
        }
        Path current = startDir.toAbsolutePath().normalize();
        Path stop = stopDir.toAbsolutePath().normalize();
        while (current.startsWith(stop) && !current.equals(stop)) {
            try (var stream = Files.list(current)) {
                if (stream.findAny().isPresent()) {
                    return;
                }
            } catch (IOException e) {
                log.warn("Failed to inspect empty target directory: dir={}, error={}", current, e.getMessage());
                warnings.add("旧目标空目录清理失败：" + current);
                return;
            }

            try {
                Files.delete(current);
                log.info("Empty target directory deleted after correction: {}", current);
            } catch (IOException e) {
                log.warn("Failed to delete empty target directory: dir={}, error={}", current, e.getMessage());
                warnings.add("旧目标空目录清理失败：" + current);
                return;
            }
            current = current.getParent();
            if (current == null) {
                return;
            }
        }
    }

    private void saveSelectedCandidate(MediaTask task, MatchResult match) {
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
        candidate.setGenre1(genreAt(match, 0));
        candidate.setGenre2(genreAt(match, 1));
        candidate.setGenre3(genreAt(match, 2));
        candidate.setGenre4(genreAt(match, 3));
        candidate.setCountry(match.getCountry());
        candidate.setEpisodeTitle(match.getEpisodeTitle());
        candidate.setRank(1);
        candidate.setSelected(true);
        candidateRepository.save(candidate);
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
                log.warn("TMDB detail lookup failed during correction, will retry: tmdbId={}, mediaType={}, attempt={}/{}, error={}",
                        tmdbId, mediaType, attempt, attempts, e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }
        throw lastError != null ? lastError : new IllegalStateException("TMDB 详情不可用：" + tmdbId);
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
            Thread.sleep(300L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待重试 TMDB 详情时被中断", e);
        }
    }

    private void fillEpisodeTitleIfNeeded(MediaTask task, MatchResult detail) {
        if (!MediaTask.MediaType.TV_SHOW.name().equals(detail.getMediaType())
                || task.getParsedSeason() == null
                || task.getParsedEpisode() == null
                || task.getParsedEpisodeEnd() != null) {
            detail.setEpisodeTitle(null);
            return;
        }

        try {
            detail.setEpisodeTitle(metadataMatcher.getEpisodeTitle(
                    detail.getSourceId(),
                    task.getParsedSeason(),
                    task.getParsedEpisode()
            ));
        } catch (RuntimeException e) {
            if (renameService.templateUsesVariable(task, "episode_title")) {
                throw new IllegalStateException("TMDB 分集详情不可用，无法填充 {episode_title}", e);
            }
            log.warn("TMDB episode detail lookup failed during correction but template does not use episode_title: taskId={}, tmdbId={}, season={}, episode={}, error={}",
                    task.getCorrectedFromTaskId(), detail.getSourceId(), task.getParsedSeason(), task.getParsedEpisode(), e.getMessage());
            detail.setEpisodeTitle(null);
        }
    }

    private String genreAt(MatchResult match, int index) {
        List<String> genres = match.getGenres();
        if (genres == null || genres.size() <= index) {
            return null;
        }
        String value = genres.get(index);
        return value == null || value.isBlank() ? null : value;
    }

    private String extension(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot) : "";
    }

    private String basename(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private Path replaceExtension(Path file, String newExtension) {
        String filename = file.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String newName = (dot > 0 ? filename.substring(0, dot) : filename) + newExtension;
        return file.resolveSibling(newName);
    }

    private boolean isSamePath(Path left, Path right) {
        if (left == null || right == null) {
            return false;
        }
        Path normalizedLeft = left.toAbsolutePath().normalize();
        Path normalizedRight = right.toAbsolutePath().normalize();
        if (normalizedLeft.equals(normalizedRight)) {
            return true;
        }
        try {
            return Files.exists(normalizedLeft, LinkOption.NOFOLLOW_LINKS)
                    && Files.exists(normalizedRight, LinkOption.NOFOLLOW_LINKS)
                    && Files.isSameFile(normalizedLeft, normalizedRight);
        } catch (IOException e) {
            return false;
        }
    }

    private record RecognitionInput(
            MediaTask.MediaType mediaType,
            String parsedTitle,
            Integer parsedYear,
            Integer parsedSeason,
            Integer parsedEpisode,
            Integer parsedEpisodeEnd
    ) {
    }

    private record FileMove(Path source, Path target) {
    }

    private record CorrectionPlan(
            MediaTask originalTask,
            MediaTask correctedTask,
            MatchResult selectedMatch,
            Path correctedTarget,
            List<FileMove> moves,
            Path cleanupStartDir,
            Path cleanupStopDir,
            boolean regenerateNfo,
            TaskCorrectionPreview preview
    ) {
    }
}
