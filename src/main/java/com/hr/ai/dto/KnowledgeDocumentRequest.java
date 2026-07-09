package com.hr.ai.dto;

import com.hr.ai.model.enums.KnowledgeCategory;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeDocumentRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    private KnowledgeCategory category;

    private String sourceFile;
}
