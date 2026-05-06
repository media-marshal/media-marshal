package com.mediamarshal.model.entity;

/**
 * 源媒体资产类型。
 *
 * MediaTask 表示一个媒体资产处理任务；普通视频、ISO 镜像和蓝光原盘目录
 * 共享同一任务生命周期，但整理语义不同。
 */
public enum MediaAssetType {
    VIDEO_FILE,
    ISO_IMAGE,
    BLURAY_DIRECTORY
}
