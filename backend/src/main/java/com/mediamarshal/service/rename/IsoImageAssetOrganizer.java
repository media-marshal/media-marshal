package com.mediamarshal.service.rename;

import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.MediaTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * ISO 本版本在发现层记录为 SKIPPED，不会进入该策略。
 * 保留策略外壳，便于后续版本启用 ISO 单文件整理时复用 RenameService。
 */
@Component
@RequiredArgsConstructor
public class IsoImageAssetOrganizer implements AssetOrganizerStrategy {

    private final RenameService renameService;

    @Override
    public boolean supports(MediaAssetType assetType) {
        return MediaAssetType.ISO_IMAGE.equals(assetType);
    }

    @Override
    public Path organize(MediaTask task) throws IOException {
        return renameService.rename(task);
    }
}
