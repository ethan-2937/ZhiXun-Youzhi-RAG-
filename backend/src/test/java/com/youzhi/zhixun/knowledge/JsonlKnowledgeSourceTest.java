package com.youzhi.zhixun.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youzhi.zhixun.common.RagException;
import com.youzhi.zhixun.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonlKnowledgeSourceTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsBoundedDocumentWithExplicitAcl() throws IOException {
        Path source = tempDir.resolve("documents.jsonl");
        Files.writeString(source, validLine("[\"test-user-demo-001\"]"));
        JsonlKnowledgeSource knowledgeSource = source(source);

        assertThat(knowledgeSource.load())
            .singleElement()
            .satisfies(document -> {
                assertThat(document.documentId()).isEqualTo("doc-test-policy-001");
                assertThat(document.allowedUserIds()).containsExactly("test-user-demo-001");
            });
    }

    @Test
    void rejectsDocumentWithoutAcl() throws IOException {
        Path source = tempDir.resolve("documents.jsonl");
        Files.writeString(source, validLine("[]"));
        JsonlKnowledgeSource knowledgeSource = source(source);

        assertThatThrownBy(knowledgeSource::load)
            .isInstanceOf(RagException.class)
            .hasMessage("知识文档必须配置有效的用户授权范围");
    }

    @Test
    void rejectsSourceLargerThanConfiguredByteBudget() throws IOException {
        Path source = tempDir.resolve("documents.jsonl");
        Files.writeString(source, validLine("[\"test-user-demo-001\"]"));
        RagProperties properties = new RagProperties();
        properties.getKnowledge().setFile(source.toString());
        properties.getKnowledge().setMaxSourceBytes(32);
        JsonlKnowledgeSource knowledgeSource = new JsonlKnowledgeSource(properties, new ObjectMapper());

        assertThatThrownBy(knowledgeSource::load)
            .isInstanceOf(RagException.class)
            .hasMessage("知识文件大小超过限制");
    }

    @Test
    void rejectsUnsafeRawSourcePath() throws IOException {
        Path source = tempDir.resolve("documents.jsonl");
        Files.writeString(source, validLine("[\"test-user-demo-001\"]")
            .replace("虚构测试资料.md", "../escape.md"));

        assertThatThrownBy(source(source)::load)
            .isInstanceOf(RagException.class)
            .hasMessage("知识文档字段缺失或超过限制");
    }

    private JsonlKnowledgeSource source(Path source) {
        RagProperties properties = new RagProperties();
        properties.getKnowledge().setFile(source.toString());
        return new JsonlKnowledgeSource(properties, new ObjectMapper());
    }

    private String validLine(String allowedUserIds) {
        return """
            {"documentId":"doc-test-policy-001","title":"虚构测试制度","spaceId":"space-test",
            "spaceName":"测试空间","nodeId":"node-test","nodeName":"测试目录","section":"第一节",
            "updatedAt":"2026-07-01","content":"这是一段完全虚构的测试内容。",
            "sourceFile":"虚构测试资料.md","sourceFormat":"md","allowedUserIds":%s}
            """.formatted(allowedUserIds).replace("\n", "");
    }
}
