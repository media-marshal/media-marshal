package com.mediamarshal.service.discovery.asset;

import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.WatchRule;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class IsoImageAssetDetector implements MediaAssetDetector {

    public static final String ISO_UNSUPPORTED_REASON = "当前版本暂不支持 ISO 镜像";

    @Override
    public Optional<MediaAsset> detect(Path path, WatchRule rule) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        String filename = path.getFileName().toString();
        if (!filename.toLowerCase().endsWith(".iso")) {
            return Optional.empty();
        }
        String displayName = filename.substring(0, filename.length() - ".iso".length());
        return Optional.of(new MediaAsset(path, MediaAssetType.ISO_IMAGE, displayName, false, ISO_UNSUPPORTED_REASON));
    }

    @Override
    public int priority() {
        return 200;
    }
}
