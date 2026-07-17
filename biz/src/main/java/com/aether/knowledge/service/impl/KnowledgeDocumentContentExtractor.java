package com.aether.knowledge.service.impl;

import com.aether.exception.ServerException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Component
public class KnowledgeDocumentContentExtractor {
    public String extract(String fileName, byte[] bytes) {
        String name = fileName == null ? "" : fileName.toLowerCase();
        try {
            if (name.endsWith(".txt") || name.endsWith(".md")) return new String(bytes, StandardCharsets.UTF_8);
            if (name.endsWith(".pdf")) {
                try (PDDocument pdf = PDDocument.load(bytes)) { return new PDFTextStripper().getText(pdf); }
            }
            if (name.endsWith(".docx")) {
                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes)); XWPFWordExtractor extractor = new XWPFWordExtractor(document)) { return extractor.getText(); }
            }
        } catch (Exception e) { throw new ServerException(422, "failed to parse knowledge document: " + e.getMessage()); }
        throw new ServerException(422, "only txt, md, pdf and docx are supported");
    }
}
