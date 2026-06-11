package com.mediamarshal.service.rename;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void optionalSegmentIsKeptWhenAllVariablesArePresent() {
        TemplateVariables variables = TemplateVariables.builder()
                .title("影片名")
                .year(2026)
                .resolution("2160p")
                .ext(".mkv")
                .build();

        String rendered = renderer.render("{title} ({year})[[ - {resolution}]]{ext}", variables);

        assertThat(rendered).isEqualTo("影片名 (2026) - 2160p.mkv");
    }

    @Test
    void optionalSegmentIsRemovedWhenVariableIsMissing() {
        TemplateVariables variables = TemplateVariables.builder()
                .title("影片名")
                .year(2026)
                .ext(".mkv")
                .build();

        String rendered = renderer.render("{title} ({year})[[ - {resolution}]]{ext}", variables);

        assertThat(rendered).isEqualTo("影片名 (2026).mkv");
    }

    @Test
    void optionalSegmentIsRemovedWhenAnyVariableIsMissing() {
        TemplateVariables variables = TemplateVariables.builder()
                .title("影片名")
                .year(2026)
                .resolution("2160p")
                .ext(".mkv")
                .build();

        String rendered = renderer.render("{title} ({year})[[ - {resolution} - {codec}]]{ext}", variables);

        assertThat(rendered).isEqualTo("影片名 (2026).mkv");
    }

    @Test
    void missingVariableOutsideOptionalSegmentKeepsPlaceholder() {
        TemplateVariables variables = TemplateVariables.builder()
                .title("影片名")
                .year(2026)
                .ext(".mkv")
                .build();

        String rendered = renderer.render("{title} ({year}) - {resolution}{ext}", variables);

        assertThat(rendered).isEqualTo("影片名 (2026) - {resolution}.mkv");
    }

    @Test
    void stringVariablesAreSanitizedBeforeRenderingPathSegments() {
        TemplateVariables variables = TemplateVariables.builder()
                .title("Bad/Movie:Name?")
                .releaseGroup("TEAM|A")
                .ext(".mkv")
                .build();

        String rendered = renderer.render("{title}[[ - {release_group}]]{ext}", variables);

        assertThat(rendered).isEqualTo("Bad_Movie_Name_ - TEAM_A.mkv");
    }

    @Test
    void optionalSegmentIsRemovedWhenSanitizedVariableIsBlank() {
        TemplateVariables variables = TemplateVariables.builder()
                .title("Movie")
                .releaseGroup("   ")
                .ext(".mkv")
                .build();

        String rendered = renderer.render("{title}[[ - {release_group}]]{ext}", variables);

        assertThat(rendered).isEqualTo("Movie.mkv");
    }

    @Test
    void emptyExtRendersAsEmptyString() {
        TemplateVariables variables = TemplateVariables.builder()
                .title("Movie")
                .ext("")
                .build();

        String rendered = renderer.render("{title}{ext}", variables);

        assertThat(rendered).isEqualTo("Movie");
    }

    @Test
    void formattedVariablesWorkInsideOptionalSegment() {
        TemplateVariables variables = TemplateVariables.builder()
                .title("剧名")
                .season(3)
                .episode(7)
                .ext(".mkv")
                .build();

        String rendered = renderer.render("{title}[[ - S{season:02d}E{episode:02d}]]{ext}", variables);

        assertThat(rendered).isEqualTo("剧名 - S03E07.mkv");
    }

    @Test
    void placeholderParametersRenderAffixesForSingleValues() {
        TemplateVariables variables = TemplateVariables.builder()
                .season(1)
                .episode(16)
                .build();

        String rendered = renderer.render("{season:02d;prefix=S}{episode:02d;prefix=E}", variables);

        assertThat(rendered).isEqualTo("S01E16");
    }

    @Test
    void placeholderParametersRenderAffixesForRanges() {
        TemplateVariables variables = TemplateVariables.builder()
                .season(1)
                .episode(new TemplateRange(16, 17))
                .build();

        String rendered = renderer.render("{season:02d;prefix=S}{episode:02d;prefix=E}", variables);

        assertThat(rendered).isEqualTo("S01E16-E17");
    }

    @Test
    void rangeRenderingCanSkipRepeatedAffixes() {
        TemplateVariables variables = TemplateVariables.builder()
                .episode(new TemplateRange(21, 28))
                .build();

        String rendered = renderer.render(
                "{episode:02d;prefix=E;suffix=X;repeatPrefix=false;repeatSuffix=false}",
                variables
        );

        assertThat(rendered).isEqualTo("E21-28X");
    }

    @Test
    void rangeRenderingUsesCustomSeparator() {
        TemplateVariables variables = TemplateVariables.builder()
                .episode(new TemplateRange(1, 2))
                .build();

        String rendered = renderer.render("{episode:03d;prefix=EP;separator=_}", variables);

        assertThat(rendered).isEqualTo("EP001_EP002");
    }

    @Test
    void placeholderParameterValuesAreSanitized() {
        TemplateVariables variables = TemplateVariables.builder()
                .episode(new TemplateRange(1, 2))
                .build();

        String rendered = renderer.render("{episode:02d;prefix=E/;suffix=:X;separator=/}", variables);

        assertThat(rendered).isEqualTo("E_01_X_E_02_X");
    }

    @Test
    void parameterizedPlaceholderWorksInsideOptionalSegment() {
        TemplateVariables variables = TemplateVariables.builder()
                .title("Friends")
                .season(1)
                .episode(new TemplateRange(16, 17))
                .ext(".mkv")
                .build();

        String rendered = renderer.render("{title}[[ - S{season:02d}{episode:02d;prefix=E}]]{ext}", variables);

        assertThat(rendered).isEqualTo("Friends - S01E16-E17.mkv");
    }

    @Test
    void iterableRangesMustBeContiguous() {
        TemplateVariables variables = TemplateVariables.builder()
                .episode(List.of(16, 18))
                .build();

        assertThatThrownBy(() -> renderer.render("{episode:02d;prefix=E}", variables))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only contiguous ranges are supported");
    }
}
