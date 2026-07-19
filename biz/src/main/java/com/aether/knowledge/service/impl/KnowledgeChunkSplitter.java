package com.aether.knowledge.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits knowledge content without cutting across headings and paragraphs unnecessarily. */
@Component
public class KnowledgeChunkSplitter {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern SENTENCE = Pattern.compile("[^。！？.!?]+[。！？.!?]+|[^。！？.!?]+$");

    private final int maxChars;
    private final int overlapChars;

    public KnowledgeChunkSplitter() {
        this(1200, 200);
    }

    KnowledgeChunkSplitter(int maxChars, int overlapChars) {
        if (maxChars <= 0 || overlapChars < 0 || overlapChars >= maxChars) {
            throw new IllegalArgumentException("invalid chunk size configuration");
        }
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    public List<Segment> split(String content) {
        if (StringUtils.isBlank(content)) {
            return Collections.emptyList();
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<Segment> result = new ArrayList<>();
        String[] headings = new String[6];
        String sectionPath = "ROOT";
        StringBuilder section = new StringBuilder();
        for (String line : normalized.split("\n", -1)) {
            Matcher matcher = HEADING.matcher(line.trim());
            if (matcher.matches()) {
                appendSection(result, section.toString(), sectionPath);
                section.setLength(0);
                int level = matcher.group(1).length();
                headings[level - 1] = matcher.group(2).trim();
                for (int i = level; i < headings.length; i++) {
                    headings[i] = null;
                }
                sectionPath = joinHeadings(headings);
            }
            if (section.length() > 0) {
                section.append('\n');
            }
            section.append(line);
        }
        appendSection(result, section.toString(), sectionPath);
        return result;
    }

    public int estimateTokens(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isCjk(codePoint)) {
                cjk++;
            } else if (!Character.isWhitespace(codePoint)) {
                other++;
            }
        }
        return Math.max(1, cjk + (other + 3) / 4);
    }

    private void appendSection(List<Segment> output, String value, String sectionPath) {
        String section = StringUtils.trimToEmpty(value);
        if (section.isEmpty()) {
            return;
        }
        List<String> units = new ArrayList<>();
        for (String paragraph : section.split("\\n\\s*\\n+")) {
            addUnit(units, paragraph.trim());
        }
        pack(output, units, sectionPath);
    }

    private void addUnit(List<String> units, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        if (value.length() <= maxChars) {
            units.add(value);
            return;
        }
        Matcher matcher = SENTENCE.matcher(value);
        boolean foundSentence = false;
        while (matcher.find()) {
            foundSentence = true;
            addHardSplit(units, matcher.group().trim());
        }
        if (!foundSentence) {
            addHardSplit(units, value);
        }
    }

    private void addHardSplit(List<String> units, String value) {
        int start = 0;
        while (start < value.length()) {
            int end = Math.min(value.length(), start + maxChars);
            if (end < value.length()) {
                int boundary = Math.max(value.lastIndexOf('\n', end), value.lastIndexOf(' ', end));
                if (boundary > start + maxChars / 2) {
                    end = boundary;
                }
            }
            String part = value.substring(start, end).trim();
            if (!part.isEmpty()) {
                units.add(part);
            }
            start = end;
            while (start < value.length() && Character.isWhitespace(value.charAt(start))) {
                start++;
            }
        }
    }

    private void pack(List<Segment> output, List<String> units, String sectionPath) {
        List<String> current = new ArrayList<>();
        for (String unit : units) {
            while (!current.isEmpty() && joinedLength(current) + 2 + unit.length() > maxChars) {
                output.add(new Segment(StringUtils.join(current, "\n\n"), sectionPath));
                current = overlapTail(current);
                while (!current.isEmpty() && joinedLength(current) + 2 + unit.length() > maxChars) {
                    current.remove(0);
                }
            }
            current.add(unit);
        }
        if (!current.isEmpty()) {
            output.add(new Segment(StringUtils.join(current, "\n\n"), sectionPath));
        }
    }

    private List<String> overlapTail(List<String> current) {
        List<String> tail = new ArrayList<>();
        for (int i = current.size() - 1; i >= 0; i--) {
            String unit = current.get(i);
            int candidateLength = joinedLength(tail) + (tail.isEmpty() ? 0 : 2) + unit.length();
            if (candidateLength > overlapChars) {
                break;
            }
            tail.add(0, unit);
        }
        return tail;
    }

    private int joinedLength(List<String> values) {
        if (values.isEmpty()) {
            return 0;
        }
        int length = (values.size() - 1) * 2;
        for (String value : values) {
            length += value.length();
        }
        return length;
    }

    private String joinHeadings(String[] headings) {
        List<String> path = new ArrayList<>();
        for (String heading : headings) {
            if (StringUtils.isNotBlank(heading)) {
                path.add(heading);
            }
        }
        return path.isEmpty() ? "ROOT" : StringUtils.join(path, " > ");
    }

    private boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    public static class Segment {
        private final String content;
        private final String sectionPath;

        Segment(String content, String sectionPath) {
            this.content = content;
            this.sectionPath = sectionPath;
        }

        public String getContent() {
            return content;
        }

        public String getSectionPath() {
            return sectionPath;
        }
    }
}
