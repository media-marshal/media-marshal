package com.mediamarshal.service.discovery.asset;

import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.WatchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BluRayDirectoryAssetDetectorTest {

    @TempDir
    Path tempDir;

    private final BluRayDirectoryAssetDetector detector = new BluRayDirectoryAssetDetector();

    @Test
    void detectsStandardBlurayRootFromIndexFile() throws Exception {
        Path movie = tempDir.resolve("The Matrix (1999)");
        Files.createDirectories(movie.resolve("BDMV"));
        Files.writeString(movie.resolve("BDMV").resolve("index.bdmv"), "index");

        var asset = detector.detect(movie.resolve("BDMV").resolve("index.bdmv"), rule()).orElseThrow();

        assertThat(asset.rootPath()).isEqualTo(movie.toAbsolutePath().normalize());
        assertThat(asset.type()).isEqualTo(MediaAssetType.BLURAY_DIRECTORY);
        assertThat(asset.displayName()).isEqualTo("The Matrix (1999)");
        assertThat(asset.shouldPruneChildren()).isTrue();
        assertThat(asset.shouldSkip()).isFalse();
    }

    @Test
    void normalizesInternalStreamFileToBlurayRoot() throws Exception {
        Path movie = tempDir.resolve("Movie");
        Path stream = movie.resolve("BDMV").resolve("STREAM");
        Files.createDirectories(stream);
        Path m2ts = stream.resolve("00001.m2ts");
        Files.writeString(m2ts, "media");

        var asset = detector.detect(m2ts, rule()).orElseThrow();

        assertThat(asset.rootPath()).isEqualTo(movie.toAbsolutePath().normalize());
        assertThat(asset.type()).isEqualTo(MediaAssetType.BLURAY_DIRECTORY);
    }

    @Test
    void nakedBdmvUnderSourceRootIsSkippedAsNonStandard() throws Exception {
        Path bdmv = tempDir.resolve("BDMV");
        Files.createDirectories(bdmv);
        Files.writeString(bdmv.resolve("MovieObject.bdmv"), "object");

        var asset = detector.detect(bdmv.resolve("MovieObject.bdmv"), rule()).orElseThrow();

        assertThat(asset.rootPath()).isEqualTo(bdmv.toAbsolutePath().normalize());
        assertThat(asset.type()).isEqualTo(MediaAssetType.BLURAY_DIRECTORY);
        assertThat(asset.skipReason()).contains("非标解析失败");
    }

    private WatchRule rule() {
        WatchRule rule = new WatchRule();
        rule.setSourceDir(tempDir.toString());
        return rule;
    }
}
