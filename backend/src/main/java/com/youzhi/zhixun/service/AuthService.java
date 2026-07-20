package com.youzhi.zhixun.service;

import com.youzhi.zhixun.vo.AuthConfigVO;
import com.youzhi.zhixun.vo.CurrentUserVO;
import com.youzhi.zhixun.vo.DingTalkLoginRequestVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

public interface AuthService {
    AuthConfigVO config(String csrfToken);

    CurrentUserVO authenticateDemo(HttpServletRequest request);

    CurrentUserVO authenticateDingTalk(DingTalkLoginRequestVO loginRequest, HttpServletRequest request);

    CurrentUserVO currentUser(Authentication authentication);
}
