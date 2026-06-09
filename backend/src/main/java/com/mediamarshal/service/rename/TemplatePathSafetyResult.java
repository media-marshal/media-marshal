package com.mediamarshal.service.rename;

import java.nio.file.Path;
import java.util.List;

public record TemplatePathSafetyResult(
        boolean safe,
        boolean unsafePath,
        Path resolvedPath,
        List<String> errors
) {
}
