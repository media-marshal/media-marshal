package com.mediamarshal.model.dto;

import java.util.List;

/**
 * WatchRule 保存前校验结果。
 */
public record WatchRuleValidationResult(
        boolean valid,
        String message,
        List<String> details
) {

    public static WatchRuleValidationResult ok() {
        return new WatchRuleValidationResult(true, null, List.of());
    }

    public static WatchRuleValidationResult fail(String message, List<String> details) {
        return new WatchRuleValidationResult(false, message, details);
    }
}
