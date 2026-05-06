package com.mediamarshal.service.discovery.asset;

import com.mediamarshal.model.entity.WatchRule;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class MediaAssetDetectionService {

    private final List<MediaAssetDetector> detectors;

    public MediaAssetDetectionService(List<MediaAssetDetector> detectors) {
        this.detectors = detectors.stream()
                .sorted(Comparator.comparingInt(MediaAssetDetector::priority))
                .toList();
    }

    public Optional<MediaAsset> detect(Path path, WatchRule rule) {
        for (MediaAssetDetector detector : detectors) {
            Optional<MediaAsset> asset = detector.detect(path, rule);
            if (asset.isPresent()) {
                return asset;
            }
        }
        return Optional.empty();
    }
}
