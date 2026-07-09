package com.hr.ai.dto;

import com.hr.ai.model.enums.KnowledgeCategory;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgeUploadResponse {
    private String sourceFile;
    private String title;
    private KnowledgeCategory category;
    private Integer chunkCount;
    private Integer totalCharacters;
    private List<KnowledgeDocumentResponse> documents;
}
