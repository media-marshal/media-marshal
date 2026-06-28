package com.mediamarshal.model.dto;

import lombok.Data;

@Data
public class TaskCorrectionRequest {
    private String mediaType;
    private String parsedTitle;
    private Integer parsedYear;
    private Integer parsedSeason;
    private Integer parsedEpisode;
    private Integer parsedEpisodeEnd;
    private Long tmdbId;
    private Boolean regenerateNfo;
}
