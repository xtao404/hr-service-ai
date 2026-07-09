package com.hr.ai.repository;

import com.hr.ai.model.entity.KnowledgeDocument;
import com.hr.ai.model.enums.KnowledgeCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByCategory(KnowledgeCategory category);
}
