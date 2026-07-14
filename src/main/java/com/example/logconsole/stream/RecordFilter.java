package com.example.logconsole.stream;

import com.example.logconsole.model.ParsedRecord;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class RecordFilter {
    private final Set<String> levels;
    private final String text;
    private final boolean emptyMeansAll;

    public RecordFilter(Set<String> levels, String text) {
        this(levels, text, true);
    }

    public RecordFilter(Set<String> levels, String text, boolean emptyMeansAll) {
        Set<String> normalized = new HashSet<String>();
        if (levels != null) for (String level : levels) normalized.add(level.toUpperCase(Locale.ROOT));
        this.levels = Collections.unmodifiableSet(normalized);
        this.text = text == null ? "" : text.toLowerCase(Locale.ROOT);
        this.emptyMeansAll = emptyMeansAll;
    }

    public boolean test(ParsedRecord record) {
        if ((levels.isEmpty() && !emptyMeansAll) || (!levels.isEmpty() && !levels.contains(record.getLevel()))) return false;
        return text.isEmpty() || record.searchableText().toLowerCase(Locale.ROOT).contains(text);
    }

    public Set<String> getLevels() { return levels; }
    public String getText() { return text; }
}
