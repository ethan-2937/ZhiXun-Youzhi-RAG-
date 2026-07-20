package com.youzhi.zhixun.controller;

import com.youzhi.zhixun.service.DemoExperienceService;
import com.youzhi.zhixun.security.AuthenticatedPrincipal;
import com.youzhi.zhixun.vo.DemoChatRequestVO;
import com.youzhi.zhixun.vo.DemoChatResponseVO;
import com.youzhi.zhixun.vo.WorkspaceVO;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoExperienceController {
    private final DemoExperienceService demoExperienceService;

    public DemoExperienceController(DemoExperienceService demoExperienceService) {
        this.demoExperienceService = demoExperienceService;
    }

    @GetMapping("/workspace")
    public WorkspaceVO workspace(Authentication authentication) {
        return demoExperienceService.workspace(subject(authentication));
    }

    @PostMapping("/chat")
    public DemoChatResponseVO chat(
        @Valid @RequestBody DemoChatRequestVO request,
        Authentication authentication
    ) {
        return demoExperienceService.answer(request.question(), request.spaceId(), subject(authentication));
    }

    private String subject(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal.userId();
        }
        return authentication.getName();
    }
}
