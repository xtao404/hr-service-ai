package com.hr.ai.service.texttosql;

import org.springframework.stereotype.Component;

/**
 * 提供 HR 业务库的 Schema 描述、表关系说明与 Few-shot SQL 示例。
 */
@Component
public class HrSchemaProvider {

    public String buildSchemaDescription() {
        return """
                === 表结构 ===

                biz_department（部门）
                  dept_id       VARCHAR  部门编码，如 D001
                  dept_name     VARCHAR  部门名称
                  manager_id    VARCHAR  负责人员工编号
                  headcount     INT      在职人数

                biz_employee（员工，核心维度表）
                  employee_id         VARCHAR  员工编号 E001
                  name                VARCHAR  姓名
                  gender              VARCHAR  男/女
                  dept_id             VARCHAR  → biz_department.dept_id
                  position            VARCHAR  岗位
                  hire_date           DATE     入职日期
                  status              VARCHAR  ACTIVE=在职, LEAVE=休假, RESIGNED=离职
                  education           VARCHAR  本科/硕士/博士 等
                  satisfaction_score  DECIMAL  满意度 1-10

                biz_attendance（考勤汇总，按季度）
                  employee_id     VARCHAR  → biz_employee.employee_id
                  quarter         VARCHAR  如 2026-Q1
                  leave_balance   DECIMAL  年假余额(天)
                  overtime_hours  DECIMAL  加班时长(小时)
                  late_count      INT      迟到次数
                  absent_days     DECIMAL  缺勤天数

                biz_salary（薪酬，敏感，仅 HRBP/HR_ADMIN）
                  employee_id       VARCHAR
                  salary_band       VARCHAR  P3-P7/M1/M2
                  base_salary       DECIMAL  基本月薪(元)
                  last_adjust_date  DATE

                biz_performance（绩效）
                  employee_id         VARCHAR
                  perf_year           INT      如 2026（注意：列名是 perf_year，不是 year）
                  quarter             VARCHAR  Q1/Q2/Q3/Q4
                  rating              VARCHAR  A/B/C/D
                  score               DECIMAL  0-100
                  promotion_eligible  TINYINT  0/1

                biz_turnover_risk（离职风险预测）
                  employee_id     VARCHAR
                  risk_score      DECIMAL  0-1，如 0.85 表示 85%%
                  risk_level      VARCHAR  LOW/MEDIUM/HIGH/CRITICAL
                  factors         TEXT     风险因素描述
                  recommendation  TEXT     挽留建议

                === 常用 JOIN ===
                  biz_employee e JOIN biz_department d ON e.dept_id = d.dept_id
                  biz_employee e JOIN biz_attendance a ON e.employee_id = a.employee_id
                  biz_employee e JOIN biz_salary s ON e.employee_id = s.employee_id
                  biz_employee e JOIN biz_performance p ON e.employee_id = p.employee_id
                  biz_employee e JOIN biz_turnover_risk r ON e.employee_id = r.employee_id

                === 业务提示 ===
                  - 考勤默认季度: 2026-Q1
                  - 绩效默认年份: 2026
                  - 高风险离职: risk_level IN ('HIGH','CRITICAL')
                  - 统计在职人数: e.status = 'ACTIVE'
                """;
    }

    public String buildFewShotExamples() {
        return """
                示例1 - 各部门加班时长对比排名:
                SELECT d.dept_name AS 部门名称,
                       SUM(a.overtime_hours) AS 总加班时长,
                       AVG(a.overtime_hours) AS 平均加班时长
                FROM biz_department d
                JOIN biz_employee e ON d.dept_id = e.dept_id AND e.status = 'ACTIVE'
                JOIN biz_attendance a ON e.employee_id = a.employee_id
                WHERE a.quarter = '2026-Q1'
                GROUP BY d.dept_name
                ORDER BY 总加班时长 DESC
                LIMIT 20

                示例2 - 绩效C且加班超过50小时的员工:
                SELECT e.name AS 姓名, d.dept_name AS 部门,
                       p.rating AS 绩效评级, p.score AS 绩效分数,
                       a.overtime_hours AS 加班时长
                FROM biz_employee e
                JOIN biz_department d ON e.dept_id = d.dept_id
                JOIN biz_performance p ON e.employee_id = p.employee_id
                JOIN biz_attendance a ON e.employee_id = a.employee_id
                WHERE p.rating = 'C' AND a.overtime_hours > 50
                  AND a.quarter = '2026-Q1' AND e.status = 'ACTIVE'
                LIMIT 50

                示例3 - 满意度低于7的在职员工:
                SELECT e.name AS 姓名, d.dept_name AS 部门,
                       e.satisfaction_score AS 满意度, e.position AS 岗位
                FROM biz_employee e
                JOIN biz_department d ON e.dept_id = d.dept_id
                WHERE e.status = 'ACTIVE' AND e.satisfaction_score < 7
                ORDER BY e.satisfaction_score ASC
                LIMIT 30

                示例4 - 各部门在职人数:
                SELECT d.dept_name AS 部门名称, COUNT(e.id) AS 在职人数
                FROM biz_department d
                LEFT JOIN biz_employee e ON d.dept_id = e.dept_id AND e.status = 'ACTIVE'
                GROUP BY d.dept_name
                ORDER BY 在职人数 DESC
                LIMIT 20

                示例5 - 高风险离职员工明细:
                SELECT e.name AS 姓名, d.dept_name AS 部门,
                       r.risk_score AS 风险分, r.risk_level AS 风险等级,
                       r.factors AS 风险因素, r.recommendation AS 建议
                FROM biz_turnover_risk r
                JOIN biz_employee e ON r.employee_id = e.employee_id
                JOIN biz_department d ON e.dept_id = d.dept_id
                WHERE r.risk_level IN ('HIGH', 'CRITICAL')
                ORDER BY r.risk_score DESC
                LIMIT 30
                """;
    }
}
