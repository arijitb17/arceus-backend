package com.arijit.notificationservice.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TemplateRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    public String render(String template, Map<String, Object> variables) {
        if (template == null) return null;
        Map<String, Object> vars = variables == null ? Map.of() : variables;

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = vars.get(key);
            String replacement = value != null ? Matcher.quoteReplacement(String.valueOf(value)) : matcher.group(0);
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
