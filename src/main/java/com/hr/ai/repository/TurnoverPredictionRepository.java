package com.hr.ai.repository;

import com.hr.ai.model.entity.TurnoverPrediction;
import com.hr.ai.model.enums.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TurnoverPredictionRepository extends JpaRepository<TurnoverPrediction, Long> {

    List<TurnoverPrediction> findByDepartmentId(String departmentId);

    List<TurnoverPrediction> findByRiskLevelInOrderByRiskScoreDesc(List<RiskLevel> riskLevels);
}
