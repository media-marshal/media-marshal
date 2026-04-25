package com.mediamarshal.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务候选匹配项（ADR-004）
 *
 * TMDB 搜索结果会长期保存在 task_candidate 表中，不随任务完成而删除，
 * 供人工确认、后续看板和报表统计使用。
 */
@Data
@Entity
@Table(name = "task_candidate")
public class TaskCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    @JsonIgnore
    private MediaTask task;

    @Column(nullable = false)
    private Long tmdbId;

    @Column(length = 500)
    private String title;

    @Column(length = 500)
    private String originalTitle;

    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MediaTask.MediaType mediaType;

    /** 匹配置信度 0.0-1.0 */
    private Double confidence;

    @Column(length = 500)
    private String posterUrl;

    @Column(columnDefinition = "TEXT")
    private String overview;

    /** 候选排序，1 表示系统最高推荐 */
    private Integer rank;

    /** 是否为最终选中的候选（自动高置信度匹配或人工确认） */
    @Column(nullable = false)
    private Boolean selected = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * JSON 响应中暴露 taskId，而不是整个 MediaTask 对象，避免递归序列化。
     */
    public Long getTaskId() {
        return task != null ? task.getId() : null;
    }
}
