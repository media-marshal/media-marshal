package com.mediamarshal.controller;

import com.mediamarshal.model.dto.ApiResponse;
import com.mediamarshal.model.dto.TemplatePreviewRequest;
import com.mediamarshal.model.dto.TemplatePreviewResponse;
import com.mediamarshal.model.dto.TemplateVariableGroup;
import com.mediamarshal.service.rename.TemplateVariableCatalogService;
import com.mediamarshal.service.rename.TemplatePreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 路径模板变量帮助接口。
 */
@RestController
@RequestMapping("/api/template-variables")
@RequiredArgsConstructor
public class TemplateVariableController {

    private final TemplateVariableCatalogService templateVariableCatalogService;
    private final TemplatePreviewService templatePreviewService;

    @GetMapping
    public ApiResponse<List<TemplateVariableGroup>> listVariables() {
        return ApiResponse.ok(templateVariableCatalogService.listVariables());
    }

    @PostMapping("/preview")
    public ApiResponse<TemplatePreviewResponse> preview(@RequestBody TemplatePreviewRequest request) {
        return ApiResponse.ok(templatePreviewService.preview(request));
    }
}
