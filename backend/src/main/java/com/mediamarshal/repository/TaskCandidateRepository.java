package com.mediamarshal.repository;

import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.TaskCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskCandidateRepository extends JpaRepository<TaskCandidate, Long> {

    /**
     * 按关联任务 ID 查询候选列表。
     *
     * 注意：TaskCandidate 实体字段是 task（ManyToOne），不是 taskId；
     * Spring Data JPA 访问关联字段需要使用下划线路径 task.id → Task_Id。
     */
    List<TaskCandidate> findByTask_IdOrderByRankAsc(Long taskId);

    Optional<TaskCandidate> findByTask_IdAndTmdbIdAndMediaType(
            Long taskId,
            Long tmdbId,
            MediaTask.MediaType mediaType
    );
}
