package com.hr.ai.repository.biz;

import com.hr.ai.model.entity.biz.BizEmployee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BizEmployeeRepository extends JpaRepository<BizEmployee, Long> {

    Optional<BizEmployee> findByEmployeeId(String employeeId);

    List<BizEmployee> findByName(String name);

    List<BizEmployee> findByDeptIdAndStatus(String deptId, String status);

    long countByDeptIdAndStatus(String deptId, String status);

    long countByStatus(String status);
}
