package com.youzhi.zhixun.service.impl;

import com.youzhi.zhixun.service.DemoExperienceService;
import com.youzhi.zhixun.retrieval.RagQueryService;
import com.youzhi.zhixun.vo.CitationVO;
import com.youzhi.zhixun.vo.DemoChatResponseVO;
import com.youzhi.zhixun.vo.DemoUserVO;
import com.youzhi.zhixun.vo.KnowledgeNodeVO;
import com.youzhi.zhixun.vo.KnowledgeSpaceVO;
import com.youzhi.zhixun.vo.WorkspaceVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class DemoExperienceServiceImpl implements DemoExperienceService {
    private static final String DEMO_MODE = "DEMO_FIXTURE";
    private final RagQueryService ragQueryService;

    public DemoExperienceServiceImpl(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    @Override
    public WorkspaceVO workspace(String userId) {
        if (ragQueryService.isReady()) return ragQueryService.workspace(userId);
        List<KnowledgeSpaceVO> spaces = List.of(
            new KnowledgeSpaceVO(
                "space-company-policy",
                "公司制度",
                "日常协作与行政制度",
                18,
                List.of(
                    new KnowledgeNodeVO("node-travel", "差旅与报销", "folder", 6),
                    new KnowledgeNodeVO("node-leave", "考勤与休假", "folder", 5),
                    new KnowledgeNodeVO("node-office", "行政办事", "folder", 7)
                )
            ),
            new KnowledgeSpaceVO(
                "space-product",
                "产品手册",
                "产品流程与交付标准",
                24,
                List.of(
                    new KnowledgeNodeVO("node-release", "发布流程", "folder", 8),
                    new KnowledgeNodeVO("node-customer", "客户交付", "folder", 9),
                    new KnowledgeNodeVO("node-quality", "质量标准", "folder", 7)
                )
            ),
            new KnowledgeSpaceVO(
                "space-engineering",
                "研发规范",
                "工程实践与安全约束",
                13,
                List.of(
                    new KnowledgeNodeVO("node-change", "变更管理", "folder", 5),
                    new KnowledgeNodeVO("node-security", "安全基线", "folder", 4),
                    new KnowledgeNodeVO("node-incident", "故障响应", "folder", 4)
                )
            )
        );
        return new WorkspaceVO(
            "智询",
            "领导体验版 · MVP 0.1",
            new DemoUserVO("演示用户", "产品体验组", DEMO_MODE),
            spaces,
            List.of(
                "差旅报销需要哪些材料？",
                "年假应该如何申请？",
                "生产变更上线前要完成什么？"
            ),
            55,
            spaces.size()
        );
    }

    @Override
    public DemoChatResponseVO answer(String question, String spaceId, String userId) {
        if (ragQueryService.isReady()) return ragQueryService.answer(question, spaceId, userId);
        String normalized = question.strip().toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "差旅", "报销", "票据")) {
            return answered(
                "差旅报销建议按三个步骤完成：先在出行前发起差旅审批；行程结束后整理合规票据和费用明细；最后在规定时间内提交报销并关联原审批单。具体额度仍应以适用部门和职级规则为准。",
                new CitationVO(
                    "doc-demo-travel-v3",
                    "差旅与费用管理办法（演示）",
                    "第 4 章 · 报销材料",
                    "报销申请应关联已完成的差旅审批，并附合规票据与费用明细。",
                    "2026-06-18"
                ),
                List.of("哪些费用不能报销？", "报销需要在几天内提交？")
            );
        }
        if (containsAny(normalized, "年假", "请假", "休假")) {
            return answered(
                "年假申请需要先确认可用假期余额，再在计划休假前提交申请并由直属负责人审批。涉及连续多日或关键交付期时，应提前完成工作交接。",
                new CitationVO(
                    "doc-demo-leave-v2",
                    "员工休假指引（演示）",
                    "2.3 年假申请",
                    "员工应在休假前提交申请；连续休假应同步说明工作交接安排。",
                    "2026-05-09"
                ),
                List.of("临时请假怎么处理？", "休假期间如何交接工作？")
            );
        }
        if (containsAny(normalized, "上线", "发布", "生产变更", "变更")) {
            return answered(
                "生产变更上线前至少要完成变更工单、影响范围评估、回滚方案和双人复核；高风险变更还需要在约定窗口执行，并保留验证结果。",
                new CitationVO(
                    "doc-demo-release-v4",
                    "生产变更规范（演示）",
                    "3.1 上线前检查",
                    "所有生产变更必须具备工单、影响评估、可执行回滚方案和复核记录。",
                    "2026-07-02"
                ),
                List.of("什么情况属于高风险变更？", "回滚方案需要包含什么？")
            );
        }
        return new DemoChatResponseVO(
            "insufficient",
            "当前演示资料中没有找到足够依据。我不会用通用常识补写公司规则；后续接入正式文档后，可以在这里返回可核验的答案和来源。",
            false,
            DEMO_MODE,
            List.of(),
            workspace(userId).sampleQuestions()
        );
    }

    private DemoChatResponseVO answered(String answer, CitationVO citation, List<String> suggestedQuestions) {
        return new DemoChatResponseVO(
            "answered",
            answer,
            true,
            DEMO_MODE,
            List.of(citation),
            suggestedQuestions
        );
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
