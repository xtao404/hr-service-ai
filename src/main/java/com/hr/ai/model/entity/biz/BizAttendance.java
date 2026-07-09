package com.hr.ai.model.entity.biz;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "biz_attendance")
public class BizAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    private String quarter;

    @Column(name = "leave_balance")
    private Double leaveBalance;

    @Column(name = "overtime_hours")
    private Double overtimeHours;

    @Column(name = "late_count")
    private Integer lateCount;

    @Column(name = "absent_days")
    private Double absentDays;
}
