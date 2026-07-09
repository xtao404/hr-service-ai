package com.hr.ai.service;

import com.hr.ai.dto.*;
import com.hr.ai.model.entity.RecruitmentInsight;
import com.hr.ai.model.entity.SkillGapPrediction;
import com.hr.ai.model.entity.TurnoverPrediction;
import com.hr.ai.model.enums.RiskLevel;
import com.hr.ai.repository.RecruitmentInsightRepository;
import com.hr.ai.repository.SkillGapPredictionRepository;
import com.hr.ai.repository.TurnoverPredictionRepository;
import com.hr.ai.security.PermissionService;
import com.hr.ai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 预测分析服务 - 生产环境对接 XGBoost / 随机森林等 ML 模型
 */
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final TurnoverPredictionRepository turnoverRepository;
    private final SkillGapPredictionRepository skillGapRepository;
    private final RecruitmentInsightRepository recruitmentRepository;
    private final PermissionService permissionService;

    public AnalyticsDashboardResponse getDashboard() {
        UserPrincipal user = permissionService.currentUser();
        List<TurnoverPrediction> risks = filterTurnoverByPermission(
                turnoverRepository.findByRiskLevelInOrderByRiskScoreDesc(
                        List.of(RiskLevel.HIGH, RiskLevel.CRITICAL)));
        List<SkillGapPrediction> gaps = filterSkillGapsByPermission(
                skillGapRepository.findAllByOrderByPriorityDesc());

        List<RecruitmentInsight> insights = recruitmentRepository.findAll();
        double avgSuccess = insights.stream()
                .mapToDouble(RecruitmentInsight::getSuccessRate)
                .average().orElse(0.0);

        AnalyticsDashboardResponse dashboard = new AnalyticsDashboardResponse();
        dashboard.setHighRiskEmployeeCount(risks.size());
        dashboard.setSkillGapCount(gaps.size());
        dashboard.setAvgRecruitmentSuccessRate(Math.round(avgSuccess * 100) / 100.0);
        dashboard.setTopRisks(risks.stream().limit(5).map(this::toTurnoverResponse).collect(Collectors.toList()));
        dashboard.setCriticalGaps(gaps.stream().limit(5).map(this::toSkillGapResponse).collect(Collectors.toList()));
        return dashboard;
    }

    public List<TurnoverPredictionResponse> getTurnoverPredictions(String departmentId) {
        if (departmentId != null) {
            permissionService.checkDepartmentAccess(departmentId);
            return turnoverRepository.findByDepartmentId(departmentId).stream()
                    .map(this::toTurnoverResponse).collect(Collectors.toList());
        }
        return filterTurnoverByPermission(
                turnoverRepository.findByRiskLevelInOrderByRiskScoreDesc(
                        List.of(RiskLevel.LOW, RiskLevel.MEDIUM, RiskLevel.HIGH, RiskLevel.CRITICAL)))
                .stream().map(this::toTurnoverResponse).collect(Collectors.toList());
    }

    public List<SkillGapResponse> getSkillGaps(String departmentId) {
        List<SkillGapPrediction> gaps = departmentId != null
                ? skillGapRepository.findByDepartmentIdOrderByPriorityDesc(departmentId)
                : skillGapRepository.findAllByOrderByPriorityDesc();
        if (departmentId != null) {
            permissionService.checkDepartmentAccess(departmentId);
        }
        return filterSkillGapsByPermission(gaps).stream()
                .map(this::toSkillGapResponse).collect(Collectors.toList());
    }

    public List<RecruitmentInsightResponse> getRecruitmentInsights() {
        return recruitmentRepository.findAll().stream()
                .map(this::toRecruitmentResponse).collect(Collectors.toList());
    }

    private List<TurnoverPrediction> filterTurnoverByPermission(List<TurnoverPrediction> predictions) {
        UserPrincipal user = permissionService.currentUser();
        return switch (user.getRole()) {
            case HR_ADMIN, HRBP -> predictions;
            case MANAGER -> predictions.stream()
                    .filter(p -> user.getDepartmentId().equals(p.getDepartmentId()))
                    .collect(Collectors.toList());
            default -> List.of();
        };
    }

    private List<SkillGapPrediction> filterSkillGapsByPermission(List<SkillGapPrediction> gaps) {
        UserPrincipal user = permissionService.currentUser();
        return switch (user.getRole()) {
            case HR_ADMIN, HRBP -> gaps;
            case MANAGER -> gaps.stream()
                    .filter(g -> user.getDepartmentId().equals(g.getDepartmentId()))
                    .collect(Collectors.toList());
            default -> List.of();
        };
    }

    private TurnoverPredictionResponse toTurnoverResponse(TurnoverPrediction p) {
        TurnoverPredictionResponse r = new TurnoverPredictionResponse();
        r.setEmployeeId(p.getEmployeeId());
        r.setEmployeeName(p.getEmployeeName());
        r.setDepartmentName(p.getDepartmentName());
        r.setRiskScore(p.getRiskScore());
        r.setRiskLevel(p.getRiskLevel());
        r.setRecommendation(p.getRecommendation());
        if (p.getFactors() != null) {
            r.setFactors(Arrays.asList(p.getFactors().split(";")));
        }
        return r;
    }

    private SkillGapResponse toSkillGapResponse(SkillGapPrediction g) {
        SkillGapResponse r = new SkillGapResponse();
        r.setPositionName(g.getPositionName());
        r.setRequiredSkill(g.getRequiredSkill());
        r.setCurrentSupply(g.getCurrentSupply());
        r.setProjectedDemand(g.getProjectedDemand());
        r.setGapCount(g.getGapCount());
        r.setPriority(g.getPriority());
        r.setRecommendation(g.getRecommendation());
        return r;
    }

    private RecruitmentInsightResponse toRecruitmentResponse(RecruitmentInsight i) {
        RecruitmentInsightResponse r = new RecruitmentInsightResponse();
        r.setPositionName(i.getPositionName());
        r.setSuccessRate(i.getSuccessRate());
        r.setSampleSize(i.getSampleSize());
        if (i.getSuccessTraits() != null) {
            r.setSuccessTraits(Arrays.asList(i.getSuccessTraits().split(";")));
        }
        if (i.getOptimizationSuggestions() != null) {
            r.setOptimizationSuggestions(Arrays.asList(i.getOptimizationSuggestions().split(";")));
        }
        return r;
    }
}
