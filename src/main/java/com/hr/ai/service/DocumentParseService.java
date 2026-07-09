package com.hr.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Service
public class DocumentParseService {

    public ParsedDocument parse(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String extension = getExtension(filename);
        String text = switch (extension) {
            case "pdf" -> parsePdf(file.getInputStream());
            case "docx" -> parseDocx(file.getInputStream());
            case "doc" -> parseDoc(file.getInputStream());
            default -> throw new IllegalArgumentException("不支持的文件格式: ." + extension + "，仅支持 PDF、Word(.doc/.docx)");
        };

        text = normalizeText(text);
        if (text.isBlank()) {
            throw new IllegalArgumentException("未能从文件中提取到文本内容，请确认文件未加密且包含可识别文字");
        }

        String title = filename.replaceAll("\\.[^.]+$", "");
        return new ParsedDocument(title, text, filename);
    }

    private String parsePdf(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String parseDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String parseDoc(InputStream inputStream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String normalizeText(String text) {
        return text.replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .replaceAll("[ \\t\\f\\u000B]+", " ")
                .trim();
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("无法识别文件扩展名: " + filename);
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    public record ParsedDocument(String title, String content, String sourceFile) {}
}
