package com.mediamarshal.model.dto;

import lombok.Data;

@Data
public class QueueRecognitionRequest {

    private String mediaType;

    private String parsedTitle;

    private Integer parsedYear;

    private Integer parsedSeason;

    private Integer parsedEpisode;
}
