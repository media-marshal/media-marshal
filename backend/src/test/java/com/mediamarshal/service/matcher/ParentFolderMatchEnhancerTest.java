package com.mediamarshal.service.matcher;

import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.service.discovery.asset.MediaAsset;
import com.mediamarshal.service.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentFolderMatchEnhancerTest {

    private final MetadataMatcher metadataMatcher = mock(MetadataMatcher.class);
    private final ParentFolderContextExtractor contextExtractor = mock(ParentFolderContextExtractor.class);
    private final SettingsService settingsService = mock(SettingsService.class);
    private ParentFolderMatchEnhancer enhancer;

    @BeforeEach
    void setUp() {
        enhancer = new ParentFolderMatchEnhancer(metadataMatcher, contextExtractor, settingsService);
        when(settingsService.get("watcher.parent-folder-context-enabled", "true")).thenReturn("true");
        when(settingsService.get("watcher.parent-folder-parent-only-confidence-cap", "0.75")).thenReturn("0.75");
        when(settingsService.get("watcher.parent-folder-mutual-boost", "0.05")).thenReturn("0.05");
    }

    @Test
    void skipsParentSearchWhenFilenameCandidateAlreadyMeetsThreshold() {
        List<MatchResult> filenameCandidates = List.of(candidate("100", "家业", "家业", 2026, "TV_SHOW", 0.91));

        List<MatchResult> results = enhancer.enhanceIfNeeded(asset(), filenameParse(), filenameCandidates, 0.8);

        assertThat(results).isSameAs(filenameCandidates);
        verify(contextExtractor, never()).extract(any(), any(), any());
        verify(metadataMatcher, never()).search(any());
    }

    @Test
    void boostsCandidateOnlyWhenFilenameAndParentFolderAgreeOnSameTmdbId() {
        List<MatchResult> filenameCandidates = List.of(candidate("100", "家业", "家业", 2026, "TV_SHOW", 0.30));
        when(contextExtractor.extract(any(), any(), any()))
                .thenReturn(Optional.of(new ParentFolderContext("家业.The.Heir.S01.2026.2160p.WEB-DL", "家业 The Heir")));
        when(metadataMatcher.search(any(ParseResult.class)))
                .thenReturn(List.of(candidate("100", "家业", "家业", 2026, "TV_SHOW", 0.90)));

        List<MatchResult> results = enhancer.enhanceIfNeeded(asset(), filenameParse(), filenameCandidates, 0.8);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getSourceId()).isEqualTo("100");
        assertThat(results.getFirst().getConfidence()).isCloseTo(0.95, within(0.0001));

        ArgumentCaptor<ParseResult> parentParse = ArgumentCaptor.forClass(ParseResult.class);
        verify(metadataMatcher).search(parentParse.capture());
        assertThat(parentParse.getValue().getOriginalFilename()).isEqualTo("家业.The.Heir.S01.2026.2160p.WEB-DL");
        assertThat(parentParse.getValue().getTitle()).isEqualTo("The Heir");
        assertThat(parentParse.getValue().getSeason()).isEqualTo(1);
        assertThat(parentParse.getValue().getEpisode()).isEqualTo(25);
    }

    @Test
    void capsParentOnlyCandidatesBelowAutoThreshold() {
        when(contextExtractor.extract(any(), any(), any()))
                .thenReturn(Optional.of(new ParentFolderContext("家业.The.Heir.S01.2026.2160p.WEB-DL", "家业 The Heir")));
        when(metadataMatcher.search(any(ParseResult.class)))
                .thenReturn(List.of(candidate("100", "家业", "家业", 2026, "TV_SHOW", 0.92)));

        List<MatchResult> results = enhancer.enhanceIfNeeded(asset(), filenameParse(), List.of(), 0.8);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getConfidence()).isEqualTo(0.75);
        assertThat(results.getFirst().getConfidence()).isLessThan(0.8);
    }

    @Test
    void keepsConflictingParentCandidateBelowAutoThreshold() {
        List<MatchResult> filenameCandidates = List.of(candidate("100", "Another Show", "Another Show", 2026, "TV_SHOW", 0.72));
        when(contextExtractor.extract(any(), any(), any()))
                .thenReturn(Optional.of(new ParentFolderContext("家业.The.Heir.S01.2026.2160p.WEB-DL", "家业 The Heir")));
        when(metadataMatcher.search(any(ParseResult.class)))
                .thenReturn(List.of(candidate("200", "家业", "家业", 2026, "TV_SHOW", 0.95)));

        List<MatchResult> results = enhancer.enhanceIfNeeded(asset(), filenameParse(), filenameCandidates, 0.8);

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(result -> assertThat(result.getConfidence()).isLessThan(0.8));
        assertThat(results.stream().filter(result -> "200".equals(result.getSourceId())).findFirst())
                .hasValueSatisfying(result -> assertThat(result.getConfidence()).isEqualTo(0.75));
    }

    @Test
    void leavesCandidatesUntouchedWhenParentFolderContextIsDisabled() {
        when(settingsService.get("watcher.parent-folder-context-enabled", "true")).thenReturn("false");
        List<MatchResult> filenameCandidates = List.of(candidate("100", "家业", "家业", 2026, "TV_SHOW", 0.30));

        List<MatchResult> results = enhancer.enhanceIfNeeded(asset(), filenameParse(), filenameCandidates, 0.8);

        assertThat(results).isSameAs(filenameCandidates);
        verify(contextExtractor, never()).extract(any(), any(), any());
        verify(metadataMatcher, never()).search(any());
    }

    private MediaAsset asset() {
        return new MediaAsset(
                Path.of(
                        "D:/incoming/家业.The.Heir.S01.2026.2160p.WEB-DL",
                        "The.Heir.S01E25.2026.2160p.WEB-DL.mkv"
                ),
                MediaAssetType.VIDEO_FILE,
                "The.Heir.S01E25.2026.2160p.WEB-DL.mkv",
                false
        );
    }

    private ParseResult filenameParse() {
        ParseResult parseResult = new ParseResult();
        parseResult.setTitle("The Heir");
        parseResult.setYear(2026);
        parseResult.setSeason(1);
        parseResult.setEpisode(25);
        parseResult.setType("episode");
        parseResult.setOriginalFilename("The.Heir.S01E25.2026.2160p.WEB-DL.mkv");
        return parseResult;
    }

    private MatchResult candidate(String id, String title, String originalTitle, Integer year, String mediaType, Double confidence) {
        MatchResult result = new MatchResult();
        result.setSource("tmdb");
        result.setSourceId(id);
        result.setTitle(title);
        result.setOriginalTitle(originalTitle);
        result.setYear(year);
        result.setMediaType(mediaType);
        result.setConfidence(confidence);
        return result;
    }
}
