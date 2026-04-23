package com.mediamarshal.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * guessit sidecar 解析结果 DTO
 *
 * 字段名称与 guessit 返回的 JSON 字段保持一致（使用 @JsonProperty 映射）。
 */
@Data
public class ParseResult {

    private String title;

    private Integer year;

    @JsonProperty("episode")
    private Integer episode;

    @JsonProperty("season")
    private Integer season;

    @JsonProperty("type")
    private String type;           // "movie" or "episode"

    @JsonProperty("release_group")
    private String releaseGroup;

    @JsonProperty("screen_size")
    private String screenSize;

    @JsonProperty("video_codec")
    private String videoCodec;

    /** 原始文件名（由 Java 端补充，guessit 不返回此字段） */
    private String originalFilename;
}
