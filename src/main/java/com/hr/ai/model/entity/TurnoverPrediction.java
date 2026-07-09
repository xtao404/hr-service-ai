package com.hr.ai.model.entity;

import com.hr.ai.model.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "turnover_predictions")
public class TurnoverPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String employeeId;

    private String employeeName;

    private String departmentId;

    private String departmentName;

    private Double riskScore;

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @Column(columnDefinition = "LONGTEXT")
    private String factors;

    @Column(columnDefinition = "LONGTEXT")
    private String recommendation;

    private LocalDateTime predictedAt = LocalDateTime.now();
}
