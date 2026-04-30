package com.mediamarshal.model.dto;

import java.util.List;

/**
 * 路径模板变量帮助分组。
 *
 * 后端统一提供变量名、解释和样例，前端只负责展示，避免自定义模板帮助内容与
 * TemplateVariables 的实际支持范围长期漂移。
 */
public record TemplateVariableGroup(
        String category,
        String categoryName,
        List<TemplateVariableItem> variables
) {

    public record TemplateVariableItem(
            String name,
            String placeholder,
            String type,
            String source,
            String description,
            String example,
            List<String> mediaTypes,
            String status
    ) {
    }
}
