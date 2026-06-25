package com.mediamarshal.service.discovery;

import com.mediamarshal.service.discovery.asset.VideoFileAssetDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SampleVideoDetectorTest {

    @TempDir
    Path tempDir;

    private final SampleVideoDetector detector = new SampleVideoDetector(new VideoFileAssetDetector());

    @Test
    void detectsDotSampleWhenMatchingMainVideoExists() throws Exception {
        Path main = createFile("Movie.Name.2026.mkv");
        Path sample = createFile("Movie.Name.2026.Sample.mkv");

        assertThat(detector.findMainVideo(sample)).contains(main);
    }

    @Test
    void detectsSampleAcrossDifferentVideoExtensionsAndCase() throws Exception {
        Path main = createFile("MOVIE.NAME.2026.mp4");
        Path sample = createFile("movie.name.2026-SAMPLE.MKV");

        assertThat(detector.findMainVideo(sample)).contains(main);
    }

    @Test
    void detectsUnderscoreSampleSuffix() throws Exception {
        Path main = createFile("Movie.Name.2026.avi");
        Path sample = createFile("Movie.Name.2026_sample.mov");

        assertThat(detector.findMainVideo(sample)).contains(main);
    }

    @Test
    void doesNotIgnoreSuffixWithoutMatchingMainVideo() throws Exception {
        Path sample = createFile("The.Movie.Sample.mkv");

        assertThat(detector.findMainVideo(sample)).isEmpty();
    }

    @Test
    void doesNotIgnoreTitleContainingSampleOutsideSuffix() throws Exception {
        createFile("A.Movie.2024.mkv");
        Path titleContainingSample = createFile("A.Sample.Movie.2024.mkv");

        assertThat(detector.findMainVideo(titleContainingSample)).isEmpty();
    }

    @Test
    void requiresExactMainBasenameInsteadOfPrefixSimilarity() throws Exception {
        createFile("Movie.Name.2026.Extended.mkv");
        Path sample = createFile("Movie.Name.2026.Sample.mkv");

        assertThat(detector.findMainVideo(sample)).isEmpty();
    }

    @Test
    void ignoresNonVideoFiles() throws Exception {
        createFile("Movie.Name.2026.mkv");
        Path checksum = createFile("Movie.Name.2026.Sample.md5");

        assertThat(detector.findMainVideo(checksum)).isEmpty();
    }

    private Path createFile(String name) throws Exception {
        return Files.writeString(tempDir.resolve(name), "media");
    }
}
