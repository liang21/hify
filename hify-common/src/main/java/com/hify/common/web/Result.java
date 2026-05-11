package com.hify.common.web;

import com.hify.common.exception.ErrorCode;
import lombok.Data;

/**
 * Unified API response wrapper
 *
 * @param <T> data type
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;

    private static final int SUCCESS_CODE = 200;
    private static final int FAIL_CODE = 500;
    private static final String SUCCESS_MESSAGE = "success";

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * Success response with data
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /**
     * Success response without data
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * Success response with custom message
     */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(SUCCESS_CODE, message, data);
    }

    /**
     * Failure response with default code and message
     */
    public static <T> Result<T> fail() {
        return new Result<T>(FAIL_CODE, "Internal server error", null);
    }

    /**
     * Failure response with custom message
     */
    public static <T> Result<T> fail(String message) {
        return new Result<T>(FAIL_CODE, message, null);
    }

    /**
     * Failure response with custom code and message
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<T>(code, message, null);
    }

    /**
     * Failure response from ErrorCode
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<T>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * Check if response is successful
     */
    public boolean isSuccess() {
        return code == SUCCESS_CODE;
    }
}
