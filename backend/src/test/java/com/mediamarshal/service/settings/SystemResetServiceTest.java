package com.mediamarshal.service.settings;

import com.mediamarshal.model.entity.AppSetting;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.TaskCandidate;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.AppSettingRepository;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.repository.TaskCandidateRepository;
import com.mediamarshal.repository.WatchRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SystemResetServiceTest {

    @Autowired
    private SystemResetService systemResetService;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Autowired
    private WatchRuleRepository watchRuleRepository;

    @Autowired
    private MediaTaskRepository mediaTaskRepository;

    @Autowired
    private TaskCandidateRepository taskCandidateRepository;

    @BeforeEach
    void clean() {
        taskCandidateRepository.deleteAllInBatch();
        mediaTaskRepository.deleteAllInBatch();
        watchRuleRepository.deleteAllInBatch();
        appSettingRepository.deleteAllInBatch();
    }

    @Test
    void resetDeletesAllApplicationData() {
        AppSetting setting = new AppSetting();
        setting.setKey("tmdb.api-key");
        setting.setValue("test-key");
        setting.setSensitive(true);
        appSettingRepository.save(setting);

        WatchRule rule = new WatchRule();
        rule.setName("Movies");
        rule.setSourceDir("/media/inbox");
        rule.setTargetDir("/media/library");
        watchRuleRepository.save(rule);

        MediaTask task = new MediaTask();
        task.setSourcePath("/media/inbox/movie.mkv");
        task = mediaTaskRepository.save(task);

        TaskCandidate candidate = new TaskCandidate();
        candidate.setTask(task);
        candidate.setTmdbId(603L);
        candidate.setMediaType(MediaTask.MediaType.MOVIE);
        candidate.setRank(1);
        taskCandidateRepository.save(candidate);

        systemResetService.reset();

        assertThat(taskCandidateRepository.count()).isZero();
        assertThat(mediaTaskRepository.count()).isZero();
        assertThat(watchRuleRepository.count()).isZero();
        assertThat(appSettingRepository.count()).isZero();
    }
}
