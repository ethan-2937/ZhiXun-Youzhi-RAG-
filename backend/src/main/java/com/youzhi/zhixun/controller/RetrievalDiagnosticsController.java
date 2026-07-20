package com.youzhi.zhixun.controller;

import com.youzhi.zhixun.retrieval.RetrievalDiagnosticsService;
import com.youzhi.zhixun.security.AuthenticatedPrincipal;
import com.youzhi.zhixun.vo.RetrievalDiagnosticsRequestVO;
import com.youzhi.zhixun.vo.RetrievalDiagnosticsVO;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/retrieval-diagnostics")
@ConditionalOnExpression(
    "'${app.auth.mode:demo}' == 'demo' && '${app.rag.enabled:false}' == 'true' "
        + "&& '${app.rag.diagnostics.enabled:false}' == 'true'"
)
public class RetrievalDiagnosticsController {
    private final RetrievalDiagnosticsService diagnosticsService;

    public RetrievalDiagnosticsController(RetrievalDiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @PostMapping
    public RetrievalDiagnosticsVO diagnose(
        @Valid @RequestBody RetrievalDiagnosticsRequestVO request,
        Authentication authentication
    ) {
        return diagnosticsService.diagnose(
            request.question(), request.spaceId(), request.limit(), subject(authentication)
        );
    }

    private String subject(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal.userId();
        }
        return authentication.getName();
    }
}
