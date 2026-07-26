package com.aether.knowledge.util;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;

public final class MarkdownNormalizer {

    private static final Parser PARSER = Parser.builder().build();

    public static String normalize(String content) {
        if (content == null || content.isEmpty()) return content;
        String cleaned = content.replace("\r\n", "\n").replace('\r', '\n');
        Node document = PARSER.parse(cleaned);
        StringBuilder sb = new StringBuilder(cleaned.length());
        renderChildren(document, sb);
        String result = sb.toString();
        return result.trim() + "\n";
    }

    private static void renderChildren(Node parent, StringBuilder sb) {
        Node child = parent.getFirstChild();
        while (child != null) {
            renderNode(child, sb);
            child = child.getNext();
        }
    }

    private static void renderNode(Node node, StringBuilder sb) {
        if (node instanceof ThematicBreak) {
            sb.append("---\n\n");
            return;
        }

        if (node instanceof Heading) {
            Heading h = (Heading) node;
            for (int i = 0; i < h.getLevel(); i++) sb.append('#');
            sb.append(' ');
            renderChildren(node, sb);
            sb.append('\n');
            if (node.getNext() != null) sb.append('\n');
            return;
        }

        if (node instanceof Paragraph) {
            renderChildren(node, sb);
            sb.append('\n');
            if (node.getNext() != null) sb.append('\n');
            return;
        }

        if (node instanceof FencedCodeBlock) {
            FencedCodeBlock cb = (FencedCodeBlock) node;
            String lang = cb.getInfo().isNotNull() ? cb.getInfo().toString().trim() : "";
            sb.append("```");
            if (!lang.isEmpty()) sb.append(lang);
            sb.append('\n');
            CharSequence chars = cb.getContentChars();
            String code = chars != null ? stripTrailingEmptyLines(chars.toString()) : "";
            sb.append(code);
            if (!code.isEmpty() && !code.endsWith("\n")) sb.append('\n');
            sb.append("```\n\n");
            return;
        }

        if (node instanceof IndentedCodeBlock) {
            IndentedCodeBlock cb = (IndentedCodeBlock) node;
            CharSequence chars = cb.getContentChars();
            String code = chars != null ? stripTrailingEmptyLines(chars.toString()) : "";
            for (String line : code.split("\n", -1)) {
                sb.append("    ");
                sb.append(line);
                sb.append('\n');
            }
            sb.append('\n');
            return;
        }

        if (node instanceof BlockQuote) {
            BlockQuote bq = (BlockQuote) node;
            String inner = extractInner(bq);
            for (String line : inner.split("\n", -1)) {
                sb.append("> ");
                sb.append(line);
                sb.append('\n');
            }
            if (node.getNext() != null) sb.append('\n');
            return;
        }

        if (node instanceof BulletList) {
            renderItems(node, sb, "- ");
            if (node.getNext() != null) sb.append('\n');
            return;
        }

        if (node instanceof OrderedList) {
            renderItems(node, sb, "1. ");
            if (node.getNext() != null) sb.append('\n');
            return;
        }

        if (node instanceof ListItem) {
            renderChildren(node, sb);
            return;
        }

        if (node instanceof Emphasis) {
            sb.append('*');
            renderChildren(node, sb);
            sb.append('*');
            return;
        }

        if (node instanceof StrongEmphasis) {
            sb.append("**");
            renderChildren(node, sb);
            sb.append("**");
            return;
        }

        if (node instanceof Code) {
            sb.append('`');
            CharSequence chars = node.getChars();
            if (chars != null) sb.append(chars);
            sb.append('`');
            return;
        }

        if (node instanceof SoftLineBreak) {
            sb.append('\n');
            return;
        }

        if (node instanceof HardLineBreak) {
            sb.append("  \n");
            return;
        }

        if (node instanceof Link) {
            Link link = (Link) node;
            sb.append('[');
            renderChildren(node, sb);
            sb.append(']');
            sb.append('(');
            String url = link.getUrl().isNotNull() ? link.getUrl().toString() : "";
            sb.append(url);
            String title = link.getTitle().isNotNull() ? link.getTitle().toString() : "";
            if (!title.isEmpty()) sb.append(" \"").append(title).append('"');
            sb.append(')');
            return;
        }

        if (node instanceof Image) {
            Image img = (Image) node;
            sb.append("![");
            renderChildren(node, sb);
            sb.append(']');
            sb.append('(');
            String url = img.getUrl().isNotNull() ? img.getUrl().toString() : "";
            sb.append(url);
            String title = img.getTitle().isNotNull() ? img.getTitle().toString() : "";
            if (!title.isEmpty()) sb.append(" \"").append(title).append('"');
            sb.append(')');
            return;
        }

        if (node instanceof HtmlBlock || node instanceof HtmlInline) {
            CharSequence chars = node.getChars();
            if (chars != null) sb.append(chars);
            if (node instanceof HtmlBlock) sb.append('\n');
            return;
        }

        if (node instanceof Text || node instanceof TextBase) {
            CharSequence chars = node.getChars();
            if (chars != null) sb.append(chars);
            return;
        }

        renderChildren(node, sb);
    }

    private static void renderItems(Node list, StringBuilder sb, String marker) {
        Node child = list.getFirstChild();
        boolean first = true;
        while (child != null) {
            if (child instanceof ListItem) {
                if (!first) sb.append('\n');
                first = false;
                sb.append(marker);
                Node itemChild = child.getFirstChild();
                if (itemChild != null) {
                    renderNode(itemChild, sb);
                    Node sib = itemChild.getNext();
                    while (sib != null) {
                        sb.append('\n').append("  ");
                        renderNode(sib, sb);
                        sib = sib.getNext();
                    }
                }
            }
            child = child.getNext();
        }
        sb.append('\n');
    }

    private static String extractInner(BlockQuote bq) {
        StringBuilder inner = new StringBuilder();
        Node child = bq.getFirstChild();
        while (child != null) {
            StringBuilder chunk = new StringBuilder();
            renderNode(child, chunk);
            inner.append(chunk.toString().trim());
            child = child.getNext();
            if (child != null) inner.append('\n');
        }
        return inner.toString();
    }

    private static String stripTrailingEmptyLines(String s) {
        int end = s.length();
        while (end > 0) {
            char c = s.charAt(end - 1);
            if (c != '\n' && c != '\r') break;
            end--;
        }
        return s.substring(0, end);
    }

    private MarkdownNormalizer() {}
}
