package com.youzhi.zhixun.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DingTalkLoginRequestVO(
    @NotBlank(message = "免登码不能为空")
    @Size(max = 512, message = "免登码格式不合法")
    String code,

    @NotBlank(message = "企业标识不能为空")
    @Size(max = 128, message = "企业标识格式不合法")
    String corpId
) {
}
