package com.hr.ai.model.entity.biz;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "biz_performance")
public class BizPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "perf_year")
    private Integer year;

    private String quarter;

    private String rating;

    private Double score;

    @Column(name = "promotion_eligible")
    private Boolean promotionEligible;
}
