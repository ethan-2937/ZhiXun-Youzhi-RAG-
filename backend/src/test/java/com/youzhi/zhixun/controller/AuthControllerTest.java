package com.youzhi.zhixun.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void configIsPublicAndEstablishesSessionBoundCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("demo"))
            .andExpect(jsonPath("$.dingtalkReady").value(false))
            .andExpect(jsonPath("$.csrfToken").isNotEmpty())
            .andReturn();
        assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    @Test
    void protectedApiRejectsUnauthenticatedRequestWithSafeCode() throws Exception {
        mockMvc.perform(get("/api/workspace"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void demoLoginRequiresCsrfAndCreatesServerSession() throws Exception {
        mockMvc.perform(post("/api/auth/demo"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        MvcResult login = mockMvc.perform(post("/api/auth/demo").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value("test-user-demo-001"))
            .andExpect(jsonPath("$.authSource").value("DEMO"))
            .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("演示用户"));
        mockMvc.perform(get("/api/workspace").session(session))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/workspace").session(session))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void dingtalkEndpointFailsClosedWhenModeIsDemo() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "code", "fictional-one-time-code",
            "corpId", "corp-example"
        ));

        mockMvc.perform(post("/api/auth/dingtalk/inside")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("DINGTALK_LOGIN_DISABLED"));
    }

    @Test
    void oversizedAuthorizationCodeIsRejectedBeforeAnyAdapterCall() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "code", "x".repeat(513),
            "corpId", "corp-example"
        ));

        mockMvc.perform(post("/api/auth/dingtalk/inside")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
