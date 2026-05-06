package com.mediamarshal.service.discovery.asset;

import com.mediamarshal.model.entity.WatchRule;

import java.nio.file.Path;
import java.util.Optional;

public interface MediaAssetDetector {
    Optional<MediaAsset> detect(Path path, WatchRule rule);

    int priority();
}
