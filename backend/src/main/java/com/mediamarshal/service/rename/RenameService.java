package com.mediamarshal.service.rename;

import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

/**
 * 重命名服务（ADR-001 + ADR-002 整合）
 *
 * 职责：
 *   1. 通过 task.ruleId 加载对应的 WatchRule
 *   2. 根据任务最终媒体类型选取有效路径模板（规则类型模板 > 全局默认模板）
 *   3. 从 task 数据构建 TemplateVariables 变量袋
 *   4. 调用 TemplateRenderer 渲染最终相对路径
 *   5. 拼接 targetDir + 渲染路径，得到目标绝对路径
 *   6. 委托对应的 FileOperationStrategy 执行文件操作
 *
 * 全局默认模板配置键（AppSetting / application.yml）：
 *   rename.template.movie → {title} ({year})/{title} ({year}){ext}
 *   rename.template.tv    → {title}/Season {season:02d}/{title} - S{season:02d}E{episode:02d}{ext}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenameService {

    /** 全局默认电影模板（ADR-001 v1 默认值） */
    private static final String DEFAULT_MOVIE_TEMPLATE =
            "{title} ({year})/{title} ({year}){ext}";

    /** 全局默认剧集模板（ADR-001 v1 默认值） */
    private static final String DEFAULT_TV_TEMPLATE =
            "{title}/Season {season:02d}/{title} - S{season:02d}E{episode:02d}{ext}";

    private final Map<String, FileOperationStrategy> strategies;
    private final WatchRuleRepository watchRuleRepository;
    private final SettingsService settingsService;
    private final TemplateRenderer templateRenderer;

    /**
     * 根据 MediaTask 执行重命名操作，返回目标文件绝对路径
     *
     * @param task 包含完整元数据（tmdbId、confirmedTitle、year、season、episode 等均已填充）
     * @return 目标文件路径
     * @throws IOException              文件操作失败
     * @throws IllegalArgumentException ruleId 无效或规则不存在
     */
    public Path rename(MediaTask task) throws IOException {
        boolean isDebug = Boolean.parseBoolean(settingsService.get("debug", "false"));

        // 1. 加载规则
        Long ruleId = Objects.requireNonNull(task.getRuleId(), "MediaTask.ruleId is required for rename");
        WatchRule rule = watchRuleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "WatchRule not found: ruleId=" + ruleId));

        // 2. 选取路径模板：规则模板 > 全局配置 > 内置默认值
        String template = resolveTemplate(rule, task.getMediaType());

        if (isDebug) {
            log.debug("Template resolved: rule='{}', mediaType={}, template='{}'",
                    rule.getName(), task.getMediaType(), template);
        }

        // 3. 构建变量袋
        TemplateVariables variables = buildVariables(task);

        if (isDebug) {
            log.debug("TemplateVariables: title='{}', year={}, season={}, episode={}, ext='{}'",
                    variables.getTitle(), variables.getYear(),
                    variables.getSeason(), variables.getEpisode(), variables.getExt());
        }

        // 4. 渲染相对路径
        String relativePath = templateRenderer.render(template, variables);

        // 5. 拼接目标绝对路径
        Path target = Paths.get(rule.getTargetDir()).resolve(relativePath).normalize();

        log.info("Rename plan: source='{}' -> target='{}'", task.getSourcePath(), target);

        // 6. 执行文件操作
        FileOperationStrategy strategy = resolveStrategy(rule.getOperation().name());
        strategy.execute(Paths.get(task.getSourcePath()), target);

        log.info("Rename completed: {}", target);
        return target;
    }

    /**
     * 模板选取优先级：对应媒体类型的规则自定义模板 > 全局配置模板 > 内置默认模板
     */
    String resolveTemplate(WatchRule rule, MediaTask.MediaType mediaType) {
        if (mediaType == MediaTask.MediaType.MOVIE) {
            String ruleTemplate = rule.getMoviePathTemplate();
            if (ruleTemplate != null && !ruleTemplate.isBlank()) {
                return ruleTemplate;
            }
            return settingsService.get("rename.template.movie", DEFAULT_MOVIE_TEMPLATE);
        }

        if (mediaType == MediaTask.MediaType.TV_SHOW) {
            String ruleTemplate = rule.getTvPathTemplate();
            if (ruleTemplate != null && !ruleTemplate.isBlank()) {
                return ruleTemplate;
            }
            return settingsService.get("rename.template.tv", DEFAULT_TV_TEMPLATE);
        }

        throw new IllegalArgumentException("Media type is required before rendering target path");
    }

    /**
     * 从 MediaTask 构建 TemplateVariables 变量袋
     * task 中的字段在 Pipeline Step 4 完成后应已全部填充
     */
    private TemplateVariables buildVariables(MediaTask task) {
        String sourcePath = task.getSourcePath();
        String ext = sourcePath.contains(".")
                ? sourcePath.substring(sourcePath.lastIndexOf('.'))
                : "";

        return TemplateVariables.builder()
                .title(task.getConfirmedTitle())
                .year(task.getConfirmedYear())
                .tmdbId(task.getTmdbId())
                .mediaType(task.getMediaType() != null ? task.getMediaType().name() : null)
                .season(task.getParsedSeason())
                .episode(task.getParsedEpisode())
                .ext(ext)
                // 以下字段为预留，v1 不填充
                .originalTitle(null)
                .episodeTitle(null)
                .titleInitial(null)
                .genre1(null).genre2(null).genre3(null).genre4(null)
                .country(null)
                .resolution(null)
                .codec(null)
                .releaseGroup(null)
                .build();
    }

    private FileOperationStrategy resolveStrategy(String type) {
        return strategies.values().stream()
                .filter(s -> s.getType().name().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown file operation strategy: " + type));
    }
}
