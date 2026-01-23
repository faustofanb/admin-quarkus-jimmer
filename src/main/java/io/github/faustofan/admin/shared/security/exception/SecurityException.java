package io.github.faustofan.admin.shared.security.exception;

import io.github.faustofan.admin.shared.security.constants.SecurityErrorCode;

/**
 * 安全异常基类
 * <p>
 * 所有安全相关异常的父类，包含错误码。
 */
public abstract class SecurityException extends RuntimeException {

    private final SecurityErrorCode errorCode;

    protected SecurityException(SecurityErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    protected SecurityException(SecurityErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected SecurityException(SecurityErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    protected SecurityException(SecurityErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 获取错误码
     */
    public SecurityErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 获取错误码字符串
     */
    public String getCode() {
        return errorCode.getCode();
    }

    @Override
    public String toString() {
        return String.format("%s: [%s] %s",
            getClass().getSimpleName(),
            errorCode.getCode(),
            getMessage()
        );
    }
}
