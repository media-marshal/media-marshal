package com.mediamarshal.model.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 系统配置项实体
 *
 * 配置优先级（高到低）：环境变量 > application.yml > 数据库（本表）
 * 通过 Web UI 修改的配置保存在本表中作为兜底默认值。
 */
@Data
@Entity
@Table(name = "app_setting")
public class AppSetting {

    /** 配置键，全局唯一，推荐使用点分命名（如 tmdb.api-key） */
    @Id
    private String key;

    /** 配置值 */
    @Column(columnDefinition = "TEXT")
    private String value;

    /** 配置描述，展示在 Web UI 设置页 */
    private String description;

    /** 是否为敏感数据（API Key 等），前端返回时脱敏 */
    @Column(nullable = false)
    private Boolean sensitive = false;
}
