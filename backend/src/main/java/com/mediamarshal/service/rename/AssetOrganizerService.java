package com.mediamarshal.service.rename;

import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.MediaTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetOrganizerService {

    private final List<AssetOrganizerStrategy> strategies;

    public Path organize(MediaTask task) throws IOException {
        MediaAssetType assetType = task.getAssetType() != null
                ? task.getAssetType()
                : MediaAssetType.VIDEO_FILE;
        return strategies.stream()
                .filter(strategy -> strategy.supports(assetType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported media asset type: " + assetType))
                .organize(task);
    }
}
