package com.mediamarshal.service.matcher;

import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbConfidenceScorerTest {

    private final TmdbConfidenceScorer scorer = new TmdbConfidenceScorer();

    @Test
    void scoresExactTitleYearAndTypeHighly() {
        ParseResult parseResult = parse("movie", "飞驰人生3", 2026, null, null);
        MatchResult candidate = candidate("飞驰人生3", "Pegasus 3", 2026, "MOVIE");
        TitleSearchPlan plan = new TitleSearchPlan(null, "飞驰人生3", "飞驰人生3 Pegasus 3", List.of(
                new TitleSearchQuery("飞驰人生3", TitleSearchQueryType.LOCALIZED, 1.0),
                new TitleSearchQuery("Pegasus 3", TitleSearchQueryType.ORIGINAL, 1.0)));

        TmdbScore score = scorer.score(parseResult, candidate, plan, plan.queries().getFirst());

        assertThat(score.confidence()).isGreaterThan(0.88);
        assertThat(score.bestQuery()).isEqualTo("飞驰人生3");
        assertThat(score.yearScore()).isEqualTo(1.0);
        assertThat(score.mediaTypeScore()).isEqualTo(1.0);
        assertThat(score.structureBonus()).isEqualTo(1.0);
    }

    @Test
    void filtersStrongMediaTypeMismatch() {
        ParseResult parseResult = parse("episode", "长乐曲", 2024, 1, 1);
        MatchResult candidate = candidate("长乐曲", "Melody Of Golden Age", 2024, "MOVIE");

        assertThat(scorer.isStrongMediaTypeMismatch(parseResult, candidate)).isTrue();
    }

    @Test
    void givesPartialCreditForAdjacentYear() {
        ParseResult parseResult = parse("movie", "影片名", 2026, null, null);
        MatchResult candidate = candidate("影片名", "Movie Title", 2025, "MOVIE");
        TitleSearchPlan plan = new TitleSearchPlan(null, "影片名", "影片名", List.of(
                new TitleSearchQuery("影片名", TitleSearchQueryType.LOCALIZED, 1.0)));

        TmdbScore score = scorer.score(parseResult, candidate, plan, plan.queries().getFirst());

        assertThat(score.yearScore()).isEqualTo(0.3);
        assertThat(score.structureBonus()).isZero();
        assertThat(score.confidence()).isGreaterThan(0.75);
    }

    @Test
    void awardsStructureBonusOnlyWhenLocalizedAndOriginalQueriesHitSameCandidate() {
        ParseResult parseResult = parse("episode", "长乐曲", 2024, 1, 2);
        MatchResult candidate = candidate("长乐曲", "Melody Of Golden Age", 2024, "TV_SHOW");
        TitleSearchPlan plan = new TitleSearchPlan(null, "长乐曲", "长乐曲 Melody Of Golden Age", List.of(
                new TitleSearchQuery("长乐曲", TitleSearchQueryType.LOCALIZED, 1.0),
                new TitleSearchQuery("Melody Of Golden Age", TitleSearchQueryType.ORIGINAL, 0.95)));

        TmdbScore score = scorer.score(parseResult, candidate, plan, plan.queries().getFirst());

        assertThat(score.structureBonus()).isEqualTo(1.0);
        assertThat(score.confidence()).isGreaterThan(0.88);
    }

    @Test
    void doesNotAwardStructureBonusForSeriesStructureAlone() {
        ParseResult parseResult = parse("episode", "Your Friends and Neighbors", 2025, 1, 1);
        MatchResult candidate = candidate("Your Friends and Neighbors", "Your Friends and Neighbors", 2025, "TV_SHOW");
        TitleSearchPlan plan = new TitleSearchPlan(null, "Your Friends and Neighbors", "Your Friends and Neighbors", List.of(
                new TitleSearchQuery("Your Friends and Neighbors", TitleSearchQueryType.ORIGINAL, 0.95)));

        TmdbScore score = scorer.score(parseResult, candidate, plan, plan.queries().getFirst());

        assertThat(score.structureBonus()).isZero();
    }

    @Test
    void boostsConfidenceWhenEnglishAliasUniquelyFindsChineseMovieCandidate() {
        ParseResult parseResult = parse("movie", "The Flowers of War", 2011, null, null);
        MatchResult candidate = candidate("金陵十三钗", "金陵十三钗", 2011, "MOVIE");
        TitleSearchQuery query = new TitleSearchQuery("The Flowers of War", TitleSearchQueryType.ORIGINAL, 0.95);
        TitleSearchPlan plan = new TitleSearchPlan(null, "The Flowers of War", "The Flowers of War", List.of(query));

        TmdbScore score = scorer.score(parseResult, candidate, plan, query, 1);

        assertThat(score.titleScore()).isGreaterThan(0.87);
        assertThat(score.confidence()).isBetween(0.81, 0.84);
    }

    @Test
    void boostsConfidenceWhenEnglishAliasUniquelyFindsChineseSeriesCandidate() {
        ParseResult parseResult = parse("episode", "For the Sake of the Republic", 2003, 1, 1);
        MatchResult candidate = candidate("走向共和", "走向共和", 2003, "TV_SHOW");
        TitleSearchQuery query = new TitleSearchQuery("For the Sake of the Republic", TitleSearchQueryType.ORIGINAL, 0.95);
        TitleSearchPlan plan = new TitleSearchPlan(null, "For the Sake of the Republic", "For the Sake of the Republic", List.of(query));

        TmdbScore score = scorer.score(parseResult, candidate, plan, query, 1);

        assertThat(score.titleScore()).isGreaterThan(0.87);
        assertThat(score.confidence()).isBetween(0.81, 0.84);
    }

    @Test
    void penalizesAliasEvidenceWhenYearIsMissing() {
        ParseResult parseResult = parse("movie", "The Flowers of War", null, null, null);
        MatchResult candidate = candidate("金陵十三钗", "金陵十三钗", 2011, "MOVIE");
        TitleSearchQuery query = new TitleSearchQuery("The Flowers of War", TitleSearchQueryType.ORIGINAL, 0.95);
        TitleSearchPlan plan = new TitleSearchPlan(null, "The Flowers of War", "The Flowers of War", List.of(query));

        TmdbScore score = scorer.score(parseResult, candidate, plan, query, 1);

        assertThat(score.yearScore()).isEqualTo(0.3);
        assertThat(score.confidence()).isLessThan(0.45);
    }

    @Test
    void penalizesAliasEvidenceWhenYearDoesNotMatch() {
        ParseResult parseResult = parse("episode", "For the Sake of the Republic", 2004, 1, 1);
        MatchResult candidate = candidate("走向共和", "走向共和", 2003, "TV_SHOW");
        TitleSearchQuery query = new TitleSearchQuery("For the Sake of the Republic", TitleSearchQueryType.ORIGINAL, 0.95);
        TitleSearchPlan plan = new TitleSearchPlan(null, "For the Sake of the Republic", "For the Sake of the Republic", List.of(query));

        TmdbScore score = scorer.score(parseResult, candidate, plan, query, 1);

        assertThat(score.yearScore()).isEqualTo(0.3);
        assertThat(score.confidence()).isLessThan(0.45);
    }

    private ParseResult parse(String type, String title, Integer year, Integer season, Integer episode) {
        ParseResult parseResult = new ParseResult();
        parseResult.setType(type);
        parseResult.setTitle(title);
        parseResult.setYear(year);
        parseResult.setSeason(season);
        parseResult.setEpisode(episode);
        return parseResult;
    }

    private MatchResult candidate(String title, String originalTitle, Integer year, String mediaType) {
        MatchResult result = new MatchResult();
        result.setTitle(title);
        result.setOriginalTitle(originalTitle);
        result.setYear(year);
        result.setMediaType(mediaType);
        return result;
    }
}
