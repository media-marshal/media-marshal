package com.mediamarshal.service.matcher;

import com.mediamarshal.model.dto.ParseResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbMetadataMatcherTest {

    @Test
    void omitsSearchYearForLaterSeasonTv() {
        assertThat(TmdbMetadataMatcher.resolveSearchYear("tv", parse("episode", 2025, 2))).isNull();
    }

    @Test
    void keepsSearchYearForFirstSeasonTv() {
        assertThat(TmdbMetadataMatcher.resolveSearchYear("tv", parse("episode", 2025, 1))).isEqualTo(2025);
    }

    @Test
    void keepsSearchYearForMovies() {
        assertThat(TmdbMetadataMatcher.resolveSearchYear("movie", parse("movie", 2025, null))).isEqualTo(2025);
    }

    private ParseResult parse(String type, Integer year, Integer season) {
        ParseResult parseResult = new ParseResult();
        parseResult.setType(type);
        parseResult.setYear(year);
        parseResult.setSeason(season);
        return parseResult;
    }
}
