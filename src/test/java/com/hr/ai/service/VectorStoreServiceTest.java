package com.hr.ai.service;

import com.hr.ai.config.RagProperties;
import com.hr.ai.model.entity.KnowledgeDocument;
import com.hr.ai.model.enums.KnowledgeCategory;
import com.hr.ai.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorStoreServiceTest {

    @Test
    void search_disabledByDefault_returnsEmptyWithoutLoadingDocs() {
        RagProperties ragProperties = new RagProperties();
        KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);

        VectorStoreService service = new VectorStoreService(repository, ragProperties);

        assertFalse(service.isSearchEnabled());
        assertTrue(service.search("年假有多少天").isEmpty());
        verify(repository, never()).findAll();
    }

    @Test
    void search_enabled_returnsMatches() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(true);

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle("年假管理制度");
        doc.setContent("员工年假按工龄计算，最少5天。");
        doc.setCategory(KnowledgeCategory.LEAVE);

        KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
        when(repository.findAll()).thenReturn(List.of(doc));

        VectorStoreService service = new VectorStoreService(repository, ragProperties);

        assertTrue(service.isSearchEnabled());
        assertFalse(service.search("年假有多少天").isEmpty());
    }
}
