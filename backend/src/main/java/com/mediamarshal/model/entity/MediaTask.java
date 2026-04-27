package com.mediamarshal.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 媒体处理任务实体
 *
 * 生命周期：文件被监控发现 -> PENDING -> PROCESSING -> DONE / AWAITING_CONFIRMATION / FAILED
 */
@Data
@Entity
@Table(name = "media_task")
public class MediaTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 原始文件绝对路径 */
    @Column(nullable = false)
    private String sourcePath;

    /** 处理后文件路径（移动/复制完成后填写） */
    private String targetPath;

    /** 任务状态 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    /** 媒体类型 */
    @Enumerated(EnumType.STRING)
    private MediaType mediaType;

    /** guessit 解析出的原始标题 */
    private String parsedTitle;

    /** guessit 解析出的年份 */
    private Integer parsedYear;

    /** guessit 解析出的季号（剧集） */
    private Integer parsedSeason;

    /** guessit 解析出的集号（剧集） */
    private Integer parsedEpisode;

    /** TMDB 匹配到的 ID */
    private Long tmdbId;

    /** TMDB 返回的标准标题（用于重命名） */
    private String confirmedTitle;

    /** TMDB 返回的发布年份 */
    private Integer confirmedYear;

    /** 匹配置信度 0.0-1.0，低于阈值进入 AWAITING_CONFIRMATION */
    private Double matchConfidence;

    /**
     * 触发此任务的监控规则 ID（FK → watch_rule.id，ADR-002）
     * 用于 RenameService 查询目标目录和路径模板
     */
    private Long ruleId;

    /** 执行的文件操作类型（MOVE / COPY / HARD_LINK / SYMBOLIC_LINK） */
    private String operationType;

    /** 失败或警告信息 */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /** 跳过原因，仅 status=SKIPPED 时使用 */
    @Column(length = 500)
    private String skipReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum TaskStatus {
        /** 新入队，等待调度 */
        PENDING,
        /** 正在处理 */
        PROCESSING,
        /** 低置信度，等待人工确认后继续 */
        AWAITING_CONFIRMATION,
        /** 处理完成 */
        DONE,
        /** 处理失败 */
        FAILED,
        /** 已扫描但根据规则跳过，不属于错误 */
        SKIPPED
    }

    public enum MediaType {
        MOVIE,
        TV_SHOW
    }
}
