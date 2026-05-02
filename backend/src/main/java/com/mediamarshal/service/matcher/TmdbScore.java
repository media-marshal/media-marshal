package com.mediamarshal.service.matcher;

record TmdbScore(
        double confidence,
        String bestQuery,
        TitleSearchQueryType bestQueryType,
        double titleScore,
        double yearScore,
        double mediaTypeScore,
        double structureBonus
) {
}
