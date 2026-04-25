package com.mediamarshal.repository;

import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.TaskCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskCandidateRepository extends JpaRepository<TaskCandidate, Long> {

    List<TaskCandidate> findByTaskIdOrderByRankAsc(Long taskId);

    Optional<TaskCandidate> findByTaskIdAndTmdbIdAndMediaType(
            Long taskId,
            Long tmdbId,
            MediaTask.MediaType mediaType
    );
}
