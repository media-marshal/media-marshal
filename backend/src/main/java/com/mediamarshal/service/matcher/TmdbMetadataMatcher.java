package com.mediamarshal.service.matcher;

import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * TMDB API v3 元数据匹配实现
 *
 * API 文档：https://developer.themoviedb.org/reference/intro/getting-started
 *
 * API Key 读取顺序（由 SettingsService 统一处理）：
 *   环境变量 MEDIA_MARSHAL_TMDB_KEY > application.yml > 数据库配置
 *
 * TODO:
 *  - 实现 search() 调用 /search/movie 和 /search/tv
 *  - 实现 getById() 调用 /movie/{id} 和 /tv/{id}
 *  - 计算置信度（标题相似度 + 年份匹配度）
 *  - 处理多语言返回（language 参数）
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
        // TODO: implement TMDB search
        throw new UnsupportedOperationException("TMDB search not yet implemented");
    }

    @Override
    public MatchResult getById(String sourceId, String mediaType) {
        // TODO: implement TMDB getById
        throw new UnsupportedOperationException("TMDB getById not yet implemented");
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    private String getApiKey() {
        return settingsService.get("tmdb.api-key", "");
    }
}
