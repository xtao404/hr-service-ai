package com.hr.ai.repository.biz;

import com.hr.ai.model.entity.biz.BizDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BizDepartmentRepository extends JpaRepository<BizDepartment, Long> {

    Optional<BizDepartment> findByDeptId(String deptId);
}
