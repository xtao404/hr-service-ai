package com.hr.ai.repository;

import com.hr.ai.model.entity.SkillGapPrediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillGapPredictionRepository extends JpaRepository<SkillGapPrediction, Long> {

    List<SkillGapPrediction> findByDepartmentIdOrderByPriorityDesc(String departmentId);

    List<SkillGapPrediction> findAllByOrderByPriorityDesc();
}
