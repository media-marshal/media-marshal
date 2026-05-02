package com.mediamarshal.service.rename;

import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
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
 *   rename.template.movie → {title} ({year})/{title} ({year}) - {resolution}{ext}
 *   rename.template.tv    → {title} ({year})/S{season:02d}/{title} ({year}) - S{season:02d}E{episode:02d} - {resolution}{ext}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenameService {

    /** 全局默认电影模板 */
    private static final String DEFAULT_MOVIE_TEMPLATE =
            "{title} ({year})/{title} ({year}) - {resolution}{ext}";

    /** 全局默认剧集模板 */
    private static final String DEFAULT_TV_TEMPLATE =
            "{title} ({year})/S{season:02d}/{title} ({year}) - S{season:02d}E{episode:02d} - {resolution}{ext}";

    private static final Charset GBK = Charset.forName("GBK");
    private static final int[] GB2312_AREA_CODES = {
            -20319, -20283, -19775, -19218, -18710, -18526, -18239, -17922,
            -17417, -16474, -16212, -15640, -15165, -14922, -14914, -14630,
            -14149, -14090, -13318, -12838, -12556, -11847, -11055
    };
    private static final String[] PINYIN_INITIALS = {
            "A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "W", "X", "Y", "Z"
    };

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
            log.debug("TemplateVariables: title='{}', year={}, season={}, episode={}, resolution='{}', ext='{}'",
                    variables.getTitle(), variables.getYear(),
                    variables.getSeason(), variables.getEpisode(), variables.getResolution(), variables.getExt());
        }

        // 4. 渲染相对路径
        String relativePath = templateRenderer.render(template, variables);

        // 5. 拼接目标绝对路径
        Path target = Paths.get(rule.getTargetDir()).resolve(relativePath).normalize();

        log.info("Rename plan: source='{}' -> target='{}'", task.getSourcePath(), target);

        if (Files.exists(target)) {
            throw new IOException("目标文件已存在，文件冲突");
        }

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
                .titleInitial(resolveTitleInitial(task.getConfirmedTitle()))
                .resolution(task.getParsedResolution())
                // 以下字段为预留，v1 不填充
                .originalTitle(null)
                .episodeTitle(null)
                .genre1(null).genre2(null).genre3(null).genre4(null)
                .country(null)
                .codec(null)
                .releaseGroup(null)
                .build();
    }

    String resolveTitleInitial(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }

        int firstCodePoint = title.strip().codePointAt(0);
        if (Character.isDigit(firstCodePoint)) {
            return "#";
        }
        if (isAsciiLetter(firstCodePoint)) {
            return String.valueOf((char) Character.toUpperCase(firstCodePoint));
        }
        if (isCjkUnifiedIdeograph(firstCodePoint)) {
            return resolveChineseInitial(firstCodePoint);
        }

        return "#";
    }

    private boolean isAsciiLetter(int codePoint) {
        return (codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= 'a' && codePoint <= 'z');
    }

    private boolean isCjkUnifiedIdeograph(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS.equals(block);
    }

    private String resolveChineseInitial(int codePoint) {
        String character = new String(Character.toChars(codePoint));
        byte[] bytes = character.getBytes(GBK);
        if (bytes.length < 2) {
            return "#";
        }

        int code = (bytes[0] & 0xff) * 256 + (bytes[1] & 0xff) - 65536;
        for (int i = 0; i < GB2312_AREA_CODES.length - 1; i++) {
            if (code >= GB2312_AREA_CODES[i] && code < GB2312_AREA_CODES[i + 1]) {
                return PINYIN_INITIALS[i];
            }
        }
        if (code >= GB2312_AREA_CODES[GB2312_AREA_CODES.length - 1]) {
            return PINYIN_INITIALS[PINYIN_INITIALS.length - 1];
        }
        return "#";
    }

    private FileOperationStrategy resolveStrategy(String type) {
        return strategies.values().stream()
                .filter(s -> s.getType().name().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown file operation strategy: " + type));
    }
}
