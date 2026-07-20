package com.youzhi.zhixun.retrieval;

import com.youzhi.zhixun.common.RagException;
import com.youzhi.zhixun.config.RagProperties;
import com.youzhi.zhixun.embedding.EmbeddingClient;
import com.youzhi.zhixun.knowledge.KnowledgeChunk;
import com.youzhi.zhixun.knowledge.KnowledgeDocument;
import com.youzhi.zhixun.knowledge.KnowledgeSource;
import com.youzhi.zhixun.knowledge.TextChunker;
import com.youzhi.zhixun.vo.CitationVO;
import com.youzhi.zhixun.vo.DemoChatResponseVO;
import com.youzhi.zhixun.vo.DemoUserVO;
import com.youzhi.zhixun.vo.KnowledgeNodeVO;
import com.youzhi.zhixun.vo.KnowledgeSpaceVO;
import com.youzhi.zhixun.vo.RetrievalCandidateVO;
import com.youzhi.zhixun.vo.RetrievalDiagnosticsVO;
import com.youzhi.zhixun.vo.WorkspaceVO;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InMemoryRagQueryService implements RagQueryService, RetrievalDiagnosticsService {
    private static final String MODE = "REAL_EMBEDDING_RETRIEVAL";

    private final RagProperties properties;
    private final KnowledgeSource knowledgeSource;
    private final EmbeddingClient embeddingClient;
    private volatile List<KnowledgeDocument> documents = List.of();
    private volatile List<EmbeddedChunk> index = List.of();
    private volatile boolean ready;

    public InMemoryRagQueryService(
        RagProperties properties,
        KnowledgeSource knowledgeSource,
        EmbeddingClient embeddingClient
    ) {
        this.properties = properties;
        this.knowledgeSource = knowledgeSource;
        this.embeddingClient = embeddingClient;
    }

    @PostConstruct
    public void initialize() {
        if (!properties.isEnabled()) return;
        validateConfiguration();
        List<KnowledgeDocument> loadedDocuments = knowledgeSource.load();
        List<KnowledgeChunk> chunks = loadedDocuments.stream()
            .flatMap(document -> TextChunker.split(
                document,
                properties.getKnowledge().getChunkChars(),
                properties.getKnowledge().getChunkOverlapChars()
            ).stream())
            .toList();
        if (chunks.isEmpty() || chunks.size() > properties.getKnowledge().getMaxChunks()) {
            throw indexError("知识分块数量超过限制");
        }

        List<EmbeddedChunk> builtIndex = new ArrayList<>(chunks.size());
        int batchSize = properties.getEmbedding().getBatchSize();
        for (int offset = 0; offset < chunks.size(); offset += batchSize) {
            List<KnowledgeChunk> batch = chunks.subList(offset, Math.min(chunks.size(), offset + batchSize));
            List<String> inputs = batch.stream().map(this::embeddingText).toList();
            List<float[]> vectors = embeddingClient.embed(inputs);
            for (int index = 0; index < batch.size(); index++) {
                builtIndex.add(new EmbeddedChunk(batch.get(index), normalize(vectors.get(index))));
            }
        }
        this.documents = List.copyOf(loadedDocuments);
        this.index = List.copyOf(builtIndex);
        this.ready = true;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public WorkspaceVO workspace(String userId) {
        ensureReady();
        List<KnowledgeDocument> authorized = documents.stream()
            .filter(document -> isAuthorized(document.allowedUserIds(), userId))
            .toList();
        List<KnowledgeSpaceVO> spaces = buildSpaces(authorized);
        List<String> sampleQuestions = authorized.stream()
            .map(document -> "“" + document.title() + "”主要讲了什么？")
            .distinct()
            .limit(3)
            .toList();
        return new WorkspaceVO(
            "智询",
            "真实语义检索 · 试运行",
            new DemoUserVO("当前用户", "授权资料范围", MODE),
            spaces,
            sampleQuestions,
            authorized.size(),
            spaces.size()
        );
    }

    @Override
    public DemoChatResponseVO answer(String question, String spaceId, String userId) {
        ensureReady();
        List<ScoredChunk> matches = rankAuthorized(question, spaceId, userId, properties.getRetrieval().getTopK())
            .stream()
            .filter(item -> item.score() >= properties.getRetrieval().getMinScore())
            .toList();
        if (matches.isEmpty()) return insufficient();

        List<CitationVO> citations = citations(matches, userId);
        if (citations.isEmpty()) return insufficient();
        String answer = composeExtractiveAnswer(citations);
        return new DemoChatResponseVO("answered", answer, true, MODE, citations, List.of());
    }

    @Override
    public RetrievalDiagnosticsVO diagnose(String question, String spaceId, int limit, String userId) {
        if (!properties.getDiagnostics().isEnabled()) {
            throw new RagException("RAG_DIAGNOSTICS_DISABLED", "检索诊断未启用", HttpStatus.NOT_FOUND);
        }
        ensureReady();
        int boundedLimit = Math.min(limit, properties.getDiagnostics().getMaxCandidates());
        List<ScoredChunk> matches = rankAuthorized(question, spaceId, userId, boundedLimit);
        List<RetrievalCandidateVO> candidates = new ArrayList<>(matches.size());
        for (int index = 0; index < matches.size(); index++) {
            ScoredChunk match = matches.get(index);
            candidates.add(new RetrievalCandidateVO(
                index + 1,
                match.chunk().documentId(),
                match.chunk().chunkId(),
                roundedScore(match.score())
            ));
        }
        return new RetrievalDiagnosticsVO(MODE, !matches.isEmpty(), List.copyOf(candidates));
    }

    private List<ScoredChunk> rankAuthorized(String question, String spaceId, String userId, int limit) {
        List<EmbeddedChunk> authorized = index.stream()
            .filter(item -> isAuthorized(item.chunk().allowedUserIds(), userId))
            .filter(item -> spaceId == null || spaceId.isBlank() || item.chunk().spaceId().equals(spaceId))
            .toList();
        if (authorized.isEmpty()) return List.of();

        float[] queryVector = normalize(embeddingClient.embed(List.of(question.strip())).getFirst());
        return authorized.stream()
            .map(item -> new ScoredChunk(item.chunk(), dot(queryVector, item.vector())))
            .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
            .limit(limit)
            .toList();
    }

    private double roundedScore(double score) {
        return Math.round(score * 1_000_000d) / 1_000_000d;
    }

    private List<KnowledgeSpaceVO> buildSpaces(List<KnowledgeDocument> authorized) {
        Map<String, List<KnowledgeDocument>> bySpace = new LinkedHashMap<>();
        authorized.forEach(document -> bySpace.computeIfAbsent(document.spaceId(), ignored -> new ArrayList<>()).add(document));
        return bySpace.values().stream().map(spaceDocuments -> {
            KnowledgeDocument first = spaceDocuments.getFirst();
            Map<String, List<KnowledgeDocument>> byNode = new LinkedHashMap<>();
            spaceDocuments.forEach(document -> byNode.computeIfAbsent(document.nodeId(), ignored -> new ArrayList<>()).add(document));
            List<KnowledgeNodeVO> nodes = byNode.values().stream().map(nodeDocuments -> {
                KnowledgeDocument node = nodeDocuments.getFirst();
                return new KnowledgeNodeVO(
                    node.nodeId(),
                    node.nodeName(),
                    "file",
                    nodeDocuments.size(),
                    node.sourceFormat(),
                    node.updatedAt()
                );
            }).toList();
            return new KnowledgeSpaceVO(first.spaceId(), first.spaceName(), "当前账号授权资料", spaceDocuments.size(), nodes);
        }).toList();
    }

    private List<CitationVO> citations(List<ScoredChunk> matches, String userId) {
        Map<String, CitationVO> unique = new LinkedHashMap<>();
        for (ScoredChunk match : matches) {
            KnowledgeChunk chunk = match.chunk();
            if (!isAuthorized(chunk.allowedUserIds(), userId)) continue;
            unique.putIfAbsent(chunk.documentId(), new CitationVO(
                chunk.documentId(),
                chunk.title(),
                chunk.section(),
                excerpt(chunk.content()),
                chunk.updatedAt()
            ));
            if (unique.size() >= properties.getRetrieval().getMaxCitations()) break;
        }
        return List.copyOf(unique.values());
    }

    private String composeExtractiveAnswer(List<CitationVO> citations) {
        StringBuilder answer = new StringBuilder("从当前账号有权访问的资料中检索到以下相关内容：");
        for (int index = 0; index < citations.size(); index++) {
            answer.append(index == 0 ? " " : "；")
                .append(index + 1)
                .append(". ")
                .append(citations.get(index).excerpt());
        }
        return answer.toString();
    }

    private String embeddingText(KnowledgeChunk chunk) {
        String value = chunk.title() + "\n" + chunk.section() + "\n" + chunk.content();
        int limit = properties.getEmbedding().getMaxInputChars();
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private String excerpt(String content) {
        int limit = properties.getRetrieval().getMaxExcerptChars();
        String normalized = content.replaceAll("\\s+", " ").strip();
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit) + "…";
    }

    private DemoChatResponseVO insufficient() {
        return new DemoChatResponseVO(
            "insufficient",
            "没有在当前账号有权访问的资料中找到足够依据。我不会使用模型常识补写公司内容。",
            false,
            MODE,
            List.of(),
            List.of()
        );
    }

    private boolean isAuthorized(List<String> allowedUserIds, String userId) {
        return userId != null && allowedUserIds != null && allowedUserIds.contains(userId);
    }

    private float[] normalize(float[] vector) {
        if (vector == null || vector.length != properties.getEmbedding().getDimension()) {
            throw indexError("向量维度与配置不一致");
        }
        double sum = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) throw indexError("向量包含无效数值");
            sum += value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) throw indexError("向量不能为零向量");
        float[] normalized = new float[vector.length];
        for (int index = 0; index < vector.length; index++) normalized[index] = (float) (vector[index] / norm);
        return normalized;
    }

    private double dot(float[] left, float[] right) {
        double value = 0;
        for (int index = 0; index < left.length; index++) value += left[index] * right[index];
        return value;
    }

    private void validateConfiguration() {
        RagProperties.Embedding embedding = properties.getEmbedding();
        RagProperties.Knowledge knowledge = properties.getKnowledge();
        RagProperties.Retrieval retrieval = properties.getRetrieval();
        RagProperties.Diagnostics diagnostics = properties.getDiagnostics();
        boolean invalid = embedding.getApiKey() == null || embedding.getApiKey().isBlank()
            || embedding.getDimension() <= 0 || embedding.getBatchSize() < 1 || embedding.getBatchSize() > 64
            || embedding.getMaxInputChars() < 100 || knowledge.getChunkChars() < 100
            || knowledge.getChunkOverlapChars() < 0 || knowledge.getChunkOverlapChars() >= knowledge.getChunkChars()
            || knowledge.getRawDirectory() == null || knowledge.getRawDirectory().isBlank()
            || knowledge.getMaxRawFileBytes() < 1 || knowledge.getMaxPreviewChars() < 100
            || knowledge.getMaxPreviewChars() > 200_000 || knowledge.getMaxChunks() < 1
            || retrieval.getTopK() < 1 || retrieval.getTopK() > 20
            || retrieval.getMinScore() < -1 || retrieval.getMinScore() > 1
            || retrieval.getMaxCitations() < 1 || retrieval.getMaxCitations() > retrieval.getTopK()
            || retrieval.getMaxExcerptChars() < 40 || retrieval.getMaxExcerptChars() > 1000
            || diagnostics.getMaxCandidates() < 1 || diagnostics.getMaxCandidates() > 50;
        if (invalid) throw indexError("RAG 配置不合法");
    }

    private void ensureReady() {
        if (!ready) throw indexError("真实检索尚未启用");
    }

    private RagException indexError(String message) {
        return new RagException("RAG_INDEX_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private record EmbeddedChunk(KnowledgeChunk chunk, float[] vector) {
    }

    private record ScoredChunk(KnowledgeChunk chunk, double score) {
    }
}
