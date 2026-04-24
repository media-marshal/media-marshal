package com.mediamarshal.service.rename;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 TemplateVariables 中的可用模板变量。
 *
 * TemplateRenderer 通过反射扫描此注解，自动构建替换 Map。
 * 新增变量时只需在 TemplateVariables 中加字段 + 此注解，渲染器无需改动。
 *
 * @see TemplateVariables
 * @see TemplateRenderer
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TemplateVar {

    /**
     * 模板中使用的变量名，如 "title"、"season"。
     * 与 ADR-001 变量袋定义的名称保持一致。
     */
    String value();
}
