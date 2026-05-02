package com.mediamarshal.service.matcher;

import java.util.List;

record TitleSearchPlan(
        String originalFilename,
        String guessitTitle,
        String titleRegion,
        List<TitleSearchQuery> queries
) {
}
