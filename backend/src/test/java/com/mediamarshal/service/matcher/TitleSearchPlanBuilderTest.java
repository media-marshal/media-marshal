package com.mediamarshal.service.matcher;

import com.mediamarshal.model.dto.ParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TitleSearchPlanBuilderTest {

    private final TitleSearchPlanBuilder builder = new TitleSearchPlanBuilder();

    @Test
    void buildsChineseAndEnglishQueriesForBilingualMovieTitle() {
        TitleSearchPlan plan = builder.build(parse("飞驰人生3.Pegasus.3.2026.2160p.WEB-DL.mkv", "飞驰人生3 Pegasus 3", 2026, null));

        assertThat(assertQueries(plan)).containsSubsequence("飞驰人生3", "Pegasus 3");
    }

    @Test
    void buildsChineseAndEnglishQueriesForBilingualSeriesTitle() {
        TitleSearchPlan plan = builder.build(parse("长乐曲.Melody.Of.Golden.Age.S01E01.2024.1080p.mkv", "长乐曲", 2024, 1));

        assertThat(assertQueries(plan)).containsSubsequence("长乐曲", "Melody Of Golden Age");
    }

    @Test
    void extractsBracketedChineseAndEnglishTitle() {
        TitleSearchPlan plan = builder.build(parse("[钢铁森林].Sunsets.Secrets.Regrets.S01E01.2025.1080p.mkv", "钢铁森林", 2025, 1));

        assertThat(assertQueries(plan)).containsSubsequence("钢铁森林", "Sunsets Secrets Regrets");
    }

    @Test
    void extractsBracketedChineseAndEnglishTitleWithShortChineseName() {
        TitleSearchPlan plan = builder.build(parse("[逍遥].The.Unclouded.Soul.S01E01.2026.1080p.mkv", "逍遥", 2026, 1));

        assertThat(assertQueries(plan)).containsSubsequence("逍遥", "The Unclouded Soul");
    }

    @Test
    void buildsChineseAndEnglishQueriesForEightHundredSeriesTitle() {
        TitleSearchPlan plan = builder.build(parse(
                "方圆八百米.Eight.Hundred.S01E02.2026.2160p.WEB-DL.H265.AAC-HHWEB.mp4",
                "方圆八百米",
                2026,
                1));

        assertThat(assertQueries(plan)).containsSubsequence("方圆八百米", "Eight Hundred");
    }

    @Test
    void doesNotUseEpisodeTitleAsSeriesQuery() {
        TitleSearchPlan plan = builder.build(parse(
                "Your.Friends.and.Neighbors.S01E01.This.Is.What.Happens.2025.1080p.mkv",
                "Your Friends and Neighbors",
                2025,
                1));

        assertThat(assertQueries(plan)).contains("Your Friends and Neighbors");
        assertThat(assertQueries(plan)).noneMatch(query -> query.contains("This Is What Happens"));
    }

    private ParseResult parse(String originalFilename, String title, Integer year, Integer season) {
        ParseResult parseResult = new ParseResult();
        parseResult.setOriginalFilename(originalFilename);
        parseResult.setTitle(title);
        parseResult.setYear(year);
        parseResult.setSeason(season);
        parseResult.setType(season == null ? "movie" : "episode");
        return parseResult;
    }

    private List<String> assertQueries(TitleSearchPlan plan) {
        return plan.queries().stream()
                .map(TitleSearchQuery::query)
                .toList();
    }
}
