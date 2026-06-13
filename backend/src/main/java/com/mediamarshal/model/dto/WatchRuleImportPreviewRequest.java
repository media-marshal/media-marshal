package com.mediamarshal.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class WatchRuleImportPreviewRequest {

    @JsonProperty("package")
    private JsonNode importPackage;

    private Boolean preserveEnabledState;

    public boolean shouldPreserveEnabledState() {
        return Boolean.TRUE.equals(preserveEnabledState);
    }
}
