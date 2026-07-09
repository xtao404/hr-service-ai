package com.hr.ai.model.entity.biz;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "biz_employee")
public class BizEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", unique = true, nullable = false)
    private String employeeId;

    private String name;

    private String gender;

    @Column(name = "dept_id", nullable = false)
    private String deptId;

    private String position;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    private String status;

    private String education;

    @Column(name = "satisfaction_score")
    private Double satisfactionScore;
}
