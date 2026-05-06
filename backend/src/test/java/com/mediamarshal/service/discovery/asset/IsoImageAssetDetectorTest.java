package com.mediamarshal.service.discovery.asset;

import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.WatchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class IsoImageAssetDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsIsoAsUnsupportedAssetForThisVersion() throws Exception {
        Path iso = tempDir.resolve("Movie.iso");
        Files.writeString(iso, "iso");

        var asset = new IsoImageAssetDetector().detect(iso, new WatchRule()).orElseThrow();

        assertThat(asset.type()).isEqualTo(MediaAssetType.ISO_IMAGE);
        assertThat(asset.displayName()).isEqualTo("Movie");
        assertThat(asset.skipReason()).isEqualTo(IsoImageAssetDetector.ISO_UNSUPPORTED_REASON);
    }
}
