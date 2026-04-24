package com.mediamarshal.service.rename;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 命名模板渲染引擎（ADR-001）
 *
 * 通过反射扫描 TemplateVariables 中的 @TemplateVar 字段，自动构建替换 Map，
 * 再将模板字符串中的占位符替换为实际值。
 *
 * 支持的占位符格式：
 *   {varname}        → 直接 toString，如 {title} → "The Dark Knight"
 *   {varname:02d}    → 数值格式化，如 {season:02d} → "03"（对应 String.format("%02d", value)）
 *
 * Null 值处理：占位符保持原样（不替换）。
 * 这样用户能从路径中看出哪个变量未被填充，便于调试。
 */
@Slf4j
@Component
public class TemplateRenderer {

    /**
     * 匹配 {varname} 或 {varname:format} 的正则
     * group(1) = varname，group(2) = format（可为 null）
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)(?::([^}]+))?}");

    /**
     * 渲染模板字符串
     *
     * @param template  模板字符串，如 "{title} ({year})/{title} ({year}){ext}"
     * @param variables 变量袋
     * @return 渲染后的路径字符串（不含目标根目录）
     */
    public String render(String template, TemplateVariables variables) {
        Map<String, Object> varMap = buildVarMap(variables);
        log.debug("Rendering template='{}' with vars={}", template, varMap);

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String format  = matcher.group(2);   // null if no format specifier
            Object value   = varMap.get(varName);

            if (value == null) {
                // 变量未填充，保持占位符原样
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                log.debug("Variable '{}' is null, keeping placeholder", varName);
                continue;
            }

            String rendered = applyFormat(varName, value, format);
            matcher.appendReplacement(result, Matcher.quoteReplacement(rendered));
        }
        matcher.appendTail(result);

        String output = result.toString();
        log.debug("Template rendered: '{}'", output);
        return output;
    }

    /**
     * 应用格式化规则
     * 目前仅支持整数的 printf 风格格式（02d、04d 等）
     */
    private String applyFormat(String varName, Object value, String format) {
        if (format == null) {
            return String.valueOf(value);
        }
        // 仅对数值类型应用格式化
        if (value instanceof Number number) {
            try {
                return String.format("%" + format, number.intValue());
            } catch (Exception e) {
                log.warn("Failed to apply format '%{}' to variable '{}', using raw value. Error: {}",
                        format, varName, e.getMessage());
                return String.valueOf(value);
            }
        }
        log.warn("Format specifier '{}' ignored for non-numeric variable '{}'", format, varName);
        return String.valueOf(value);
    }

    /**
     * 通过反射扫描 @TemplateVar 注解，构建 varName → value 映射
     */
    private Map<String, Object> buildVarMap(TemplateVariables variables) {
        Map<String, Object> map = new HashMap<>();
        for (Field field : TemplateVariables.class.getDeclaredFields()) {
            TemplateVar annotation = field.getAnnotation(TemplateVar.class);
            if (annotation == null) continue;
            field.setAccessible(true);
            try {
                Object value = field.get(variables);
                map.put(annotation.value(), value);
            } catch (IllegalAccessException e) {
                log.error("Failed to read TemplateVariables field: {}", field.getName(), e);
            }
        }
        return map;
    }
}
