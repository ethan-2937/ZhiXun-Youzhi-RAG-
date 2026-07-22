package com.youzhi.zhixun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {
    private boolean enabled;
    private Embedding embedding = new Embedding();
    private Chat chat = new Chat();
    private Agent agent = new Agent();
    private Knowledge knowledge = new Knowledge();
    private Retrieval retrieval = new Retrieval();
    private Diagnostics diagnostics = new Diagnostics();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    public Chat getChat() { return chat; }
    public void setChat(Chat chat) { this.chat = chat; }
    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }

    public Knowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(Retrieval retrieval) {
        this.retrieval = retrieval;
    }

    public Diagnostics getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public static class Embedding {
        private String baseUrl = "http://127.0.0.1:11434/v1";
        private String apiKey = "";
        private String model = "text-embedding-v4";
        private int dimension = 1024;
        private int batchSize = 8;
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(30);
        private int maxInputChars = 1600;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getDimension() { return dimension; }
        public void setDimension(int dimension) { this.dimension = dimension; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
        public int getMaxInputChars() { return maxInputChars; }
        public void setMaxInputChars(int maxInputChars) { this.maxInputChars = maxInputChars; }
    }

    public static class Chat {
        private String baseUrl = "http://127.0.0.1:11434/v1";
        private String apiKey = "";
        private String model = "qwen-plus";
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(60);
        private int maxRequestChars = 20_000;
        private int maxResponseChars = 12_000;
        private int maxOutputTokens = 800;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
        public int getMaxRequestChars() { return maxRequestChars; }
        public void setMaxRequestChars(int maxRequestChars) { this.maxRequestChars = maxRequestChars; }
        public int getMaxResponseChars() { return maxResponseChars; }
        public void setMaxResponseChars(int maxResponseChars) { this.maxResponseChars = maxResponseChars; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    }

    public static class Agent {
        private boolean enabled;
        private int maxQueries = 3;
        private int maxRounds = 2;
        private int maxQueryChars = 500;
        private int candidateTopK = 8;
        private double minScore = 0.30;
        private int maxEvidence = 6;
        private int maxContextChars = 8000;
        private int maxContextTokens = 6000;
        private int maxAnswerChars = 4000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxQueries() { return maxQueries; }
        public void setMaxQueries(int maxQueries) { this.maxQueries = maxQueries; }
        public int getMaxRounds() { return maxRounds; }
        public void setMaxRounds(int maxRounds) { this.maxRounds = maxRounds; }
        public int getMaxQueryChars() { return maxQueryChars; }
        public void setMaxQueryChars(int maxQueryChars) { this.maxQueryChars = maxQueryChars; }
        public int getCandidateTopK() { return candidateTopK; }
        public void setCandidateTopK(int candidateTopK) { this.candidateTopK = candidateTopK; }
        public double getMinScore() { return minScore; }
        public void setMinScore(double minScore) { this.minScore = minScore; }
        public int getMaxEvidence() { return maxEvidence; }
        public void setMaxEvidence(int maxEvidence) { this.maxEvidence = maxEvidence; }
        public int getMaxContextChars() { return maxContextChars; }
        public void setMaxContextChars(int maxContextChars) { this.maxContextChars = maxContextChars; }
        public int getMaxContextTokens() { return maxContextTokens; }
        public void setMaxContextTokens(int maxContextTokens) { this.maxContextTokens = maxContextTokens; }
        public int getMaxAnswerChars() { return maxAnswerChars; }
        public void setMaxAnswerChars(int maxAnswerChars) { this.maxAnswerChars = maxAnswerChars; }
    }

    public static class Knowledge {
        private String file = "/knowledge/documents.jsonl";
        private String rawDirectory = "/knowledge/raw";
        private long maxSourceBytes = 5_242_880;
        private long maxRawFileBytes = 52_428_800;
        private int maxDocuments = 200;
        private int maxDocumentChars = 100_000;
        private int maxPreviewChars = 50_000;
        private int chunkChars = 1200;
        private int chunkOverlapChars = 120;
        private int maxChunks = 2000;

        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        public String getRawDirectory() { return rawDirectory; }
        public void setRawDirectory(String rawDirectory) { this.rawDirectory = rawDirectory; }
        public long getMaxSourceBytes() { return maxSourceBytes; }
        public void setMaxSourceBytes(long maxSourceBytes) { this.maxSourceBytes = maxSourceBytes; }
        public long getMaxRawFileBytes() { return maxRawFileBytes; }
        public void setMaxRawFileBytes(long maxRawFileBytes) { this.maxRawFileBytes = maxRawFileBytes; }
        public int getMaxDocuments() { return maxDocuments; }
        public void setMaxDocuments(int maxDocuments) { this.maxDocuments = maxDocuments; }
        public int getMaxDocumentChars() { return maxDocumentChars; }
        public void setMaxDocumentChars(int maxDocumentChars) { this.maxDocumentChars = maxDocumentChars; }
        public int getMaxPreviewChars() { return maxPreviewChars; }
        public void setMaxPreviewChars(int maxPreviewChars) { this.maxPreviewChars = maxPreviewChars; }
        public int getChunkChars() { return chunkChars; }
        public void setChunkChars(int chunkChars) { this.chunkChars = chunkChars; }
        public int getChunkOverlapChars() { return chunkOverlapChars; }
        public void setChunkOverlapChars(int chunkOverlapChars) { this.chunkOverlapChars = chunkOverlapChars; }
        public int getMaxChunks() { return maxChunks; }
        public void setMaxChunks(int maxChunks) { this.maxChunks = maxChunks; }
    }

    public static class Retrieval {
        private int topK = 4;
        private double minScore = 0.45;
        private int maxCitations = 3;
        private int maxExcerptChars = 260;

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public double getMinScore() { return minScore; }
        public void setMinScore(double minScore) { this.minScore = minScore; }
        public int getMaxCitations() { return maxCitations; }
        public void setMaxCitations(int maxCitations) { this.maxCitations = maxCitations; }
        public int getMaxExcerptChars() { return maxExcerptChars; }
        public void setMaxExcerptChars(int maxExcerptChars) { this.maxExcerptChars = maxExcerptChars; }
    }

    public static class Diagnostics {
        private boolean enabled;
        private int maxCandidates = 50;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxCandidates() { return maxCandidates; }
        public void setMaxCandidates(int maxCandidates) { this.maxCandidates = maxCandidates; }
    }
}
