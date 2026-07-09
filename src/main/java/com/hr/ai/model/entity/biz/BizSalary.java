package com.hr.ai.model.entity.biz;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "biz_salary")
public class BizSalary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", unique = true, nullable = false)
    private String employeeId;

    @Column(name = "salary_band")
    private String salaryBand;

    @Column(name = "base_salary")
    private Double baseSalary;

    @Column(name = "last_adjust_date")
    private LocalDate lastAdjustDate;
}
