package com.mediamarshal.service.queue;

import com.mediamarshal.model.dto.BatchRecognitionField;
import com.mediamarshal.model.dto.EpisodeAssignmentMode;
import com.mediamarshal.model.dto.EpisodeSortDirection;
import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.model.dto.QueueBatchRecognitionPreview;
import com.mediamarshal.model.dto.QueueBatchRecognitionPreviewItem;
import com.mediamarshal.model.dto.QueueBatchRecognitionRematchResponse;
import com.mediamarshal.model.dto.QueueBatchRecognitionRequest;
import com.mediamarshal.model.dto.QueueBatchRecognitionSaveResponse;
import com.mediamarshal.model.dto.QueueBatchRecognitionRematchResult;
import com.mediamarshal.model.dto.QueueBatchRecognitionRematchStatus;
import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.TaskCandidate;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.repository.TaskCandidateRepository;
import com.mediamarshal.service.matcher.MetadataMatcher;
import com.mediamarshal.websocket.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueueBatchRecognitionServiceTest {

    private MediaTaskRepository taskRepository;
    private TaskCandidateRepository candidateRepository;
    private MetadataMatcher metadataMatcher;
    private PlatformTransactionManager transactionManager;
    private QueueBatchRecognitionService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(MediaTaskRepository.class);
        candidateRepository = mock(TaskCandidateRepository.class);
        metadataMatcher = mock(MetadataMatcher.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new QueueBatchRecognitionService(
                taskRepository,
                candidateRepository,
                metadataMatcher,
                mock(EventPublisher.class),
                transactionManager
        );
        when(taskRepository.save(any(MediaTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidateRepository.save(any(TaskCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void previewAssignsSequentialEpisodesByNaturalFilenameOrder() {
        MediaTask episode10 = tvTask(10L, "/media/show/Show.S01E10.mkv", 1, null, null);
        MediaTask episode2 = tvTask(2L, "/media/show/Show.S01E02.mkv", 1, null, null);
        MediaTask episode1 = tvTask(1L, "/media/show/Show.S01E01.mkv", 1, null, null);
        when(taskRepository.findAllById(any())).thenReturn(List.of(episode10, episode2, episode1));

        QueueBatchRecognitionRequest request = new QueueBatchRecognitionRequest();
        request.setTaskIds(List.of(10L, 2L, 1L));
        request.setEpisodeAssignmentMode(EpisodeAssignmentMode.SEQUENTIAL);
        request.setEpisodeStart(1);
        request.setEpisodeSortDirection(EpisodeSortDirection.ASC);

        QueueBatchRecognitionPreview preview = service.preview(request);

        assertThat(preview.canApply()).isTrue();
        assertThat(preview.items()).map(QueueBatchRecognitionPreviewItem::taskId).containsExactly(1L, 2L, 10L);
        assertThat(preview.items()).map(QueueBatchRecognitionPreviewItem::effectiveEpisode).containsExactly(1, 2, 3);
        assertThat(preview.sequentialCount()).isEqualTo(3);
    }

    @Test
    void previewBlocksPreserveModeWhenTvEpisodeIsMissing() {
        MediaTask task = tvTask(1L, "/media/show/Show.S01E01.mkv", 1, null, null);
        when(taskRepository.findAllById(any())).thenReturn(List.of(task));

        QueueBatchRecognitionRequest request = new QueueBatchRecognitionRequest();
        request.setTaskIds(List.of(1L));
        request.setUpdateFields(List.of(BatchRecognitionField.PARSED_TITLE));
        request.setParsedTitle("Show");
        request.setEpisodeAssignmentMode(EpisodeAssignmentMode.PRESERVE);

        QueueBatchRecognitionPreview preview = service.preview(request);

        assertThat(preview.canApply()).isFalse();
        assertThat(preview.items().getFirst().blockers()).contains("保留集号模式要求每条剧集任务已有集号");
    }

    @Test
    void previewBlocksSequentialModeForEpisodeRanges() {
        MediaTask task = tvTask(1L, "/media/show/Show.S01E01-E02.mkv", 1, 1, 2);
        when(taskRepository.findAllById(any())).thenReturn(List.of(task));

        QueueBatchRecognitionRequest request = new QueueBatchRecognitionRequest();
        request.setTaskIds(List.of(1L));
        request.setEpisodeAssignmentMode(EpisodeAssignmentMode.SEQUENTIAL);
        request.setEpisodeStart(1);

        QueueBatchRecognitionPreview preview = service.preview(request);

        assertThat(preview.canApply()).isFalse();
        assertThat(preview.items().getFirst().blockers()).contains("多集范围任务暂不支持顺序集号生成");
    }

    @Test
    void saveCanChangeTvTaskToMovieAndClearsEpisodeFieldsWithoutTouchingCandidates() {
        MediaTask task = tvTask(1L, "/media/show/Show.S01E01.mkv", 1, 1, null);
        when(taskRepository.findAllById(any())).thenReturn(List.of(task));

        QueueBatchRecognitionRequest request = new QueueBatchRecognitionRequest();
        request.setTaskIds(List.of(1L));
        request.setUpdateFields(List.of(BatchRecognitionField.MEDIA_TYPE));
        request.setMediaType(MediaTask.MediaType.MOVIE);

        QueueBatchRecognitionSaveResponse response = service.save(request);

        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(task.getMediaType()).isEqualTo(MediaTask.MediaType.MOVIE);
        assertThat(task.getParsedSeason()).isNull();
        assertThat(task.getParsedEpisode()).isNull();
        assertThat(task.getParsedEpisodeEnd()).isNull();
        verify(candidateRepository, never()).deleteByTask_Id(anyLong());
    }

    @Test
    void rematchKeepsSavedRecognitionWhenOneTaskSearchFails() {
        MediaTask first = tvTask(1L, "/media/show/Show.S01E01.mkv", 1, 1, null);
        MediaTask second = tvTask(2L, "/media/show/Show.S01E02.mkv", 1, 2, null);
        when(taskRepository.findAllById(any())).thenReturn(List.of(first, second));
        when(taskRepository.findById(1L)).thenReturn(java.util.Optional.of(first));
        when(taskRepository.findById(2L)).thenReturn(java.util.Optional.of(second));
        when(metadataMatcher.search(any(ParseResult.class))).thenAnswer(invocation -> {
            ParseResult parseResult = invocation.getArgument(0);
            if (parseResult.getEpisode() == 1) {
                MatchResult match = new MatchResult();
                match.setSourceId("100");
                match.setTitle("New Show");
                match.setMediaType("TV_SHOW");
                match.setConfidence(0.91);
                return List.of(match);
            }
            throw new IllegalStateException("TMDB unavailable");
        });

        QueueBatchRecognitionRequest request = new QueueBatchRecognitionRequest();
        request.setTaskIds(List.of(1L, 2L));
        request.setUpdateFields(List.of(BatchRecognitionField.PARSED_TITLE));
        request.setParsedTitle("New Show");
        request.setEpisodeAssignmentMode(EpisodeAssignmentMode.PRESERVE);

        QueueBatchRecognitionRematchResponse response = service.saveAndRematch(request);

        assertThat(first.getParsedTitle()).isEqualTo("New Show");
        assertThat(second.getParsedTitle()).isEqualTo("New Show");
        assertThat(response.matchedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.results()).map(QueueBatchRecognitionRematchResult::status)
                .containsExactly(
                        QueueBatchRecognitionRematchStatus.MATCHED,
                        QueueBatchRecognitionRematchStatus.FAILED
                );
        verify(candidateRepository, times(2)).deleteByTask_Id(anyLong());
        assertThat(second.getMatchConfidence()).isNull();
    }

    private MediaTask tvTask(Long id, String sourcePath, Integer season, Integer episode, Integer episodeEnd) {
        MediaTask task = new MediaTask();
        task.setId(id);
        task.setSourcePath(sourcePath);
        task.setStatus(MediaTask.TaskStatus.AWAITING_CONFIRMATION);
        task.setAssetType(MediaAssetType.VIDEO_FILE);
        task.setMediaType(MediaTask.MediaType.TV_SHOW);
        task.setParsedTitle("Show");
        task.setParsedSeason(season);
        task.setParsedEpisode(episode);
        task.setParsedEpisodeEnd(episodeEnd);
        return task;
    }
}
