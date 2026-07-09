package com.hr.ai.repository.biz;

import com.hr.ai.model.entity.biz.BizAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BizAttendanceRepository extends JpaRepository<BizAttendance, Long> {

    Optional<BizAttendance> findByEmployeeIdAndQuarter(String employeeId, String quarter);

    List<BizAttendance> findByQuarter(String quarter);

    @Query("""
            SELECT COALESCE(SUM(a.overtimeHours), 0) FROM BizAttendance a
            JOIN BizEmployee e ON a.employeeId = e.employeeId
            WHERE e.deptId = :deptId AND a.quarter = :quarter
            """)
    Double sumOvertimeByDeptAndQuarter(@Param("deptId") String deptId, @Param("quarter") String quarter);

    @Query("""
            SELECT COALESCE(AVG(a.overtimeHours), 0) FROM BizAttendance a
            JOIN BizEmployee e ON a.employeeId = e.employeeId
            WHERE e.deptId = :deptId AND a.quarter = :quarter
            """)
    Double avgOvertimeByDeptAndQuarter(@Param("deptId") String deptId, @Param("quarter") String quarter);

    @Query("""
            SELECT COALESCE(SUM(a.lateCount), 0) FROM BizAttendance a
            JOIN BizEmployee e ON a.employeeId = e.employeeId
            WHERE e.deptId = :deptId AND a.quarter = :quarter
            """)
    Long sumLateCountByDeptAndQuarter(@Param("deptId") String deptId, @Param("quarter") String quarter);
}
