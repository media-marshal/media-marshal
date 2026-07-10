package com.mediamarshal.service.rename;

import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.settings.SettingsService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RenameServiceTest {

    private final SettingsService settingsService = mock(SettingsService.class);
    private final WatchRuleRepository watchRuleRepository = mock(WatchRuleRepository.class);
    private final RenameService renameService = new RenameService(
            Map.of(),
            watchRuleRepository,
            settingsService,
            mock(TemplateRenderer.class),
            new TemplatePathSafetyService()
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
        when(settingsService.get("rename.template.movie", "{title} ({year})/{title} ({year})[[ - {resolution}]]{ext}"))
                .thenReturn("global-movie/{title}{ext}");

        String template = renameService.resolveTemplate(rule, MediaTask.MediaType.MOVIE);

        assertThat(template).isEqualTo("global-movie/{title}{ext}");
    }

    @Test
    void resolveTemplateFallsBackToGlobalTvTemplate() {
        WatchRule rule = new WatchRule();
        when(settingsService.get("rename.template.tv", "{title} ({year})/S{season:02d}/{title} ({year}) - S{season:02d}E{episode:02d}[[ - {resolution}]]{ext}"))
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

    @Test
    void buildVariablesIncludesEnabledTemplateFields() {
        MediaTask task = new MediaTask();
        task.setSourcePath("D:/incoming/Movie.mkv");
        task.setConfirmedTitle("Movie");
        task.setConfirmedOriginalTitle("Original Movie");
        task.setConfirmedGenre1("Action");
        task.setConfirmedGenre2("Crime");
        task.setConfirmedGenre3("Drama");
        task.setConfirmedGenre4("Mystery");
        task.setConfirmedCountry("US");
        task.setConfirmedEpisodeTitle("One Minute");
        task.setParsedCodec("H.265");
        task.setParsedReleaseGroup("TEAM");

        TemplateVariables variables = renameService.buildVariables(task, null);

        assertThat(variables.getOriginalTitle()).isEqualTo("Original Movie");
        assertThat(variables.getGenre1()).isEqualTo("Action");
        assertThat(variables.getGenre2()).isEqualTo("Crime");
        assertThat(variables.getGenre3()).isEqualTo("Drama");
        assertThat(variables.getGenre4()).isEqualTo("Mystery");
        assertThat(variables.getCountry()).isEqualTo("US");
        assertThat(variables.getEpisodeTitle()).isEqualTo("One Minute");
        assertThat(variables.getCodec()).isEqualTo("H.265");
        assertThat(variables.getReleaseGroup()).isEqualTo("TEAM");
        assertThat(variables.getExt()).isEqualTo(".mkv");
    }

    @Test
    void templateUsesVariableDetectsFormattedAndParameterizedPlaceholders() {
        WatchRule rule = new WatchRule();
        rule.setId(9L);
        rule.setTvPathTemplate("{title}/S{season:02d}/{title} - {episode:02d;prefix=E}[[ - {episode_title}]]{ext}");
        when(watchRuleRepository.findById(9L)).thenReturn(Optional.of(rule));

        MediaTask task = new MediaTask();
        task.setRuleId(9L);
        task.setMediaType(MediaTask.MediaType.TV_SHOW);

        assertThat(renameService.templateUsesVariable(task, "episode_title")).isTrue();
        assertThat(renameService.templateUsesVariable(task, "episode")).isTrue();
        assertThat(renameService.templateUsesVariable(task, "country")).isFalse();
    }

    @Test
    void resolveTitleInitialReturnsHashForNumericTitle() {
        assertThat(renameService.resolveTitleInitial("2001太空漫游")).isEqualTo("#");
    }

    @Test
    void resolveTitleInitialReturnsPinyinInitialForChineseTitle() {
        assertThat(renameService.resolveTitleInitial("黑袍纠察队")).isEqualTo("H");
        assertThat(renameService.resolveTitleInitial("巅峰猎杀")).isEqualTo("D");
        assertThat(renameService.resolveTitleInitial("逍遥")).isEqualTo("X");
    }

    @Test
    void resolveTitleInitialReturnsUppercaseInitialForEnglishTitle() {
        assertThat(renameService.resolveTitleInitial("breaking bad")).isEqualTo("B");
    }

    @Test
    void resolveTitleInitialReturnsHashForSpecialCharacterTitle() {
        assertThat(renameService.resolveTitleInitial("·秘密")).isEqualTo("#");
    }
}
