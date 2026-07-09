package com.hr.ai.config;

import com.hr.ai.model.entity.biz.*;
import com.hr.ai.repository.biz.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 初始化 HR 业务表（biz_*）演示数据。
 * 若 biz_employee 为空则自动加载；已导入 hr_test_data.sql 时跳过。
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class BizDataInitializer implements CommandLineRunner {

    private final BizDepartmentRepository departmentRepository;
    private final BizEmployeeRepository employeeRepository;
    private final BizAttendanceRepository attendanceRepository;
    private final BizSalaryRepository salaryRepository;
    private final BizPerformanceRepository performanceRepository;
    private final BizTurnoverRiskRepository turnoverRiskRepository;

    @Override
    public void run(String... args) {
        if (employeeRepository.count() > 0) {
            return;
        }
        initDepartments();
        initEmployees();
        initAttendance();
        initSalary();
        initPerformance();
        initTurnoverRisk();
    }

    private void initDepartments() {
        saveDept("D001", "技术研发部", "E002", 8);
        saveDept("D002", "市场营销部", "E201", 5);
        saveDept("D003", "产品设计部", "E301", 4);
        saveDept("D000", "人力资源部", "E003", 3);
    }

    private void saveDept(String deptId, String name, String managerId, int headcount) {
        BizDepartment d = new BizDepartment();
        d.setDeptId(deptId);
        d.setDeptName(name);
        d.setManagerId(managerId);
        d.setHeadcount(headcount);
        departmentRepository.save(d);
    }

    private void initEmployees() {
        saveEmp("E000", "系统管理员", "男", "D000", "HR管理员", "2016-01-01", "本科", 8.5);
        saveEmp("E001", "张三", "男", "D001", "高级工程师", "2021-03-15", "本科", 7.5);
        saveEmp("E002", "李四", "男", "D001", "技术经理", "2018-06-01", "硕士", 8.2);
        saveEmp("E003", "王五", "女", "D000", "HRBP", "2019-09-10", "硕士", 8.0);
        saveEmp("E101", "赵六", "男", "D001", "高级Java工程师", "2020-01-20", "本科", 5.8);
        saveEmp("E102", "钱七", "女", "D001", "前端工程师", "2021-07-01", "本科", 6.2);
        saveEmp("E103", "周八", "男", "D001", "测试工程师", "2022-04-18", "本科", 7.8);
        saveEmp("E104", "吴九", "女", "D001", "DevOps工程师", "2019-11-05", "硕士", 8.5);
        saveEmp("E105", "郑十", "男", "D001", "初级Java工程师", "2023-08-01", "本科", 7.0);
        saveEmp("E201", "孙八", "女", "D002", "市场总监", "2017-05-20", "硕士", 6.5);
        saveEmp("E202", "冯十一", "男", "D002", "品牌经理", "2020-09-15", "本科", 7.2);
        saveEmp("E203", "陈十二", "女", "D002", "市场专员", "2022-02-28", "本科", 6.8);
        saveEmp("E204", "褚十三", "男", "D002", "渠道经理", "2021-12-01", "本科", 5.5);
        saveEmp("E205", "卫十四", "女", "D002", "内容运营", "2023-03-10", "本科", 7.5);
        saveEmp("E301", "蒋十五", "男", "D003", "产品总监", "2018-08-15", "硕士", 8.0);
        saveEmp("E302", "沈十六", "女", "D003", "高级产品经理", "2020-06-01", "本科", 7.6);
        saveEmp("E303", "韩十七", "男", "D003", "UI设计师", "2021-10-20", "本科", 7.3);
        saveEmp("E304", "杨十八", "女", "D003", "产品助理", "2024-01-08", "本科", 8.1);
    }

    private void saveEmp(String id, String name, String gender, String deptId,
                         String position, String hireDate, String education, double satisfaction) {
        BizEmployee e = new BizEmployee();
        e.setEmployeeId(id);
        e.setName(name);
        e.setGender(gender);
        e.setDeptId(deptId);
        e.setPosition(position);
        e.setHireDate(LocalDate.parse(hireDate));
        e.setStatus("ACTIVE");
        e.setEducation(education);
        e.setSatisfactionScore(satisfaction);
        employeeRepository.save(e);
    }

    private void initAttendance() {
        saveAtt("E000", 10.0, 12.0, 0, 0);
        saveAtt("E001", 8.5, 42.0, 1, 0);
        saveAtt("E002", 12.0, 28.5, 0, 0);
        saveAtt("E003", 10.0, 15.0, 0, 0);
        saveAtt("E101", 3.0, 68.5, 5, 0.5);
        saveAtt("E102", 5.5, 55.0, 3, 0);
        saveAtt("E103", 9.0, 20.0, 1, 0);
        saveAtt("E104", 11.0, 18.5, 0, 0);
        saveAtt("E105", 7.0, 35.0, 2, 0);
        saveAtt("E201", 6.0, 45.0, 2, 0);
        saveAtt("E202", 8.0, 38.0, 1, 0);
        saveAtt("E203", 10.0, 22.0, 0, 0);
        saveAtt("E204", 4.0, 52.0, 4, 1.0);
        saveAtt("E205", 9.5, 25.0, 1, 0);
        saveAtt("E301", 11.5, 30.0, 0, 0);
        saveAtt("E302", 8.0, 32.0, 1, 0);
        saveAtt("E303", 9.0, 24.0, 0, 0);
        saveAtt("E304", 12.0, 12.0, 0, 0);
    }

    private void saveAtt(String empId, double leave, double overtime, int late, double absent) {
        BizAttendance a = new BizAttendance();
        a.setEmployeeId(empId);
        a.setQuarter("2026-Q1");
        a.setLeaveBalance(leave);
        a.setOvertimeHours(overtime);
        a.setLateCount(late);
        a.setAbsentDays(absent);
        attendanceRepository.save(a);
    }

    private void initSalary() {
        saveSal("E000", "M1", 38000.00, "2025-04-01");
        saveSal("E001", "P6", 28000.00, "2025-04-01");
        saveSal("E002", "M1", 45000.00, "2025-04-01");
        saveSal("E003", "P7", 32000.00, "2025-04-01");
        saveSal("E101", "P6", 30000.00, "2024-04-01");
        saveSal("E102", "P5", 22000.00, "2025-04-01");
        saveSal("E103", "P5", 20000.00, "2025-04-01");
        saveSal("E104", "P6", 29000.00, "2025-04-01");
        saveSal("E105", "P4", 15000.00, "2025-04-01");
        saveSal("E201", "M2", 52000.00, "2025-04-01");
        saveSal("E202", "P6", 26000.00, "2025-04-01");
        saveSal("E203", "P4", 14000.00, "2025-04-01");
        saveSal("E204", "P5", 21000.00, "2024-04-01");
        saveSal("E205", "P4", 13000.00, "2025-04-01");
        saveSal("E301", "M1", 48000.00, "2025-04-01");
        saveSal("E302", "P6", 27000.00, "2025-04-01");
        saveSal("E303", "P5", 19000.00, "2025-04-01");
        saveSal("E304", "P3", 10000.00, "2025-04-01");
    }

    private void saveSal(String empId, String band, double salary, String adjustDate) {
        BizSalary s = new BizSalary();
        s.setEmployeeId(empId);
        s.setSalaryBand(band);
        s.setBaseSalary(salary);
        s.setLastAdjustDate(LocalDate.parse(adjustDate));
        salaryRepository.save(s);
    }

    private void initPerformance() {
        savePerf("E000", 2026, "Q1", "A", 93.0, true);
        savePerf("E001", 2025, "Q4", "B", 82.0, false);
        savePerf("E001", 2026, "Q1", "B", 80.5, false);
        savePerf("E002", 2025, "Q4", "A", 92.0, true);
        savePerf("E002", 2026, "Q1", "A", 91.5, true);
        savePerf("E101", 2025, "Q4", "C", 68.0, false);
        savePerf("E101", 2026, "Q1", "C", 65.5, false);
        savePerf("E102", 2025, "Q4", "B", 75.0, false);
        savePerf("E102", 2026, "Q1", "B", 74.0, false);
        savePerf("E103", 2025, "Q4", "B", 85.0, false);
        savePerf("E104", 2025, "Q4", "A", 90.0, true);
        savePerf("E201", 2025, "Q4", "B", 78.0, false);
        savePerf("E201", 2026, "Q1", "C", 70.0, false);
        savePerf("E204", 2025, "Q4", "C", 62.0, false);
        savePerf("E204", 2026, "Q1", "C", 60.5, false);
        savePerf("E301", 2025, "Q4", "A", 88.0, true);
        savePerf("E302", 2025, "Q4", "B", 83.0, false);
    }

    private void savePerf(String empId, int year, String quarter, String rating,
                          double score, boolean promotionEligible) {
        BizPerformance p = new BizPerformance();
        p.setEmployeeId(empId);
        p.setYear(year);
        p.setQuarter(quarter);
        p.setRating(rating);
        p.setScore(score);
        p.setPromotionEligible(promotionEligible);
        performanceRepository.save(p);
    }

    private void initTurnoverRisk() {
        saveRisk("E101", 0.87, "HIGH", "近3月考勤异常增加;绩效评级下降;晋升停滞超过18个月",
                "建议HRBP在一周内安排一对一沟通，了解职业发展诉求");
        saveRisk("E102", 0.72, "HIGH", "满意度调查得分偏低;加班时长持续偏高;内部调岗申请被拒",
                "关注工作负荷，考虑团队内轮岗或项目调整");
        saveRisk("E201", 0.91, "CRITICAL", "竞品公司接触记录;关键技能匹配外部高薪岗位;近1月请假异常",
                "高风险预警！建议立即启动挽留方案");
        saveRisk("E204", 0.68, "MEDIUM", "绩效连续两个季度C级;满意度低于部门均值;加班时长偏高",
                "安排绩效改进计划(PIP)，加强辅导");
        saveRisk("E105", 0.35, "LOW", "入职不满3年，整体状态稳定", "正常关注即可");
    }

    private void saveRisk(String empId, double score, String level, String factors, String recommendation) {
        BizTurnoverRisk r = new BizTurnoverRisk();
        r.setEmployeeId(empId);
        r.setRiskScore(score);
        r.setRiskLevel(level);
        r.setFactors(factors);
        r.setRecommendation(recommendation);
        r.setPredictedAt(LocalDateTime.now());
        turnoverRiskRepository.save(r);
    }
}
