package com.youzhi.zhixun.controller;

import com.youzhi.zhixun.retrieval.RetrievalDiagnosticsService;
import com.youzhi.zhixun.vo.RetrievalCandidateVO;
import com.youzhi.zhixun.vo.RetrievalDiagnosticsVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = RetrievalDiagnosticsController.class,
    properties = {"app.rag.enabled=true", "app.rag.diagnostics.enabled=true"}
)
@WithMockUser(username = "test-user-diagnostics-001", roles = "EMPLOYEE")
class RetrievalDiagnosticsControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RetrievalDiagnosticsService diagnosticsService;

    @Test
    void returnsOnlyCandidateIdentifiersRanksAndScores() throws Exception {
        when(diagnosticsService.diagnose(anyString(), anyString(), anyInt(), anyString()))
            .thenReturn(new RetrievalDiagnosticsVO(
                "REAL_EMBEDDING_RETRIEVAL",
                true,
                List.of(new RetrievalCandidateVO(1, "doc-test-public", "doc-test-public#0", 0.812345))
            ));

        mockMvc.perform(post("/api/admin/retrieval-diagnostics")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question":"虚构测试问题","spaceId":"space-test-public","limit":4}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidates[0].documentId").value("doc-test-public"))
            .andExpect(jsonPath("$.candidates[0].chunkId").value("doc-test-public#0"))
            .andExpect(jsonPath("$.candidates[0].rank").value(1))
            .andExpect(jsonPath("$.candidates[0].score").value(0.812345))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.anyOf(
                    org.hamcrest.Matchers.containsString("content"),
                    org.hamcrest.Matchers.containsString("title"),
                    org.hamcrest.Matchers.containsString("excerpt"),
                    org.hamcrest.Matchers.containsString("section")
                )
            )));
    }

    @Test
    void rejectsCandidateLimitAbovePayloadContract() throws Exception {
        mockMvc.perform(post("/api/admin/retrieval-diagnostics")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question":"虚构测试问题","limit":51}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
