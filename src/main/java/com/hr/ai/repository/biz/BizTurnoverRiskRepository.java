package com.hr.ai.repository.biz;

import com.hr.ai.model.entity.biz.BizTurnoverRisk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BizTurnoverRiskRepository extends JpaRepository<BizTurnoverRisk, Long> {

    List<BizTurnoverRisk> findByRiskLevelInOrderByRiskScoreDesc(List<String> riskLevels);

    @Query("""
            SELECT r FROM BizTurnoverRisk r
            JOIN BizEmployee e ON r.employeeId = e.employeeId
            WHERE e.deptId = :deptId AND r.riskLevel IN :levels
            ORDER BY r.riskScore DESC
            """)
    List<BizTurnoverRisk> findByDeptAndRiskLevels(@Param("deptId") String deptId,
                                                   @Param("levels") List<String> levels);

    long countByRiskLevelIn(List<String> riskLevels);
}
