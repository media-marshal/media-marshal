package com.mediamarshal.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mediamarshal.service.rename.FileOperationStrategy;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 监控规则实体（ADR-002）
 *
 * 每条规则代表一个"源目录 → 目标目录"的完整整理策略，
 * 替代原来的 watcher.watch-dirs / output.movie-dir 等平铺配置项。
 *
 * media_type = AUTO 时，系统用 guessit 自动判断，无法判断则进人工确认队列。
 * movie_path_template / tv_path_template 为 NULL 时，fallback 到对应全局模板配置。
 *
 * user_id：v1 始终为 null，多用户版本启用后按此字段隔离规则，无需改表结构。
 */
@Data
@Entity
@Table(name = "watch_rule")
public class WatchRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户起的友好名称，如"电影库"、"剧集收录" */
    @Column(nullable = false, length = 100)
    private String name;

    /** 监控的源目录绝对路径（容器内路径） */
    @Column(nullable = false, length = 500)
    private String sourceDir;

    /** 整理后输出的目标根目录（容器内路径） */
    @Column(nullable = false, length = 500)
    private String targetDir;

    /**
     * 媒体类型约束
     * MOVIE / TV_SHOW：强制按此类型处理
     * AUTO：guessit 自动识别，无法识别时进人工确认队列
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleMediaType mediaType = RuleMediaType.AUTO;

    /** 电影目标目录内的路径模板（ADR-001 变量系统），NULL 时 fallback 到全局电影模板 */
    @Column(length = 500)
    private String moviePathTemplate;

    /** 剧集目标目录内的路径模板（ADR-001 变量系统），NULL 时 fallback 到全局剧集模板 */
    @Column(length = 500)
    private String tvPathTemplate;

    /** 文件操作方式，v1 仅实现 MOVE */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileOperationStrategy.OperationType operation = FileOperationStrategy.OperationType.MOVE;

    /** 是否启用，禁用后 FileWatcherService 不注册此目录 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 是否自动移动同名字幕、用户 NFO、封面等附属文件 */
    @Column(nullable = false)
    private Boolean moveAssociatedFiles = true;

    /** MOVE 成功后是否清理源目录中产生的空文件夹 */
    @Column(nullable = false)
    private Boolean cleanupEmptyDirs = false;

    /** 没有用户自带 NFO 时是否自动生成 NFO */
    @Column(nullable = false)
    private Boolean generateNfo = false;

    /** v1 保留字段；NULL 表示使用系统默认忽略规则 */
    @Column(columnDefinition = "TEXT")
    private String ignoredFilePatterns;

    /** 多用户预留字段，v1 始终为 null */
    private Long userId;

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

    /**
     * 规则级别的媒体类型枚举（与 MediaTask.MediaType 区分）
     * AUTO 是规则特有的，表示"由系统自动判断"
     */
    public enum RuleMediaType {
        MOVIE,
        TV_SHOW,
        AUTO
    }

    public List<String> getIgnoredFilePatterns() {
        return ignoredFilePatterns == null || ignoredFilePatterns.isBlank()
                ? null
                : Arrays.stream(ignoredFilePatterns.split("\n")).filter(s -> !s.isBlank()).toList();
    }

    public void setIgnoredFilePatterns(List<String> ignoredFilePatterns) {
        this.ignoredFilePatterns = ignoredFilePatterns == null || ignoredFilePatterns.isEmpty()
                ? null
                : String.join("\n", ignoredFilePatterns);
    }

    @JsonIgnore
    public String getIgnoredFilePatternsText() {
        return ignoredFilePatterns;
    }
}
