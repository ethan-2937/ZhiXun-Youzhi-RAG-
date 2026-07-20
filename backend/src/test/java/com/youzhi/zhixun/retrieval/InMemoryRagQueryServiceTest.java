package com.youzhi.zhixun.retrieval;

import com.youzhi.zhixun.config.RagProperties;
import com.youzhi.zhixun.common.RagException;
import com.youzhi.zhixun.embedding.EmbeddingClient;
import com.youzhi.zhixun.knowledge.KnowledgeDocument;
import com.youzhi.zhixun.knowledge.KnowledgeSource;
import com.youzhi.zhixun.vo.DemoChatResponseVO;
import com.youzhi.zhixun.vo.RetrievalDiagnosticsVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryRagQueryServiceTest {
    @Test
    void filtersAclBeforeSimilarityAndNeverCitesForbiddenDocument() {
        RagProperties properties = properties();
        RecordingEmbeddingClient embeddingClient = new RecordingEmbeddingClient();
        InMemoryRagQueryService service = new InMemoryRagQueryService(
            properties,
            source(List.of(
                document("doc-authorized", "授权制度", "授权内容规定应先申请再执行。", "test-user-allowed"),
                document("doc-forbidden", "禁止查看制度", "禁止内容与问题完全一致。", "test-user-other")
            )),
            embeddingClient
        );
        service.initialize();

        DemoChatResponseVO response = service.answer("申请制度", "space-test", "test-user-allowed");

        assertThat(response.status()).isEqualTo("answered");
        assertThat(response.citations()).extracting(citation -> citation.documentId())
            .containsExactly("doc-authorized")
            .doesNotContain("doc-forbidden");
        assertThat(response.answer()).doesNotContain("禁止内容");

        RetrievalDiagnosticsVO diagnostics = service.diagnose(
            "申请制度", "space-test", 10, "test-user-allowed"
        );
        assertThat(diagnostics.hasAuthorizedCandidate()).isTrue();
        assertThat(diagnostics.candidates())
            .extracting(candidate -> candidate.documentId())
            .containsOnly("doc-authorized")
            .doesNotContain("doc-forbidden");
    }

    @Test
    void subjectWithoutAuthorizedDocumentsFailsClosedBeforeEmbeddingQuestion() {
        RagProperties properties = properties();
        RecordingEmbeddingClient embeddingClient = new RecordingEmbeddingClient();
        InMemoryRagQueryService service = new InMemoryRagQueryService(
            properties,
            source(List.of(document(
                "doc-forbidden",
                "禁止查看制度",
                "禁止内容与问题完全一致。",
                "test-user-other"
            ))),
            embeddingClient
        );
        service.initialize();
        int callsAfterIndexing = embeddingClient.calls.size();

        DemoChatResponseVO response = service.answer("禁止内容", "space-test", "test-user-denied");

        assertThat(response.status()).isEqualTo("insufficient");
        assertThat(response.citations()).isEmpty();
        assertThat(embeddingClient.calls).hasSize(callsAfterIndexing);
        assertThat(service.workspace("test-user-denied").spaces()).isEmpty();

        RetrievalDiagnosticsVO diagnostics = service.diagnose(
            "禁止内容", "space-test", 10, "test-user-denied"
        );
        assertThat(diagnostics.hasAuthorizedCandidate()).isFalse();
        assertThat(diagnostics.candidates()).isEmpty();
        assertThat(embeddingClient.calls).hasSize(callsAfterIndexing);
    }

    @Test
    void diagnosticsAppliesSpaceFilterAndConfiguredCandidateLimit() {
        RagProperties properties = properties();
        properties.getDiagnostics().setMaxCandidates(1);
        InMemoryRagQueryService service = new InMemoryRagQueryService(
            properties,
            source(List.of(
                document("doc-target", "目标资料", "目标空间内容。", "test-user-allowed", "space-target"),
                document("doc-other", "其他资料", "其他空间内容。", "test-user-allowed", "space-other")
            )),
            new RecordingEmbeddingClient()
        );
        service.initialize();

        RetrievalDiagnosticsVO diagnostics = service.diagnose(
            "目标空间", "space-target", 50, "test-user-allowed"
        );

        assertThat(diagnostics.candidates()).hasSize(1);
        assertThat(diagnostics.candidates().getFirst().documentId()).isEqualTo("doc-target");
        assertThat(diagnostics.candidates().getFirst().rank()).isEqualTo(1);
    }

    @Test
    void diagnosticsFailsClosedWhenDisabled() {
        RagProperties properties = properties();
        properties.getDiagnostics().setEnabled(false);
        InMemoryRagQueryService service = new InMemoryRagQueryService(
            properties,
            source(List.of(document(
                "doc-authorized", "授权制度", "授权内容。", "test-user-allowed"
            ))),
            new RecordingEmbeddingClient()
        );
        service.initialize();

        assertThatThrownBy(() -> service.diagnose(
            "授权制度", "space-test", 10, "test-user-allowed"
        )).isInstanceOf(RagException.class)
            .hasMessage("检索诊断未启用");
    }

    @Test
    void rejectsIndexThatExceedsChunkBudget() {
        RagProperties properties = properties();
        properties.getKnowledge().setChunkChars(100);
        properties.getKnowledge().setChunkOverlapChars(10);
        properties.getKnowledge().setMaxChunks(1);
        InMemoryRagQueryService service = new InMemoryRagQueryService(
            properties,
            source(List.of(document(
                "doc-large",
                "超长虚构资料",
                "虚构段落。".repeat(80),
                "test-user-allowed"
            ))),
            new RecordingEmbeddingClient()
        );

        assertThatThrownBy(service::initialize)
            .isInstanceOf(RagException.class)
            .hasMessage("知识分块数量超过限制");
    }

    private RagProperties properties() {
        RagProperties properties = new RagProperties();
        properties.setEnabled(true);
        properties.getEmbedding().setApiKey("fictional-key");
        properties.getEmbedding().setDimension(2);
        properties.getEmbedding().setBatchSize(8);
        properties.getEmbedding().setMaxInputChars(1600);
        properties.getKnowledge().setChunkChars(200);
        properties.getKnowledge().setChunkOverlapChars(20);
        properties.getRetrieval().setTopK(2);
        properties.getRetrieval().setMaxCitations(2);
        properties.getRetrieval().setMinScore(0.1);
        properties.getDiagnostics().setEnabled(true);
        return properties;
    }

    private KnowledgeSource source(List<KnowledgeDocument> documents) {
        return () -> documents;
    }

    private KnowledgeDocument document(String id, String title, String content, String allowedUserId) {
        return document(id, title, content, allowedUserId, "space-test");
    }

    private KnowledgeDocument document(
        String id,
        String title,
        String content,
        String allowedUserId,
        String spaceId
    ) {
        return new KnowledgeDocument(
            id,
            title,
            spaceId,
            "测试空间",
            "node-test",
            "测试目录",
            "第一节",
            "2026-07-01",
            content,
            "虚构测试资料.md",
            "md",
            List.of(allowedUserId)
        );
    }

    private static class RecordingEmbeddingClient implements EmbeddingClient {
        private final List<List<String>> calls = new ArrayList<>();

        @Override
        public List<float[]> embed(List<String> inputs) {
            calls.add(List.copyOf(inputs));
            return inputs.stream().map(input -> {
                if (input.contains("禁止")) return new float[]{1.0f, 0.0f};
                if (input.contains("授权")) return new float[]{0.8f, 0.2f};
                return new float[]{1.0f, 0.0f};
            }).toList();
        }
    }
}
