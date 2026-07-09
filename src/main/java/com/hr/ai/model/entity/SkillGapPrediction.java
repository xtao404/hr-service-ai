package com.hr.ai.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "skill_gap_predictions")
public class SkillGapPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String positionName;

    private String departmentId;

    private String requiredSkill;

    private Integer currentSupply;

    private Integer projectedDemand;

    private Integer gapCount;

    private Integer priority;

    @Column(columnDefinition = "LONGTEXT")
    private String recommendation;

    private LocalDateTime predictedAt = LocalDateTime.now();
}
