package com.hr.ai.model.entity;

import com.hr.ai.model.enums.KnowledgeCategory;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    private KnowledgeCategory category;

    @Column(columnDefinition = "LONGTEXT")
    private String embedding;

    private String sourceFile;

    private LocalDateTime updatedAt = LocalDateTime.now();
}
