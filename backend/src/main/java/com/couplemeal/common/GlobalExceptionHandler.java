package com.couplemeal.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：业务异常、参数校验、缺 token 头等统一收敛为 R。
 * 拦截器抛出的 BizException(NOT_LOGIN) 也会在此捕获。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：直接返回其 ErrorCode */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException ex) {
        return R.fail(ex.getErrorCode());
    }

    /** 缺请求头（如未带 Authorization）：视为未登录 */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public R<Void> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("缺失请求头: {}", ex.getHeaderName());
        return R.fail(ErrorCode.NOT_LOGIN);
    }

    /** 参数校验失败：取首条字段错误文案，兜底码 PARAM_INVALID */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException ex) {
        FieldError first = ex.getBindingResult().getFieldError();
        String msg = first != null ? first.getDefaultMessage() : "参数有误";
        return R.fail(ErrorCode.PARAM_INVALID.getCode(), msg);
    }

    /** 其余未预期异常：记录日志，返回系统错误 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleOther(Exception ex, HttpServletRequest request) {
        log.error("未处理异常 uri={}", request.getRequestURI(), ex);
        return R.fail(ErrorCode.SYS_ERROR);
    }
}
