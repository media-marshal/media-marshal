package com.mediamarshal.service.matcher;

record TitleSearchQuery(
        String query,
        TitleSearchQueryType type,
        double weight
) {
}
