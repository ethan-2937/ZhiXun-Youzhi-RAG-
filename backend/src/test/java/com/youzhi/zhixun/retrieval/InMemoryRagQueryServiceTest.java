package com.youzhi.zhixun.retrieval;

import com.youzhi.zhixun.config.RagProperties;
import com.youzhi.zhixun.common.RagException;
import com.youzhi.zhixun.embedding.EmbeddingClient;
import com.youzhi.zhixun.knowledge.KnowledgeDocument;
import com.youzhi.zhixun.knowledge.KnowledgeSource;
import com.youzhi.zhixun.vo.DemoChatResponseVO;
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
        return properties;
    }

    private KnowledgeSource source(List<KnowledgeDocument> documents) {
        return () -> documents;
    }

    private KnowledgeDocument document(String id, String title, String content, String allowedUserId) {
        return new KnowledgeDocument(
            id,
            title,
            "space-test",
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
