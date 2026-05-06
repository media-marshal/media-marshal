package com.mediamarshal.service.rename;

import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BluRayDirectoryAssetOrganizerTest {

    @TempDir
    Path tempDir;

    @Test
    void copyKeepsBlurayStructureAndUsesMovieTemplateParentAsTargetRoot() throws Exception {
        Path source = tempDir.resolve("The.Matrix.1999.BluRay");
        Files.createDirectories(source.resolve("BDMV").resolve("STREAM"));
        Files.writeString(source.resolve("BDMV").resolve("index.bdmv"), "index");
        Files.writeString(source.resolve("BDMV").resolve("STREAM").resolve("00001.m2ts"), "media");

        WatchRule rule = new WatchRule();
        rule.setId(7L);
        rule.setTargetDir(tempDir.resolve("library").toString());
        rule.setMoviePathTemplate("电影/{year}/{title} ({year})/{title} ({year}){ext}");
        rule.setOperation(FileOperationStrategy.OperationType.COPY);

        WatchRuleRepository ruleRepository = mock(WatchRuleRepository.class);
        when(ruleRepository.findById(7L)).thenReturn(Optional.of(rule));
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.get("debug", "false")).thenReturn("false");

        TemplateRenderer renderer = new TemplateRenderer();
        RenameService renameService = new RenameService(Map.of(), ruleRepository, settingsService, renderer);
        BluRayDirectoryAssetOrganizer organizer = new BluRayDirectoryAssetOrganizer(
                renameService,
                ruleRepository,
                renderer,
                Map.of()
        );

        Path target = organizer.organize(task(source));

        assertThat(target).isEqualTo(tempDir.resolve("library").resolve("电影").resolve("1999").resolve("The Matrix (1999)").normalize());
        assertThat(Files.readString(target.resolve("BDMV").resolve("index.bdmv"))).isEqualTo("index");
        assertThat(Files.readString(target.resolve("BDMV").resolve("STREAM").resolve("00001.m2ts"))).isEqualTo("media");
        assertThat(Files.exists(source)).isTrue();
    }

    @Test
    void templateWithoutParentFallsBackToTitleYearDirectory() throws Exception {
        Path source = tempDir.resolve("Movie");
        Files.createDirectories(source.resolve("BDMV"));
        Files.writeString(source.resolve("BDMV").resolve("index.bdmv"), "index");

        WatchRule rule = new WatchRule();
        rule.setId(8L);
        rule.setTargetDir(tempDir.resolve("library").toString());
        rule.setMoviePathTemplate("{title} ({year}){ext}");
        rule.setOperation(FileOperationStrategy.OperationType.COPY);

        WatchRuleRepository ruleRepository = mock(WatchRuleRepository.class);
        when(ruleRepository.findById(8L)).thenReturn(Optional.of(rule));
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.get("debug", "false")).thenReturn("false");

        TemplateRenderer renderer = new TemplateRenderer();
        RenameService renameService = new RenameService(Map.of(), ruleRepository, settingsService, renderer);
        BluRayDirectoryAssetOrganizer organizer = new BluRayDirectoryAssetOrganizer(
                renameService,
                ruleRepository,
                renderer,
                Map.of()
        );

        MediaTask task = task(source);
        task.setRuleId(8L);
        Path target = organizer.organize(task);

        assertThat(target).isEqualTo(tempDir.resolve("library").resolve("The Matrix (1999)").normalize());
        assertThat(Files.exists(target.resolve("BDMV").resolve("index.bdmv"))).isTrue();
    }

    private MediaTask task(Path source) {
        MediaTask task = new MediaTask();
        task.setSourcePath(source.toString());
        task.setAssetType(MediaAssetType.BLURAY_DIRECTORY);
        task.setRuleId(7L);
        task.setMediaType(MediaTask.MediaType.MOVIE);
        task.setConfirmedTitle("The Matrix");
        task.setConfirmedYear(1999);
        return task;
    }
}
