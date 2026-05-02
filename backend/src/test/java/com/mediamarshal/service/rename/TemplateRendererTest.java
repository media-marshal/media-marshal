package com.mediamarshal.service.rename;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
