package com.hr.ai.model.entity.biz;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "biz_department")
public class BizDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dept_id", unique = true, nullable = false)
    private String deptId;

    @Column(name = "dept_name", nullable = false)
    private String deptName;

    @Column(name = "manager_id")
    private String managerId;

    private Integer headcount;
}
