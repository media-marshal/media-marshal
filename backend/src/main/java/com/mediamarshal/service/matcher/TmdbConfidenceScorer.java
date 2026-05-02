package com.mediamarshal.service.matcher;

import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.dto.ParseResult;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
class TmdbConfidenceScorer {

    TmdbScore score(ParseResult parseResult, MatchResult candidate, TitleSearchPlan plan, TitleSearchQuery matchedQuery) {
        return score(parseResult, candidate, plan, matchedQuery, 0);
    }

    TmdbScore score(ParseResult parseResult, MatchResult candidate, TitleSearchPlan plan,
                    TitleSearchQuery matchedQuery, int searchResultCount) {
        TitleScore titleScore = titleScore(candidate, plan.queries(), matchedQuery, searchResultCount);
        double yearScore = yearScore(parseResult.getYear(), candidate.getYear());
        double mediaTypeScore = mediaTypeScore(parseResult, candidate);
        double structureBonus = structureBonus(candidate, plan);
        double confidence = titleScore.score() * 0.60
                + yearScore * 0.20
                + mediaTypeScore * 0.10
                + structureBonus * 0.10;
        return new TmdbScore(Math.min(1.0, confidence), titleScore.query(), titleScore.queryType(),
                titleScore.score(), yearScore, mediaTypeScore, structureBonus);
    }

    private TitleScore titleScore(MatchResult candidate, List<TitleSearchQuery> queries,
                                  TitleSearchQuery matchedQuery, int searchResultCount) {
        TitleScore best = new TitleScore(null, null, 0.0);
        for (TitleSearchQuery query : queries) {
            double rawScore = Math.max(
                    similarity(query.query(), candidate.getTitle()),
                    similarity(query.query(), candidate.getOriginalTitle()));
            double weighted = rawScore * query.weight();
            if (weighted > best.score()) {
                best = new TitleScore(query.query(), query.type(), weighted);
            }
        }
        TitleScore aliasEvidence = aliasSearchEvidenceScore(candidate, matchedQuery, searchResultCount);
        if (aliasEvidence.score() > best.score()) {
            return aliasEvidence;
        }
        return best;
    }

    private TitleScore aliasSearchEvidenceScore(MatchResult candidate, TitleSearchQuery matchedQuery, int searchResultCount) {
        if (matchedQuery == null || searchResultCount <= 0 || isTitleDirectlyComparable(candidate)) {
            return new TitleScore(null, null, 0.0);
        }
        if (matchedQuery.type() != TitleSearchQueryType.ORIGINAL && matchedQuery.type() != TitleSearchQueryType.GUESSIT) {
            return new TitleScore(null, null, 0.0);
        }

        /*
         * TMDB 可以用英文别名召回中文条目，但 search API 返回的 title/originalTitle
         * 未必包含该英文别名。若英文 query 只召回极少候选，就把“搜索命中本身”
         * 作为弱标题证据，避免正确中文候选因标题字段不可比而固定落到 30%。
         */
        double evidence = searchResultCount == 1 ? 0.75 : (searchResultCount <= 3 ? 0.55 : 0.0);
        return new TitleScore(matchedQuery.query(), matchedQuery.type(), evidence * matchedQuery.weight());
    }

    private boolean isTitleDirectlyComparable(MatchResult candidate) {
        return hasAscii(candidate.getTitle()) || hasAscii(candidate.getOriginalTitle());
    }

    private boolean hasAscii(String value) {
        return value != null && value.chars().anyMatch(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z'));
    }

    private double yearScore(Integer parsedYear, Integer candidateYear) {
        if (parsedYear == null) {
            return 0.5;
        }
        if (candidateYear == null) {
            return 0.3;
        }
        if (parsedYear.equals(candidateYear)) {
            return 1.0;
        }
        return Math.abs(parsedYear - candidateYear) == 1 ? 0.6 : 0.0;
    }

    private double mediaTypeScore(ParseResult parseResult, MatchResult candidate) {
        String expected = expectedMediaType(parseResult);
        if (expected == null) {
            return 0.5;
        }
        return expected.equals(candidate.getMediaType()) ? 1.0 : 0.0;
    }

    boolean isStrongMediaTypeMismatch(ParseResult parseResult, MatchResult candidate) {
        String expected = expectedMediaType(parseResult);
        return expected != null && !expected.equals(candidate.getMediaType());
    }

    private String expectedMediaType(ParseResult parseResult) {
        if ("episode".equalsIgnoreCase(parseResult.getType())
                || parseResult.getSeason() != null
                || parseResult.getEpisode() != null) {
            return "TV_SHOW";
        }
        if ("movie".equalsIgnoreCase(parseResult.getType())) {
            return "MOVIE";
        }
        return null;
    }

    private double structureBonus(MatchResult candidate, TitleSearchPlan plan) {
        /*
         * ADR-018 的结构加分只服务双语标题：本地化 query 命中 TMDB 本地化标题，
         * 原始语言 query 命中 originalTitle，且二者指向当前同一候选时给满分。
         * 这里使用轻量相似度阈值近似判断，不做分词或 NLP；因此不会为“剧集结构”
         * 或单一高相似标题泛化加分。
         */
        boolean localizedHit = plan.queries().stream()
                .filter(query -> query.type() == TitleSearchQueryType.LOCALIZED)
                .anyMatch(query -> similarity(query.query(), candidate.getTitle()) >= 0.90);
        boolean originalHit = plan.queries().stream()
                .filter(query -> query.type() == TitleSearchQueryType.ORIGINAL)
                .anyMatch(query -> similarity(query.query(), candidate.getOriginalTitle()) >= 0.90);

        if (localizedHit && originalHit) {
            return 1.0;
        }
        return 0.0;
    }

    double similarity(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isBlank() || b.isBlank()) return 0.0;
        if (a.equals(b)) return 1.0;

        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return (double) dp[a.length()][b.length()] / Math.max(a.length(), b.length());
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private record TitleScore(String query, TitleSearchQueryType queryType, double score) {
    }
}
