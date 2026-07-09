package com.hr.ai.repository.biz;

import com.hr.ai.model.entity.biz.BizPerformance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BizPerformanceRepository extends JpaRepository<BizPerformance, Long> {

    Optional<BizPerformance> findFirstByEmployeeIdOrderByYearDescQuarterDesc(String employeeId);

    List<BizPerformance> findByEmployeeIdOrderByYearDescQuarterDesc(String employeeId);
}
