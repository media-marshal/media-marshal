package com.mediamarshal.service.matcher;

import com.mediamarshal.model.dto.ParseResult;
import com.mediamarshal.model.entity.MediaAssetType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ParentFolderContextExtractor {

    private static final Pattern RELEASE_MARKER = Pattern.compile(
            "(?i)(?:^|[. _\\-\\[\\(])("
                    + "s\\d{1,2}(?:e\\d{1,3})?|season\\s*\\d+|第\\s*\\d+\\s*季|"
                    + "(?:19|20)\\d{2}|\\d{3,4}p|2160p|1080p|720p|480p|4k|8k|"
                    + "bluray|blu-ray|web-dl|webrip|hdtv|hdrip|dvdrip|bdrip|x264|x265|"
                    + "h\\.264|h\\.265|h264|h265|hevc|aac|ddp?\\d?(?:\\.\\d)?|dts|atmos|proper|repack"
                    + ")(?:$|[. _\\-\\]\\)])");
    private static final Pattern SEASON_MARKER = Pattern.compile(
            "(?i)(?:^|[. _\\-])(?:s(\\d{1,2})|season\\s*(\\d+)|第\\s*(\\d+)\\s*季)(?:$|[. _\\-])");
    private static final Pattern CONTENT_CHARS = Pattern.compile("[\\p{IsHan}A-Za-z0-9]");
    private static final Set<String> GENERIC_TITLE_REGIONS = Set.of(
            "download",
            "downloads",
            "complete",
            "completed",
            "incoming",
            "media",
            "library",
            "movie",
            "movies",
            "tv",
            "tv show",
            "tv shows",
            "show",
            "shows",
            "series",
            "season",
            "season 1",
            "season 01",
            "s01",
            "电视剧",
            "剧集",
            "国产剧",
            "电影",
            "合集"
    );

    public Optional<ParentFolderContext> extract(Path sourcePath, MediaAssetType assetType, ParseResult parseResult) {
        if (!MediaAssetType.VIDEO_FILE.equals(assetType) || sourcePath == null || parseResult == null) {
            return Optional.empty();
        }

        Path parent = sourcePath.getParent();
        if (parent == null || parent.getFileName() == null) {
            return Optional.empty();
        }

        String folderName = parent.getFileName().toString();
        Integer folderSeason = extractSeason(folderName);
        if (folderSeason != null && parseResult.getSeason() == null) {
            return Optional.empty();
        }
        if (folderSeason != null && !folderSeason.equals(parseResult.getSeason())) {
            return Optional.empty();
        }

        int markerIndex = markerIndex(folderName);
        if (markerIndex <= 0) {
            return Optional.empty();
        }

        String titleRegion = normalizeTitleRegion(trimSeparators(folderName.substring(0, markerIndex)));
        if (!isMeaningfulTitleRegion(titleRegion)) {
            return Optional.empty();
        }

        return Optional.of(new ParentFolderContext(folderName, titleRegion));
    }

    private Integer extractSeason(String folderName) {
        var matcher = SEASON_MARKER.matcher(folderName);
        if (!matcher.find()) {
            return null;
        }
        for (int group = 1; group <= matcher.groupCount(); group++) {
            String value = matcher.group(group);
            if (value != null) {
                return Integer.parseInt(value);
            }
        }
        return null;
    }

    private int markerIndex(String folderName) {
        var matcher = RELEASE_MARKER.matcher(folderName);
        return matcher.find() ? matcher.start() : -1;
    }

    private boolean isMeaningfulTitleRegion(String titleRegion) {
        if (titleRegion == null || titleRegion.isBlank()) {
            return false;
        }
        String normalized = titleRegion.toLowerCase(Locale.ROOT);
        if (GENERIC_TITLE_REGIONS.contains(normalized)) {
            return false;
        }
        if (normalized.matches("s\\d{1,2}|season\\s*\\d+|第\\s*\\d+\\s*季")) {
            return false;
        }
        return CONTENT_CHARS.matcher(titleRegion).find();
    }

    private String normalizeTitleRegion(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("[._]+", " ")
                .replaceAll("[\\[\\]【】()（）]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String trimSeparators(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("^[\\s._\\-]+|[\\s._\\-]+$", "");
    }
}
