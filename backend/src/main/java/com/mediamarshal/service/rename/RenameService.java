package com.mediamarshal.service.rename;

import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 重命名服务
 *
 * 职责：
 *  1. 根据 MediaTask 中的确认信息，套用命名模板生成目标路径
 *  2. 委托 FileOperationStrategy 执行实际的文件操作
 *  3. 策略通过配置项 media-marshal.operation.strategy 动态选取
 *
 * 命名模板格式（可在 Settings 中配置）：
 *  电影：{title} ({year})/{title} ({year}){ext}
 *  剧集：{title}/Season {season}/{title} - S{season:02d}E{episode:02d}{ext}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenameService {

    private final Map<String, FileOperationStrategy> strategies;
    private final SettingsService settingsService;

    /**
     * 根据 MediaTask 执行重命名操作，返回目标路径
     */
    public Path rename(MediaTask task) throws IOException {
        String strategyType = settingsService.get("operation.strategy", "MOVE");
        FileOperationStrategy strategy = resolveStrategy(strategyType);

        Path source = Paths.get(task.getSourcePath());
        Path target = buildTargetPath(task);

        log.info("Executing {} : {} -> {}", strategy.getType(), source, target);
        strategy.execute(source, target);
        return target;
    }

    private FileOperationStrategy resolveStrategy(String type) {
        return strategies.values().stream()
                .filter(s -> s.getType().name().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown strategy: " + type));
    }

    /**
     * TODO: 根据媒体类型和命名模板构建目标路径
     * 模板读取顺序：数据库配置 -> application.yml -> 内置默认值
     */
    private Path buildTargetPath(MediaTask task) {
        // TODO: implement template engine
        throw new UnsupportedOperationException("Template-based path building not yet implemented");
    }
}
