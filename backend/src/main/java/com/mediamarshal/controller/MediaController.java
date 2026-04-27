package com.mediamarshal.controller;

import com.mediamarshal.model.dto.ApiResponse;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.repository.TaskCandidateRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 媒体任务 REST API
 *
 * GET  /api/tasks              查询所有任务（支持状态过滤）
 * GET  /api/tasks/{id}         查询单个任务详情
 * DELETE /api/tasks/{id}       删除任务记录（不影响文件）
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class MediaController {

    private final MediaTaskRepository taskRepository;
    private final TaskCandidateRepository candidateRepository;

    @GetMapping
    public ApiResponse<List<MediaTask>> listTasks(
            @RequestParam(required = false) MediaTask.TaskStatus status) {
        List<MediaTask> tasks = status != null
                ? taskRepository.findByStatusOrderByCreatedAtDesc(status)
                : taskRepository.findAll();
        return ApiResponse.ok(tasks);
    }

    @GetMapping("/{id}")
    public ApiResponse<MediaTask> getTask(@PathVariable Long id) {
        return taskRepository.findById(Objects.requireNonNull(id))
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail("Task not found: " + id));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        Long taskId = Objects.requireNonNull(id);
        candidateRepository.deleteAll(Objects.requireNonNull(candidateRepository.findByTask_IdOrderByRankAsc(taskId)));
        taskRepository.deleteById(taskId);
        return ApiResponse.ok();
    }
}
