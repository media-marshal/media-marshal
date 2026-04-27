package com.mediamarshal.service.rename;

import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.settings.SettingsService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RenameServiceTest {

    private final SettingsService settingsService = mock(SettingsService.class);
    private final RenameService renameService = new RenameService(
            Map.of(),
            mock(WatchRuleRepository.class),
            settingsService,
            mock(TemplateRenderer.class)
    );

    @Test
    void resolveTemplateUsesMovieRuleTemplateFirst() {
        WatchRule rule = new WatchRule();
        rule.setMoviePathTemplate("movies/{title}{ext}");
        rule.setTvPathTemplate("tv/{title}{ext}");

        String template = renameService.resolveTemplate(rule, MediaTask.MediaType.MOVIE);

        assertThat(template).isEqualTo("movies/{title}{ext}");
    }

    @Test
    void resolveTemplateUsesTvRuleTemplateFirst() {
        WatchRule rule = new WatchRule();
        rule.setMoviePathTemplate("movies/{title}{ext}");
        rule.setTvPathTemplate("tv/{title}/S{season:02d}E{episode:02d}{ext}");

        String template = renameService.resolveTemplate(rule, MediaTask.MediaType.TV_SHOW);

        assertThat(template).isEqualTo("tv/{title}/S{season:02d}E{episode:02d}{ext}");
    }

    @Test
    void resolveTemplateFallsBackToGlobalMovieTemplate() {
        WatchRule rule = new WatchRule();
        when(settingsService.get("rename.template.movie", "{title} ({year})/{title} ({year}){ext}"))
                .thenReturn("global-movie/{title}{ext}");

        String template = renameService.resolveTemplate(rule, MediaTask.MediaType.MOVIE);

        assertThat(template).isEqualTo("global-movie/{title}{ext}");
    }

    @Test
    void resolveTemplateFallsBackToGlobalTvTemplate() {
        WatchRule rule = new WatchRule();
        when(settingsService.get("rename.template.tv", "{title}/Season {season:02d}/{title} - S{season:02d}E{episode:02d}{ext}"))
                .thenReturn("global-tv/{title}{ext}");

        String template = renameService.resolveTemplate(rule, MediaTask.MediaType.TV_SHOW);

        assertThat(template).isEqualTo("global-tv/{title}{ext}");
    }

    @Test
    void resolveTemplateRequiresFinalMediaType() {
        WatchRule rule = new WatchRule();

        assertThatThrownBy(() -> renameService.resolveTemplate(rule, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Media type is required");
    }
}
