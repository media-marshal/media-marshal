package com.mediamarshal.service.discovery;

import com.mediamarshal.service.discovery.asset.VideoFileAssetDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Identifies high-confidence Sample videos by suffix and an exact sibling main-video basename.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleVideoDetector {

    private static final List<String> SAMPLE_SUFFIXES = List.of(".sample", "-sample", "_sample");

    private final VideoFileAssetDetector videoFileAssetDetector;

    public Optional<Path> findMainVideo(Path candidate) {
        if (!Files.isRegularFile(candidate) || !videoFileAssetDetector.isVideoFile(candidate)) {
            return Optional.empty();
        }

        String candidateBasename = basename(candidate.getFileName().toString());
        String mainBasename = removeSampleSuffix(candidateBasename);
        if (mainBasename == null) {
            return Optional.empty();
        }

        Path parent = candidate.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return Optional.empty();
        }

        try (Stream<Path> siblings = Files.list(parent)) {
            return siblings
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.equals(candidate))
                    .filter(videoFileAssetDetector::isVideoFile)
                    .filter(path -> basename(path.getFileName().toString()).equalsIgnoreCase(mainBasename))
                    .findFirst();
        } catch (IOException e) {
            log.debug("Failed to inspect sibling videos for Sample detection: path={}", candidate, e);
            return Optional.empty();
        }
    }

    private String removeSampleSuffix(String basename) {
        String lowerBasename = basename.toLowerCase(Locale.ROOT);
        for (String suffix : SAMPLE_SUFFIXES) {
            if (lowerBasename.endsWith(suffix)) {
                return basename.substring(0, basename.length() - suffix.length());
            }
        }
        return null;
    }

    private String basename(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
