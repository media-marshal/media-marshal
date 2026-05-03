package com.mediamarshal.controller;

import com.mediamarshal.model.dto.ApiResponse;
import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.service.matcher.MetadataMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataMatcher metadataMatcher;

    @GetMapping("/search")
    public ApiResponse<List<MatchResult>> search(
            @RequestParam String q,
            @RequestParam String mediaType
    ) {
        String keyword = q == null ? "" : q.trim();
        if (keyword.isBlank()) {
            return ApiResponse.ok(List.of());
        }

        MediaTask.MediaType type = MediaTask.MediaType.valueOf(mediaType);
        ParseResult parseResult = new ParseResult();
        parseResult.setTitle(keyword);
        parseResult.setType(MediaTask.MediaType.TV_SHOW.equals(type) ? "episode" : "movie");

        List<MatchResult> keywordResults = metadataMatcher.search(parseResult);
        if (!keyword.matches("\\d+")) {
            return ApiResponse.ok(keywordResults);
        }

        List<MatchResult> idResults = getByIdIfExists(keyword, type.name());
        return ApiResponse.ok(mergeResults(idResults, keywordResults));
    }

    private List<MatchResult> getByIdIfExists(String tmdbId, String mediaType) {
        try {
            return List.of(metadataMatcher.getById(tmdbId, mediaType));
        } catch (Exception e) {
            log.debug("Metadata id lookup missed: id={}, mediaType={}, error={}", tmdbId, mediaType, e.getMessage());
            return List.of();
        }
    }

    private List<MatchResult> mergeResults(List<MatchResult> idResults, List<MatchResult> keywordResults) {
        Map<String, MatchResult> merged = new LinkedHashMap<>();
        for (MatchResult result : idResults) {
            merged.put(resultKey(result), result);
        }
        for (MatchResult result : keywordResults) {
            merged.putIfAbsent(resultKey(result), result);
        }
        return new ArrayList<>(merged.values());
    }

    private String resultKey(MatchResult result) {
        return result.getMediaType() + ":" + result.getSourceId();
    }
}
