package com.mediamarshal.config;

import com.mediamarshal.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.stereotype.Component;

/**
 * Keeps the code-level media-marshal.debug switch aligned with logger output.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebugLoggingConfigurer implements ApplicationRunner {

    private static final String APP_LOGGER = "com.mediamarshal";

    private final SettingsService settingsService;

    @Override
    public void run(ApplicationArguments args) {
        if (!Boolean.parseBoolean(settingsService.get("debug", "false"))) {
            return;
        }

        LoggingSystem.get(getClass().getClassLoader())
                .setLogLevel(APP_LOGGER, LogLevel.DEBUG);
        log.info("Media Marshal debug logging enabled: logger={}, level={}", APP_LOGGER, LogLevel.DEBUG);
    }
}
