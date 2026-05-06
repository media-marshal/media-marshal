package com.mediamarshal.service.rename;

import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.MediaTask;

import java.io.IOException;
import java.nio.file.Path;

public interface AssetOrganizerStrategy {
    boolean supports(MediaAssetType assetType);

    Path organize(MediaTask task) throws IOException;
}
