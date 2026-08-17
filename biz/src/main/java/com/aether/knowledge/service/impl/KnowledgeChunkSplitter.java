package com.aether.knowledge.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hybrid splitter: preserves Markdown structure first, then applies paragraph,
 * table-aware, sentence and token-budget boundaries as successive fallbacks.
 */
@Component
public class KnowledgeChunkSplitter {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern SENTENCE = Pattern.compile("[^。！？.!?]+[。！？.!?]+|[^。！？.!?]+$");

    private final int maxChars;
    private final int overlapChars;
    private final int maxTokens;

    /**
     * 创建 {@code KnowledgeChunkSplitter} 实例。
     */
    public KnowledgeChunkSplitter() {
        // A chunk must normally contain a complete explanation, not merely a
        // sentence-sized retrieval hint.  Retrieval expands around a hit too,
        // but using a reasonably sized primary chunk avoids losing the answer
        // when the relevant material is concentrated in one section.
        this(2400, 320, 1400);
    }

    /**
     * 创建 {@code KnowledgeChunkSplitter} 实例。
     */
    KnowledgeChunkSplitter(int maxChars, int overlapChars) {
        this(maxChars, overlapChars, maxChars);
    }

    /**
     * 创建 {@code KnowledgeChunkSplitter} 实例。
     */
    KnowledgeChunkSplitter(int maxChars, int overlapChars, int maxTokens) {
        if (maxChars <= 0 || overlapChars < 0 || overlapChars >= maxChars || maxTokens <= 0) {
            throw new IllegalArgumentException("invalid chunk size configuration");
        }
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
        this.maxTokens = maxTokens;
    }

