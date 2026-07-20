package com.youzhi.zhixun.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RetrievalDiagnosticsRequestVO(
    @NotBlank(message = "问题不能为空")
    @Size(max = 1000, message = "问题不能超过1000个字符")
    String question,

    @Size(max = 64, message = "空间标识不能超过64个字符")
    String spaceId,

    @Min(value = 1, message = "候选数量不能小于1")
    @Max(value = 50, message = "候选数量不能超过50")
    int limit
) {
}
