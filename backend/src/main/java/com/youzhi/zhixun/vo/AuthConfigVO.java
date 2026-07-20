package com.youzhi.zhixun.vo;

public record AuthConfigVO(
    String mode,
    String corpId,
    boolean dingtalkReady,
    int authorizationCodeMaxLength,
    String csrfToken
) {
}
