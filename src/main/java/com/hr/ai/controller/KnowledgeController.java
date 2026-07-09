package com.hr.ai.controller;

import com.hr.ai.dto.*;
import com.hr.ai.model.enums.KnowledgeCategory;
import com.hr.ai.service.KnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @GetMapping
    public ApiResponse<List<KnowledgeDocumentResponse>> list() {
        return ApiResponse.success(knowledgeService.listAll());
    }

    @PostMapping
    public ApiResponse<KnowledgeDocumentResponse> create(@Valid @RequestBody KnowledgeDocumentRequest request) {
        return ApiResponse.success(knowledgeService.create(request));
    }

    @PostMapping("/upload")
    public ApiResponse<KnowledgeUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", required = false) KnowledgeCategory category) throws IOException {
        return ApiResponse.success(knowledgeService.uploadFile(file, category));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ApiResponse.success(null);
    }
}
