package com.mediamarshal.service.matcher;

import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.service.discovery.asset.MediaAsset;
import com.mediamarshal.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParentFolderMatchEnhancer {

    private static final double MIN_PARENT_SUPPORT_CONFIDENCE = 0.60;

    private final MetadataMatcher metadataMatcher;
    private final ParentFolderContextExtractor parentFolderContextExtractor;
    private final SettingsService settingsService;

    public List<MatchResult> enhanceIfNeeded(
            MediaAsset asset,
            ParseResult filenameParseResult,
            List<MatchResult> filenameCandidates,
            double confidenceThreshold
    ) {
        List<MatchResult> safeFilenameCandidates = filenameCandidates == null ? List.of() : filenameCandidates;
        if (hasAutoMatch(safeFilenameCandidates, confidenceThreshold)) {
            return safeFilenameCandidates;
        }
        if (!isEnabled()) {
            return safeFilenameCandidates;
        }

        return parentFolderContextExtractor.extract(asset.rootPath(), asset.type(), filenameParseResult)
                .map(context -> enhanceWithParentContext(context, filenameParseResult, safeFilenameCandidates, confidenceThreshold))
                .orElse(safeFilenameCandidates);
    }

    private List<MatchResult> enhanceWithParentContext(
            ParentFolderContext context,
            ParseResult filenameParseResult,
            List<MatchResult> filenameCandidates,
            double confidenceThreshold
    ) {
        List<MatchResult> parentCandidates;
        try {
            parentCandidates = metadataMatcher.search(parentContextParseResult(filenameParseResult, context));
        } catch (RuntimeException e) {
            log.warn("Parent folder metadata search failed: folder={}, error={}", context.folderName(), e.getMessage());
            return filenameCandidates;
        }

        if (parentCandidates.isEmpty()) {
            return filenameCandidates;
        }

        Map<String, CandidateEvidence> merged = new LinkedHashMap<>();
        for (MatchResult candidate : filenameCandidates) {
            merged.computeIfAbsent(resultKey(candidate), ignored -> new CandidateEvidence()).filenameCandidate = candidate;
        }
        for (MatchResult candidate : parentCandidates) {
            merged.computeIfAbsent(resultKey(candidate), ignored -> new CandidateEvidence()).parentCandidate = candidate;
        }

        return merged.values().stream()
                .map(evidence -> evidence.toMatchResult(confidenceThreshold, parentOnlyConfidenceCap(confidenceThreshold), mutualBoost()))
                .sorted(Comparator.comparing(
                        MatchResult::getConfidence,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    private ParseResult parentContextParseResult(ParseResult source, ParentFolderContext context) {
        ParseResult copy = new ParseResult();
        copy.setTitle(source.getTitle());
        copy.setYear(source.getYear());
        copy.setSeason(source.getSeason());
        copy.setEpisode(source.getEpisode());
        copy.setEpisodeEnd(source.getEpisodeEnd());
        copy.setType(source.getType());
        copy.setReleaseGroup(source.getReleaseGroup());
        copy.setScreenSize(source.getScreenSize());
        copy.setVideoCodec(source.getVideoCodec());
        copy.setOriginalFilename(context.folderName());
        return copy;
    }

    private boolean hasAutoMatch(List<MatchResult> candidates, double confidenceThreshold) {
        return candidates.stream()
                .map(MatchResult::getConfidence)
                .anyMatch(confidence -> confidence != null && confidence >= confidenceThreshold);
    }

    private boolean isEnabled() {
        return Boolean.parseBoolean(settingsService.get("watcher.parent-folder-context-enabled", "true"));
    }

    private double parentOnlyConfidenceCap(double confidenceThreshold) {
        double configuredCap = parseDoubleSetting("watcher.parent-folder-parent-only-confidence-cap", 0.75);
        double thresholdCap = Math.max(0.0, confidenceThreshold - 0.01);
        return Math.min(configuredCap, thresholdCap);
    }

    private double mutualBoost() {
        return parseDoubleSetting("watcher.parent-folder-mutual-boost", 0.05);
    }

    private double parseDoubleSetting(String key, double fallback) {
        String value = settingsService.get(key, String.valueOf(fallback));
        try {
            return clamp(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            log.warn("Invalid {}='{}', fallback to {}", key, value, fallback);
            return fallback;
        }
    }

    private String resultKey(MatchResult result) {
        return result.getMediaType() + "|" + result.getSourceId();
    }

    private static MatchResult copyWithConfidence(MatchResult source, double confidence) {
        MatchResult copy = new MatchResult();
        copy.setSource(source.getSource());
        copy.setSourceId(source.getSourceId());
        copy.setTitle(source.getTitle());
        copy.setOriginalTitle(source.getOriginalTitle());
        copy.setYear(source.getYear());
        copy.setMediaType(source.getMediaType());
        copy.setOverview(source.getOverview());
        copy.setPosterUrl(source.getPosterUrl());
        copy.setConfidence(clamp(confidence));
        return copy;
    }

    private static double clamp(double value) {
        return Math.min(1.0, Math.max(0.0, value));
    }

    private static double confidence(MatchResult candidate) {
        return candidate == null || candidate.getConfidence() == null ? 0.0 : candidate.getConfidence();
    }

    private static class CandidateEvidence {
        private MatchResult filenameCandidate;
        private MatchResult parentCandidate;

        private MatchResult toMatchResult(double confidenceThreshold, double parentOnlyCap, double mutualBoost) {
            double filenameScore = confidence(filenameCandidate);
            double parentScore = confidence(parentCandidate);

            if (filenameCandidate != null && parentCandidate != null) {
                double adjusted = Math.max(filenameScore, parentScore);
                if (parentScore >= MIN_PARENT_SUPPORT_CONFIDENCE) {
                    adjusted += mutualBoost;
                }
                MatchResult source = parentScore >= filenameScore ? parentCandidate : filenameCandidate;
                return copyWithConfidence(source, adjusted);
            }

            if (filenameCandidate != null) {
                return copyWithConfidence(filenameCandidate, filenameScore);
            }

            double adjusted = Math.min(parentScore, Math.min(parentOnlyCap, Math.max(0.0, confidenceThreshold - 0.01)));
            return copyWithConfidence(parentCandidate, adjusted);
        }
    }
}
