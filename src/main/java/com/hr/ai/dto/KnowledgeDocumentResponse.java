package com.hr.ai.dto;

import com.hr.ai.model.enums.KnowledgeCategory;
import lombok.Data;

@Data
public class KnowledgeDocumentResponse {
    private Long id;
    private String title;
    private String content;
    private KnowledgeCategory category;
    private String sourceFile;
    private String updatedAt;
}
