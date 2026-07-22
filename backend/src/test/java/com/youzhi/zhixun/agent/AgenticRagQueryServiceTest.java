package com.youzhi.zhixun.agent;

import com.youzhi.zhixun.common.RagException;
import com.youzhi.zhixun.config.RagProperties;
import com.youzhi.zhixun.retrieval.AuthorizedEvidence;
import com.youzhi.zhixun.retrieval.AuthorizedKnowledgeSearch;
import com.youzhi.zhixun.retrieval.InMemoryRagQueryService;
import com.youzhi.zhixun.vo.CitationVO;
import com.youzhi.zhixun.vo.DemoChatResponseVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgenticRagQueryServiceTest {
    @Test
    void injectsServerPrincipalIntoAuthorizedSearchAndReturnsVerifiedCitation() {
        Fixture fixture = fixture();
        when(fixture.workflow.plan("虚构制度如何执行？", List.of()))
            .thenReturn(new AgentPlan(List.of("虚构制度执行流程")));
        when(fixture.search.search(
            "虚构制度执行流程", "space-test", "test-user-allowed", 8, 0.30
        )).thenReturn(List.of(evidence("doc-test-safe", 0.82, "应先申请再执行。")));
        when(fixture.workflow.generate(eq("虚构制度如何执行？"), anyList()))
            .thenReturn(new AgentDraft(
                "answered", "根据授权制度，应先申请再执行。", List.of("doc-test-safe")
            ));

        DemoChatResponseVO response = fixture.service.answer(
            "虚构制度如何执行？", "space-test", "test-user-allowed"
        );

        assertThat(response.mode()).isEqualTo("AGENTIC_RAG");
        assertThat(response.citations()).extracting(CitationVO::documentId)
            .containsExactly("doc-test-safe");
        verify(fixture.search).search(
            "虚构制度执行流程", "space-test", "test-user-allowed", 8, 0.30
        );
    }

    @Test
    void rejectsCitationOutsideAuthorizedEvidenceAndFallsBackToExtractiveAnswer() {
        Fixture fixture = fixture();
        DemoChatResponseVO fallback = fallback();
        when(fixture.delegate.answer("虚构问题", "space-test", "test-user-allowed"))
            .thenReturn(fallback);
        when(fixture.workflow.plan("虚构问题", List.of()))
            .thenReturn(new AgentPlan(List.of("虚构查询")));
        when(fixture.search.search(
            "虚构查询", "space-test", "test-user-allowed", 8, 0.30
        )).thenReturn(List.of(evidence("doc-test-safe", 0.8, "授权内容")));
        when(fixture.workflow.generate(eq("虚构问题"), anyList()))
            .thenReturn(new AgentDraft(
                "answered", "不应采用此回答", List.of("doc-test-forbidden")
            ));

        DemoChatResponseVO response = fixture.service.answer(
            "虚构问题", "space-test", "test-user-allowed"
        );

        assertThat(response).isSameAs(fallback);
        assertThat(response.answer()).doesNotContain("不应采用此回答");
    }

    @Test
    void modelFailureFallsBackWithoutCallingSearch() {
        Fixture fixture = fixture();
        DemoChatResponseVO fallback = fallback();
        when(fixture.delegate.answer("虚构问题", null, "test-user-allowed"))
            .thenReturn(fallback);
        when(fixture.workflow.plan("虚构问题", List.of()))
            .thenThrow(new RagException(
                "RAG_CHAT_MODEL_UNAVAILABLE", "对话模型暂时不可用", HttpStatus.SERVICE_UNAVAILABLE
            ));

        DemoChatResponseVO response = fixture.service.answer(
            "虚构问题", null, "test-user-allowed"
        );

        assertThat(response).isSameAs(fallback);
        verify(fixture.search, never()).search(any(), nullable(String.class), any(), eq(8), eq(0.30));
    }

    @Test
    void subjectWithoutAuthorizedKnowledgeSkipsModelAndSearch() {
        Fixture fixture = fixture();
        when(fixture.search.hasAuthorizedKnowledge("space-test", "test-user-denied"))
            .thenReturn(false);

        DemoChatResponseVO response = fixture.service.answer(
            "虚构问题", "space-test", "test-user-denied"
        );

        assertThat(response.status()).isEqualTo("insufficient");
        verifyNoInteractions(fixture.workflow);
        verify(fixture.search, never()).search(any(), nullable(String.class), any(), eq(8), eq(0.30));
    }

    @Test
    void retriesOnlyOnceWhenFirstRoundEvidenceIsWeak() {
        Fixture fixture = fixture();
        when(fixture.workflow.plan(eq("虚构缩写问题"), anyList()))
            .thenReturn(
                new AgentPlan(List.of("虚构缩写")),
                new AgentPlan(List.of("虚构缩写完整名称"))
            );
        when(fixture.search.search(
            "虚构缩写", null, "test-user-allowed", 8, 0.30
        )).thenReturn(List.of(evidence("doc-test-weak", 0.35, "弱相关内容")));
        when(fixture.search.search(
            "虚构缩写完整名称", null, "test-user-allowed", 8, 0.30
        )).thenReturn(List.of(evidence("doc-test-strong", 0.80, "强相关内容")));
        when(fixture.workflow.generate(eq("虚构缩写问题"), anyList()))
            .thenReturn(new AgentDraft(
                "answered", "强相关回答", List.of("doc-test-strong")
            ));

        DemoChatResponseVO response = fixture.service.answer(
            "虚构缩写问题", null, "test-user-allowed"
        );

        assertThat(response.answer()).isEqualTo("强相关回答");
        verify(fixture.workflow).plan("虚构缩写问题", List.of());
        verify(fixture.workflow).plan("虚构缩写问题", List.of("虚构缩写"));
    }

    @Test
    void refusesWithoutGenerationAfterTwoEmptyAuthorizedSearches() {
        Fixture fixture = fixture();
        when(fixture.workflow.plan(eq("虚构未知问题"), anyList()))
            .thenReturn(new AgentPlan(List.of("第一次查询")), new AgentPlan(List.of("第二次查询")));
        when(fixture.search.search(eq("第一次查询"), eq(null), eq("test-user-denied"), eq(8), eq(0.30)))
            .thenReturn(List.of());
        when(fixture.search.search(eq("第二次查询"), eq(null), eq("test-user-denied"), eq(8), eq(0.30)))
            .thenReturn(List.of());

        DemoChatResponseVO response = fixture.service.answer(
            "虚构未知问题", null, "test-user-denied"
        );

        assertThat(response.status()).isEqualTo("insufficient");
        assertThat(response.citations()).isEmpty();
        verify(fixture.workflow, never()).generate(eq("虚构未知问题"), anyList());
    }

    @Test
    void truncatesUntrustedContextBeforeGeneration() {
        Fixture fixture = fixture();
        fixture.properties.getAgent().setMaxContextChars(500);
        fixture.properties.getAgent().setMaxContextTokens(500);
        when(fixture.workflow.plan("虚构长文问题", List.of()))
            .thenReturn(new AgentPlan(List.of("虚构长文查询")));
        when(fixture.search.search(
            "虚构长文查询", null, "test-user-allowed", 8, 0.30
        )).thenReturn(List.of(evidence("doc-test-long", 0.9, "字".repeat(5000))));
        when(fixture.workflow.generate(eq("虚构长文问题"), anyList()))
            .thenReturn(new AgentDraft("answered", "有界回答", List.of("doc-test-long")));

        fixture.service.answer("虚构长文问题", null, "test-user-allowed");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuthorizedEvidence>> captor = ArgumentCaptor.forClass(List.class);
        verify(fixture.workflow).generate(eq("虚构长文问题"), captor.capture());
        assertThat(captor.getValue().getFirst().content().length()).isLessThan(500);
    }

    private Fixture fixture() {
        RagProperties properties = properties();
        InMemoryRagQueryService delegate = mock(InMemoryRagQueryService.class);
        AuthorizedKnowledgeSearch search = mock(AuthorizedKnowledgeSearch.class);
        AgentWorkflow workflow = mock(AgentWorkflow.class);
        AgenticRagQueryService service = new AgenticRagQueryService(
            delegate, search, workflow, properties
        );
        when(search.hasAuthorizedKnowledge(nullable(String.class), anyString())).thenReturn(true);
        service.validateConfiguration();
        return new Fixture(service, delegate, search, workflow, properties);
    }

    private RagProperties properties() {
        RagProperties properties = new RagProperties();
        properties.getAgent().setEnabled(true);
        properties.getChat().setApiKey("fictional-chat-key");
        properties.getChat().setMaxRequestChars(20_000);
        properties.getChat().setMaxResponseChars(12_000);
        properties.getChat().setMaxOutputTokens(1200);
        return properties;
    }

    private AuthorizedEvidence evidence(String documentId, double score, String content) {
        return new AuthorizedEvidence(
            documentId, documentId + "#0", "虚构标题", "第一节", content, "2026-07-01", score
        );
    }

    private DemoChatResponseVO fallback() {
        return new DemoChatResponseVO(
            "answered", "抽取式安全降级回答", true, "REAL_EMBEDDING_RETRIEVAL", List.of(), List.of()
        );
    }

    private record Fixture(
        AgenticRagQueryService service,
        InMemoryRagQueryService delegate,
        AuthorizedKnowledgeSearch search,
        AgentWorkflow workflow,
        RagProperties properties
    ) {
    }
}
