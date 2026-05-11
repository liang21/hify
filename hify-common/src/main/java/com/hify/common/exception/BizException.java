package com.hify.common.exception;

/**
 * Business exception
 */
public class BizException extends RuntimeException {

    private final int code;
    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.errorCode = null;
    }

    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    public int getCode() {
        return code;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Create BizException from ErrorCode
     */
    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    /**
     * Create BizException from ErrorCode with custom message
     */
    public static BizException of(ErrorCode errorCode, String customMessage) {
        return new BizException(errorCode, customMessage);
    }
}
