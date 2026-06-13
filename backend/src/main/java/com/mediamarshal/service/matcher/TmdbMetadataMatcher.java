package com.mediamarshal.service.matcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.service.settings.SettingsService;
import com.mediamarshal.service.settings.TmdbProxySettingsService;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

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
    private final TmdbProxySettingsService tmdbProxySettingsService;
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
                Integer searchYear = resolveSearchYear(endpoint, parseResult);
                SearchResponse response = callTmdbSearch(endpoint, query.query(), searchYear);
                JsonNode root = response.root();
                if (root == null) continue;
                JsonNode items = root.path("results");
                searchCalls.add(new SearchCall(endpoint, query.query(), searchYear,
                        response.cacheStatus(), items.isArray() ? items.size() : 0));
                if (isEmptyItems(items) && searchYear != null) {
                    response = callTmdbSearch(endpoint, query.query(), null);
                    root = response.root();
                    if (root == null) continue;
                    items = root.path("results");
                    searchCalls.add(new SearchCall(endpoint, query.query(), null,
                            response.cacheStatus(), items.isArray() ? items.size() : 0));
                }
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

    static Integer resolveSearchYear(String endpoint, ParseResult parseResult) {
        if ("tv".equals(endpoint) && isLaterSeasonTv(parseResult)) {
            return null;
        }
        return parseResult.getYear();
    }

    private static boolean isLaterSeasonTv(ParseResult parseResult) {
        return parseResult.getSeason() != null && parseResult.getSeason() >= 2;
    }

    @Override
    @SuppressWarnings("null")
    public MatchResult getById(String sourceId, String mediaType) {
        String endpoint = "TV_SHOW".equalsIgnoreCase(mediaType) ? "tv" : "movie";
        String cacheKey = String.join("|", "detail", endpoint, sourceId, getLanguage());
        TmdbInMemoryCache.CacheLookup<MatchResult> lookup = cache.getWithStatus(
                cacheKey,
                () -> getByIdUncached(sourceId, endpoint),
                ignored -> getDuration("tmdb.detail-cache-ttl-minutes", 1440)
        );
        logCacheStatus("TMDB detail", cacheKey, lookup.status());
        return lookup.value();
    }

    @Override
    @SuppressWarnings("null")
    public String getEpisodeTitle(String sourceId, int seasonNumber, int episodeNumber) {
        String cacheKey = String.join("|",
                "tv",
                sourceId,
                "season",
                String.valueOf(seasonNumber),
                "episode",
                String.valueOf(episodeNumber),
                getLanguage());
        TmdbInMemoryCache.CacheLookup<String> lookup = cache.getWithStatus(
                cacheKey,
                () -> getEpisodeTitleUncached(sourceId, seasonNumber, episodeNumber),
                ignored -> getDuration("tmdb.detail-cache-ttl-minutes", 1440)
        );
        logCacheStatus("TMDB episode detail", cacheKey, lookup.status());
        return lookup.value();
    }

    @SuppressWarnings("null")
    private MatchResult getByIdUncached(String sourceId, String endpoint) {
        TmdbRequestContext context = createRequestContext();
        logRequestRoute("TMDB detail request", context);
        JsonNode root = executeTmdbRequest(context, context.client().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{endpoint}/{id}")
                        .queryParam("api_key", getApiKey())
                        .queryParam("language", getLanguage())
                        .build(endpoint, sourceId)));

        if (root == null || root.isMissingNode()) {
            throw new IllegalStateException("TMDB detail response is empty: id=" + sourceId);
        }
        return mapDetail(root, endpoint);
    }

    @SuppressWarnings("null")
    private String getEpisodeTitleUncached(String sourceId, int seasonNumber, int episodeNumber) {
        TmdbRequestContext context = createRequestContext();
        logRequestRoute("TMDB episode detail request", context);
        JsonNode root = executeTmdbRequest(context, context.client().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tv/{id}/season/{season}/episode/{episode}")
                        .queryParam("api_key", getApiKey())
                        .queryParam("language", getLanguage())
                        .build(sourceId, seasonNumber, episodeNumber)));

        if (root == null || root.isMissingNode()) {
            throw new IllegalStateException("TMDB episode detail response is empty: id=" + sourceId);
        }
        return text(root, "name");
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
        TmdbRequestContext context = createRequestContext();
        logRequestRoute("TMDB search request", context);
        return executeTmdbRequest(
                context,
                context.client().get().uri(uriBuilder -> {
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
        );
    }

    private boolean isEmptySearch(JsonNode root) {
        JsonNode results = root == null ? null : root.path("results");
        return isEmptyItems(results);
    }

    private boolean isEmptyItems(JsonNode items) {
        return items == null || !items.isArray() || items.isEmpty();
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

    private void logCacheStatus(String label, String cacheKey, TmdbInMemoryCache.CacheStatus status) {
        if (Boolean.parseBoolean(settingsService.get("debug", "false"))) {
            log.debug("{} cache status: key={}, status={}", label, cacheKey, status);
        }
    }

    private void logRequestRoute(String label, TmdbRequestContext context) {
        if (!Boolean.parseBoolean(settingsService.get("debug", "false"))) {
            return;
        }

        TmdbProxySettingsService.TmdbProxyConfig proxyConfig = context.proxyConfig();
        if (proxyConfig.enabled()) {
            log.debug("{} route: proxy=true, proxyUrl={}, baseUrl={}, timeoutSeconds={}",
                    label, proxyConfig.httpUrl(), getBaseUrl(), context.timeoutSeconds());
        } else {
            log.debug("{} route: proxy=false, baseUrl={}, configuredProxyUrl={}, timeoutSeconds={}",
                    label, getBaseUrl(), proxyConfig.httpUrl(), context.timeoutSeconds());
        }
    }

    private TmdbRequestContext createRequestContext() {
        long timeoutSeconds = getTimeoutSeconds();
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        TmdbProxySettingsService.TmdbProxyConfig proxyConfig = tmdbProxySettingsService.resolve();

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis(timeoutSeconds))
                .responseTimeout(timeout);

        if (proxyConfig.enabled()) {
            httpClient = httpClient.proxy(proxy -> proxy
                    .type(ProxyProvider.Proxy.HTTP)
                    .host(proxyConfig.host())
                    .port(proxyConfig.port()));
        }

        WebClient client = webClientBuilder.clone()
                .baseUrl(getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        return new TmdbRequestContext(client, proxyConfig, timeoutSeconds);
    }

    private int connectTimeoutMillis(long timeoutSeconds) {
        long cappedSeconds = Math.min(timeoutSeconds, Integer.MAX_VALUE / 1000L);
        return (int) Math.max(cappedSeconds * 1000L, 1000L);
    }

    private JsonNode executeTmdbRequest(TmdbRequestContext context, WebClient.RequestHeadersSpec<?> request) {
        try {
            return request.retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(context.timeoutSeconds()));
        } catch (RuntimeException e) {
            throw new IllegalStateException(resolveTmdbFailureMessage(e, context.proxyConfig()), e);
        }
    }

    private String resolveTmdbFailureMessage(
            RuntimeException error,
            TmdbProxySettingsService.TmdbProxyConfig proxyConfig
    ) {
        if (error instanceof WebClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            if (status == 407) {
                return "TMDB 代理认证失败：当前版本不支持需要认证的 HTTP 代理";
            }
            if (status == 401 || status == 403) {
                return "TMDB 请求失败：请检查 TMDB API Key 是否有效";
            }
            return "TMDB 请求失败：HTTP " + status;
        }

        if (isTimeout(error)) {
            return "TMDB 连接超时：请检查网络连通性或代理配置";
        }

        if (proxyConfig.enabled() && (hasCause(error, ConnectException.class)
                || hasCause(error, UnknownHostException.class)
                || hasCauseClassName(error, "ProxyConnectException")
                || hasMessage(error, "proxy"))) {
            return "TMDB 代理连接失败：请检查代理地址、端口和代理服务是否可用";
        }

        if (hasCause(error, WebClientRequestException.class)
                || hasCause(error, ConnectException.class)
                || hasCause(error, UnknownHostException.class)) {
            return "TMDB 连接失败：请检查网络连通性或 TMDB 代理配置";
        }

        String message = error.getMessage();
        return message == null || message.isBlank() ? "TMDB 请求失败" : "TMDB 请求失败：" + message;
    }

    private boolean isTimeout(Throwable error) {
        return hasCause(error, TimeoutException.class)
                || hasMessage(error, "Timeout on blocking read")
                || hasMessage(error, "timed out");
    }

    private boolean hasCause(Throwable error, Class<?> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasCauseClassName(Throwable error, String className) {
        Throwable current = error;
        while (current != null) {
            if (current.getClass().getSimpleName().contains(className)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasMessage(Throwable error, String needle) {
        Throwable current = error;
        String normalizedNeedle = needle.toLowerCase();
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains(normalizedNeedle)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
        result.setGenres(extractGenres(root));
        result.setCountry(extractCountry(root));
        result.setConfidence(1.0);
        return result;
    }

    private List<String> extractGenres(JsonNode root) {
        JsonNode genres = root.path("genres");
        if (!genres.isArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (JsonNode genre : genres) {
            String name = text(genre, "name");
            if (name != null && !name.isBlank()) {
                values.add(name);
            }
            if (values.size() >= 4) {
                break;
            }
        }
        return values;
    }

    private String extractCountry(JsonNode root) {
        JsonNode originCountry = root.path("origin_country");
        if (originCountry.isArray() && !originCountry.isEmpty()) {
            String value = originCountry.get(0).asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        JsonNode productionCountries = root.path("production_countries");
        if (productionCountries.isArray() && !productionCountries.isEmpty()) {
            String value = text(productionCountries.get(0), "iso_3166_1");
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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

    private record TmdbRequestContext(
            WebClient client,
            TmdbProxySettingsService.TmdbProxyConfig proxyConfig,
            long timeoutSeconds
    ) {
    }
}
