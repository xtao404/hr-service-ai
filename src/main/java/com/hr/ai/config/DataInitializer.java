package com.hr.ai.config;

import com.hr.ai.model.entity.*;
import com.hr.ai.model.enums.KnowledgeCategory;
import com.hr.ai.model.enums.RiskLevel;
import com.hr.ai.model.enums.UserRole;
import com.hr.ai.repository.*;
import com.hr.ai.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final KnowledgeDocumentRepository knowledgeRepository;
    private final TurnoverPredictionRepository turnoverRepository;
    private final SkillGapPredictionRepository skillGapRepository;
    private final RecruitmentInsightRepository recruitmentRepository;
    private final VectorStoreService vectorStoreService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            initUsers();
        }
        if (knowledgeRepository.count() == 0) {
            initKnowledge();
        }
        if (turnoverRepository.count() == 0) {
            initPredictions();
        }
    }

    private void initUsers() {
        saveUser("employee1", "员工张三", UserRole.EMPLOYEE, "D001", "技术研发部", "E001");
        saveUser("manager1", "经理李四", UserRole.MANAGER, "D001", "技术研发部", "E002");
        saveUser("hrbp1", "HRBP王五", UserRole.HRBP, "D000", "人力资源部", "E003");
        saveUser("admin", "系统管理员", UserRole.HR_ADMIN, "D000", "人力资源部", "E000");
    }

    private void saveUser(String username, String name, UserRole role, String deptId, String deptName, String empId) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setName(name);
        user.setRole(role);
        user.setDepartmentId(deptId);
        user.setDepartmentName(deptName);
        user.setEmployeeId(empId);
        userRepository.save(user);
    }

    private void initKnowledge() {
        saveKnowledge("员工考勤管理制度", KnowledgeCategory.ATTENDANCE,
                "公司实行标准工时制，工作日为周一至周五，每日工作8小时。迟到15分钟以内扣款50元，"
                        + "迟到超过15分钟按旷工半天处理。每月迟到3次以上将影响绩效考核。"
                        + "员工可通过OA系统提交请假申请，需提前1个工作日申请。");
        saveKnowledge("年假与假期政策", KnowledgeCategory.LEAVE,
                "员工入职满1年享有5天年假，满3年享有10天年假，满5年享有15天年假。"
                        + "年假需提前在OA系统申请，当年未休完的年假可结转至次年3月31日前。"
                        + "病假需提供医院证明，事假需直属领导审批。");
        saveKnowledge("薪酬福利方案", KnowledgeCategory.SALARY_POLICY,
                "公司薪酬体系采用宽带薪酬制，每年4月进行年度调薪。绩效奖金按季度发放，"
                        + "与部门及个人绩效挂钩。13薪在春节前发放。"
                        + "薪酬信息属于公司机密，员工不得互相打听或泄露。");
        saveKnowledge("五险一金与补充福利", KnowledgeCategory.BENEFITS,
                "公司为员工缴纳五险一金，缴费基数按上年度月平均工资核定。"
                        + "补充福利包括：年度体检、商业医疗保险、节日礼品、员工旅游、"
                        + "健身房补贴、子女教育补贴等。");
        saveKnowledge("入职办理流程", KnowledgeCategory.ONBOARDING,
                "新员工入职流程：1.收到offer并确认 2.提交入职材料（身份证、学历证、离职证明）"
                        + "3.签署劳动合同 4.办理工牌和门禁 5.参加入职培训 6.部门报到。"
                        + "入职当天由HR专人引导完成所有手续。");
        saveKnowledge("离职办理流程", KnowledgeCategory.OFFBOARDING,
                "员工离职需提前30天提交书面辞职申请。离职流程包括："
                        + "1.直属领导审批 2.HR面谈 3.工作交接 4.资产归还 5.离职证明开具。"
                        + "关键岗位员工需签订竞业限制协议。");
    }

    private void saveKnowledge(String title, KnowledgeCategory category, String content) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle(title);
        doc.setContent(content);
        doc.setCategory(category);
        doc.setSourceFile(title + ".pdf");
        vectorStoreService.indexDocument(doc);
    }

    private void initPredictions() {
        saveTurnover("E101", "赵六", "D001", "技术研发部", 0.87, RiskLevel.HIGH,
                "近3月考勤异常增加;绩效评级下降;晋升停滞超过18个月",
                "建议HRBP在一周内安排一对一沟通，了解职业发展诉求，评估调薪或晋升可能性。");
        saveTurnover("E102", "钱七", "D001", "技术研发部", 0.72, RiskLevel.HIGH,
                "满意度调查得分偏低;加班时长持续偏高;内部调岗申请被拒",
                "关注工作负荷，考虑团队内轮岗或项目调整，降低倦怠感。");
        saveTurnover("E201", "孙八", "D002", "市场营销部", 0.91, RiskLevel.CRITICAL,
                "竞品公司接触记录;关键技能匹配外部高薪岗位;近1月请假异常",
                "高风险预警！建议立即启动挽留方案，由部门负责人和HRBP联合介入。");

        saveSkillGap("高级Java工程师", "D001", "云原生/K8s", 3, 8, 5, 9,
                "建议在Q3启动云原生技术培训，同时开启2个高级岗位社招。");
        saveSkillGap("数据分析师", "D002", "Python/机器学习", 2, 6, 4, 8,
                "考虑内部培养+校招组合策略，与业务部门共建数据分析梯队。");
        saveSkillGap("产品经理", "D003", "AI产品设计", 1, 4, 3, 7,
                "AI产品线扩张导致人才缺口，建议从内部技术骨干中选拔转型。");

        RecruitmentInsight insight1 = new RecruitmentInsight();
        insight1.setPositionName("高级Java工程师");
        insight1.setSuccessTraits("985/211院校;3年以上互联网经验;有分布式系统项目经验;良好的跨部门沟通能力");
        insight1.setOptimizationSuggestions("增加系统设计环节面试权重;关注候选人的技术社区活跃度;缩短面试周期至2周内");
        insight1.setSuccessRate(0.68);
        insight1.setSampleSize(45);
        recruitmentRepository.save(insight1);

        RecruitmentInsight insight2 = new RecruitmentInsight();
        insight2.setPositionName("产品经理");
        insight2.setSuccessTraits("有B端产品经验;数据驱动意识强;具备技术背景;过往项目有从0到1经验");
        insight2.setOptimizationSuggestions("优化JD突出业务场景;增加case study环节;建立内部推荐激励");
        insight2.setSuccessRate(0.55);
        insight2.setSampleSize(32);
        recruitmentRepository.save(insight2);
    }

    private void saveTurnover(String empId, String name, String deptId, String deptName,
                              double score, RiskLevel level, String factors, String recommendation) {
        TurnoverPrediction p = new TurnoverPrediction();
        p.setEmployeeId(empId);
        p.setEmployeeName(name);
        p.setDepartmentId(deptId);
        p.setDepartmentName(deptName);
        p.setRiskScore(score);
        p.setRiskLevel(level);
        p.setFactors(factors);
        p.setRecommendation(recommendation);
        turnoverRepository.save(p);
    }

    private void saveSkillGap(String position, String deptId, String skill,
                              int supply, int demand, int gap, int priority, String recommendation) {
        SkillGapPrediction g = new SkillGapPrediction();
        g.setPositionName(position);
        g.setDepartmentId(deptId);
        g.setRequiredSkill(skill);
        g.setCurrentSupply(supply);
        g.setProjectedDemand(demand);
        g.setGapCount(gap);
        g.setPriority(priority);
        g.setRecommendation(recommendation);
        skillGapRepository.save(g);
    }
}
