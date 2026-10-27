package com.couplemeal.common;

import lombok.Getter;

/**
 * 业务异常：携带 ErrorCode，由 GlobalExceptionHandler 统一转成 R。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(errorCode.getMsg() + "：" + detail);
        this.errorCode = errorCode;
    }
}
