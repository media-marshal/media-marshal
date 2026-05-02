package com.mediamarshal.service.matcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TMDB API v3 元数据匹配实现
 *
 * API 文档：https://developer.themoviedb.org/reference/intro/getting-started
 *
 * API Key 读取方式（由 SettingsService 统一处理）：
 *   仅通过 Web UI 写入 app_setting 表，不再从环境变量读取
 *
 * ADR-018：文件名标题区生成多 query，TMDB 搜索结果去重合并后再用多维评分排序。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbMetadataMatcher implements MetadataMatcher {

    private static final String SOURCE_NAME = "tmdb";
    private static final String BASE_URL = "https://api.themoviedb.org/3";

    private final SettingsService settingsService;
    private final WebClient.Builder webClientBuilder;
    private final TitleSearchPlanBuilder titleSearchPlanBuilder;
    private final TmdbInMemoryCache cache;
    private final TmdbConfidenceScorer confidenceScorer;

    @Override
    public List<MatchResult> search(ParseResult parseResult) {
        TitleSearchPlan plan = titleSearchPlanBuilder.build(parseResult);
        if (plan.queries().isEmpty()) {
            log.warn("TMDB search skipped because title search plan is empty: {}", parseResult);
            return List.of();
        }

        List<String> endpoints = resolveSearchEndpoints(parseResult);
        Map<String, ScoredMatch> merged = new LinkedHashMap<>();
        List<SearchCall> searchCalls = new ArrayList<>();
        for (String endpoint : endpoints) {
            for (TitleSearchQuery query : plan.queries()) {
                SearchResponse response = callTmdbSearch(endpoint, query.query(), parseResult.getYear());
                JsonNode root = response.root();
                if (root == null) continue;
                JsonNode items = root.path("results");
                searchCalls.add(new SearchCall(endpoint, query.query(), parseResult.getYear(),
                        response.cacheStatus(), items.isArray() ? items.size() : 0));
                if (!items.isArray()) continue;

                for (JsonNode item : items) {
                    MatchResult result = mapSearchItem(item, endpoint);
                    if (result == null || confidenceScorer.isStrongMediaTypeMismatch(parseResult, result)) {
                        continue;
                    }
                    TmdbScore score = confidenceScorer.score(parseResult, result, plan, query, items.size());
                    result.setConfidence(score.confidence());
                    String key = result.getMediaType() + "|" + result.getSourceId();
                    ScoredMatch current = merged.get(key);
                    if (current == null || score.confidence() > current.score().confidence()) {
                        merged.put(key, new ScoredMatch(result, score));
                    }
                }
            }
        }

        List<MatchResult> results = new ArrayList<>(merged.values().stream()
                .map(ScoredMatch::result)
                .toList());
        results.sort(Comparator.comparing(
                MatchResult::getConfidence,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        boolean isDebug = Boolean.parseBoolean(settingsService.get("debug", "false"));
        if (isDebug) {
            log.debug("TMDB match explanation: originalFilename={}, guessitTitle={}, titleRegion={}, queries={}, endpoints={}, searchCalls={}, totalCandidates={}, topCandidates={}",
                    plan.originalFilename(),
                    plan.guessitTitle(),
                    plan.titleRegion(),
                    plan.queries().stream()
                            .map(query -> "%s(%s, weight=%.2f)".formatted(query.query(), query.type(), query.weight()))
                            .toList(),
                    endpoints,
                    searchCalls,
                    results.size(),
                    merged.values().stream()
                            .sorted(Comparator.comparing((ScoredMatch match) -> match.score().confidence()).reversed())
                            .limit(5)
                            .map(this::explainScore)
                            .toList());
        }
        return results;
    }

    @Override
    @SuppressWarnings("null")
    public MatchResult getById(String sourceId, String mediaType) {
        String endpoint = "TV_SHOW".equalsIgnoreCase(mediaType) ? "tv" : "movie";
        String cacheKey = String.join("|", "detail", endpoint, sourceId, getLanguage());
        return cache.get(cacheKey, () -> getByIdUncached(sourceId, endpoint), ignored -> getDuration("tmdb.detail-cache-ttl-minutes", 1440));
    }

    @SuppressWarnings("null")
    private MatchResult getByIdUncached(String sourceId, String endpoint) {
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
                .block(Duration.ofSeconds(getTimeoutSeconds()));

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
    private SearchResponse callTmdbSearch(String endpoint, String query, Integer year) {
        String cacheKey = String.join("|", "search", endpoint, query, String.valueOf(year), getLanguage());
        TmdbInMemoryCache.CacheLookup<JsonNode> lookup = cache.getWithStatus(cacheKey,
                () -> callTmdbSearchUncached(endpoint, query, year),
                root -> isEmptySearch(root)
                        ? getDuration("tmdb.empty-search-cache-ttl-minutes", 10)
                        : getDuration("tmdb.search-cache-ttl-minutes", 360));
        return new SearchResponse(lookup.value(), lookup.status());
    }

    @SuppressWarnings("null")
    private JsonNode callTmdbSearchUncached(String endpoint, String query, Integer year) {
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
                .block(Duration.ofSeconds(getTimeoutSeconds()));
    }

    private boolean isEmptySearch(JsonNode root) {
        JsonNode results = root == null ? null : root.path("results");
        return results == null || !results.isArray() || results.isEmpty();
    }

    private long getTimeoutSeconds() {
        String value = settingsService.get("tmdb.timeout-seconds", "30");
        try {
            return Math.max(Long.parseLong(value), 1);
        } catch (NumberFormatException e) {
            log.warn("Invalid tmdb.timeout-seconds='{}', fallback to 30", value);
            return 30;
        }
    }

    private Duration getDuration(String key, long fallbackMinutes) {
        String value = settingsService.get(key, String.valueOf(fallbackMinutes));
        try {
            return Duration.ofMinutes(Math.max(Long.parseLong(value), 1));
        } catch (NumberFormatException e) {
            log.warn("Invalid {}='{}', fallback to {} minutes", key, value, fallbackMinutes);
            return Duration.ofMinutes(fallbackMinutes);
        }
    }

    private MatchResult mapSearchItem(JsonNode item, String endpoint) {
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

    private String explainScore(ScoredMatch match) {
        TmdbScore score = match.score();
        MatchResult result = match.result();
        return "id=%s,type=%s,title=%s,confidence=%.3f,bestQuery=%s,bestQueryType=%s,title=%.3f,year=%.3f,mediaType=%.3f,structure=%.3f"
                .formatted(
                        result.getSourceId(),
                        result.getMediaType(),
                        result.getTitle(),
                        score.confidence(),
                        score.bestQuery(),
                        score.bestQueryType(),
                        score.titleScore(),
                        score.yearScore(),
                        score.mediaTypeScore(),
                        score.structureBonus());
    }

    private record ScoredMatch(MatchResult result, TmdbScore score) {
    }

    private record SearchResponse(JsonNode root, TmdbInMemoryCache.CacheStatus cacheStatus) {
    }

    private record SearchCall(String endpoint, String query, Integer year,
                              TmdbInMemoryCache.CacheStatus cacheStatus, int resultCount) {
    }
}
