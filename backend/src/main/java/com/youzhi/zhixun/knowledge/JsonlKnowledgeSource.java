package com.youzhi.zhixun.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youzhi.zhixun.common.RagException;
import com.youzhi.zhixun.config.RagProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class JsonlKnowledgeSource implements KnowledgeSource {
    private final RagProperties.Knowledge config;
    private final ObjectMapper objectMapper;

    public JsonlKnowledgeSource(RagProperties properties, ObjectMapper objectMapper) {
        this.config = properties.getKnowledge();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<KnowledgeDocument> load() {
        Path source = Path.of(config.getFile()).toAbsolutePath().normalize();
        validateFile(source);
        List<KnowledgeDocument> documents = new ArrayList<>();
        Set<String> documentIds = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                if (documents.size() >= config.getMaxDocuments()) {
                    throw sourceError("知识文档数量超过限制");
                }
                KnowledgeDocument document = parse(line, lineNumber);
                validateDocument(document);
                if (!documentIds.add(document.documentId())) {
                    throw sourceError("知识文档标识重复");
                }
                documents.add(document);
            }
        } catch (RagException exception) {
            throw exception;
        } catch (IOException exception) {
            throw sourceError("知识文件暂时无法读取");
        }
        if (documents.isEmpty()) {
            throw sourceError("知识文件没有可索引文档");
        }
        return List.copyOf(documents);
    }

    private void validateFile(Path source) {
        try {
            if (!Files.isRegularFile(source)) {
                throw sourceError("知识文件尚未准备");
            }
            long size = Files.size(source);
            if (size <= 0 || size > config.getMaxSourceBytes()) {
                throw sourceError("知识文件大小超过限制");
            }
        } catch (IOException exception) {
            throw sourceError("知识文件暂时无法读取");
        }
    }

    private KnowledgeDocument parse(String line, int lineNumber) {
        try {
            return objectMapper.readValue(line, KnowledgeDocument.class);
        } catch (JsonProcessingException exception) {
            throw sourceError("知识文件第 " + lineNumber + " 行格式不合法");
        }
    }

    private void validateDocument(KnowledgeDocument document) {
        if (document == null
            || invalid(document.documentId(), 128)
            || invalid(document.title(), 200)
            || invalid(document.spaceId(), 64)
            || invalid(document.spaceName(), 100)
            || invalid(document.nodeId(), 64)
            || invalid(document.nodeName(), 100)
            || invalid(document.section(), 200)
            || invalid(document.updatedAt(), 32)
            || invalid(document.content(), config.getMaxDocumentChars())
            || unsafeSourceFile(document.sourceFile())
            || invalid(document.sourceFormat(), 16)
            || !document.sourceFormat().matches("[a-z0-9]+")) {
            throw sourceError("知识文档字段缺失或超过限制");
        }
        List<String> subjects = document.allowedUserIds();
        if (subjects == null || subjects.isEmpty() || subjects.size() > 500
            || subjects.stream().anyMatch(subject -> invalid(subject, 128))) {
            throw sourceError("知识文档必须配置有效的用户授权范围");
        }
    }

    private boolean invalid(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }

    private boolean unsafeSourceFile(String value) {
        if (invalid(value, 512)) return true;
        if (value.chars().anyMatch(character -> character < 32)) return true;
        String normalized = value.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) return true;
        return List.of(normalized.split("/")).contains("..");
    }

    private RagException sourceError(String message) {
        return new RagException("RAG_SOURCE_INVALID", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
