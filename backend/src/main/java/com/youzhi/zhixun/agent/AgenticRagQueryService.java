package com.youzhi.zhixun.agent;

import com.youzhi.zhixun.common.RagException;
import com.youzhi.zhixun.config.RagProperties;
import com.youzhi.zhixun.retrieval.AuthorizedEvidence;
import com.youzhi.zhixun.retrieval.AuthorizedKnowledgeSearch;
import com.youzhi.zhixun.retrieval.InMemoryRagQueryService;
import com.youzhi.zhixun.retrieval.RagQueryService;
import com.youzhi.zhixun.vo.CitationVO;
import com.youzhi.zhixun.vo.DemoChatResponseVO;
import com.youzhi.zhixun.vo.DemoUserVO;
import com.youzhi.zhixun.vo.WorkspaceVO;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Primary
@Service
public class AgenticRagQueryService implements RagQueryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticRagQueryService.class);
    private static final String MODE = "AGENTIC_RAG";
    private static final String INSUFFICIENT =
        "没有在当前账号有权访问的资料中找到足够依据。我不会使用模型常识补写公司内容。";

    private final InMemoryRagQueryService delegate;
    private final AuthorizedKnowledgeSearch authorizedSearch;
    private final AgentWorkflow workflow;
    private final RagProperties properties;

    public AgenticRagQueryService(
        InMemoryRagQueryService delegate,
        AuthorizedKnowledgeSearch authorizedSearch,
        AgentWorkflow workflow,
        RagProperties properties
    ) {
        this.delegate = delegate;
        this.authorizedSearch = authorizedSearch;
        this.workflow = workflow;
        this.properties = properties;
    }

    @PostConstruct
    void validateConfiguration() {
        if (!properties.getAgent().isEnabled()) return;
        RagProperties.Agent agent = properties.getAgent();
        RagProperties.Chat chat = properties.getChat();
        boolean invalid = chat.getApiKey() == null || chat.getApiKey().isBlank()
            || chat.getBaseUrl() == null || chat.getBaseUrl().isBlank()
            || chat.getModel() == null || chat.getModel().isBlank()
            || chat.getMaxRequestChars() < 2000 || chat.getMaxRequestChars() > 100_000
            || chat.getMaxResponseChars() < 100 || chat.getMaxResponseChars() > 50_000
            || chat.getMaxOutputTokens() < 64 || chat.getMaxOutputTokens() > 8000
            || agent.getMaxQueries() < 1 || agent.getMaxQueries() > 3
            || agent.getMaxRounds() < 1 || agent.getMaxRounds() > 2
            || agent.getMaxQueryChars() < 20 || agent.getMaxQueryChars() > 1000
            || agent.getCandidateTopK() < 1 || agent.getCandidateTopK() > 20
            || agent.getMinScore() < -1 || agent.getMinScore() > 1
            || agent.getMaxEvidence() < 1 || agent.getMaxEvidence() > 10
            || agent.getMaxContextChars() < 500 || agent.getMaxContextChars() > 20_000
            || agent.getMaxContextTokens() < 500 || agent.getMaxContextTokens() > 12_000
            || agent.getMaxAnswerChars() < 100 || agent.getMaxAnswerChars() > 8000;
        if (invalid) throw unavailable("Agentic RAG 配置不合法");
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public String mode() {
        return properties.getAgent().isEnabled() && isReady() ? MODE : RagQueryService.super.mode();
    }

    @Override
    public WorkspaceVO workspace(String userId) {
        WorkspaceVO workspace = delegate.workspace(userId);
        if (!properties.getAgent().isEnabled()) return workspace;
        DemoUserVO user = workspace.user();
        return new WorkspaceVO(
            workspace.productName(),
            "Agentic RAG · 受控试运行",
            new DemoUserVO(user.displayName(), user.department(), MODE),
            workspace.spaces(),
            workspace.sampleQuestions(),
            workspace.indexedDocuments(),
            workspace.availableSpaces()
        );
    }

    @Override
    public DemoChatResponseVO answer(String question, String spaceId, String userId) {
        if (!properties.getAgent().isEnabled()) return delegate.answer(question, spaceId, userId);
        try {
            return agentAnswer(question, spaceId, userId);
        } catch (RagException exception) {
            LOGGER.warn("Agentic RAG fallback: code={}, reason={}", exception.getCode(), exception.getMessage());
            return delegate.answer(question, spaceId, userId);
        }
    }

    private DemoChatResponseVO agentAnswer(String question, String spaceId, String userId) {
        if (!authorizedSearch.hasAuthorizedKnowledge(spaceId, userId)) return insufficient();
        RagProperties.Agent config = properties.getAgent();
        List<String> previousQueries = new ArrayList<>();
        Map<String, AuthorizedEvidence> merged = new LinkedHashMap<>();
        for (int round = 0; round < config.getMaxRounds(); round++) {
            AgentPlan plan = workflow.plan(question, List.copyOf(previousQueries));
            previousQueries.addAll(plan.queries());
            for (String query : plan.queries()) {
                for (AuthorizedEvidence evidence : authorizedSearch.search(
                    query, spaceId, userId, config.getCandidateTopK(), config.getMinScore()
                )) {
                    merged.merge(evidence.chunkId(), evidence, this::higherScore);
                }
            }
            if (hasSufficientEvidence(merged.values())) break;
        }
        if (!hasSufficientEvidence(merged.values())) return insufficient();

        List<AuthorizedEvidence> evidence = boundedEvidence(merged.values());
        if (evidence.isEmpty()) return insufficient();
        AgentDraft draft = workflow.generate(question, evidence);
        return verifiedResponse(draft, evidence);
    }

    private boolean hasSufficientEvidence(Iterable<AuthorizedEvidence> evidence) {
        for (AuthorizedEvidence item : evidence) {
            if (item.score() >= properties.getRetrieval().getMinScore()) return true;
        }
        return false;
    }

    private AuthorizedEvidence higherScore(AuthorizedEvidence left, AuthorizedEvidence right) {
        return left.score() >= right.score() ? left : right;
    }

    private List<AuthorizedEvidence> boundedEvidence(Iterable<AuthorizedEvidence> candidates) {
        List<AuthorizedEvidence> ordered = new ArrayList<>();
        candidates.forEach(ordered::add);
        ordered.sort(Comparator.comparingDouble(AuthorizedEvidence::score).reversed());
        RagProperties.Agent config = properties.getAgent();
        int characterBudget = Math.min(config.getMaxContextChars(), config.getMaxContextTokens());
        int used = 0;
        List<AuthorizedEvidence> result = new ArrayList<>();
        for (AuthorizedEvidence item : ordered) {
            if (result.size() >= config.getMaxEvidence() || used >= characterBudget) break;
            int metadataChars = item.documentId().length() + item.title().length() + item.section().length() + 32;
            int remaining = characterBudget - used - metadataChars;
            if (remaining < 80) continue;
            String content = item.content().length() <= remaining
                ? item.content()
                : item.content().substring(0, remaining);
            result.add(new AuthorizedEvidence(
                item.documentId(), item.chunkId(), item.title(), item.section(),
                content, item.updatedAt(), item.score()
            ));
            used += metadataChars + content.length();
        }
        return List.copyOf(result);
    }

    private DemoChatResponseVO verifiedResponse(AgentDraft draft, List<AuthorizedEvidence> evidence) {
        if (draft.status().equals("insufficient")) return insufficient();
        if (!draft.status().equals("answered") || draft.citationDocumentIds().isEmpty()) {
            throw unavailable("Agent 回答缺少有效引用");
        }
        Map<String, AuthorizedEvidence> byDocument = new LinkedHashMap<>();
        evidence.forEach(item -> byDocument.putIfAbsent(item.documentId(), item));
        Set<String> uniqueIds = new LinkedHashSet<>(draft.citationDocumentIds());
        if (uniqueIds.size() > properties.getRetrieval().getMaxCitations()
            || !byDocument.keySet().containsAll(uniqueIds)) {
            throw unavailable("Agent 回答引用越过授权证据范围");
        }
        List<CitationVO> citations = uniqueIds.stream()
            .map(byDocument::get)
            .map(this::citation)
            .toList();
        return new DemoChatResponseVO(
            "answered", draft.answer(), true, MODE, citations, List.of()
        );
    }

    private CitationVO citation(AuthorizedEvidence evidence) {
        String normalized = evidence.content().replaceAll("\\s+", " ").strip();
        int limit = properties.getRetrieval().getMaxExcerptChars();
        String excerpt = normalized.length() <= limit ? normalized : normalized.substring(0, limit) + "…";
        return new CitationVO(
            evidence.documentId(), evidence.title(), evidence.section(), excerpt, evidence.updatedAt()
        );
    }

    private DemoChatResponseVO insufficient() {
        return new DemoChatResponseVO("insufficient", INSUFFICIENT, false, MODE, List.of(), List.of());
    }

    private RagException unavailable(String message) {
        return new RagException("RAG_AGENT_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
