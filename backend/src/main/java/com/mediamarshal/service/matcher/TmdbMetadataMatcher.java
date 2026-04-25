package com.mediamarshal.service.matcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * TMDB API v3 元数据匹配实现
 *
 * API 文档：https://developer.themoviedb.org/reference/intro/getting-started
 *
 * API Key 读取顺序（由 SettingsService 统一处理）：
 *   环境变量 MEDIA_MARSHAL_TMDB_KEY > application.yml > 数据库配置
 *
 * 置信度策略（v1 简化版）：标题相似度 80% + 年份匹配 20%。
 * 后续可替换为更成熟的文本相似度算法，不影响 Pipeline 调用契约。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbMetadataMatcher implements MetadataMatcher {

    private static final String SOURCE_NAME = "tmdb";
    private static final String BASE_URL = "https://api.themoviedb.org/3";

    private final SettingsService settingsService;
    private final WebClient.Builder webClientBuilder;

    @Override
    public List<MatchResult> search(ParseResult parseResult) {
        String query = parseResult.getTitle();
        if (query == null || query.isBlank()) {
            log.warn("TMDB search skipped because parsed title is empty: {}", parseResult);
            return List.of();
        }

        List<String> endpoints = resolveSearchEndpoints(parseResult);
        List<MatchResult> results = new ArrayList<>();
        for (String endpoint : endpoints) {
            JsonNode root = callTmdbSearch(endpoint, query, parseResult.getYear());
            JsonNode items = root.path("results");
            if (!items.isArray()) continue;

            for (JsonNode item : items) {
                MatchResult result = mapSearchItem(item, endpoint, parseResult);
                if (result != null) {
                    results.add(result);
                }
            }
        }

        results.sort(Comparator.comparing(
                MatchResult::getConfidence,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        boolean isDebug = Boolean.parseBoolean(settingsService.get("debug", "false"));
        if (isDebug) {
            log.debug("TMDB search result: query={}, count={}, top={}",
                    query, results.size(), results.isEmpty() ? null : results.getFirst());
        }
        return results;
    }

    @Override
    @SuppressWarnings("null")
    public MatchResult getById(String sourceId, String mediaType) {
        String endpoint = "TV_SHOW".equalsIgnoreCase(mediaType) ? "tv" : "movie";
        JsonNode root = webClientBuilder.baseUrl(getBaseUrl())
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{endpoint}/{id}")
                        .queryParam("api_key", getApiKey())
                        .queryParam("language", getLanguage())
                        .build(endpoint, sourceId))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(10));

        if (root == null || root.isMissingNode()) {
            throw new IllegalStateException("TMDB detail response is empty: id=" + sourceId);
        }
        return mapDetail(root, endpoint);
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    private String getApiKey() {
        return settingsService.get("tmdb.api-key", "");
    }

    private String getBaseUrl() {
        return settingsService.get("tmdb.base-url", BASE_URL);
    }

    private String getLanguage() {
        return settingsService.get("tmdb.language", "zh-CN");
    }

    private List<String> resolveSearchEndpoints(ParseResult parseResult) {
        String type = parseResult.getType();
        if ("episode".equalsIgnoreCase(type) || parseResult.getSeason() != null || parseResult.getEpisode() != null) {
            return List.of("tv");
        }
        if ("movie".equalsIgnoreCase(type)) {
            return List.of("movie");
        }
        // 类型不明确时两边都搜，交给置信度排序。
        return List.of("movie", "tv");
    }

    @SuppressWarnings("null")
    private JsonNode callTmdbSearch(String endpoint, String query, Integer year) {
        WebClient client = webClientBuilder.baseUrl(getBaseUrl()).build();
        return client.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/search/{endpoint}")
                            .queryParam("api_key", getApiKey())
                            .queryParam("language", getLanguage())
                            .queryParam("query", query)
                            .queryParam("include_adult", false);
                    if (year != null) {
                        if ("movie".equals(endpoint)) {
                            builder.queryParam("year", year);
                        } else {
                            builder.queryParam("first_air_date_year", year);
                        }
                    }
                    return builder.build(endpoint);
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(10));
    }

    private MatchResult mapSearchItem(JsonNode item, String endpoint, ParseResult parseResult) {
        long id = item.path("id").asLong(0);
        if (id == 0) return null;

        String title = text(item, "movie".equals(endpoint) ? "title" : "name");
        String originalTitle = text(item, "movie".equals(endpoint) ? "original_title" : "original_name");
        String date = text(item, "movie".equals(endpoint) ? "release_date" : "first_air_date");
        Integer year = extractYear(date);

        MatchResult result = new MatchResult();
        result.setSource(SOURCE_NAME);
        result.setSourceId(String.valueOf(id));
        result.setTitle(title);
        result.setOriginalTitle(originalTitle);
        result.setYear(year);
        result.setMediaType("movie".equals(endpoint) ? "MOVIE" : "TV_SHOW");
        result.setOverview(text(item, "overview"));
        result.setPosterUrl(buildPosterUrl(text(item, "poster_path")));
        result.setConfidence(calculateConfidence(parseResult, title, originalTitle, year));
        return result;
    }

    private MatchResult mapDetail(JsonNode root, String endpoint) {
        MatchResult result = new MatchResult();
        result.setSource(SOURCE_NAME);
        result.setSourceId(root.path("id").asText());
        result.setTitle(text(root, "movie".equals(endpoint) ? "title" : "name"));
        result.setOriginalTitle(text(root, "movie".equals(endpoint) ? "original_title" : "original_name"));
        result.setYear(extractYear(text(root, "movie".equals(endpoint) ? "release_date" : "first_air_date")));
        result.setMediaType("movie".equals(endpoint) ? "MOVIE" : "TV_SHOW");
        result.setOverview(text(root, "overview"));
        result.setPosterUrl(buildPosterUrl(text(root, "poster_path")));
        result.setConfidence(1.0);
        return result;
    }

    private double calculateConfidence(ParseResult parseResult, String title, String originalTitle, Integer candidateYear) {
        double titleScore = Math.max(
                similarity(parseResult.getTitle(), title),
                similarity(parseResult.getTitle(), originalTitle)
        );
        double yearScore = 0.0;
        if (parseResult.getYear() != null && candidateYear != null) {
            yearScore = parseResult.getYear().equals(candidateYear) ? 1.0 : 0.0;
        } else if (parseResult.getYear() == null) {
            yearScore = 0.5;
        }
        return Math.min(1.0, titleScore * 0.8 + yearScore * 0.2);
    }

    /**
     * 轻量级字符串相似度：基于规范化字符串的最长公共子序列比例。
     * 这里避免引入额外依赖，后续可替换为更成熟算法。
     */
    private double similarity(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isBlank() || b.isBlank()) return 0.0;
        if (a.equals(b)) return 1.0;

        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return (double) dp[a.length()][b.length()] / Math.max(a.length(), b.length());
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Integer extractYear(String date) {
        if (date == null || date.length() < 4) return null;
        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildPosterUrl(String posterPath) {
        if (posterPath == null || posterPath.isBlank()) return null;
        return "https://image.tmdb.org/t/p/w500" + posterPath;
    }
}
