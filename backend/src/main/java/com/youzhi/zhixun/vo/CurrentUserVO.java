package com.youzhi.zhixun.vo;

import java.util.List;

public record CurrentUserVO(
    String userId,
    String displayName,
    String department,
    String authSource,
    List<String> roles
) {
}
