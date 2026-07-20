package com.youzhi.zhixun.security;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.List;

public record AuthenticatedPrincipal(
    String userId,
    String unionId,
    String displayName,
    String department,
    String authSource,
    List<String> roles
) implements Principal, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return userId;
    }
}
