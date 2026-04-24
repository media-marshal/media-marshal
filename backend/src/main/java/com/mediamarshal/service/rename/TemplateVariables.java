package com.mediamarshal.service.rename;

import lombok.Builder;
import lombok.Getter;

/**
 * 命名模板变量袋（ADR-001）
 *
 * 解析阶段一次性收集所有可用变量；渲染阶段由 TemplateRenderer 读取。
 *
 * 变量命名规范：严格遵循 ADR-001 定义，任何新增变量必须先在设计文档登记。
 * v1 未使用的字段保留为 null，渲染时对应占位符原样保留（可配置为空字符串）。
 *
 * 扩展方式：新增字段 + @TemplateVar 注解，渲染器自动识别，无需其他改动。
 */
@Getter
@Builder
public class TemplateVariables {

    // ─── 基础信息（来自 TMDB）────────────────────────────────────

    /** TMDB 本地化标题（随 tmdb.language 配置变化） */
    @TemplateVar("title")
    private String title;

    /** TMDB 原语言标题 */
    @TemplateVar("original_title")
    private String originalTitle;

    /** TMDB 发布年份 */
    @TemplateVar("year")
    private Integer year;

    /** TMDB 数字 ID */
    @TemplateVar("tmdb_id")
    private Long tmdbId;

    /** 媒体类型字符串（MOVIE / TV_SHOW） */
    @TemplateVar("media_type")
    private String mediaType;

    // ─── 剧集专用（来自 guessit + TMDB）─────────────────────────

    /** 季号（原始数值，用于 {season} 和格式化变体） */
    @TemplateVar("season")
    private Integer season;

    /** 集号（原始数值，用于 {episode} 和格式化变体） */
    @TemplateVar("episode")
    private Integer episode;

    /** TMDB 分集标题（预留，v1 不填充） */
    @TemplateVar("episode_title")
    private String episodeTitle;

    // ─── 归类辅助（来自 TMDB，预留）────────────────────────────

    @TemplateVar("title_initial")
    private String titleInitial;

    @TemplateVar("genre_1")
    private String genre1;

    @TemplateVar("genre_2")
    private String genre2;

    @TemplateVar("genre_3")
    private String genre3;

    @TemplateVar("genre_4")
    private String genre4;

    @TemplateVar("country")
    private String country;

    // ─── 技术参数（来自 guessit，预留）──────────────────────────

    @TemplateVar("resolution")
    private String resolution;

    @TemplateVar("codec")
    private String codec;

    @TemplateVar("release_group")
    private String releaseGroup;

    /** 原始文件扩展名（含点，如 ".mkv"） */
    @TemplateVar("ext")
    private String ext;
}
