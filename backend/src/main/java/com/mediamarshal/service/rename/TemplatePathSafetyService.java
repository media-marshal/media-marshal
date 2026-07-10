package com.mediamarshal.service.rename;

import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.exception.MediaTaskFailureException;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class TemplatePathSafetyService {

    private static final Pattern WINDOWS_DRIVE_PATH = Pattern.compile("^[A-Za-z]:.*");
    private static final Pattern PATH_SEGMENT_SEPARATOR = Pattern.compile("[/\\\\]+");
    private static final String UNSAFE_TARGET_PATH_MESSAGE = "路径模板渲染结果不安全，已阻止文件操作";

    public Path resolveSafeTargetPath(String targetDir, String renderedRelativePath) {
        TemplatePathSafetyResult result = validate(targetDir, renderedRelativePath, true);
        if (!result.safe()) {
            String details = String.join("；", result.errors());
            throw new MediaTaskFailureException(
                    MediaTask.TaskErrorCode.UNSAFE_TARGET_PATH,
                    details.isBlank() ? UNSAFE_TARGET_PATH_MESSAGE : UNSAFE_TARGET_PATH_MESSAGE + "：" + details
            );
        }
        return result.resolvedPath();
    }

    public TemplatePathSafetyResult validateForPreview(String targetDir, String renderedRelativePath) {
        return validate(targetDir, renderedRelativePath, false);
    }

    private TemplatePathSafetyResult validate(String targetDir, String renderedRelativePath, boolean requireTargetDir) {
        List<String> errors = new ArrayList<>();
        String rawPath = renderedRelativePath == null ? "" : renderedRelativePath.trim();

        if (rawPath.isBlank()) {
            errors.add("模板渲染结果为空");
        }
        if (isWindowsDrivePath(rawPath)) {
            errors.add("模板渲染结果不能是 Windows 盘符路径");
        }
        if (isUncPath(rawPath)) {
            errors.add("模板渲染结果不能是 UNC 网络路径");
        }
        if (containsParentTraversal(rawPath)) {
            errors.add("模板渲染结果不能包含 .. 路径段");
        }

        Path targetRoot = resolveTargetRoot(targetDir, requireTargetDir, errors);
        Path renderedPath = null;
        Path resolvedPath = null;
        try {
            renderedPath = rawPath.isBlank() ? Paths.get("") : Paths.get(rawPath);
            if (renderedPath.isAbsolute()) {
                errors.add("模板渲染结果不能是绝对路径");
            }
            resolvedPath = targetRoot == null
                    ? null
                    : targetRoot.resolve(rawPath).normalize();
        } catch (InvalidPathException e) {
            errors.add("模板渲染结果包含当前系统不支持的路径字符");
        }

        if (targetRoot != null && resolvedPath != null && !resolvedPath.startsWith(targetRoot)) {
            errors.add("模板渲染结果不能逃逸目标根目录");
        }

        List<String> distinctErrors = errors.stream().distinct().toList();
        boolean safe = distinctErrors.isEmpty();
        return new TemplatePathSafetyResult(safe, !safe, resolvedPath, distinctErrors);
    }

    private Path resolveTargetRoot(String targetDir, boolean requireTargetDir, List<String> errors) {
        if (targetDir == null || targetDir.isBlank()) {
            if (requireTargetDir) {
                errors.add("目标根目录不能为空");
                return null;
            }
            return Paths.get("template-preview-target").toAbsolutePath().normalize();
        }
        return Paths.get(targetDir).toAbsolutePath().normalize();
    }

    private boolean isWindowsDrivePath(String path) {
        return WINDOWS_DRIVE_PATH.matcher(path).matches();
    }

    private boolean isUncPath(String path) {
        return path.startsWith("\\\\") || path.startsWith("//");
    }

    private boolean containsParentTraversal(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        for (String segment : PATH_SEGMENT_SEPARATOR.split(path)) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }
}
