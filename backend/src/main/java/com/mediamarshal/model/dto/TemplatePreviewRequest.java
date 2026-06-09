package com.mediamarshal.model.dto;

import com.mediamarshal.model.entity.MediaTask;
import lombok.Data;

import java.util.Map;

@Data
public class TemplatePreviewRequest {

    private String template;

    private MediaTask.MediaType mediaType;

    private String targetDir;

    private Map<String, Object> context;
}
