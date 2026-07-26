package com.aether.knowledge.service.impl;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.client.DoclingClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Component
public class KnowledgeDocumentContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentContentExtractor.class);

    private final DoclingClient doclingClient;

    public KnowledgeDocumentContentExtractor(DoclingClient doclingClient) {
        this.doclingClient = doclingClient;
    }

    public String extract(String fileName, byte[] bytes) {
        String name = fileName == null ? "" : fileName.toLowerCase();
        try {
            if (name.endsWith(".txt") || name.endsWith(".md")) {
                return new String(bytes, StandardCharsets.UTF_8);
            }

            String doclingResult = tryDocling(name, bytes);
            if (doclingResult != null) return doclingResult;

            if (name.endsWith(".pdf")) return extractPdf(bytes);
            if (name.endsWith(".docx")) return extractDocx(bytes);
            if (name.endsWith(".xlsx")) {
                if (!doclingClient.isEnabled()) {
                    throw new ServerException(422, I18nUtils.getMessage("knowledge.document.file.docling-unavailable"));
                }
                return extractXlsx(bytes);
            }
        } catch (ServerException e) {
            throw e;
        } catch (Exception e) {
            throw new ServerException(422, I18nUtils.getMessage("knowledge.document.parse.failed"));
        }
        throw new ServerException(422, I18nUtils.getMessage("knowledge.document.file-type.unsupported"));
    }

    private String tryDocling(String name, byte[] bytes) {
        if (!doclingClient.isEnabled()) return null;
        String result = doclingClient.convert(name, bytes);
        if (result != null) return result;
        log.warn("Docling returned no result for {}, falling back to native extractor", name);
        return null;
    }

    private String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument pdf = PDDocument.load(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(pdf);
        }
    }

    private String extractDocx(byte[] bytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String text = p.getText();
                if (StringUtils.isBlank(text)) continue;
                int level = resolveHeadingLevel(doc, p.getStyle());
                if (level > 0) {
                    for (int i = 0; i < level && i < 6; i++) sb.append('#');
                    sb.append(' ').append(text.trim()).append("\n\n");
                } else {
                    sb.append(text.trim()).append("\n\n");
                }
            }
            for (XWPFTable t : doc.getTables()) {
                sb.append(renderDocxTable(t)).append("\n\n");
            }
            return sb.toString().trim();
        }
    }

    private String extractXlsx(byte[] bytes) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(i);
                if (sheet.getPhysicalNumberOfRows() == 0) continue;
                sb.append("# ").append(sheet.getSheetName()).append("\n\n");
                boolean headerRow = true;
                for (org.apache.poi.ss.usermodel.Row row : sheet) {
                    if (isEmptyRow(row)) continue;
                    sb.append("|");
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        org.apache.poi.ss.usermodel.Cell cell = row.getCell(c);
                        String val = cell == null ? "" : cell.toString().replace("\n", " ").trim();
                        sb.append(" ").append(val).append(" |");
                    }
                    sb.append("\n");
                    if (headerRow) {
                        sb.append("|");
                        int colCount = row.getLastCellNum();
                        for (int c = 0; c < colCount; c++) {
                            sb.append(" --- |");
                        }
                        sb.append("\n");
                        headerRow = false;
                    }
                }
                sb.append("\n\n");
            }
            return sb.toString().trim();
        }
    }

    private int resolveHeadingLevel(XWPFDocument doc, String styleId) {
        if (styleId == null) return 0;
        String lower = styleId.toLowerCase();
        if (lower.startsWith("heading")) {
            String num = lower.replaceAll("\\D+", "");
            if (!num.isEmpty()) return Integer.parseInt(num);
        }
        if (lower.startsWith("标题")) {
            String num = lower.replaceAll("\\D+", "");
            if (!num.isEmpty()) return Integer.parseInt(num);
        }
        try {
            XWPFStyle style = doc.getStyles().getStyle(styleId);
            if (style != null && style.getName() != null) {
                String name = style.getName().toLowerCase();
                if (name.startsWith("heading") || name.startsWith("标题")) {
                    String num = name.replaceAll("\\D+", "");
                    if (!num.isEmpty()) return Integer.parseInt(num);
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private String renderDocxTable(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        boolean headerRow = true;
        for (XWPFTableRow row : table.getRows()) {
            sb.append("|");
            for (XWPFTableCell cell : row.getTableCells()) {
                String val = cell.getText().replace("\n", " ").trim();
                sb.append(" ").append(val).append(" |");
            }
            sb.append("\n");
            if (headerRow) {
                sb.append("|");
                for (int i = 0; i < row.getTableCells().size(); i++) {
                    sb.append(" --- |");
                }
                sb.append("\n");
                headerRow = false;
            }
        }
        return sb.toString();
    }

    private boolean isEmptyRow(org.apache.poi.ss.usermodel.Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            org.apache.poi.ss.usermodel.Cell cell = row.getCell(c);
            if (cell != null && StringUtils.isNotBlank(cell.toString())) return false;
        }
        return true;
    }
}
