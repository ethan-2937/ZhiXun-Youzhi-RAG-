package com.youzhi.zhixun.common;

import com.youzhi.zhixun.vo.ApiErrorVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorVO> handleAuth(AuthException exception) {
        return ResponseEntity.status(exception.getStatus())
            .body(new ApiErrorVO(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(RagException.class)
    public ResponseEntity<ApiErrorVO> handleRag(RagException exception) {
        return ResponseEntity.status(exception.getStatus())
            .body(new ApiErrorVO(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorVO> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage() == null ? "请求参数不合法" : error.getDefaultMessage())
            .orElse("请求参数不合法");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiErrorVO("VALIDATION_FAILED", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorVO> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiErrorVO("INTERNAL_ERROR", "服务暂时不可用，请稍后再试"));
    }
}
