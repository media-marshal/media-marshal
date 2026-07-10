package com.mediamarshal.model.dto;

import java.util.List;

public record TemplatePreviewResponse(
        String output,
        List<String> warnings,
        List<String> errors,
        List<String> usedVariables,
        List<String> unknownVariables,
        List<String> reservedVariables,
        boolean unsafePath
) {
}
