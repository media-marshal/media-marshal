package com.mediamarshal.service.correction;

import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.TaskCorrectionPreview;
import com.mediamarshal.model.dto.TaskCorrectionRequest;
import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.TaskCandidate;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.repository.TaskCandidateRepository;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.matcher.MetadataMatcher;
import com.mediamarshal.service.nfo.NfoGeneratorService;
import com.mediamarshal.service.rename.RenameService;
import com.mediamarshal.service.rename.TemplatePathSafetyService;
import com.mediamarshal.service.rename.TemplateRenderer;
import com.mediamarshal.service.settings.SettingsService;
import com.mediamarshal.websocket.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskCorrectionServiceTest {

    @TempDir
    Path tempDir;

    private MediaTaskRepository taskRepository;
    private TaskCandidateRepository candidateRepository;
    private WatchRuleRepository watchRuleRepository;
    private MetadataMatcher metadataMatcher;
    private EventPublisher eventPublisher;
    private TaskCorrectionService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(MediaTaskRepository.class);
        candidateRepository = mock(TaskCandidateRepository.class);
        watchRuleRepository = mock(WatchRuleRepository.class);
        metadataMatcher = mock(MetadataMatcher.class);
        eventPublisher = mock(EventPublisher.class);
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.get("debug", "false")).thenReturn("false");
        when(settingsService.get("tmdb.confirm-retry-attempts", "3")).thenReturn("1");

        TemplateRenderer templateRenderer = new TemplateRenderer();
        RenameService renameService = new RenameService(
                Map.of(),
                watchRuleRepository,
                settingsService,
                templateRenderer,
                new TemplatePathSafetyService()
        );

        service = new TaskCorrectionService(
                taskRepository,
                candidateRepository,
                watchRuleRepository,
                metadataMatcher,
                renameService,
                templateRenderer,
                mock(NfoGeneratorService.class),
                settingsService,
                eventPublisher
        );

        when(taskRepository.save(any(MediaTask.class))).thenAnswer(invocation -> {
            MediaTask task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(2L);
            }
            return task;
        });
    }

    @Test
    void applyCreatesDoneCorrectionTaskEvenWhenTargetPathIsUnchanged() throws Exception {
        Path target = tempDir.resolve("library/Movie (2024)/Movie (2024).mkv");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "video");

        MediaTask original = doneTask(target);
        WatchRule rule = movieRule("{title} ({year})/{title} ({year}){ext}");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(original));
        when(watchRuleRepository.findById(9L)).thenReturn(Optional.of(rule));
        when(metadataMatcher.getById("100", "MOVIE")).thenReturn(movieMatch(100L, "Movie", 2024));

        service.apply(1L, movieRequest("Movie", 2024, 100L, false));

        ArgumentCaptor<MediaTask> taskCaptor = ArgumentCaptor.forClass(MediaTask.class);
        verify(taskRepository, org.mockito.Mockito.atLeast(2)).save(taskCaptor.capture());
        MediaTask corrected = taskCaptor.getAllValues().stream()
                .filter(task -> MediaTask.ConfirmationSource.MANUAL_CORRECTION.equals(task.getConfirmationSource()))
                .findFirst()
                .orElseThrow();

        assertThat(corrected.getStatus()).isEqualTo(MediaTask.TaskStatus.DONE);
        assertThat(corrected.getTargetPath()).isEqualTo(target.toAbsolutePath().normalize().toString());
        assertThat(corrected.getCorrectedFromTaskId()).isEqualTo(1L);
        assertThat(original.getStatus()).isEqualTo(MediaTask.TaskStatus.CORRECTED);
        assertThat(original.getCorrectedToTaskId()).isEqualTo(2L);
        assertThat(Files.exists(target)).isTrue();

        ArgumentCaptor<TaskCandidate> candidateCaptor = ArgumentCaptor.forClass(TaskCandidate.class);
        verify(candidateRepository).save(candidateCaptor.capture());
        assertThat(candidateCaptor.getValue().getSelected()).isTrue();
        assertThat(candidateCaptor.getValue().getTmdbId()).isEqualTo(100L);
    }

    @Test
    void applyMovesSameBaseAssociatedFilesButLeavesGenericCoverInPlace() throws Exception {
        Path current = tempDir.resolve("library/Old (2024)/Old (2024).mkv");
        Files.createDirectories(current.getParent());
        Files.writeString(current, "video");
        Files.writeString(current.resolveSibling("Old (2024).srt"), "subtitle");
        Files.writeString(current.resolveSibling("Old (2024).md5"), "md5");
        Files.writeString(current.resolveSibling("poster.jpg"), "poster");

        MediaTask original = doneTask(current);
        WatchRule rule = movieRule("{title} ({year})/{title} ({year}){ext}");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(original));
        when(watchRuleRepository.findById(9L)).thenReturn(Optional.of(rule));
        when(metadataMatcher.getById("200", "MOVIE")).thenReturn(movieMatch(200L, "New", 2025));

        service.apply(1L, movieRequest("New", 2025, 200L, false));

        Path corrected = tempDir.resolve("library/New (2025)/New (2025).mkv");
        assertThat(Files.exists(corrected)).isTrue();
        assertThat(Files.readString(corrected.resolveSibling("New (2025).srt"))).isEqualTo("subtitle");
        assertThat(Files.readString(corrected.resolveSibling("New (2025).md5"))).isEqualTo("md5");
        assertThat(Files.exists(current.resolveSibling("poster.jpg"))).isTrue();
        assertThat(Files.exists(current)).isFalse();
    }

    @Test
    void previewBlocksTvCorrectionForBlurayDirectory() throws Exception {
        Path target = tempDir.resolve("library/Movie (2024)");
        Files.createDirectories(target.resolve("BDMV"));

        MediaTask original = doneTask(target);
        original.setAssetType(MediaAssetType.BLURAY_DIRECTORY);
        WatchRule rule = movieRule("{title} ({year})/{title} ({year}){ext}");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(original));
        when(watchRuleRepository.findById(9L)).thenReturn(Optional.of(rule));

        TaskCorrectionRequest request = new TaskCorrectionRequest();
        request.setMediaType("TV_SHOW");
        request.setParsedTitle("Show");
        request.setParsedSeason(1);
        request.setParsedEpisode(1);
        request.setTmdbId(300L);

        TaskCorrectionPreview preview = service.preview(1L, request);

        assertThat(preview.isCanApply()).isFalse();
        assertThat(preview.getBlockers()).contains("第一版暂不支持剧集蓝光原盘修正");
    }

    private MediaTask doneTask(Path target) {
        MediaTask task = new MediaTask();
        task.setId(1L);
        task.setStatus(MediaTask.TaskStatus.DONE);
        task.setSourcePath(tempDir.resolve("source/Missing.mkv").toString());
        task.setTargetPath(target.toString());
        task.setAssetType(MediaAssetType.VIDEO_FILE);
        task.setMediaType(MediaTask.MediaType.MOVIE);
        task.setParsedTitle("Old");
        task.setParsedYear(2024);
        task.setRuleId(9L);
        task.setTmdbId(99L);
        task.setConfirmedTitle("Old");
        task.setConfirmedYear(2024);
        return task;
    }

    private WatchRule movieRule(String template) {
        WatchRule rule = new WatchRule();
        rule.setId(9L);
        rule.setTargetDir(tempDir.resolve("library").toString());
        rule.setMoviePathTemplate(template);
        return rule;
    }

    private TaskCorrectionRequest movieRequest(String title, int year, long tmdbId, boolean regenerateNfo) {
        TaskCorrectionRequest request = new TaskCorrectionRequest();
        request.setMediaType("MOVIE");
        request.setParsedTitle(title);
        request.setParsedYear(year);
        request.setTmdbId(tmdbId);
        request.setRegenerateNfo(regenerateNfo);
        return request;
    }

    private MatchResult movieMatch(long tmdbId, String title, int year) {
        MatchResult match = new MatchResult();
        match.setSource("tmdb");
        match.setSourceId(String.valueOf(tmdbId));
        match.setTitle(title);
        match.setOriginalTitle(title);
        match.setYear(year);
        match.setMediaType("MOVIE");
        match.setConfidence(1.0);
        return match;
    }
}
