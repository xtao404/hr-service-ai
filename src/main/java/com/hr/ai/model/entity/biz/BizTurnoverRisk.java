package com.hr.ai.model.entity.biz;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "biz_turnover_risk")
public class BizTurnoverRisk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", unique = true, nullable = false)
    private String employeeId;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(columnDefinition = "TEXT")
    private String factors;

    @Column(columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "predicted_at")
    private LocalDateTime predictedAt;
}
