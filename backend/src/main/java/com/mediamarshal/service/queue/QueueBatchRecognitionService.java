package com.mediamarshal.service.queue;

import com.mediamarshal.model.dto.BatchRecognitionField;
import com.mediamarshal.model.dto.EpisodeAssignmentMode;
import com.mediamarshal.model.dto.EpisodeSortDirection;
import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.model.dto.QueueBatchRecognitionPreview;
import com.mediamarshal.model.dto.QueueBatchRecognitionPreviewItem;
import com.mediamarshal.model.dto.QueueBatchRecognitionRematchResponse;
import com.mediamarshal.model.dto.QueueBatchRecognitionRematchResult;
import com.mediamarshal.model.dto.QueueBatchRecognitionRematchStatus;
import com.mediamarshal.model.dto.QueueBatchRecognitionRequest;
import com.mediamarshal.model.dto.QueueBatchRecognitionSaveResponse;
import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.TaskCandidate;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.repository.TaskCandidateRepository;
import com.mediamarshal.service.matcher.MetadataMatcher;
import com.mediamarshal.websocket.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueBatchRecognitionService {

    private final MediaTaskRepository taskRepository;
    private final TaskCandidateRepository candidateRepository;
    private final MetadataMatcher metadataMatcher;
    private final EventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    public QueueBatchRecognitionPreview preview(QueueBatchRecognitionRequest request) {
        return buildPlan(request).preview();
    }

    @Transactional
    public QueueBatchRecognitionSaveResponse save(QueueBatchRecognitionRequest request) {
        RecognitionPlan plan = buildPlan(request);
        ensureCanApply(plan);
        List<MediaTask> updatedTasks = applyRecognitionFields(plan, false);
        return new QueueBatchRecognitionSaveResponse(updatedTasks.size(), updatedTasks, plan.preview());
    }

    public QueueBatchRecognitionRematchResponse saveAndRematch(QueueBatchRecognitionRequest request) {
        RecognitionPlan plan = buildPlan(request);
        ensureCanApply(plan);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        List<MediaTask> updatedTasks = transactionTemplate.execute(status -> applyRecognitionFields(plan, true));
        if (updatedTasks == null) {
            updatedTasks = List.of();
        }

        List<QueueBatchRecognitionRematchResult> results = new ArrayList<>();
        int matchedCount = 0;
        int emptyCount = 0;
        int failedCount = 0;

        for (MediaTask task : updatedTasks) {
            MediaTask taskForSearch = clearCandidatesForRematch(transactionTemplate, task.getId());

            try {
                List<MatchResult> matches = metadataMatcher.search(toParseResult(taskForSearch));
                RematchPersistResult persisted = persistRematchCandidates(transactionTemplate, task.getId(), matches);
                if (persisted.candidates().isEmpty()) {
                    emptyCount++;
                    results.add(new QueueBatchRecognitionRematchResult(
                            task.getId(),
                            QueueBatchRecognitionRematchStatus.EMPTY,
                            "未匹配到新的候选",
                            List.of()
                    ));
                } else {
                    matchedCount++;
                    results.add(new QueueBatchRecognitionRematchResult(
                            task.getId(),
                            QueueBatchRecognitionRematchStatus.MATCHED,
                            null,
                            persisted.candidates()
                    ));
                }
                eventPublisher.publishAwaitingConfirmation(persisted.task());
            } catch (Exception e) {
                failedCount++;
                resetMatchConfidenceAfterRematchFailure(transactionTemplate, task.getId())
                        .ifPresent(eventPublisher::publishAwaitingConfirmation);
                log.warn("Queue batch recognition rematch failed: taskId={}, error={}", task.getId(), e.getMessage());
                results.add(new QueueBatchRecognitionRematchResult(
                        task.getId(),
                        QueueBatchRecognitionRematchStatus.FAILED,
                        e.getMessage(),
                        List.of()
                ));
            }
        }

        return new QueueBatchRecognitionRematchResponse(
                updatedTasks.size(),
                matchedCount,
                emptyCount,
                failedCount,
                plan.preview(),
                results
        );
    }

    private RecognitionPlan buildPlan(QueueBatchRecognitionRequest request) {
        QueueBatchRecognitionRequest safeRequest = request == null ? new QueueBatchRecognitionRequest() : request;
        LinkedHashSet<Long> requestedIds = normalizeTaskIds(safeRequest.getTaskIds());
        EnumSet<BatchRecognitionField> updateFields = normalizeUpdateFields(safeRequest.getUpdateFields());
        EpisodeAssignmentMode episodeMode = safeRequest.getEpisodeAssignmentMode() == null
                ? EpisodeAssignmentMode.PRESERVE
                : safeRequest.getEpisodeAssignmentMode();
        EpisodeSortDirection sortDirection = safeRequest.getEpisodeSortDirection() == null
                ? EpisodeSortDirection.ASC
                : safeRequest.getEpisodeSortDirection();
        boolean sequential = EpisodeAssignmentMode.SEQUENTIAL.equals(episodeMode);

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (requestedIds.isEmpty()) {
            blockers.add("请先选择需要批量编辑的任务");
        }
        if (updateFields.isEmpty() && !sequential) {
            blockers.add("请至少选择一个公共字段，或启用顺序集号生成");
        }
        if (updateFields.contains(BatchRecognitionField.MEDIA_TYPE) && safeRequest.getMediaType() == null) {
            blockers.add("批量修改媒体类型时必须选择媒体类型");
        }
        String parsedTitle = safeRequest.getParsedTitle() == null ? null : safeRequest.getParsedTitle().trim();
        if (updateFields.contains(BatchRecognitionField.PARSED_TITLE) && (parsedTitle == null || parsedTitle.isBlank())) {
            blockers.add("批量修改解析标题时标题不能为空");
        }
        if (sequential && safeRequest.getEpisodeStart() == null) {
            blockers.add("启用顺序集号生成时必须填写起始集号");
        }
        if (sequential && safeRequest.getEpisodeStart() != null && safeRequest.getEpisodeStart() < 0) {
            blockers.add("起始集号不能小于 0");
        }

        List<MediaTask> tasks = loadTasks(requestedIds);
        Set<Long> loadedIds = tasks.stream().map(MediaTask::getId).collect(Collectors.toSet());
        for (Long taskId : requestedIds) {
            if (!loadedIds.contains(taskId)) {
                blockers.add("任务不存在：" + taskId);
            }
        }

        List<MediaTask> sortedTasks = tasks.stream()
                .sorted(taskComparator(sortDirection))
                .toList();
        Map<Long, Integer> generatedEpisodes = buildGeneratedEpisodes(sortedTasks, safeRequest, sequential);
        List<QueueBatchRecognitionPreviewItem> items = buildItems(
                sortedTasks,
                updateFields,
                parsedTitle,
                safeRequest,
                sequential,
                generatedEpisodes
        );

        if (sequential) {
            Set<MediaTask.MediaType> effectiveTypes = items.stream()
                    .map(QueueBatchRecognitionPreviewItem::effectiveMediaType)
                    .collect(Collectors.toSet());
            if (effectiveTypes.size() != 1 || !effectiveTypes.contains(MediaTask.MediaType.TV_SHOW)) {
                blockers.add("顺序集号生成只支持最终媒体类型全部为剧集的任务");
            }
        }

        addBatchWarnings(tasks, updateFields, sequential, warnings);

        int itemBlockerCount = (int) items.stream().filter(item -> !item.blockers().isEmpty()).count();
        int itemWarningCount = items.stream().mapToInt(item -> item.warnings().size()).sum();
        int blockerCount = blockers.size() + itemBlockerCount;
        int warningCount = warnings.size() + itemWarningCount;
        int editableCount = (int) items.stream().filter(item -> item.blockers().isEmpty()).count();
        int sequentialCount = sequential
                ? (int) items.stream().filter(item -> item.effectiveEpisode() != null).count()
                : 0;

        QueueBatchRecognitionPreview preview = new QueueBatchRecognitionPreview(
                requestedIds.size(),
                editableCount,
                sequentialCount,
                blockerCount,
                warningCount,
                blockerCount == 0,
                List.copyOf(blockers),
                List.copyOf(warnings),
                List.copyOf(items)
        );
        return new RecognitionPlan(preview, sortedTasks, indexItems(items));
    }

    private LinkedHashSet<Long> normalizeTaskIds(List<Long> taskIds) {
        if (taskIds == null) {
            return new LinkedHashSet<>();
        }
        return taskIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private EnumSet<BatchRecognitionField> normalizeUpdateFields(List<BatchRecognitionField> updateFields) {
        EnumSet<BatchRecognitionField> fields = EnumSet.noneOf(BatchRecognitionField.class);
        if (updateFields == null) {
            return fields;
        }
        updateFields.stream()
                .filter(Objects::nonNull)
                .forEach(fields::add);
        return fields;
    }

    private List<MediaTask> loadTasks(LinkedHashSet<Long> requestedIds) {
        if (requestedIds.isEmpty()) {
            return List.of();
        }
        return taskRepository.findAllById(requestedIds);
    }

    private Map<Long, Integer> buildGeneratedEpisodes(
            List<MediaTask> sortedTasks,
            QueueBatchRecognitionRequest request,
            boolean sequential
    ) {
        if (!sequential || request.getEpisodeStart() == null || request.getEpisodeStart() < 0) {
            return Map.of();
        }

        Map<Long, Integer> generated = new HashMap<>();
        int episode = request.getEpisodeStart();
        for (MediaTask task : sortedTasks) {
            generated.put(task.getId(), episode++);
        }
        return generated;
    }

    private List<QueueBatchRecognitionPreviewItem> buildItems(
            List<MediaTask> tasks,
            EnumSet<BatchRecognitionField> updateFields,
            String parsedTitle,
            QueueBatchRecognitionRequest request,
            boolean sequential,
            Map<Long, Integer> generatedEpisodes
    ) {
        List<QueueBatchRecognitionPreviewItem> items = new ArrayList<>();
        int sequenceIndex = 1;
        for (MediaTask task : tasks) {
            List<String> blockers = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            MediaTask.MediaType effectiveMediaType = updateFields.contains(BatchRecognitionField.MEDIA_TYPE)
                    ? request.getMediaType()
                    : task.getMediaType();
            String effectiveTitle = updateFields.contains(BatchRecognitionField.PARSED_TITLE)
                    ? parsedTitle
                    : task.getParsedTitle();
            Integer effectiveYear = updateFields.contains(BatchRecognitionField.PARSED_YEAR)
                    ? request.getParsedYear()
                    : task.getParsedYear();
            Integer effectiveSeason = updateFields.contains(BatchRecognitionField.PARSED_SEASON)
                    ? request.getParsedSeason()
                    : task.getParsedSeason();
            Integer effectiveEpisode = task.getParsedEpisode();
            Integer effectiveEpisodeEnd = task.getParsedEpisodeEnd();
            Integer itemSequenceIndex = null;

            if (!MediaTask.TaskStatus.AWAITING_CONFIRMATION.equals(task.getStatus())) {
                blockers.add("任务不是待确认状态");
            }
            if (effectiveMediaType == null) {
                blockers.add("无法确定媒体类型");
            }

            if (MediaTask.MediaType.MOVIE.equals(effectiveMediaType)) {
                effectiveSeason = null;
                effectiveEpisode = null;
                effectiveEpisodeEnd = null;
            } else if (MediaTask.MediaType.TV_SHOW.equals(effectiveMediaType)) {
                if (!MediaAssetType.VIDEO_FILE.equals(assetType(task))) {
                    blockers.add("当前版本仅支持普通视频剧集批量生成集号");
                }
                if (effectiveSeason == null) {
                    blockers.add("剧集任务必须有季号");
                }
                if (sequential) {
                    if (task.getParsedEpisodeEnd() != null) {
                        blockers.add("多集范围任务暂不支持顺序集号生成");
                    }
                    effectiveEpisode = generatedEpisodes.get(task.getId());
                    effectiveEpisodeEnd = null;
                    itemSequenceIndex = sequenceIndex++;
                    if (task.getParsedEpisode() != null && !Objects.equals(task.getParsedEpisode(), effectiveEpisode)) {
                        warnings.add("顺序集号将覆盖当前集号");
                    }
                } else if (effectiveEpisode == null) {
                    blockers.add("保留集号模式要求每条剧集任务已有集号");
                }
            }

            items.add(new QueueBatchRecognitionPreviewItem(
                    task.getId(),
                    task.getSourcePath(),
                    task.getMediaType(),
                    effectiveMediaType,
                    task.getParsedTitle(),
                    effectiveTitle,
                    task.getParsedYear(),
                    effectiveYear,
                    task.getParsedSeason(),
                    effectiveSeason,
                    task.getParsedEpisode(),
                    task.getParsedEpisodeEnd(),
                    effectiveEpisode,
                    effectiveEpisodeEnd,
                    itemSequenceIndex,
                    List.copyOf(blockers),
                    List.copyOf(warnings)
            ));
        }
        return items;
    }

    private void addBatchWarnings(
            List<MediaTask> tasks,
            EnumSet<BatchRecognitionField> updateFields,
            boolean sequential,
            List<String> warnings
    ) {
        if (tasks.isEmpty()) {
            return;
        }

        if (tasks.stream().map(task -> parentPath(task.getSourcePath())).distinct().count() > 1) {
            warnings.add("选中任务来自多个源目录，请确认是否属于同一批剧集");
        }
        if (tasks.stream().map(MediaTask::getRuleId).distinct().count() > 1) {
            warnings.add("选中任务来自多个路径规则，请确认整理策略一致");
        }
        if (!updateFields.isEmpty()) {
            warnings.add("仅保存不会刷新候选，旧候选可能仍基于修改前的信息");
        }
        if (sequential && tasks.stream().anyMatch(task -> task.getParsedEpisode() != null)) {
            warnings.add("顺序集号会覆盖已有集号");
        }
        addDifferenceWarning(tasks, MediaTask::getMediaType, "选中任务当前媒体类型不一致", warnings);
        addDifferenceWarning(tasks, MediaTask::getParsedTitle, "选中任务当前解析标题不一致", warnings);
        addDifferenceWarning(tasks, MediaTask::getParsedYear, "选中任务当前解析年份不一致", warnings);
        addDifferenceWarning(tasks, MediaTask::getParsedSeason, "选中任务当前季号不一致", warnings);
    }

    private <T> void addDifferenceWarning(
            List<MediaTask> tasks,
            java.util.function.Function<MediaTask, T> extractor,
            String message,
            List<String> warnings
    ) {
        if (tasks.stream().map(extractor).distinct().count() > 1) {
            warnings.add(message);
        }
    }

    private Map<Long, QueueBatchRecognitionPreviewItem> indexItems(List<QueueBatchRecognitionPreviewItem> items) {
        Map<Long, QueueBatchRecognitionPreviewItem> index = new LinkedHashMap<>();
        for (QueueBatchRecognitionPreviewItem item : items) {
            index.put(item.taskId(), item);
        }
        return index;
    }

    private void ensureCanApply(RecognitionPlan plan) {
        if (plan.preview().canApply()) {
            return;
        }
        List<String> messages = new ArrayList<>(plan.preview().blockers());
        plan.preview().items().stream()
                .filter(item -> !item.blockers().isEmpty())
                .forEach(item -> messages.add("任务 " + item.taskId() + "：" + String.join("；", item.blockers())));
        throw new IllegalArgumentException(String.join("；", messages));
    }

    private List<MediaTask> applyRecognitionFields(RecognitionPlan plan, boolean clearCandidates) {
        List<MediaTask> updatedTasks = new ArrayList<>();
        for (MediaTask task : plan.tasks()) {
            QueueBatchRecognitionPreviewItem item = plan.itemsByTaskId().get(task.getId());
            task.setMediaType(item.effectiveMediaType());
            task.setParsedTitle(item.effectiveTitle());
            task.setParsedYear(item.effectiveYear());
            if (MediaTask.MediaType.TV_SHOW.equals(item.effectiveMediaType())) {
                task.setParsedSeason(item.effectiveSeason());
                task.setParsedEpisode(item.effectiveEpisode());
                task.setParsedEpisodeEnd(item.effectiveEpisodeEnd());
            } else {
                task.setParsedSeason(null);
                task.setParsedEpisode(null);
                task.setParsedEpisodeEnd(null);
            }
            if (clearCandidates) {
                task.setMatchConfidence(null);
            }
            updatedTasks.add(taskRepository.save(task));
            eventPublisher.publishAwaitingConfirmation(task);
        }
        return updatedTasks;
    }

    private MediaTask clearCandidatesForRematch(TransactionTemplate transactionTemplate, Long taskId) {
        MediaTask task = transactionTemplate.execute(status -> {
            MediaTask current = loadTask(taskId);
            candidateRepository.deleteByTask_Id(taskId);
            current.setMatchConfidence(null);
            return taskRepository.save(current);
        });
        if (task == null) {
            throw new IllegalStateException("Task not found: " + taskId);
        }
        return task;
    }

    private RematchPersistResult persistRematchCandidates(
            TransactionTemplate transactionTemplate,
            Long taskId,
            List<MatchResult> matches
    ) {
        RematchPersistResult result = transactionTemplate.execute(status -> {
            MediaTask current = loadTask(taskId);
            List<TaskCandidate> candidates = saveCandidates(current, matches);
            current.setMatchConfidence(candidates.isEmpty() ? null : candidates.getFirst().getConfidence());
            taskRepository.save(current);
            return new RematchPersistResult(current, candidates);
        });
        if (result == null) {
            throw new IllegalStateException("Task not found: " + taskId);
        }
        return result;
    }

    private Optional<MediaTask> resetMatchConfidenceAfterRematchFailure(
            TransactionTemplate transactionTemplate,
            Long taskId
    ) {
        return Optional.ofNullable(transactionTemplate.execute(status -> {
            MediaTask current = loadTask(taskId);
            current.setMatchConfidence(null);
            return taskRepository.save(current);
        }));
    }

    private MediaTask loadTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    private List<TaskCandidate> saveCandidates(MediaTask task, List<MatchResult> matches) {
        List<MatchResult> sorted = (matches == null ? List.<MatchResult>of() : matches).stream()
                .sorted(Comparator.comparing(
                        MatchResult::getConfidence,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();

        List<TaskCandidate> saved = new ArrayList<>();
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
            candidate.setGenre1(genreAt(match, 0));
            candidate.setGenre2(genreAt(match, 1));
            candidate.setGenre3(genreAt(match, 2));
            candidate.setGenre4(genreAt(match, 3));
            candidate.setCountry(match.getCountry());
            candidate.setEpisodeTitle(match.getEpisodeTitle());
            candidate.setRank(rank++);
            candidate.setSelected(false);
            saved.add(candidateRepository.save(candidate));
        }
        return saved;
    }

    private String genreAt(MatchResult match, int index) {
        if (match.getGenres() == null || match.getGenres().size() <= index) {
            return null;
        }
        return match.getGenres().get(index);
    }

    private MediaAssetType assetType(MediaTask task) {
        return task.getAssetType() == null ? MediaAssetType.VIDEO_FILE : task.getAssetType();
    }

    private ParseResult toParseResult(MediaTask task) {
        ParseResult parseResult = new ParseResult();
        parseResult.setTitle(task.getParsedTitle());
        parseResult.setYear(task.getParsedYear());
        parseResult.setSeason(task.getParsedSeason());
        parseResult.setEpisode(task.getParsedEpisode());
        parseResult.setEpisodeEnd(task.getParsedEpisodeEnd());
        parseResult.setScreenSize(task.getParsedResolution());
        parseResult.setVideoCodec(task.getParsedCodec());
        parseResult.setReleaseGroup(task.getParsedReleaseGroup());
        parseResult.setType(MediaTask.MediaType.TV_SHOW.equals(task.getMediaType()) ? "episode" : "movie");
        return parseResult;
    }

    private Comparator<MediaTask> taskComparator(EpisodeSortDirection direction) {
        return (left, right) -> {
            int result = compareNatural(fileStem(left.getSourcePath()), fileStem(right.getSourcePath()));
            if (result == 0) {
                result = compareNatural(normalizedPath(left.getSourcePath()), normalizedPath(right.getSourcePath()));
            }
            if (result == 0) {
                result = Comparator.nullsLast(Long::compareTo).compare(left.getId(), right.getId());
            }
            return EpisodeSortDirection.DESC.equals(direction) ? -result : result;
        };
    }

    private int compareNatural(String left, String right) {
        String a = left == null ? "" : left.toLowerCase(Locale.ROOT);
        String b = right == null ? "" : right.toLowerCase(Locale.ROOT);
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < a.length() && rightIndex < b.length()) {
            char leftChar = a.charAt(leftIndex);
            char rightChar = b.charAt(rightIndex);
            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                NumberRun leftRun = readNumberRun(a, leftIndex);
                NumberRun rightRun = readNumberRun(b, rightIndex);
                int numberResult = compareNumberRuns(leftRun, rightRun);
                if (numberResult != 0) {
                    return numberResult;
                }
                leftIndex = leftRun.endIndex();
                rightIndex = rightRun.endIndex();
                continue;
            }
            if (leftChar != rightChar) {
                return Character.compare(leftChar, rightChar);
            }
            leftIndex++;
            rightIndex++;
        }
        return Integer.compare(a.length(), b.length());
    }

    private NumberRun readNumberRun(String value, int startIndex) {
        int endIndex = startIndex;
        while (endIndex < value.length() && Character.isDigit(value.charAt(endIndex))) {
            endIndex++;
        }
        int significantStart = startIndex;
        while (significantStart < endIndex - 1 && value.charAt(significantStart) == '0') {
            significantStart++;
        }
        return new NumberRun(value.substring(startIndex, endIndex), value.substring(significantStart, endIndex), endIndex);
    }

    private int compareNumberRuns(NumberRun left, NumberRun right) {
        if (left.significant().length() != right.significant().length()) {
            return Integer.compare(left.significant().length(), right.significant().length());
        }
        int numericResult = left.significant().compareTo(right.significant());
        if (numericResult != 0) {
            return numericResult;
        }
        return Integer.compare(left.raw().length(), right.raw().length());
    }

    private String fileStem(String sourcePath) {
        String filename = fileName(sourcePath);
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0) {
            return filename;
        }
        return filename.substring(0, dotIndex);
    }

    private String fileName(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return "";
        }
        String normalized = sourcePath.replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
    }

    private String normalizedPath(String sourcePath) {
        return sourcePath == null ? "" : sourcePath.replace('\\', '/');
    }

    private String parentPath(String sourcePath) {
        String normalized = normalizedPath(sourcePath);
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(0, slashIndex) : "";
    }

    private record NumberRun(String raw, String significant, int endIndex) {
    }

    private record RecognitionPlan(
            QueueBatchRecognitionPreview preview,
            List<MediaTask> tasks,
            Map<Long, QueueBatchRecognitionPreviewItem> itemsByTaskId
    ) {
    }

    private record RematchPersistResult(MediaTask task, List<TaskCandidate> candidates) {
    }
}
