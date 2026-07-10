package com.mediamarshal.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 媒体处理任务实体
 *
 * 生命周期：文件被监控发现 -> PENDING -> PROCESSING -> DONE / AWAITING_CONFIRMATION / FAILED。
 * DONE 任务被用户纠错后会转为 CORRECTED，并由新的 DONE 任务承载当前有效资产。
 */
@Data
@Entity
@Table(name = "media_task")
public class MediaTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 源媒体资产绝对路径；普通视频/ISO 为文件路径，蓝光原盘为外层根目录路径。 */
    @Column(nullable = false)
    private String sourcePath;

    /** 处理后资产路径；目录型资产表示目标根目录。 */
    private String targetPath;

    /** 源媒体资产类型。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MediaAssetType assetType = MediaAssetType.VIDEO_FILE;

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

    private Integer parsedEpisodeEnd;

    /** guessit 解析出的分辨率，如 1080p */
    private String parsedResolution;

    /** guessit 解析出的视频编码，如 H.264 */
    private String parsedCodec;

    /** guessit 解析出的发布组 */
    private String parsedReleaseGroup;

    /** TMDB 匹配到的 ID */
    private Long tmdbId;

    /** TMDB 返回的标准标题（用于重命名） */
    private String confirmedTitle;

    /** TMDB 返回的原语种标题 */
    private String confirmedOriginalTitle;

    /** TMDB 返回的发布年份 */
    private Integer confirmedYear;

    @Column(length = 100)
    private String confirmedGenre1;

    @Column(length = 100)
    private String confirmedGenre2;

    @Column(length = 100)
    private String confirmedGenre3;

    @Column(length = 100)
    private String confirmedGenre4;

    @Column(length = 10)
    private String confirmedCountry;

    @Column(length = 500)
    private String confirmedEpisodeTitle;

    /** 匹配置信度 0.0-1.0，低于阈值进入 AWAITING_CONFIRMATION */
    private Double matchConfidence;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private TaskErrorCode errorCode;

    private Integer failureCount = 0;

    private LocalDateTime lastFailedAt;

    /** 元数据确认来源，用于区分自动采纳、单条人工确认和批量人工确认。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ConfirmationSource confirmationSource;

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

    /** 新修正任务指向被修正的旧任务。 */
    private Long correctedFromTaskId;

    /** 旧任务指向修正后的新任务。 */
    private Long correctedToTaskId;

    /** 旧任务被修正的时间。 */
    private LocalDateTime correctedAt;

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
        SKIPPED,
        /** 曾经完成整理，后续已被用户手动修正。 */
        CORRECTED
    }

    public enum MediaType {
        MOVIE,
        TV_SHOW
    }

    public enum ConfirmationSource {
        AUTO_MATCH,
        MANUAL_SINGLE,
        MANUAL_BATCH,
        MANUAL_CORRECTION
    }

    public enum TaskErrorCode {
        TARGET_CONFLICT,
        UNSAFE_TARGET_PATH,
        SOURCE_MISSING,
        PIPELINE_FAILED
    }
}
