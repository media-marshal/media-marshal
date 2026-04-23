package com.mediamarshal.service.parser;

import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * guessit sidecar HTTP 客户端
 *
 * 通过 HTTP 调用 Python FastAPI sidecar（parser 服务）的 /parse 接口，
 * 将文件名转化为结构化的 ParseResult。
 *
 * sidecar URL 配置项：media-marshal.parser.url
 * 默认值：http://parser:8000（Docker Compose 内部服务名）
 *
 * TODO:
 *  - 实现 parse() 方法，调用 GET /parse?filename={filename}
 *  - 添加超时配置（建议 5s）
 *  - 添加重试逻辑（sidecar 启动慢时）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuessitParserClient {

    private final SettingsService settingsService;
    private final WebClient.Builder webClientBuilder;

    /**
     * 调用 guessit sidecar 解析文件名
     *
     * @param filename 原始文件名（不含路径，仅文件名）
     * @return 解析结果
     */
    public ParseResult parse(String filename) {
        String parserUrl = settingsService.get("parser.url", "http://parser:8000");
        // TODO: implement HTTP call to sidecar
        throw new UnsupportedOperationException("Parser client not yet implemented");
    }
}
