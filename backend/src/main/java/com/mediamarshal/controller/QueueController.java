package com.mediamarshal.controller;

import com.mediamarshal.model.dto.ApiResponse;
import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.TaskCandidate;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.repository.TaskCandidateRepository;
import com.mediamarshal.service.matcher.MetadataMatcher;
import com.mediamarshal.service.pipeline.MediaProcessPipeline;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 待人工确认队列 REST API
 *
 * GET  /api/queue              查询所有 AWAITING_CONFIRMATION 任务
 * POST /api/queue/{id}/confirm 人工确认：指定 TMDB ID 后继续处理
 * POST /api/queue/batch-confirm 批量人工确认
 * POST /api/queue/{id}/skip    跳过此任务（标记为 SKIPPED，不处理）
 */
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
@Slf4j
public class QueueController {

    private final MediaTaskRepository taskRepository;
    private final TaskCandidateRepository candidateRepository;
    private final MetadataMatcher metadataMatcher;
    private final MediaProcessPipeline pipeline;

    @GetMapping
    public ApiResponse<List<MediaTask>> getPendingQueue() {
        return ApiResponse.ok(
                taskRepository.findByStatusOrderByCreatedAtDesc(MediaTask.TaskStatus.AWAITING_CONFIRMATION)
        );
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<Void> confirm(@PathVariable Long id, @RequestBody ConfirmRequest request) {
        pipeline.confirm(id, request.getTmdbId(), request.getMediaType());
        return ApiResponse.ok();
    }

    @PostMapping("/batch-confirm")
    public ApiResponse<BatchConfirmResponse> batchConfirm(@RequestBody BatchConfirmRequest request) {
        List<BatchConfirmResult> results = new ArrayList<>();
        for (ConfirmItem item : request.getItems()) {
            try {
                pipeline.confirm(
                        item.getTaskId(),
                        item.getTmdbId(),
                        item.getMediaType(),
                        MediaTask.ConfirmationSource.MANUAL_BATCH
                );
                results.add(BatchConfirmResult.success(item.getTaskId()));
            } catch (Exception e) {
                log.warn("Batch confirm item failed: taskId={}, error={}", item.getTaskId(), e.getMessage());
                results.add(BatchConfirmResult.fail(item.getTaskId(), e.getMessage()));
            }
        }
        return ApiResponse.ok(new BatchConfirmResponse(results));
    }

    @GetMapping("/{id}/candidates")
    public ApiResponse<List<TaskCandidate>> getCandidates(@PathVariable Long id) {
        return ApiResponse.ok(candidateRepository.findByTask_IdOrderByRankAsc(id));
    }

    @SuppressWarnings("null")
    @GetMapping("/{id}/search")
    public ApiResponse<List<MatchResult>> search(@PathVariable Long id, @RequestParam String q) {
        MediaTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));

        String keyword = q == null ? "" : q.trim();
        if (keyword.isBlank()) {
            return ApiResponse.ok(List.of());
        }

        ParseResult parseResult = new ParseResult();
        parseResult.setTitle(keyword);
        // 手动搜索应尽量贴近 TMDB 首页体验，只使用用户输入的关键词。
        // 不带 parsedYear/season/episode，避免文件名误解析出的年份过滤掉正确结果。
        if (MediaTask.MediaType.TV_SHOW.equals(task.getMediaType())) {
            parseResult.setType("episode");
        } else if (MediaTask.MediaType.MOVIE.equals(task.getMediaType())) {
            parseResult.setType("movie");
        }

        List<MatchResult> keywordResults = metadataMatcher.search(parseResult);
        if (!keyword.matches("\\d+")) {
            return ApiResponse.ok(keywordResults);
        }

        List<MatchResult> idResults = searchByTmdbId(keyword, task.getMediaType());
        return ApiResponse.ok(mergeResults(idResults, keywordResults));
    }

    private List<MatchResult> searchByTmdbId(String tmdbId, MediaTask.MediaType taskMediaType) {
        if (taskMediaType != null) {
            return getByIdIfExists(tmdbId, taskMediaType.name());
        }

        List<MatchResult> results = new ArrayList<>();
        results.addAll(getByIdIfExists(tmdbId, MediaTask.MediaType.MOVIE.name()));
        results.addAll(getByIdIfExists(tmdbId, MediaTask.MediaType.TV_SHOW.name()));
        return results;
    }

    private List<MatchResult> getByIdIfExists(String tmdbId, String mediaType) {
        try {
            return List.of(metadataMatcher.getById(tmdbId, mediaType));
        } catch (Exception e) {
            log.debug("TMDB id lookup missed: id={}, mediaType={}, error={}", tmdbId, mediaType, e.getMessage());
            return List.of();
        }
    }

    private List<MatchResult> mergeResults(List<MatchResult> idResults, List<MatchResult> keywordResults) {
        Map<String, MatchResult> merged = new LinkedHashMap<>();
        for (MatchResult result : idResults) {
            merged.put(resultKey(result), result);
        }
        for (MatchResult result : keywordResults) {
            merged.putIfAbsent(resultKey(result), result);
        }
        return new ArrayList<>(merged.values());
    }

    private String resultKey(MatchResult result) {
        return result.getMediaType() + ":" + result.getSourceId();
    }

    @SuppressWarnings("null")
    @PostMapping("/{id}/skip")
    public ApiResponse<Void> skip(@PathVariable Long id) {
        taskRepository.findById(id).ifPresent(task -> {
            task.setStatus(MediaTask.TaskStatus.SKIPPED);
            task.setErrorMessage(null);
            task.setSkipReason("MANUALLY_SKIPPED_BY_USER");
            taskRepository.save(task);
        });
        return ApiResponse.ok();
    }

    @Data
    public static class ConfirmRequest {
        @NotNull
        private Long tmdbId;
        @NotNull
        private String mediaType;
    }

    @Data
    public static class BatchConfirmRequest {
        private List<ConfirmItem> items = List.of();
    }

    @Data
    public static class ConfirmItem {
        @NotNull
        private Long taskId;
        @NotNull
        private Long tmdbId;
        @NotNull
        private String mediaType;
    }

    public record BatchConfirmResponse(List<BatchConfirmResult> results) {
    }

    public record BatchConfirmResult(Long taskId, boolean success, String message) {
        static BatchConfirmResult success(Long taskId) {
            return new BatchConfirmResult(taskId, true, null);
        }

        static BatchConfirmResult fail(Long taskId, String message) {
            return new BatchConfirmResult(taskId, false, message);
        }
    }
}
