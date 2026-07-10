package com.mediamarshal.controller;

import com.mediamarshal.model.dto.ApiResponse;
import com.mediamarshal.model.dto.EffectiveSettingResponse;
import com.mediamarshal.model.entity.AppSetting;
import com.mediamarshal.service.settings.SettingsService;
import com.mediamarshal.service.settings.SystemResetService;
import com.mediamarshal.service.settings.TmdbProxySettingsService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
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
    private final SystemResetService systemResetService;
    private final TmdbProxySettingsService tmdbProxySettingsService;

    @GetMapping
    public ApiResponse<List<AppSetting>> getAll() {
        return ApiResponse.ok(settingsService.getAll());
    }

    @GetMapping("/effective")
    public ApiResponse<List<EffectiveSettingResponse>> getEffective(@RequestParam String keys) {
        List<String> requestedKeys = Arrays.stream(keys.split(","))
                .map(String::trim)
                .filter(key -> !key.isBlank())
                .toList();

        List<EffectiveSettingResponse> result = requestedKeys.stream()
                .map(this::resolveEffectiveSetting)
                .toList();
        return ApiResponse.ok(result);
    }

    @PutMapping("/{key}")
    public ApiResponse<Void> update(@PathVariable String key, @RequestBody UpdateRequest request) {
        if (tmdbProxySettingsService.supports(key)) {
            return ApiResponse.fail("请使用 TMDB 代理配置接口同时保存开关和地址");
        }
        settingsService.set(key, request.getValue(), request.getDescription(), request.isSensitive());
        return ApiResponse.ok();
    }

    @PutMapping("/tmdb-proxy")
    public ApiResponse<Void> updateTmdbProxy(@RequestBody TmdbProxyUpdateRequest request) {
        try {
            tmdbProxySettingsService.save(request.isEnabled(), request.getHttpUrl());
            return ApiResponse.ok();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/reset")
    public ApiResponse<Void> reset() {
        systemResetService.reset();
        return ApiResponse.ok();
    }

    @Data
    public static class UpdateRequest {
        @NotBlank
        private String value;
        private String description;
        private boolean sensitive;
    }

    @Data
    public static class TmdbProxyUpdateRequest {
        private boolean enabled;
        private String httpUrl;
    }

    private EffectiveSettingResponse resolveEffectiveSetting(String key) {
        if (tmdbProxySettingsService.supports(key)) {
            return tmdbProxySettingsService.getEffectiveSettings(List.of(key)).getFirst();
        }
        if ("tmdb.api-key".equals(key)) {
            return new EffectiveSettingResponse(key, "", "SENSITIVE", false);
        }
        SettingsService.EffectiveValue value = settingsService.getEffectiveValue(key, "");
        return new EffectiveSettingResponse(key, value.value(), value.source(), value.overriddenByDatabase());
    }
}