    /**
     * 处理split。
     */
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
                appendHeadingContext(section, headings, level);
                continue;
            }
            if (section.length() > 0) {
                section.append('\n');
            }
            section.append(line);
        }
        appendSection(result, section.toString(), sectionPath);
        return result;
    }

    /**
     * 处理estimateTokens。
     */
    public int estimateTokens(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int offset = 0; offset < text.length(); ) {
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

    /**
     * 处理appendSection。
     */
    private void appendSection(List<Segment> output, String value, String sectionPath) {
        String section = StringUtils.trimToEmpty(value);
        // A title without body text is structural context, not a retrievable
        // unit.  It is carried into the following child section instead.
        if (section.isEmpty() || isHeadingOnly(section)) {
            return;
        }
        List<String> units = new ArrayList<>();
        for (String paragraph : section.split("\\n\\s*\\n+")) {
            String block = paragraph.trim();
            if (isMarkdownTable(block)) {
                addTableUnits(units, block);
            } else {
                addUnit(units, block);
            }
        }
        pack(output, units, sectionPath);
    }

    /**
     * Repeats the active heading hierarchy at the beginning of a child section.
     */
    private void appendHeadingContext(StringBuilder section, String[] headings, int currentLevel) {
        for (int index = 0; index < currentLevel; index++) {
            if (StringUtils.isBlank(headings[index])) {
                continue;
            }
            if (section.length() > 0) {
                section.append('\n');
            }
            section.append(StringUtils.repeat('#', index + 1)).append(' ').append(headings[index]);
        }
    }

    /**
     * 判断是否为HeadingOnly。
     */
    private boolean isHeadingOnly(String section) {
        boolean hasHeading = false;
        for (String line : section.split("\\n")) {
            if (StringUtils.isBlank(line)) {
                continue;
            }
            if (!HEADING.matcher(line.trim()).matches()) {
                return false;
            }
            hasHeading = true;
        }
        return hasHeading;
    }

    /**
     * 新增Unit。
     */
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

    /**
     * Keeps Markdown table headers with every row group instead of cutting a row by character count.
     */
    private void addTableUnits(List<String> units, String table) {
        if (table.length() <= maxChars) {
            units.add(table);
            return;
        }
        String[] lines = table.split("\\n");
        if (lines.length < 3) {
            addHardSplit(units, table);
            return;
        }
        String header = lines[0].trim();
        String separator = lines[1].trim();
        List<String> rows = new ArrayList<>();
        for (int index = 2; index < lines.length; index++) {
            if (StringUtils.isNotBlank(lines[index])) rows.add(lines[index].trim());
        }
        StringBuilder current = new StringBuilder(header).append('\n').append(separator);
        for (String row : rows) {
            if (current.length() + 1 + row.length() > maxChars && current.toString().split("\\n").length > 2) {
                units.add(current.toString());
                current.setLength(0);
                current.append(header).append('\n').append(separator);
            }
            if (current.length() + 1 + row.length() > maxChars) {
                addHardSplit(units, current.append('\n').append(row).toString());
                current.setLength(0);
                current.append(header).append('\n').append(separator);
            } else {
                current.append('\n').append(row);
            }
        }
        if (current.toString().split("\\n").length > 2) {
            units.add(current.toString());
        }
    }

    /**
     * 判断是否为MarkdownTable。
     */
    private boolean isMarkdownTable(String value) {
        String[] lines = value.split("\\n");
        return lines.length >= 2 && lines[0].contains("|")
                && lines[1].matches("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");
    }

    /**
     * 新增HardSplit。
     */
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

    /**
     * 处理pack。
     */
    private void pack(List<Segment> output, List<String> units, String sectionPath) {
        List<String> current = new ArrayList<>();
        for (String unit : units) {
            while (!current.isEmpty() && exceedsChunkBudget(current, unit)) {
                appendSegment(output, StringUtils.join(current, "\n\n"), sectionPath);
                current = overlapTail(current);
                while (!current.isEmpty() && exceedsChunkBudget(current, unit)) {
                    current.remove(0);
                }
            }
            current.add(unit);
        }
        if (!current.isEmpty()) {
            appendSegment(output, StringUtils.join(current, "\n\n"), sectionPath);
        }
    }

    /**
     * Absorbs a tiny trailing unit when it still fits its preceding semantic section.
     */
    private void appendSegment(List<Segment> output, String content, String sectionPath) {
        if (content.length() < minimumChunkChars() && !output.isEmpty()) {
            Segment previous = output.get(output.size() - 1);
            String merged = previous.getContent() + "\n\n" + content;
            if (StringUtils.equals(previous.getSectionPath(), sectionPath)
                    && !exceedsChunkBudget(Collections.singletonList(previous.getContent()), content)) {
                output.set(output.size() - 1, new Segment(merged, sectionPath));
                return;
            }
        }
        output.add(new Segment(content, sectionPath));
    }

    /**
     * 处理minimumChunkChars。
     */
    private int minimumChunkChars() {
        return Math.min(180, Math.max(32, maxChars / 4));
    }

    /**
     * 处理overlapTail。
     */
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

    /**
     * 处理joinedLength。
     */
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

    /**
     * 处理exceedsChunkBudget。
     */
    private boolean exceedsChunkBudget(List<String> current, String next) {
        return joinedLength(current) + 2 + next.length() > maxChars
                || estimateTokens(StringUtils.join(current, "\n\n") + "\n\n" + next) > maxTokens;
    }

    /**
     * 处理joinHeadings。
     */
    private String joinHeadings(String[] headings) {
        List<String> path = new ArrayList<>();
        for (String heading : headings) {
            if (StringUtils.isNotBlank(heading)) {
                path.add(heading);
            }
        }
        return path.isEmpty() ? "ROOT" : StringUtils.join(path, " > ");
    }

    /**
     * 判断是否为Cjk。
     */
    private boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    /**
     * 表示Segment。
     */
    public static class Segment {
        private final String content;
        private final String sectionPath;

        /**
         * 创建 {@code Segment} 实例。
         */
        Segment(String content, String sectionPath) {
            this.content = content;
            this.sectionPath = sectionPath;
        }

        /**
         * 获取Content。
         */
        public String getContent() {
            return content;
        }

        /**
         * 获取SectionPath。
         */
        public String getSectionPath() {
            return sectionPath;
        }
    }
}
