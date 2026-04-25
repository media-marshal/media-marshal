package com.mediamarshal.repository;

import com.mediamarshal.model.entity.MediaTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaTaskRepository extends JpaRepository<MediaTask, Long> {

    List<MediaTask> findByStatus(MediaTask.TaskStatus status);

    List<MediaTask> findByStatusOrderByCreatedAtDesc(MediaTask.TaskStatus status);

    boolean existsBySourcePath(String sourcePath);

    /**
     * ADR-005 数据库查重：
     * 同一路径如果已有非 FAILED 任务，说明正在处理或已成功处理，应跳过，避免重复入库。
     */
    boolean existsBySourcePathAndStatusNot(String sourcePath, MediaTask.TaskStatus status);
}
