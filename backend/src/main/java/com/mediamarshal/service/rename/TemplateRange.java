package com.mediamarshal.service.rename;

public record TemplateRange(Integer start, Integer end) {

    public TemplateRange {
        if (start == null) {
            throw new IllegalArgumentException("Template range start is required");
        }
        if (end != null && end < start) {
            throw new IllegalArgumentException("Template range end must be greater than or equal to start");
        }
        if (end != null && end.equals(start)) {
            end = null;
        }
    }

    public boolean isRange() {
        return end != null;
    }
}
