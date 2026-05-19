package com.mediamarshal.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParseResult {

    private String title;

    private Integer year;

    private Integer episode;

    private Integer episodeEnd;

    private List<Integer> episodes;

    @JsonProperty("season")
    private Integer season;

    @JsonProperty("type")
    private String type;           // "movie" or "episode"

    @JsonProperty("release_group")
    private String releaseGroup;

    @JsonProperty("screen_size")
    private String screenSize;

    @JsonProperty("video_codec")
    private String videoCodec;

    private String originalFilename;

    @JsonProperty("episode")
    public void setEpisode(Object value) {
        if (value == null) {
            setEpisodeRange(null, null, null);
            return;
        }

        if (value instanceof Number number) {
            int episodeNumber = number.intValue();
            setEpisodeRange(episodeNumber, null, List.of(episodeNumber));
            return;
        }

        if (value instanceof List<?> values) {
            List<Integer> parsedEpisodes = parseEpisodeList(values);
            if (parsedEpisodes.isEmpty()) {
                setEpisodeRange(null, null, List.of());
                return;
            }
            validateContiguousEpisodes(parsedEpisodes);
            setEpisodeRange(
                    parsedEpisodes.getFirst(),
                    parsedEpisodes.size() == 1 ? null : parsedEpisodes.getLast(),
                    parsedEpisodes
            );
            return;
        }

        throw new IllegalArgumentException("Unsupported episode value type: " + value.getClass().getSimpleName());
    }

    public void setEpisodeEnd(Integer episodeEnd) {
        this.episodeEnd = normalizeEpisodeEnd(episode, episodeEnd);
        episodes = buildEpisodeList(episode, this.episodeEnd);
    }

    private List<Integer> parseEpisodeList(List<?> values) {
        List<Integer> parsedEpisodes = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof Number number)) {
                throw new IllegalArgumentException("Episode array must contain only numbers: " + values);
            }
            parsedEpisodes.add(number.intValue());
        }
        return List.copyOf(parsedEpisodes);
    }

    private void validateContiguousEpisodes(List<Integer> values) {
        for (int i = 1; i < values.size(); i++) {
            int previous = values.get(i - 1);
            int current = values.get(i);
            if (current != previous + 1) {
                throw new IllegalArgumentException("Only contiguous episode ranges are supported: " + values);
            }
        }
    }

    private void setEpisodeRange(Integer start, Integer end, List<Integer> values) {
        episode = start;
        episodeEnd = normalizeEpisodeEnd(start, end);
        episodes = values == null ? null : List.copyOf(values);
    }

    private Integer normalizeEpisodeEnd(Integer start, Integer end) {
        if (start == null || end == null || end.equals(start)) {
            return null;
        }
        if (end < start) {
            throw new IllegalArgumentException("Episode range end must be greater than the start episode");
        }
        return end;
    }

    private List<Integer> buildEpisodeList(Integer start, Integer end) {
        if (start == null) {
            return null;
        }
        if (end == null) {
            return List.of(start);
        }
        List<Integer> values = new ArrayList<>();
        for (int current = start; current <= end; current++) {
            values.add(current);
        }
        return List.copyOf(values);
    }
}
