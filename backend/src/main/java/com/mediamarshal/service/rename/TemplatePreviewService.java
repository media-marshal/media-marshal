package com.mediamarshal.service.rename;

import com.mediamarshal.model.dto.TemplatePreviewRequest;
import com.mediamarshal.model.dto.TemplatePreviewResponse;
import com.mediamarshal.model.dto.TemplateVariableGroup;
import com.mediamarshal.model.dto.TemplateVariableGroup.TemplateVariableItem;
import com.mediamarshal.model.dto.TemplateVariableGroup.TemplateVariableStatus;
import com.mediamarshal.model.entity.MediaTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TemplatePreviewService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_][a-z0-9_]*)(?::([^;}]+))?(?:;([^}]+))?}");

    private final TemplateRenderer templateRenderer;
    private final TemplateVariableCatalogService catalogService;
    private final TemplatePathSafetyService pathSafetyService;

    public TemplatePreviewResponse preview(TemplatePreviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Template preview request is required");
        }
        if (request.getTemplate() == null || request.getTemplate().isBlank()) {
            throw new IllegalArgumentException("Template is required");
        }
        if (request.getMediaType() == null) {
            throw new IllegalArgumentException("Media type is required");
        }

        Map<String, TemplateVariableItem> catalog = catalogByName();
        Set<String> usedVariables = extractUsedVariables(request.getTemplate());
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> unknownVariables = new ArrayList<>();
        List<String> reservedVariables = new ArrayList<>();

        for (String variableName : usedVariables) {
            TemplateVariableItem item = catalog.get(variableName);
            if (item == null) {
                unknownVariables.add(variableName);
                errors.add("未知变量：{" + variableName + "}");
                continue;
            }
            if (TemplateVariableStatus.RESERVED.equals(item.status())
                    || TemplateVariableStatus.UNAVAILABLE.equals(item.status())) {
                reservedVariables.add(variableName);
                errors.add("当前版本暂未启用变量：{" + variableName + "}");
            }
            if (!item.mediaTypes().contains(request.getMediaType().name())) {
                errors.add("变量 {" + variableName + "} 不适用于 " + request.getMediaType().name() + " 模板");
            }
            if (TemplateVariableStatus.DEPRECATED.equals(item.status())) {
                warnings.add("变量 {" + variableName + "} 即将废弃");
            }
        }

        String output = templateRenderer.render(
                request.getTemplate(),
                buildPreviewVariables(request.getMediaType(), request.getContext())
        );
        TemplatePathSafetyResult safetyResult = pathSafetyService.validateForPreview(request.getTargetDir(), output);
        if (!safetyResult.safe()) {
            errors.addAll(safetyResult.errors());
        }

        return new TemplatePreviewResponse(
                output,
                distinct(warnings),
                distinct(errors),
                List.copyOf(usedVariables),
                distinct(unknownVariables),
                distinct(reservedVariables),
                safetyResult.unsafePath()
        );
    }

    private Map<String, TemplateVariableItem> catalogByName() {
        Map<String, TemplateVariableItem> catalog = new LinkedHashMap<>();
        for (TemplateVariableGroup group : catalogService.listVariables()) {
            for (TemplateVariableItem item : group.variables()) {
                catalog.putIfAbsent(baseName(item.name()), item);
            }
        }
        return catalog;
    }

    private Set<String> extractUsedVariables(String template) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private TemplateVariables buildPreviewVariables(MediaTask.MediaType mediaType, Map<String, Object> context) {
        TemplateVariables sample = (MediaTask.MediaType.TV_SHOW.equals(mediaType)
                ? tvShowSample()
                : movieSample())
                .build();

        if (context == null || context.isEmpty()) {
            return sample;
        }

        return TemplateVariables.builder()
                .title(stringValue(context, "title", sample.getTitle()))
                .originalTitle(stringValue(context, "original_title", sample.getOriginalTitle()))
                .year(integerValue(context, "year", sample.getYear()))
                .tmdbId(longValue(context, "tmdb_id", sample.getTmdbId()))
                .mediaType(stringValue(context, "media_type", sample.getMediaType()))
                .season(integerValue(context, "season", sample.getSeason()))
                .episode(episodeValue(context, sample.getEpisode()))
                .episodeTitle(stringValue(context, "episode_title", sample.getEpisodeTitle()))
                .titleInitial(stringValue(context, "title_initial", sample.getTitleInitial()))
                .genre1(stringValue(context, "genre_1", sample.getGenre1()))
                .genre2(stringValue(context, "genre_2", sample.getGenre2()))
                .genre3(stringValue(context, "genre_3", sample.getGenre3()))
                .genre4(stringValue(context, "genre_4", sample.getGenre4()))
                .country(stringValue(context, "country", sample.getCountry()))
                .resolution(stringValue(context, "resolution", sample.getResolution()))
                .codec(stringValue(context, "codec", sample.getCodec()))
                .releaseGroup(stringValue(context, "release_group", sample.getReleaseGroup()))
                .ext(stringValue(context, "ext", sample.getExt()))
                .build();
    }

    private TemplateVariables.TemplateVariablesBuilder movieSample() {
        return TemplateVariables.builder()
                .title("蝙蝠侠：黑暗骑士")
                .originalTitle("The Dark Knight")
                .year(2008)
                .tmdbId(155L)
                .mediaType(MediaTask.MediaType.MOVIE.name())
                .titleInitial("B")
                .genre1("Action")
                .genre2("Crime")
                .genre3("Drama")
                .genre4("Mystery")
                .country("US")
                .resolution("1080p")
                .codec("H.264")
                .releaseGroup("YIFY")
                .ext(".mkv");
    }

    private TemplateVariables.TemplateVariablesBuilder tvShowSample() {
        return TemplateVariables.builder()
                .title("绝命毒师")
                .originalTitle("Breaking Bad")
                .year(2008)
                .tmdbId(1396L)
                .mediaType(MediaTask.MediaType.TV_SHOW.name())
                .season(3)
                .episode(7)
                .episodeTitle("One Minute")
                .titleInitial("J")
                .genre1("Drama")
                .genre2("Crime")
                .country("US")
                .resolution("1080p")
                .codec("H.264")
                .releaseGroup("NTb")
                .ext(".mkv");
    }

    private String stringValue(Map<String, Object> context, String key, String fallback) {
        if (!context.containsKey(key)) {
            return fallback;
        }
        Object value = context.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer integerValue(Map<String, Object> context, String key, Integer fallback) {
        if (!context.containsKey(key)) {
            return fallback;
        }
        Object value = context.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Long longValue(Map<String, Object> context, String key, Long fallback) {
        if (!context.containsKey(key)) {
            return fallback;
        }
        Object value = context.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Object episodeValue(Map<String, Object> context, Object fallback) {
        if (!context.containsKey("episode") && !context.containsKey("episode_end")) {
            return fallback;
        }
        Integer episode = integerValue(context, "episode", fallback instanceof Number number ? number.intValue() : null);
        Integer episodeEnd = integerValue(context, "episode_end", null);
        if (episode == null || episodeEnd == null || episode.equals(episodeEnd)) {
            return episode;
        }
        return new TemplateRange(episode, episodeEnd);
    }

    private String baseName(String name) {
        int formatStart = name.indexOf(':');
        return formatStart >= 0 ? name.substring(0, formatStart) : name;
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }
}
