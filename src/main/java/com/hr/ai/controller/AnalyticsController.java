package com.hr.ai.controller;

import com.hr.ai.dto.*;
import com.hr.ai.service.HrIntegrationService;
import com.hr.ai.service.PredictionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalyticsController {

    private final PredictionService predictionService;
    private final HrIntegrationService hrIntegrationService;

    @GetMapping("/analytics/dashboard")
    public ApiResponse<AnalyticsDashboardResponse> dashboard() {
        return ApiResponse.success(predictionService.getDashboard());
    }

    @GetMapping("/analytics/turnover")
    public ApiResponse<List<TurnoverPredictionResponse>> turnover(
            @RequestParam(required = false) String departmentId) {
        return ApiResponse.success(predictionService.getTurnoverPredictions(departmentId));
    }

    @GetMapping("/analytics/skill-gaps")
    public ApiResponse<List<SkillGapResponse>> skillGaps(
            @RequestParam(required = false) String departmentId) {
        return ApiResponse.success(predictionService.getSkillGaps(departmentId));
    }

    @GetMapping("/analytics/recruitment")
    public ApiResponse<List<RecruitmentInsightResponse>> recruitment() {
        return ApiResponse.success(predictionService.getRecruitmentInsights());
    }

    @PostMapping("/reports/generate")
    public ApiResponse<ManagerReportResponse> generateReport(@Valid @RequestBody ManagerReportRequest request) {
        return ApiResponse.success(hrIntegrationService.generateReport(
                request.getQuery(), request.getPeriod(), request.getDepartmentId()));
    }
}
