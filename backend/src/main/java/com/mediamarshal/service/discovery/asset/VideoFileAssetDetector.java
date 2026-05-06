package com.mediamarshal.service.discovery.asset;

import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.WatchRule;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Component
public class VideoFileAssetDetector implements MediaAssetDetector {

    private static final List<String> VIDEO_EXTENSIONS = List.of(
            ".mkv", ".mp4", ".avi", ".mov", ".wmv", ".flv", ".ts", ".m2ts", ".rmvb"
    );

    @Override
    public Optional<MediaAsset> detect(Path path, WatchRule rule) {
        if (!Files.isRegularFile(path) || !isVideoFile(path)) {
            return Optional.empty();
        }
        String filename = path.getFileName().toString();
        return Optional.of(new MediaAsset(path, MediaAssetType.VIDEO_FILE, filename, false));
    }

    @Override
    public int priority() {
        return 300;
    }

    public boolean isVideoFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return VIDEO_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
