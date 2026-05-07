package com.mediamarshal.service.settings;

import com.mediamarshal.repository.AppSettingRepository;
import com.mediamarshal.repository.MediaTaskRepository;
import com.mediamarshal.repository.TaskCandidateRepository;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.discovery.FileDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemResetService {

    private final TaskCandidateRepository taskCandidateRepository;
    private final MediaTaskRepository mediaTaskRepository;
    private final WatchRuleRepository watchRuleRepository;
    private final AppSettingRepository appSettingRepository;
    private final FileDiscoveryService fileDiscoveryService;

    @Transactional
    public void reset() {
        taskCandidateRepository.deleteAllInBatch();
        mediaTaskRepository.deleteAllInBatch();
        watchRuleRepository.deleteAllInBatch();
        appSettingRepository.deleteAllInBatch();
        fileDiscoveryService.reload();
        log.warn("System reset completed: all application database records were deleted");
    }
}
