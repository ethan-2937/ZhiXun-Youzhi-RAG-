package com.youzhi.zhixun.service.impl;

import com.youzhi.zhixun.vo.DemoChatResponseVO;
import com.youzhi.zhixun.retrieval.RagQueryService;
import com.youzhi.zhixun.vo.WorkspaceVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoExperienceServiceImplTest {
    private final DemoExperienceServiceImpl service = new DemoExperienceServiceImpl(new DisabledRagQueryService());

    @Test
    void knownQuestionReturnsGroundedAnswerAndCitation() {
        DemoChatResponseVO response = service.answer(
            "差旅报销需要哪些材料？",
            "space-company-policy",
            "test-user-demo-001"
        );

        assertThat(response.status()).isEqualTo("answered");
        assertThat(response.grounded()).isTrue();
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().getFirst().documentId()).isEqualTo("doc-demo-travel-v3");
    }

    @Test
    void unknownQuestionFailsClosedWithoutInventedCitation() {
        DemoChatResponseVO response = service.answer("虚构资料里没有的问题", null, "test-user-demo-001");

        assertThat(response.status()).isEqualTo("insufficient");
        assertThat(response.grounded()).isFalse();
        assertThat(response.citations()).isEmpty();
        assertThat(response.answer()).contains("没有找到足够依据");
    }

    private static class DisabledRagQueryService implements RagQueryService {
        @Override
        public boolean isReady() {
            return false;
        }

        @Override
        public WorkspaceVO workspace(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DemoChatResponseVO answer(String question, String spaceId, String userId) {
            throw new UnsupportedOperationException();
        }
    }
}
