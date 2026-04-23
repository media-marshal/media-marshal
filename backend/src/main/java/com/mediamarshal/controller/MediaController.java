package com.mediamarshal.controller;

import com.mediamarshal.model.dto.ApiResponse;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.repository.MediaTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        return taskRepository.findById(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail("Task not found: " + id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        taskRepository.deleteById(id);
        return ApiResponse.ok();
    }
}
