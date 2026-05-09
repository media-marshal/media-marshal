package com.mediamarshal.service.pipeline;

import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.model.dto.QueueRecognitionRequest;
import com.mediamarshal.model.dto.QueueRecognitionResponse;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.TaskCandidate;
import com.mediamarshal.model.exception.MediaTaskFailureException;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.repository.TaskCandidateRepository;
import com.mediamarshal.notification.EmailNotificationService;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.matcher.MetadataMatcher;
import com.mediamarshal.service.nfo.NfoGeneratorService;
import com.mediamarshal.service.parser.GuessitParserClient;
import com.mediamarshal.service.rename.AssetOrganizerService;
import com.mediamarshal.service.rename.FileOperationStrategy;
import com.mediamarshal.service.settings.SettingsService;
import com.mediamarshal.websocket.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaProcessPipelineRecognitionTest {

    private MediaTaskRepository taskRepository;
    private TaskCandidateRepository candidateRepository;
    private MetadataMatcher metadataMatcher;
    private EventPublisher eventPublisher;
    private EmailNotificationService emailNotificationService;
    private MediaProcessPipeline pipeline;

    @BeforeEach
    void setUp() {
        taskRepository = mock(MediaTaskRepository.class);
        candidateRepository = mock(TaskCandidateRepository.class);
        metadataMatcher = mock(MetadataMatcher.class);
        eventPublisher = mock(EventPublisher.class);
        emailNotificationService = mock(EmailNotificationService.class);
        pipeline = new MediaProcessPipeline(
                mock(GuessitParserClient.class),
                metadataMatcher,
                mock(AssetOrganizerService.class),
                mock(NfoGeneratorService.class),
                taskRepository,
                candidateRepository,
                mock(WatchRuleRepository.class),
                mock(SettingsService.class),
                eventPublisher,
                emailNotificationService,
                Map.<String, FileOperationStrategy>of()
        );
        when(taskRepository.save(any(MediaTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updateRecognitionSavesMovieFieldsAndClearsEpisodeFields() {
        MediaTask task = awaitingTask();
        task.setParsedSeason(1);
        task.setParsedEpisode(2);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(candidateRepository.findByTask_IdOrderByRankAsc(1L)).thenReturn(List.of());

        QueueRecognitionRequest request = new QueueRecognitionRequest();
        request.setMediaType("MOVIE");
        request.setParsedTitle("The Matrix");
        request.setParsedYear(1999);
        request.setParsedSeason(9);
        request.setParsedEpisode(9);

        QueueRecognitionResponse response = pipeline.updateRecognition(1L, request);

        assertThat(response.task().getMediaType()).isEqualTo(MediaTask.MediaType.MOVIE);
        assertThat(response.task().getParsedTitle()).isEqualTo("The Matrix");
        assertThat(response.task().getParsedYear()).isEqualTo(1999);
        assertThat(response.task().getParsedSeason()).isNull();
        assertThat(response.task().getParsedEpisode()).isNull();
    }

    @Test
    void rematchUsesUpdatedRecognitionAndReplacesCandidateConfidence() {
        MediaTask task = awaitingTask();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        TaskCandidate oldCandidate = candidate(100L, "Old", 0.2);
        TaskCandidate newCandidate = candidate(200L, "New", 0.92);
        when(candidateRepository.findByTask_IdOrderByRankAsc(1L))
                .thenReturn(List.of(oldCandidate))
                .thenReturn(List.of(newCandidate));

        MatchResult match = new MatchResult();
        match.setSourceId("200");
        match.setTitle("New");
        match.setMediaType("TV_SHOW");
        match.setConfidence(0.92);
        when(metadataMatcher.search(any(ParseResult.class))).thenReturn(List.of(match));

        QueueRecognitionRequest request = new QueueRecognitionRequest();
        request.setMediaType("TV_SHOW");
        request.setParsedTitle("New Show");
        request.setParsedYear(2024);
        request.setParsedSeason(2);
        request.setParsedEpisode(3);

        QueueRecognitionResponse response = pipeline.updateRecognitionAndRematch(1L, request);

        ArgumentCaptor<ParseResult> parseCaptor = ArgumentCaptor.forClass(ParseResult.class);
        verify(metadataMatcher).search(parseCaptor.capture());
        assertThat(parseCaptor.getValue().getTitle()).isEqualTo("New Show");
        assertThat(parseCaptor.getValue().getType()).isEqualTo("episode");
        assertThat(parseCaptor.getValue().getSeason()).isEqualTo(2);
        assertThat(parseCaptor.getValue().getEpisode()).isEqualTo(3);
        verify(candidateRepository).deleteAll(List.of(oldCandidate));
        assertThat(response.task().getMatchConfidence()).isEqualTo(0.92);
        assertThat(response.candidates()).containsExactly(newCandidate);
    }

    @Test
    void updateRecognitionRejectsNonAwaitingTask() {
        MediaTask task = awaitingTask();
        task.setStatus(MediaTask.TaskStatus.PROCESSING);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        QueueRecognitionRequest request = new QueueRecognitionRequest();
        request.setMediaType("MOVIE");
        request.setParsedTitle("The Matrix");

        assertThatThrownBy(() -> pipeline.updateRecognition(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only awaiting confirmation tasks");
    }

    @Test
    void recordFailureMergesSameSourceRuleAndErrorCode() {
        MediaTask currentTask = new MediaTask();
        currentTask.setId(1L);
        currentTask.setSourcePath("D:/incoming/movie.mkv");
        currentTask.setRuleId(9L);

        MediaTask existingFailure = new MediaTask();
        existingFailure.setId(2L);
        existingFailure.setSourcePath("D:/incoming/movie.mkv");
        existingFailure.setRuleId(9L);
        existingFailure.setStatus(MediaTask.TaskStatus.FAILED);
        existingFailure.setErrorCode(MediaTask.TaskErrorCode.TARGET_CONFLICT);
        existingFailure.setFailureCount(1);

        when(taskRepository.findFirstBySourcePathAndRuleIdAndStatusAndErrorCodeOrderByUpdatedAtDesc(
                "D:/incoming/movie.mkv",
                9L,
                MediaTask.TaskStatus.FAILED,
                MediaTask.TaskErrorCode.TARGET_CONFLICT
        )).thenReturn(Optional.of(existingFailure));
        when(candidateRepository.findByTask_IdOrderByRankAsc(1L)).thenReturn(List.of());

        pipeline.recordFailure(
                currentTask,
                new MediaTaskFailureException(
                        MediaTask.TaskErrorCode.TARGET_CONFLICT,
                        "目标文件已存在，文件冲突"
                )
        );

        assertThat(existingFailure.getFailureCount()).isEqualTo(2);
        assertThat(existingFailure.getErrorMessage()).isEqualTo("目标文件已存在，文件冲突");
        assertThat(existingFailure.getLastFailedAt()).isNotNull();
        verify(taskRepository).save(existingFailure);
        verify(taskRepository).delete(currentTask);
        verify(eventPublisher).publishTaskFailed(existingFailure);
        verify(emailNotificationService, never()).notifyTaskFailed(existingFailure);
    }

    private MediaTask awaitingTask() {
        MediaTask task = new MediaTask();
        task.setId(1L);
        task.setStatus(MediaTask.TaskStatus.AWAITING_CONFIRMATION);
        task.setParsedTitle("Old Title");
        return task;
    }

    private TaskCandidate candidate(Long tmdbId, String title, Double confidence) {
        TaskCandidate candidate = new TaskCandidate();
        candidate.setId(tmdbId);
        candidate.setTmdbId(tmdbId);
        candidate.setTitle(title);
        candidate.setMediaType(MediaTask.MediaType.MOVIE);
        candidate.setConfidence(confidence);
        candidate.setRank(1);
        return candidate;
    }
}
