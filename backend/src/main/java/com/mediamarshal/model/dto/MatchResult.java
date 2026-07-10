package com.mediamarshal.model.dto;

import lombok.Data;

import java.util.List;

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

    /** TMDB 分类标签，最多保留 4 个 */
    private List<String> genres;

    /** TMDB 原产国 / 出品国 ISO 3166-1 alpha-2 代码 */
    private String country;

    /** TV 单集分集标题，多集范围为空 */
    private String episodeTitle;

    /** 匹配置信度 0.0-1.0 */
    private Double confidence;
}
