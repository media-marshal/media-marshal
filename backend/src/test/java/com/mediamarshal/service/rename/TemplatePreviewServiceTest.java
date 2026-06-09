package com.mediamarshal.service.rename;

import com.mediamarshal.model.dto.TemplatePreviewRequest;
import com.mediamarshal.model.dto.TemplatePreviewResponse;
import com.mediamarshal.model.entity.MediaTask;
import org.junit.jupiter.api.Test;

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
        request.setTemplate("../{season:02d}/{genre_1}/{unknown}{ext}");
        request.setMediaType(MediaTask.MediaType.MOVIE);
        request.setTargetDir("/media/library");

        TemplatePreviewResponse response = service.preview(request);

        assertThat(response.unsafePath()).isTrue();
        assertThat(response.unknownVariables()).containsExactly("unknown");
        assertThat(response.reservedVariables()).containsExactly("genre_1");
        assertThat(response.errors()).anyMatch(error -> error.contains("不适用于 MOVIE"));
        assertThat(response.errors()).anyMatch(error -> error.contains(".."));
    }
}
