package com.youzhi.zhixun.controller;

import com.youzhi.zhixun.service.AuthService;
import com.youzhi.zhixun.vo.AuthConfigVO;
import com.youzhi.zhixun.vo.CurrentUserVO;
import com.youzhi.zhixun.vo.DingTalkLoginRequestVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/config")
    public AuthConfigVO config(CsrfToken csrfToken) {
        return authService.config(csrfToken.getToken());
    }

    @PostMapping("/demo")
    public CurrentUserVO authenticateDemo(HttpServletRequest request) {
        return authService.authenticateDemo(request);
    }

    @PostMapping("/dingtalk/inside")
    public CurrentUserVO authenticateDingTalk(
        @Valid @RequestBody DingTalkLoginRequestVO loginRequest,
        HttpServletRequest request
    ) {
        return authService.authenticateDingTalk(loginRequest, request);
    }

    @GetMapping("/me")
    public CurrentUserVO currentUser(Authentication authentication) {
        return authService.currentUser(authentication);
    }
}
