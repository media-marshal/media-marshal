package com.mediamarshal.service.settings;

import com.mediamarshal.model.entity.AppSetting;
import com.mediamarshal.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 配置管理服务
 *
 * 统一入口读取和写入系统配置，屏蔽三个配置源的优先级差异。
 *
 * 优先级（从高到低）：
 *   1. 环境变量：MEDIA_MARSHAL_{KEY_UPPER_SNAKE}
 *   2. application.yml：media-marshal.{key}
 *   3. 数据库（app_setting 表，由 Web UI 写入）
 *
 * 例外：tmdb.api-key 只能通过 Web UI 写入数据库，避免测试 / 部署环境变量覆盖页面配置。
 *
 * 所有模块通过本服务读取配置，不得直接读取 @Value 或 Environment（统一管理便于调试）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final AppSettingRepository settingRepository;
    private final Environment environment;

    /**
     * 读取配置值，按优先级依次查找
     *
     * @param key          配置键（点分格式，如 "tmdb.api-key"）
     * @param defaultValue 所有来源均未配置时的默认值
     */
    public String get(String key, String defaultValue) {
        if ("tmdb.api-key".equals(key)) {
            return settingRepository.findByKey(key)
                    .map(AppSetting::getValue)
                    .orElse(defaultValue);
        }

        // 1. 环境变量。TMDB API Key 是敏感业务配置，只允许从 Web UI / 数据库读取。
        String envKey = "MEDIA_MARSHAL_" + key.replace(".", "_").replace("-", "_").toUpperCase();
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        // 2. application.yml
        String ymlValue = environment.getProperty("media-marshal." + key);
        if (ymlValue != null && !ymlValue.isBlank()) {
            return ymlValue;
        }

        // 3. 数据库
        return settingRepository.findByKey(key)
                .map(AppSetting::getValue)
                .orElse(defaultValue);
    }

    /**
     * 通过 Web UI 保存配置到数据库
     * 注意：此操作不会覆盖环境变量，运行时读取仍以环境变量优先
     */
    public void set(String key, String value, String description, boolean sensitive) {
        AppSetting setting = settingRepository.findByKey(key).orElseGet(AppSetting::new);
        setting.setKey(key);
        setting.setValue(value);
        setting.setDescription(description);
        setting.setSensitive(sensitive);
        settingRepository.save(setting);
        log.info("Setting updated: key={}", key);
    }

    /**
     * 查询所有配置项（敏感项脱敏后返回）
     */
    public List<AppSetting> getAll() {
        List<AppSetting> settings = settingRepository.findAll();
        settings.forEach(s -> {
            if (Boolean.TRUE.equals(s.getSensitive())) {
                s.setValue(mask(s.getValue()));
            }
        });
        return settings;
    }

    private String mask(String value) {
        if (value == null || value.length() <= 4) return "****";
        return value.substring(0, 4) + "****";
    }
}
