package com.hr.ai.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "recruitment_insights")
public class RecruitmentInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String positionName;

    @Column(columnDefinition = "LONGTEXT")
    private String successTraits;

    @Column(columnDefinition = "LONGTEXT")
    private String optimizationSuggestions;

    private Double successRate;

    private Integer sampleSize;

    private LocalDateTime analyzedAt = LocalDateTime.now();
}
