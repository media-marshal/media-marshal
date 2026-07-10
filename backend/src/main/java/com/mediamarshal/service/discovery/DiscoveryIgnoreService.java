package com.mediamarshal.service.discovery;

import com.mediamarshal.model.entity.WatchRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies WatchRule ignored-file semantics consistently across watch registration and scans.
 */
@Slf4j
@Service
public class DiscoveryIgnoreService {

    public static final List<String> DEFAULT_IGNORED_PATTERNS = List.of(
            ".DS_Store",
            "Thumbs.db",
            "desktop.ini",
            "*.part",
            "*.tmp",
            "*.crdownload",
            "*.lock",
            "~$*",
            ".*",
            "__MACOSX/",
            "@eaDir/",
            "sample/",
            "samples/"
    );

    public boolean isIgnored(Path path, WatchRule rule) {
        List<String> patterns = effectiveIgnoredPatterns(rule);
        if (patterns.isEmpty()) {
            return false;
        }

        Path root = Paths.get(rule.getSourceDir()).toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        Path relative = root.equals(normalized) || !normalized.startsWith(root)
                ? normalized.getFileName()
                : root.relativize(normalized);

        if (relative == null) {
            return false;
        }

        for (String pattern : patterns) {
            if (matchesIgnorePattern(relative, pattern)) {
                return true;
            }
        }
        return false;
    }

    public boolean usesSystemDefaults(WatchRule rule) {
        return rule.getIgnoredFilePatterns() == null;
    }

    private List<String> effectiveIgnoredPatterns(WatchRule rule) {
        List<String> patterns = rule.getIgnoredFilePatterns();
        if (patterns == null) {
            return DEFAULT_IGNORED_PATTERNS;
        }
        return patterns.stream()
                .map(String::trim)
                .filter(pattern -> !pattern.isBlank())
                .toList();
    }

    private boolean matchesIgnorePattern(Path relativePath, String pattern) {
        String normalizedPattern = pattern.replace("\\", "/").trim();
        if (normalizedPattern.isBlank()) {
            return false;
        }

        if (normalizedPattern.endsWith("/")) {
            String directoryPattern = normalizedPattern.substring(0, normalizedPattern.length() - 1);
            return pathSegments(relativePath).stream()
                    .anyMatch(segment -> matchesNamePattern(segment, directoryPattern));
        }

        Path fileName = relativePath.getFileName();
        return fileName != null && matchesNamePattern(fileName.toString(), normalizedPattern);
    }

    private List<String> pathSegments(Path relativePath) {
        List<String> segments = new ArrayList<>();
        for (Path segment : relativePath) {
            segments.add(segment.toString());
        }
        return segments;
    }

    private boolean matchesNamePattern(String name, String pattern) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        String normalizedPattern = pattern.toLowerCase(Locale.ROOT);
        if (normalizedPattern.equals(normalizedName)) {
            return true;
        }
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);
            return matcher.matches(Paths.get(normalizedName));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ignored file pattern skipped: pattern='{}', error={}", pattern, e.getMessage());
            return false;
        }
    }
}
