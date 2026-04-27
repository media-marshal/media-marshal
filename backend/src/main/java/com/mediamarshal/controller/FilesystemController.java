package com.mediamarshal.controller;

import com.mediamarshal.model.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 服务器本地文件系统浏览接口
 *
 * 提供目录列表，供前端路径选择器使用（PathsView 新增/编辑规则时）。
 * 仅返回目录，不返回文件，保证 watch_rule 的 sourceDir / targetDir 总是目录路径。
 *
 * 安全说明：
 *   此接口暴露了容器内的文件系统目录结构，适合自托管个人 NAS 场景。
 *   如未来加入多用户认证，此接口应添加权限校验。
 */
@Slf4j
@RestController
@RequestMapping("/api/filesystem")
public class FilesystemController {

    /**
     * 列出指定路径下的所有子目录
     *
     * @param path 要浏览的目录绝对路径（默认根目录 /）
     * @return 子目录列表，按名称排序
     */
    @GetMapping("/browse")
    public ApiResponse<BrowseResult> browse(
            @RequestParam(defaultValue = "/") String path) {

        String requestedPath = normalizeWindowsBreadcrumbPath(path);
        boolean windowsRootSelector = isWindows() && isVirtualRoot(requestedPath);

        if (windowsRootSelector) {
            List<DirEntry> roots = StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), false)
                    .filter(Files::isDirectory)
                    .map(root -> {
                        String rootPath = formatPath(root);
                        return new DirEntry(rootPath, rootPath);
                    })
                    .sorted(Comparator.comparing(DirEntry::name))
                    .toList();

            return ApiResponse.ok(new BrowseResult("/", null, roots));
        }

        Path dir;
        try {
            dir = Paths.get(requestedPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            log.warn("Invalid directory path requested: {}", path, e);
            return ApiResponse.fail("路径格式不正确: " + path);
        }

        if (!Files.exists(dir)) {
            return ApiResponse.fail("路径不存在: " + path);
        }
        if (!Files.isDirectory(dir)) {
            return ApiResponse.fail("不是目录: " + path);
        }

        try (Stream<Path> stream = Files.list(dir)) {
            List<DirEntry> entries = stream
                    .filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .map(p -> new DirEntry(p.getFileName().toString(), formatPath(p)))
                    .toList();

            String parentPath = dir.getParent() != null
                    ? formatPath(dir.getParent())
                    : null;

            if (isWindowsRoot(dir)) {
                parentPath = "/";
            }

            log.debug("Browse dir='{}', found {} subdirectories", dir, entries.size());
            return ApiResponse.ok(new BrowseResult(formatPath(dir), parentPath, entries));

        } catch (IOException e) {
            log.error("Cannot list directory: {}", dir, e);
            return ApiResponse.fail("无法读取目录: " + e.getMessage());
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private boolean isVirtualRoot(String path) {
        return path == null || path.isBlank() || "/".equals(path);
    }

    private String normalizeWindowsBreadcrumbPath(String path) {
        if (!isWindows() || path == null) {
            return path;
        }

        String normalized = path.replace("\\", "/");
        if (normalized.matches("^/?[A-Za-z]:/?$")) {
            String drive = normalized.replace("/", "");
            return drive + "/";
        }
        if (normalized.matches("^/[A-Za-z]:/.+")) {
            return normalized.substring(1);
        }
        return path;
    }

    private boolean isWindowsRoot(Path dir) {
        if (!isWindows()) {
            return false;
        }
        Path root = dir.getRoot();
        return root != null && root.equals(dir);
    }

    private String formatPath(Path path) {
        return path.toString().replace("\\", "/");
    }

    public record DirEntry(String name, String path) {}

    public record BrowseResult(String currentPath, String parentPath, List<DirEntry> dirs) {}
}
