package com.mediamarshal.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchRuleImportSummary {

    private int importable;
    private int ready;
    private int skipped;
    private int conflicts;
    private int invalid;
    private int warnings;
}
