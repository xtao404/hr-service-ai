-- ============================================================
-- HR AI 平台 - MySQL 测试数据脚本
-- 使用方法:
--   mysql -u root -p < scripts/mysql/hr_test_data.sql
-- 或在 MySQL 客户端中执行本文件
-- ============================================================

CREATE DATABASE IF NOT EXISTS hr_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE hr_ai;

-- ----------------------------------------------------------
-- 1. 部门表
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_department (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    dept_id         VARCHAR(20)  NOT NULL UNIQUE COMMENT '部门编码',
    dept_name       VARCHAR(100) NOT NULL COMMENT '部门名称',
    manager_id      VARCHAR(20)  COMMENT '部门负责人员工编号',
    headcount       INT          DEFAULT 0 COMMENT '在职人数'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门信息';

-- ----------------------------------------------------------
-- 2. 员工表
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_employee (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id         VARCHAR(20)  NOT NULL UNIQUE COMMENT '员工编号',
    name                VARCHAR(50)  NOT NULL COMMENT '姓名',
    gender              VARCHAR(10)  COMMENT '性别',
    dept_id             VARCHAR(20)  NOT NULL COMMENT '部门编码',
    position            VARCHAR(100) COMMENT '岗位',
    hire_date           DATE         COMMENT '入职日期',
    status              VARCHAR(20)  DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/LEAVE/RESIGNED',
    education           VARCHAR(50)  COMMENT '学历',
    satisfaction_score  DECIMAL(4,1) COMMENT '满意度评分(1-10)',
    INDEX idx_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工信息';

-- ----------------------------------------------------------
-- 3. 考勤汇总表（2026-Q1）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_attendance (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id     VARCHAR(20)  NOT NULL COMMENT '员工编号',
    quarter         VARCHAR(10)  NOT NULL COMMENT '季度，如 2026-Q1',
    leave_balance   DECIMAL(5,1) DEFAULT 0 COMMENT '年假余额(天)',
    overtime_hours  DECIMAL(6,1) DEFAULT 0 COMMENT '加班时长(小时)',
    late_count      INT          DEFAULT 0 COMMENT '迟到次数',
    absent_days     DECIMAL(4,1) DEFAULT 0 COMMENT '缺勤天数',
    UNIQUE KEY uk_emp_quarter (employee_id, quarter),
    INDEX idx_quarter (quarter)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤汇总';

-- ----------------------------------------------------------
-- 4. 薪酬表（敏感数据）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_salary (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id     VARCHAR(20)  NOT NULL UNIQUE COMMENT '员工编号',
    salary_band     VARCHAR(20)  COMMENT '薪酬等级',
    base_salary     DECIMAL(10,2) COMMENT '基本月薪(元)',
    last_adjust_date DATE        COMMENT '最近调薪日期'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='薪酬信息';

-- ----------------------------------------------------------
-- 5. 绩效表
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_performance (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id     VARCHAR(20) NOT NULL COMMENT '员工编号',
    perf_year         INT         NOT NULL COMMENT '年份',
    quarter         VARCHAR(10) NOT NULL COMMENT '季度',
    rating          VARCHAR(20) COMMENT '评级: A/B/C/D',
    score           DECIMAL(5,1) COMMENT '绩效分数',
    promotion_eligible TINYINT(1) DEFAULT 0 COMMENT '是否可晋升',
    UNIQUE KEY uk_emp_period (employee_id, perf_year, quarter)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绩效记录';

-- ----------------------------------------------------------
-- 6. 离职风险预测表
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_turnover_risk (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id     VARCHAR(20)  NOT NULL UNIQUE COMMENT '员工编号',
    risk_score      DECIMAL(4,2) NOT NULL COMMENT '风险分 0-1',
    risk_level      VARCHAR(20)  NOT NULL COMMENT 'LOW/MEDIUM/HIGH/CRITICAL',
    factors         TEXT         COMMENT '风险因素',
    recommendation  TEXT         COMMENT '建议措施',
    predicted_at    DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离职风险预测';

-- ============================================================
-- 清空旧测试数据（保留 JPA 自动创建的 users 等表）
-- ============================================================
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE biz_turnover_risk;
TRUNCATE TABLE biz_performance;
TRUNCATE TABLE biz_salary;
TRUNCATE TABLE biz_attendance;
TRUNCATE TABLE biz_employee;
TRUNCATE TABLE biz_department;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 插入部门数据
-- ============================================================
INSERT INTO biz_department (dept_id, dept_name, manager_id, headcount) VALUES
('D001', '技术研发部', 'E002', 8),
('D002', '市场营销部', 'E201', 5),
('D003', '产品设计部', 'E301', 4),
('D000', '人力资源部', 'E003', 3);

-- ============================================================
-- 插入员工数据
-- 说明: E000/E001/E002/E003 对应系统登录账号 admin/employee1/manager1/hrbp1
-- ============================================================
INSERT INTO biz_employee (employee_id, name, gender, dept_id, position, hire_date, status, education, satisfaction_score) VALUES
('E000', '系统管理员', '男', 'D000', 'HR管理员',         '2016-01-01', 'ACTIVE', '本科', 8.5),
('E001', '张三',   '男', 'D001', '高级工程师',       '2021-03-15', 'ACTIVE', '本科', 7.5),
('E002', '李四',   '男', 'D001', '技术经理',         '2018-06-01', 'ACTIVE', '硕士', 8.2),
('E003', '王五',   '女', 'D000', 'HRBP',            '2019-09-10', 'ACTIVE', '硕士', 8.0),
('E101', '赵六',   '男', 'D001', '高级Java工程师',   '2020-01-20', 'ACTIVE', '本科', 5.8),
('E102', '钱七',   '女', 'D001', '前端工程师',       '2021-07-01', 'ACTIVE', '本科', 6.2),
('E103', '周八',   '男', 'D001', '测试工程师',       '2022-04-18', 'ACTIVE', '本科', 7.8),
('E104', '吴九',   '女', 'D001', 'DevOps工程师',     '2019-11-05', 'ACTIVE', '硕士', 8.5),
('E105', '郑十',   '男', 'D001', '初级Java工程师',   '2023-08-01', 'ACTIVE', '本科', 7.0),
('E106', '赵六一', '男', 'D001', 'Java工程师',       '2022-06-15', 'ACTIVE', '本科', 7.2),
('E201', '孙八',   '女', 'D002', '市场总监',         '2017-05-20', 'ACTIVE', '硕士', 6.5),
('E202', '冯十一', '男', 'D002', '品牌经理',         '2020-09-15', 'ACTIVE', '本科', 7.2),
('E203', '陈十二', '女', 'D002', '市场专员',         '2022-02-28', 'ACTIVE', '本科', 6.8),
('E204', '褚十三', '男', 'D002', '渠道经理',         '2021-12-01', 'ACTIVE', '本科', 5.5),
('E205', '卫十四', '女', 'D002', '内容运营',         '2023-03-10', 'ACTIVE', '本科', 7.5),
('E301', '蒋十五', '男', 'D003', '产品总监',         '2018-08-15', 'ACTIVE', '硕士', 8.0),
('E302', '沈十六', '女', 'D003', '高级产品经理',     '2020-06-01', 'ACTIVE', '本科', 7.6),
('E303', '韩十七', '男', 'D003', 'UI设计师',         '2021-10-20', 'ACTIVE', '本科', 7.3),
('E304', '杨十八', '女', 'D003', '产品助理',         '2024-01-08', 'ACTIVE', '本科', 8.1);

-- ============================================================
-- 插入考勤数据（2026-Q1）
-- ============================================================
INSERT INTO biz_attendance (employee_id, quarter, leave_balance, overtime_hours, late_count, absent_days) VALUES
('E000', '2026-Q1', 10.0,  12.0, 0, 0),
('E001', '2026-Q1',  8.5,  42.0, 1, 0),
('E002', '2026-Q1', 12.0,  28.5, 0, 0),
('E003', '2026-Q1', 10.0,  15.0, 0, 0),
('E101', '2026-Q1',  3.0,  68.5, 5, 0.5),
('E102', '2026-Q1',  5.5,  55.0, 3, 0),
('E103', '2026-Q1',  9.0,  20.0, 1, 0),
('E104', '2026-Q1', 11.0,  18.5, 0, 0),
('E105', '2026-Q1',  7.0,  35.0, 2, 0),
('E201', '2026-Q1',  6.0,  45.0, 2, 0),
('E202', '2026-Q1',  8.0,  38.0, 1, 0),
('E203', '2026-Q1', 10.0,  22.0, 0, 0),
('E204', '2026-Q1',  4.0,  52.0, 4, 1.0),
('E205', '2026-Q1',  9.5,  25.0, 1, 0),
('E301', '2026-Q1', 11.5,  30.0, 0, 0),
('E302', '2026-Q1',  8.0,  32.0, 1, 0),
('E303', '2026-Q1',  9.0,  24.0, 0, 0),
('E304', '2026-Q1', 12.0,  12.0, 0, 0);

-- ============================================================
-- 插入薪酬数据
-- ============================================================
INSERT INTO biz_salary (employee_id, salary_band, base_salary, last_adjust_date) VALUES
('E000', 'M1', 38000.00, '2025-04-01'),
('E001', 'P6', 28000.00, '2025-04-01'),
('E002', 'M1', 45000.00, '2025-04-01'),
('E003', 'P7', 32000.00, '2025-04-01'),
('E101', 'P6', 30000.00, '2024-04-01'),
('E102', 'P5', 22000.00, '2025-04-01'),
('E103', 'P5', 20000.00, '2025-04-01'),
('E104', 'P6', 29000.00, '2025-04-01'),
('E105', 'P4', 15000.00, '2025-04-01'),
('E201', 'M2', 52000.00, '2025-04-01'),
('E202', 'P6', 26000.00, '2025-04-01'),
('E203', 'P4', 14000.00, '2025-04-01'),
('E204', 'P5', 21000.00, '2024-04-01'),
('E205', 'P4', 13000.00, '2025-04-01'),
('E301', 'M1', 48000.00, '2025-04-01'),
('E302', 'P6', 27000.00, '2025-04-01'),
('E303', 'P5', 19000.00, '2025-04-01'),
('E304', 'P3', 10000.00, '2025-04-01');

-- ============================================================
-- 插入绩效数据（2025-Q4 / 2026-Q1）
-- ============================================================
INSERT INTO biz_performance (employee_id, perf_year, quarter, rating, score, promotion_eligible) VALUES
('E000', 2026, 'Q1', 'A', 93.0, 1),
('E001', 2025, 'Q4', 'B', 82.0, 0),
('E001', 2026, 'Q1', 'B', 80.5, 0),
('E002', 2025, 'Q4', 'A', 92.0, 1),
('E002', 2026, 'Q1', 'A', 91.5, 1),
('E101', 2025, 'Q4', 'C', 68.0, 0),
('E101', 2026, 'Q1', 'C', 65.5, 0),
('E102', 2025, 'Q4', 'B', 75.0, 0),
('E102', 2026, 'Q1', 'B', 74.0, 0),
('E103', 2025, 'Q4', 'B', 85.0, 0),
('E104', 2025, 'Q4', 'A', 90.0, 1),
('E201', 2025, 'Q4', 'B', 78.0, 0),
('E201', 2026, 'Q1', 'C', 70.0, 0),
('E204', 2025, 'Q4', 'C', 62.0, 0),
('E204', 2026, 'Q1', 'C', 60.5, 0),
('E301', 2025, 'Q4', 'A', 88.0, 1),
('E302', 2025, 'Q4', 'B', 83.0, 0);

-- ============================================================
-- 插入离职风险预测
-- ============================================================
INSERT INTO biz_turnover_risk (employee_id, risk_score, risk_level, factors, recommendation) VALUES
('E101', 0.87, 'HIGH',     '近3月考勤异常增加;绩效评级下降;晋升停滞超过18个月',           '建议HRBP在一周内安排一对一沟通，了解职业发展诉求'),
('E102', 0.72, 'HIGH',     '满意度调查得分偏低;加班时长持续偏高;内部调岗申请被拒',         '关注工作负荷，考虑团队内轮岗或项目调整'),
('E201', 0.91, 'CRITICAL', '竞品公司接触记录;关键技能匹配外部高薪岗位;近1月请假异常',     '高风险预警！建议立即启动挽留方案'),
('E204', 0.68, 'MEDIUM',   '绩效连续两个季度C级;满意度低于部门均值;加班时长偏高',         '安排绩效改进计划(PIP)，加强辅导'),
('E105', 0.35, 'LOW',      '入职不满3年，整体状态稳定',                                   '正常关注即可');

-- ============================================================
-- 验证查询
-- ============================================================
SELECT '部门数' AS metric, COUNT(*) AS value FROM biz_department
UNION ALL
SELECT '员工数', COUNT(*) FROM biz_employee WHERE status = 'ACTIVE'
UNION ALL
SELECT '高风险离职预警', COUNT(*) FROM biz_turnover_risk WHERE risk_level IN ('HIGH', 'CRITICAL')
UNION ALL
SELECT '研发部Q1总加班(小时)', SUM(a.overtime_hours) FROM biz_attendance a
    JOIN biz_employee e ON a.employee_id = e.employee_id WHERE e.dept_id = 'D001' AND a.quarter = '2026-Q1';
