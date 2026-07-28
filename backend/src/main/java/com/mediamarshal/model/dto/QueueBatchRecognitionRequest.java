package com.mediamarshal.model.dto;

import com.mediamarshal.model.entity.MediaTask;
import lombok.Data;

import java.util.List;

@Data
public class QueueBatchRecognitionRequest {

    private List<Long> taskIds = List.of();

    private List<BatchRecognitionField> updateFields = List.of();

    private MediaTask.MediaType mediaType;

    private String parsedTitle;

    private Integer parsedYear;

    private Integer parsedSeason;

    private EpisodeAssignmentMode episodeAssignmentMode = EpisodeAssignmentMode.PRESERVE;

    private Integer episodeStart;

    private EpisodeSortDirection episodeSortDirection = EpisodeSortDirection.ASC;
}
