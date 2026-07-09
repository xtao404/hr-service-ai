package com.hr.ai.service;

import com.hr.ai.config.KnowledgeProperties;
import com.hr.ai.dto.KnowledgeDocumentRequest;
import com.hr.ai.dto.KnowledgeDocumentResponse;
import com.hr.ai.dto.KnowledgeUploadResponse;
import com.hr.ai.model.entity.KnowledgeDocument;
import com.hr.ai.model.enums.KnowledgeCategory;
import com.hr.ai.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeDocumentRepository documentRepository;
    private final VectorStoreService vectorStoreService;
    private final DocumentParseService documentParseService;
    private final DocumentChunkService documentChunkService;
    private final KnowledgeProperties knowledgeProperties;

    public List<KnowledgeDocumentResponse> listAll() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return documentRepository.findAll().stream().map(doc -> toResponse(doc, fmt)).collect(Collectors.toList());
    }

    public KnowledgeDocumentResponse create(KnowledgeDocumentRequest request) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle(request.getTitle());
        doc.setContent(request.getContent());
        doc.setCategory(request.getCategory() != null ? request.getCategory() : KnowledgeCategory.GENERAL);
        doc.setSourceFile(request.getSourceFile());
        vectorStoreService.indexDocument(doc);
        return toBriefResponse(doc);
    }

    public KnowledgeUploadResponse uploadFile(MultipartFile file, KnowledgeCategory category) throws IOException {
        validateFile(file);

        DocumentParseService.ParsedDocument parsed = documentParseService.parse(file);
        KnowledgeCategory resolvedCategory = category != null ? category : inferCategory(parsed.title(), parsed.content());
        List<String> chunks = documentChunkService.chunk(parsed.content());

        List<KnowledgeDocumentResponse> savedDocs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setTitle(buildChunkTitle(parsed.title(), i, chunks.size()));
            doc.setContent(chunks.get(i));
            doc.setCategory(resolvedCategory);
            doc.setSourceFile(parsed.sourceFile());
            vectorStoreService.indexDocument(doc);
            savedDocs.add(toBriefResponse(doc));
        }

        KnowledgeUploadResponse response = new KnowledgeUploadResponse();
        response.setSourceFile(parsed.sourceFile());
        response.setTitle(parsed.title());
        response.setCategory(resolvedCategory);
        response.setChunkCount(chunks.size());
        response.setTotalCharacters(parsed.content().length());
        response.setDocuments(savedDocs);
        return response;
    }

    public void delete(Long id) {
        documentRepository.deleteById(id);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("文件名无效");
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!knowledgeProperties.allowedExtensionSet().contains(extension)) {
            throw new IllegalArgumentException("不支持的文件格式 ." + extension + "，仅支持: " + knowledgeProperties.getAllowedExtensions());
        }
    }

    private String buildChunkTitle(String baseTitle, int index, int total) {
        if (total <= 1) {
            return baseTitle;
        }
        return baseTitle + "（第" + (index + 1) + "/" + total + "部分）";
    }

    private KnowledgeCategory inferCategory(String title, String content) {
        String text = (title + " " + content).toLowerCase(Locale.ROOT);
        if (containsAny(text, "考勤", "迟到", "加班", "旷工")) {
            return KnowledgeCategory.ATTENDANCE;
        }
        if (containsAny(text, "年假", "假期", "请假", "病假", "事假")) {
            return KnowledgeCategory.LEAVE;
        }
        if (containsAny(text, "薪酬", "工资", "薪资", "调薪", "奖金")) {
            return KnowledgeCategory.SALARY_POLICY;
        }
        if (containsAny(text, "福利", "五险一金", "公积金", "体检", "保险")) {
            return KnowledgeCategory.BENEFITS;
        }
        if (containsAny(text, "入职", "报到", "offer")) {
            return KnowledgeCategory.ONBOARDING;
        }
        if (containsAny(text, "离职", "辞职", "交接")) {
            return KnowledgeCategory.OFFBOARDING;
        }
        return KnowledgeCategory.GENERAL;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private KnowledgeDocumentResponse toBriefResponse(KnowledgeDocument doc) {
        KnowledgeDocumentResponse r = new KnowledgeDocumentResponse();
        r.setId(doc.getId());
        r.setTitle(doc.getTitle());
        r.setCategory(doc.getCategory());
        r.setSourceFile(doc.getSourceFile());
        return r;
    }

    private KnowledgeDocumentResponse toResponse(KnowledgeDocument doc, DateTimeFormatter fmt) {
        KnowledgeDocumentResponse r = toBriefResponse(doc);
        r.setContent(doc.getContent());
        r.setUpdatedAt(doc.getUpdatedAt().format(fmt));
        return r;
    }
}
