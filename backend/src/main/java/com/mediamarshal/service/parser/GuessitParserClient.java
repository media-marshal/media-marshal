package com.mediamarshal.service.parser;

import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * guessit sidecar HTTP 客户端
 *
 * 通过 HTTP 调用 Python FastAPI sidecar（parser 服务）的 /parse 接口，
 * 将文件名转化为结构化的 ParseResult。
 *
 * sidecar URL 配置项：media-marshal.parser.url
 * 默认值：http://parser:8000（Docker Compose 内部服务名）
 *
 * Debug 模式下会打印 guessit 原始解析结果，用于定位解析误差。
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
    @SuppressWarnings("null")
    public ParseResult parse(String filename) {
        String parserUrl = settingsService.get("parser.url", "http://parser:8000");
        int timeoutSeconds = Integer.parseInt(settingsService.get("parser.timeout-seconds", "5"));
        boolean isDebug = Boolean.parseBoolean(settingsService.get("debug", "false"));

        log.info("Parsing filename with guessit: {}", filename);
        ParseResult result = webClientBuilder
                .baseUrl(parserUrl)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/parse")
                        .queryParam("filename", filename)
                        .build())
                .retrieve()
                .bodyToMono(ParseResult.class)
                .block(Duration.ofSeconds(timeoutSeconds));

        if (result == null) {
            throw new IllegalStateException("Parser returned empty response for: " + filename);
        }

        result.setOriginalFilename(filename);

        if (isDebug) {
            log.debug("guessit result: filename={}, result={}", filename, result);
        }
        return result;
    }
}
