package com.mediamarshal.service.discovery.asset;

import com.mediamarshal.model.entity.MediaAssetType;

import java.nio.file.Path;

/**
 * 文件发现层识别出的媒体资产。
 *
 * skipReason 非空表示该资产已被识别，但当前版本不进入后续解析/整理流程，
 * 发现层应记录 SKIPPED 任务并停止继续扫描其内部结构。
 */
public record MediaAsset(
        Path rootPath,
        MediaAssetType type,
        String displayName,
        boolean shouldPruneChildren,
        String skipReason
) {
    public MediaAsset(Path rootPath, MediaAssetType type, String displayName, boolean shouldPruneChildren) {
        this(rootPath, type, displayName, shouldPruneChildren, null);
    }

    public boolean shouldSkip() {
        return skipReason != null && !skipReason.isBlank();
    }
}
