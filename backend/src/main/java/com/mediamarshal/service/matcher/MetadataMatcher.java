package com.mediamarshal.service.matcher;

import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;

import java.util.List;

/**
 * 元数据匹配适配器接口（Adapter Pattern）
 *
 * 当前实现：TmdbMetadataMatcher
 * 未来可扩展：DoubanMetadataMatcher、ImdbMetadataMatcher 等
 *
 * 扩展方式：新建实现类，标注 @Component，注入数据源名称，
 * MetadataMatcherFactory 自动按 sourceName 路由。
 */
public interface MetadataMatcher {

    /**
     * 根据解析结果搜索候选匹配项，按置信度降序排列
     *
     * @param parseResult guessit 解析结果
     * @return 候选列表，通常取第一项；若列表为空则标记 FAILED
     */
    List<MatchResult> search(ParseResult parseResult);

    /**
     * 按数据源 ID 直接获取详情（用于用户手动指定时）
     */
    MatchResult getById(String sourceId, String mediaType);

    /**
     * 数据源标识，用于配置路由（如 "tmdb"）
     */
    String getSourceName();
}
