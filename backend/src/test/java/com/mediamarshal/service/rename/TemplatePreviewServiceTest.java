package com.mediamarshal.service.rename;

import com.mediamarshal.model.dto.TemplatePreviewRequest;
import com.mediamarshal.model.dto.TemplatePreviewResponse;
import com.mediamarshal.model.entity.MediaTask;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplatePreviewServiceTest {

    private final TemplatePreviewService service = new TemplatePreviewService(
            new TemplateRenderer(),
            new TemplateVariableCatalogService(),
            new TemplatePathSafetyService()
    );

    @Test
    void previewsEnabledAdr023VariablesWithBackendRenderer() {
        TemplatePreviewRequest request = new TemplatePreviewRequest();
        request.setTemplate("{title}[[ - {codec}]][[ - {release_group}]][[ - {original_title}]]{ext}");
        request.setMediaType(MediaTask.MediaType.MOVIE);
        request.setTargetDir("/media/library");

        TemplatePreviewResponse response = service.preview(request);

        assertThat(response.errors()).isEmpty();
        assertThat(response.output()).isEqualTo("蝙蝠侠：黑暗骑士 - H.264 - YIFY - The Dark Knight.mkv");
        assertThat(response.usedVariables()).containsExactly("title", "codec", "release_group", "original_title", "ext");
    }

    @Test
    void reportsUnknownReservedMediaTypeAndUnsafePathErrors() {
        TemplatePreviewRequest request = new TemplatePreviewRequest();
        request.setTemplate("../{season:02d}/{unknown}{ext}");
        request.setMediaType(MediaTask.MediaType.MOVIE);
        request.setTargetDir("/media/library");

        TemplatePreviewResponse response = service.preview(request);

        assertThat(response.unsafePath()).isTrue();
        assertThat(response.unknownVariables()).containsExactly("unknown");
        assertThat(response.reservedVariables()).isEmpty();
        assertThat(response.errors()).anyMatch(error -> error.contains("不适用于 MOVIE"));
        assertThat(response.errors()).anyMatch(error -> error.contains(".."));
    }

    @Test
    void previewsAdr025MovieTmdbVariables() {
        TemplatePreviewRequest request = new TemplatePreviewRequest();
        request.setTemplate("[[{country}/]][[{genre_1}/]]{title} ({year})/{title}[[ - {genre_2}]][[ - {genre_3}]][[ - {genre_4}]]{ext}");
        request.setMediaType(MediaTask.MediaType.MOVIE);
        request.setTargetDir("/media/library");
        request.setContext(Map.of(
                "title", "Movie",
                "year", 2026,
                "country", "US",
                "genre_1", "Action",
                "genre_2", "Crime",
                "genre_3", "Drama",
                "genre_4", "Mystery",
                "ext", ".mkv"
        ));

        TemplatePreviewResponse response = service.preview(request);

        assertThat(response.errors()).isEmpty();
        assertThat(response.output()).isEqualTo("US/Action/Movie (2026)/Movie - Crime - Drama - Mystery.mkv");
        assertThat(response.usedVariables()).containsExactly(
                "country",
                "genre_1",
                "title",
                "year",
                "genre_2",
                "genre_3",
                "genre_4",
                "ext"
        );
    }

    @Test
    void previewsAdr025TvEpisodeTitleVariable() {
        TemplatePreviewRequest request = new TemplatePreviewRequest();
        request.setTemplate("{title}/S{season:02d}/{title} - S{season:02d}E{episode:02d}[[ - {episode_title}]]{ext}");
        request.setMediaType(MediaTask.MediaType.TV_SHOW);
        request.setTargetDir("/media/library");
        request.setContext(Map.of(
                "title", "Show",
                "season", 3,
                "episode", 7,
                "episode_title", "One Minute",
                "ext", ".mkv"
        ));

        TemplatePreviewResponse response = service.preview(request);

        assertThat(response.errors()).isEmpty();
        assertThat(response.output()).isEqualTo("Show/S03/Show - S03E07 - One Minute.mkv");
        assertThat(response.usedVariables()).containsExactly(
                "title",
                "season",
                "episode",
                "episode_title",
                "ext"
        );
    }
}
