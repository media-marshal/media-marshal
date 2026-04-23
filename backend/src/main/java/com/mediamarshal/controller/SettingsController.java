package com.mediamarshal.controller;

import com.mediamarshal.model.dto.ApiResponse;
import com.mediamarshal.model.entity.AppSetting;
import com.mediamarshal.service.settings.SettingsService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统配置 REST API
 *
 * GET  /api/settings           查询所有配置（敏感项脱敏）
 * PUT  /api/settings/{key}     更新单项配置（写入数据库）
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public ApiResponse<List<AppSetting>> getAll() {
        return ApiResponse.ok(settingsService.getAll());
    }

    @PutMapping("/{key}")
    public ApiResponse<Void> update(@PathVariable String key, @RequestBody UpdateRequest request) {
        settingsService.set(key, request.getValue(), request.getDescription(), request.isSensitive());
        return ApiResponse.ok();
    }

    @Data
    public static class UpdateRequest {
        @NotBlank
        private String value;
        private String description;
        private boolean sensitive;
    }
}
