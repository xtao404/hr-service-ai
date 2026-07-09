package com.hr.ai.controller;

import com.hr.ai.dto.ActionTaskRequest;
import com.hr.ai.dto.ActionTaskResponse;
import com.hr.ai.dto.ApiResponse;
import com.hr.ai.service.ActionTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class ActionController {

    private final ActionTaskService actionTaskService;

    @PostMapping("/tasks")
    public ApiResponse<ActionTaskResponse> createTask(@Valid @RequestBody ActionTaskRequest request) {
        return ApiResponse.success(actionTaskService.createTask(request));
    }
}
