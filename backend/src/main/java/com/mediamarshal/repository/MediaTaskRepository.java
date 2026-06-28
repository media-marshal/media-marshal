package com.mediamarshal.repository;

import com.mediamarshal.model.entity.MediaTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MediaTaskRepository extends JpaRepository<MediaTask, Long> {

    List<MediaTask> findByStatus(MediaTask.TaskStatus status);

    List<MediaTask> findByStatusOrderByCreatedAtDesc(MediaTask.TaskStatus status);

    boolean existsBySourcePath(String sourcePath);

    /**
     * ADR-028 后的当前有效任务查重：
     * PENDING / PROCESSING / AWAITING_CONFIRMATION / DONE / SKIPPED 视为有效记录；
     * FAILED / CORRECTED 只保留历史，不阻止后续发现。
     */
    boolean existsBySourcePathAndStatusIn(String sourcePath, Collection<MediaTask.TaskStatus> statuses);

    Optional<MediaTask> findBySourcePath(String sourcePath);

    List<MediaTask> findByRuleIdAndStatusIn(Long ruleId, Collection<MediaTask.TaskStatus> statuses);

    Optional<MediaTask> findFirstBySourcePathAndRuleIdAndStatusAndErrorCodeOrderByUpdatedAtDesc(
            String sourcePath,
            Long ruleId,
            MediaTask.TaskStatus status,
            MediaTask.TaskErrorCode errorCode
    );
}
