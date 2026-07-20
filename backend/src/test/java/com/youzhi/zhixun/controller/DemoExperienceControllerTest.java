package com.youzhi.zhixun.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "test-user-demo-001", roles = "EMPLOYEE")
class DemoExperienceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void workspaceExposesOnlyClearlyMarkedDemoData() throws Exception {
        mockMvc.perform(get("/api/workspace"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.releaseLabel").value("领导体验版 · MVP 0.1"))
            .andExpect(jsonPath("$.user.mode").value("DEMO_FIXTURE"))
            .andExpect(jsonPath("$.spaces.length()").value(3));
    }

    @Test
    void chatRejectsQuestionLargerThanPayloadContract() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of("question", "问".repeat(1001)));

        mockMvc.perform(post("/api/chat")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.message").value("问题不能超过1000个字符"));
    }

    @Test
    void chatReturnsInsufficientWithoutCitationsForUnknownQuestion() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of("question", "未知演示问题"));

        mockMvc.perform(post("/api/chat")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("insufficient"))
            .andExpect(jsonPath("$.grounded").value(false))
            .andExpect(jsonPath("$.citations.length()").value(0));
    }
}
