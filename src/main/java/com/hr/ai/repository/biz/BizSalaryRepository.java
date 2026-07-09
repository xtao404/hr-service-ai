package com.hr.ai.repository.biz;

import com.hr.ai.model.entity.biz.BizSalary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BizSalaryRepository extends JpaRepository<BizSalary, Long> {

    Optional<BizSalary> findByEmployeeId(String employeeId);

    @Query("SELECT COALESCE(AVG(s.baseSalary), 0) FROM BizSalary s")
    Double avgBaseSalary();

    @Query("""
            SELECT COALESCE(AVG(s.baseSalary), 0) FROM BizSalary s
            JOIN BizEmployee e ON s.employeeId = e.employeeId
            WHERE e.deptId = :deptId
            """)
    Double avgBaseSalaryByDept(@Param("deptId") String deptId);
}
