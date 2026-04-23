package com.mediamarshal.controller;

import com.mediamarshal.model.dto.ApiResponse;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.service.pipeline.MediaProcessPipeline;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 待人工确认队列 REST API
 *
 * GET  /api/queue              查询所有 AWAITING_CONFIRMATION 任务
 * POST /api/queue/{id}/confirm 人工确认：指定 TMDB ID 后继续处理
 * POST /api/queue/{id}/skip    跳过此任务（标记为 FAILED，不处理）
 */
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final MediaTaskRepository taskRepository;
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

    @PostMapping("/{id}/skip")
    public ApiResponse<Void> skip(@PathVariable Long id) {
        taskRepository.findById(id).ifPresent(task -> {
            task.setStatus(MediaTask.TaskStatus.FAILED);
            task.setErrorMessage("Manually skipped by user");
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
}
