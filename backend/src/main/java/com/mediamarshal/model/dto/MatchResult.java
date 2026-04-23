package com.mediamarshal.model.dto;

import lombok.Data;

/**
 * 元数据匹配结果 DTO
 *
 * 由 MetadataMatcher 实现（TMDB 等）填充，统一结构屏蔽数据源差异。
 */
@Data
public class MatchResult {

    /** 数据源标识（如 "tmdb"、"douban"） */
    private String source;

    /** 数据源内部 ID */
    private String sourceId;

    /** 标准标题 */
    private String title;

    /** 原语言标题 */
    private String originalTitle;

    /** 发布年份 */
    private Integer year;

    /** 媒体类型：MOVIE / TV_SHOW */
    private String mediaType;

    /** 简介 */
    private String overview;

    /** 海报 URL */
    private String posterUrl;

    /** 匹配置信度 0.0-1.0 */
    private Double confidence;
}
