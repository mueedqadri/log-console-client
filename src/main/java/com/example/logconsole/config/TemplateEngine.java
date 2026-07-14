package com.example.logconsole.config;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateEngine {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");

    private TemplateEngine() { }

    public static String render(String template, Map<String, String> values) {
        if (template == null) {
            throw new IllegalArgumentException("Template cannot be null");
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = values.get(key);
            if (replacement == null) {
                throw new IllegalArgumentException("Unknown or unavailable placeholder {" + key + "} in " + template);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        if (result.indexOf("{") >= 0 || result.indexOf("}") >= 0) {
            throw new IllegalArgumentException("Malformed placeholder in " + template);
        }
        return result.toString();
    }
}
