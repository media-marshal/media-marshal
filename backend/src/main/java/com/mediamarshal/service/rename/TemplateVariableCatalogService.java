package com.mediamarshal.service.rename;

import com.mediamarshal.model.dto.TemplateVariableGroup;
import com.mediamarshal.model.dto.TemplateVariableGroup.TemplateVariableItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 路径模板变量目录。
 *
 * 变量名以 TemplateVariables 上的 @TemplateVar 为准；说明和样例按 ADR-001
 * 固化在后端，供前端自定义模板帮助面板展示。
 */
@Service
public class TemplateVariableCatalogService {

    private static final List<String> ALL_MEDIA_TYPES = List.of("MOVIE", "TV_SHOW");
    private static final List<String> TV_ONLY = List.of("TV_SHOW");

    public List<TemplateVariableGroup> listVariables() {
        Set<String> supportedNames = supportedTemplateVarNames();

        return List.of(
                group("BASIC", "基础信息", List.of(
                        item("title", "String", "TMDB", "TMDB 本地化标题，随 tmdb.language 配置变化。", "蝙蝠侠：黑暗骑士", ALL_MEDIA_TYPES, "AVAILABLE"),
                        item("original_title", "String", "TMDB", "TMDB 原语言标题。当前版本预留，未填充时占位符会原样保留。", "The Dark Knight", ALL_MEDIA_TYPES, "RESERVED"),
                        item("year", "Integer", "TMDB", "TMDB 发布年份。", "2008", ALL_MEDIA_TYPES, "AVAILABLE"),
                        item("tmdb_id", "Long", "TMDB", "TMDB 数字 ID。", "155", ALL_MEDIA_TYPES, "AVAILABLE"),
                        item("media_type", "String", "Pipeline", "最终确认的媒体类型。", "MOVIE", ALL_MEDIA_TYPES, "AVAILABLE")
                ), supportedNames),
                group("EPISODE", "剧集信息", List.of(
                        item("season", "Integer", "guessit", "季号原始数值。", "3", TV_ONLY, "AVAILABLE"),
                        item("season:02d", "String", "guessit", "季号两位补零格式。", "03", TV_ONLY, "AVAILABLE"),
                        item("season:04d", "String", "guessit", "季号四位补零格式。", "0003", TV_ONLY, "AVAILABLE"),
                        item("episode", "Integer", "guessit", "集号原始数值。", "7", TV_ONLY, "AVAILABLE"),
                        item("episode:02d", "String", "guessit", "集号两位补零格式。", "07", TV_ONLY, "AVAILABLE"),
                        item("episode:04d", "String", "guessit", "集号四位补零格式。", "0007", TV_ONLY, "AVAILABLE"),
                        item("episode_title", "String", "TMDB", "TMDB 分集标题。当前版本预留，未填充时占位符会原样保留。", "Sunset", TV_ONLY, "RESERVED")
                ), supportedNames),
                group("CLASSIFICATION", "归类辅助", List.of(
                        item("title_initial", "String", "Pipeline", "确认标题首字母。数字或特殊字符返回 #，中文取拼音首字母，英文取首字母大写。", "B", ALL_MEDIA_TYPES, "AVAILABLE"),
                        item("genre_1", "String", "TMDB", "第 1 个分类标签。当前版本预留，未填充时占位符会原样保留。", "Action", ALL_MEDIA_TYPES, "RESERVED"),
                        item("genre_2", "String", "TMDB", "第 2 个分类标签。当前版本预留，未填充时占位符会原样保留。", "Crime", ALL_MEDIA_TYPES, "RESERVED"),
                        item("genre_3", "String", "TMDB", "第 3 个分类标签。当前版本预留，未填充时占位符会原样保留。", "Drama", ALL_MEDIA_TYPES, "RESERVED"),
                        item("genre_4", "String", "TMDB", "第 4 个分类标签。当前版本预留，未填充时占位符会原样保留。", "Mystery", ALL_MEDIA_TYPES, "RESERVED"),
                        item("country", "String", "TMDB", "出品国。当前版本预留，未填充时占位符会原样保留。", "US", ALL_MEDIA_TYPES, "RESERVED")
                ), supportedNames),
                group("TECHNICAL", "技术参数", List.of(
                        item("resolution", "String", "guessit", "视频分辨率，来自 guessit screen_size。", "1080p", ALL_MEDIA_TYPES, "AVAILABLE"),
                        item("codec", "String", "guessit", "视频编码。当前版本预留，未填充时占位符会原样保留。", "H.264", ALL_MEDIA_TYPES, "RESERVED"),
                        item("release_group", "String", "guessit", "发布组。当前版本预留，未填充时占位符会原样保留。", "YIFY", ALL_MEDIA_TYPES, "RESERVED"),
                        item("ext", "String", "Source file", "原始文件扩展名，包含点号。模板中不要额外再写一个点。", ".mkv", ALL_MEDIA_TYPES, "AVAILABLE")
                ), supportedNames)
        );
    }

    private TemplateVariableGroup group(String category, String categoryName, List<TemplateVariableItem> items, Set<String> supportedNames) {
        List<TemplateVariableItem> variables = items.stream()
                .filter(item -> supportedNames.contains(baseName(item.name())))
                .toList();
        return new TemplateVariableGroup(category, categoryName, variables);
    }

    private TemplateVariableItem item(
            String name,
            String type,
            String source,
            String description,
            String example,
            List<String> mediaTypes,
            String status
    ) {
        return new TemplateVariableItem(
                name,
                "{" + name + "}",
                type,
                source,
                description,
                example,
                mediaTypes,
                status
        );
    }

    private Set<String> supportedTemplateVarNames() {
        return List.of(TemplateVariables.class.getDeclaredFields()).stream()
                .map(field -> field.getAnnotation(TemplateVar.class))
                .filter(annotation -> annotation != null)
                .map(TemplateVar::value)
                .collect(Collectors.toSet());
    }

    private String baseName(String name) {
        int formatStart = name.indexOf(':');
        return formatStart >= 0 ? name.substring(0, formatStart) : name;
    }
}
