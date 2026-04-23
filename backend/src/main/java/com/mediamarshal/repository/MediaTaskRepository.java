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
}
