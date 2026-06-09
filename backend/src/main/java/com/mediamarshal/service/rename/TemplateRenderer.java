package com.mediamarshal.service.rename;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_][a-z0-9_]*)(?::([^;}]+))?(?:;([^}]+))?}");
    private static final Pattern OPTIONAL_SEGMENT = Pattern.compile("\\[\\[(.*?)]\\]");
    private static final Pattern UNSAFE_PATH_VALUE_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    public String render(String template, TemplateVariables variables) {
        Map<String, Object> varMap = buildVarMap(variables);
        log.debug("Rendering template='{}' with vars={}", template, varMap);

        String withOptionalSegments = renderOptionalSegments(template, varMap);
        String output = renderPlaceholders(withOptionalSegments, varMap);
        log.debug("Template rendered: '{}'", output);
        return output;
    }

    private String renderOptionalSegments(String template, Map<String, Object> varMap) {
        Matcher matcher = OPTIONAL_SEGMENT.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String segment = matcher.group(1);
            String replacement = canRenderAllPlaceholders(segment, varMap)
                    ? renderPlaceholders(segment, varMap)
                    : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private boolean canRenderAllPlaceholders(String segment, Map<String, Object> varMap) {
        Matcher matcher = PLACEHOLDER.matcher(segment);
        while (matcher.find()) {
            if (varMap.get(matcher.group(1)) == null) {
                return false;
            }
        }
        return true;
    }

    private String renderPlaceholders(String template, Map<String, Object> varMap) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String format = matcher.group(2);
            TemplatePlaceholderOptions options = TemplatePlaceholderOptions.parse(matcher.group(3));
            Object value = varMap.get(varName);

            if (value == null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                log.debug("Variable '{}' is null, keeping placeholder", varName);
                continue;
            }

            String rendered = renderValue(varName, value, format, options);
            matcher.appendReplacement(result, Matcher.quoteReplacement(rendered));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String renderValue(String varName, Object value, String format, TemplatePlaceholderOptions options) {
        TemplateRange range = toTemplateRange(varName, value);
        if (range != null) {
            return renderRange(varName, range, format, options);
        }
        return applyAffixes(applyFormat(varName, value, format), options);
    }

    private TemplateRange toTemplateRange(String varName, Object value) {
        if (value instanceof TemplateRange range) {
            return range;
        }
        if (value instanceof Iterable<?> iterable && !(value instanceof CharSequence)) {
            List<Integer> values = new ArrayList<>();
            for (Object item : iterable) {
                if (!(item instanceof Number number)) {
                    log.warn("Range rendering ignored for non-numeric iterable variable '{}'", varName);
                    return null;
                }
                values.add(number.intValue());
            }
            if (values.isEmpty()) {
                return null;
            }
            validateContiguousRange(varName, values);
            return new TemplateRange(values.getFirst(), values.size() == 1 ? null : values.getLast());
        }
        return null;
    }

    private void validateContiguousRange(String varName, List<Integer> values) {
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) != values.get(i - 1) + 1) {
                throw new IllegalArgumentException(
                        "Only contiguous ranges are supported for template variable '" + varName + "': " + values
                );
            }
        }
    }

    private String renderRange(
            String varName,
            TemplateRange range,
            String format,
            TemplatePlaceholderOptions options
    ) {
        String start = applyFormat(varName, range.start(), format);
        if (!range.isRange()) {
            return applyAffixes(start, options);
        }

        String end = applyFormat(varName, range.end(), format);
        String startToken = options.prefix() + start + (options.repeatSuffix() ? options.suffix() : "");
        String endToken = (options.repeatPrefix() ? options.prefix() : "") + end + options.suffix();
        return startToken + options.separator() + endToken;
    }

    private String applyAffixes(String value, TemplatePlaceholderOptions options) {
        return options.prefix() + value + options.suffix();
    }

    private String applyFormat(String varName, Object value, String format) {
        if (format == null) {
            return String.valueOf(value);
        }
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

    private Map<String, Object> buildVarMap(TemplateVariables variables) {
        Map<String, Object> map = new HashMap<>();
        for (Field field : TemplateVariables.class.getDeclaredFields()) {
            TemplateVar annotation = field.getAnnotation(TemplateVar.class);
            if (annotation == null) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object value = field.get(variables);
                map.put(annotation.value(), sanitizeValue(annotation.value(), value));
            } catch (IllegalAccessException e) {
                log.error("Failed to read TemplateVariables field: {}", field.getName(), e);
            }
        }
        return map;
    }

    private Object sanitizeValue(String varName, Object value) {
        if (!(value instanceof CharSequence text)) {
            return value;
        }

        String sanitized = UNSAFE_PATH_VALUE_CHARS.matcher(text.toString()).replaceAll("_").trim();
        if ("ext".equals(varName)) {
            return sanitized;
        }
        if (sanitized.isBlank()) {
            return null;
        }
        if (".".equals(sanitized) || "..".equals(sanitized)) {
            return "_";
        }
        return sanitized;
    }

    private record TemplatePlaceholderOptions(
            String prefix,
            String suffix,
            String separator,
            boolean repeatPrefix,
            boolean repeatSuffix
    ) {
        private static final String PREFIX = "prefix";
        private static final String SUFFIX = "suffix";
        private static final String SEPARATOR = "separator";
        private static final String REPEAT_PREFIX = "repeatPrefix";
        private static final String REPEAT_SUFFIX = "repeatSuffix";

        static TemplatePlaceholderOptions parse(String rawParams) {
            Map<String, String> params = parseParams(rawParams);
            return new TemplatePlaceholderOptions(
                    params.getOrDefault(PREFIX, ""),
                    params.getOrDefault(SUFFIX, ""),
                    params.getOrDefault(SEPARATOR, "-"),
                    parseBoolean(params.get(REPEAT_PREFIX), true),
                    parseBoolean(params.get(REPEAT_SUFFIX), true)
            );
        }

        private static Map<String, String> parseParams(String rawParams) {
            Map<String, String> params = new HashMap<>();
            if (rawParams == null || rawParams.isBlank()) {
                return params;
            }
            for (String token : rawParams.split(";")) {
                int equalsIndex = token.indexOf('=');
                if (equalsIndex <= 0) {
                    throw new IllegalArgumentException("Invalid template parameter: " + token);
                }
                String key = token.substring(0, equalsIndex).trim();
                String value = token.substring(equalsIndex + 1);
                if (!isSupportedParam(key)) {
                    throw new IllegalArgumentException("Unsupported template parameter: " + key);
                }
                params.put(key, value);
            }
            return params;
        }

        private static boolean isSupportedParam(String key) {
            return PREFIX.equals(key)
                    || SUFFIX.equals(key)
                    || SEPARATOR.equals(key)
                    || REPEAT_PREFIX.equals(key)
                    || REPEAT_SUFFIX.equals(key);
        }

        private static boolean parseBoolean(String value, boolean defaultValue) {
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
            throw new IllegalArgumentException("Boolean template parameter must be true or false: " + value);
        }
    }
}
