package com.mediamarshal.service.matcher;

import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.model.entity.MediaAssetType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ParentFolderContextExtractorTest {

    private final ParentFolderContextExtractor extractor = new ParentFolderContextExtractor();

    @Test
    void extractsBilingualReleaseFolderContext() {
        ParseResult parseResult = episodeParse(2026, 1);
        Path source = Path.of(
                "D:/incoming/家业.The.Heir.S01.2026.2160p.IQ.WEB-DL.H265.DDP5.1-CHDWEB",
                "The.Heir.S01E25.2026.2160p.IQ.WEB-DL.H265.DDP5.1-CHDWEB.mkv"
        );

        assertThat(extractor.extract(source, MediaAssetType.VIDEO_FILE, parseResult))
                .hasValueSatisfying(context -> {
                    assertThat(context.folderName()).isEqualTo("家业.The.Heir.S01.2026.2160p.IQ.WEB-DL.H265.DDP5.1-CHDWEB");
                    assertThat(context.titleRegion()).isEqualTo("家业 The Heir");
                });
    }

    @Test
    void ignoresGenericParentFolder() {
        ParseResult parseResult = episodeParse(2026, 1);
        Path source = Path.of("D:/incoming/TV Shows", "The.Heir.S01E25.2026.2160p.mkv");

        assertThat(extractor.extract(source, MediaAssetType.VIDEO_FILE, parseResult)).isEmpty();
    }

    @Test
    void ignoresPlainTitleFolderWithoutReleaseMarkers() {
        ParseResult parseResult = episodeParse(2026, 1);
        Path source = Path.of("D:/incoming/家业", "The.Heir.S01E25.2026.2160p.mkv");

        assertThat(extractor.extract(source, MediaAssetType.VIDEO_FILE, parseResult)).isEmpty();
    }

    @Test
    void ignoresFolderWhenSeasonConflictsWithFilename() {
        ParseResult parseResult = episodeParse(2026, 1);
        Path source = Path.of("D:/incoming/家业.The.Heir.S02.2026.2160p.WEB-DL", "The.Heir.S01E25.2026.2160p.mkv");

        assertThat(extractor.extract(source, MediaAssetType.VIDEO_FILE, parseResult)).isEmpty();
    }

    @Test
    void ignoresNonVideoAssets() {
        ParseResult parseResult = episodeParse(2026, 1);
        Path source = Path.of("D:/incoming/家业.The.Heir.S01.2026.2160p.WEB-DL", "The.Heir.iso");

        assertThat(extractor.extract(source, MediaAssetType.ISO_IMAGE, parseResult)).isEmpty();
    }

    private ParseResult episodeParse(Integer year, Integer season) {
        ParseResult parseResult = new ParseResult();
        parseResult.setTitle("The Heir");
        parseResult.setYear(year);
        parseResult.setSeason(season);
        parseResult.setEpisode(25);
        parseResult.setType("episode");
        return parseResult;
    }
}
