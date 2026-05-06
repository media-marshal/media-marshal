package com.mediamarshal.service.rename;

import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.MediaTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class VideoFileAssetOrganizer implements AssetOrganizerStrategy {

    private final RenameService renameService;

    @Override
    public boolean supports(MediaAssetType assetType) {
        return MediaAssetType.VIDEO_FILE.equals(assetType);
    }

    @Override
    public Path organize(MediaTask task) throws IOException {
        return renameService.rename(task);
    }
}
