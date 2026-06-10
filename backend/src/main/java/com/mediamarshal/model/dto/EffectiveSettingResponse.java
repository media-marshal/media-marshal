package com.mediamarshal.model.dto;

public record EffectiveSettingResponse(
        String key,
        String value,
        String source,
        boolean overriddenByDatabase
) {
}
