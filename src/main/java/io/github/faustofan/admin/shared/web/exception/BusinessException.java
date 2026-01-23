package io.github.faustofan.admin.shared.web.exception;

import lombok.Getter;

/**
 * 业务异常
 * <p>
 * 用于业务逻辑中抛出的可预期异常，包含错误码和错误消息。
 * 该异常会被全局异常处理器捕获并转换为统一的 API 响应格式。
 * </p>
 *
 * @author fausto
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 使用错误码枚举构造业务异常
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    /**
     * 使用错误码枚举和自定义消息构造业务异常
     *
     * @param errorCode 错误码枚举
     * @param message   自定义错误消息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.message = message;
    }

    /**
     * 使用错误码枚举和原因构造业务异常
     *
     * @param errorCode 错误码枚举
     * @param cause     原因
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    /**
     * 使用错误码和消息直接构造业务异常
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    /**
     * 快捷方法：创建数据不存在异常
     *
     * @param entityName 实体名称
     * @return 业务异常
     */
    public static BusinessException notFound(String entityName) {
        return new BusinessException(GlobalErrorCode.DATA_NOT_FOUND, entityName + "不存在");
    }

    /**
     * 快捷方法：创建数据已存在异常
     *
     * @param message 提示消息
     * @return 业务异常
     */
    public static BusinessException alreadyExists(String message) {
        return new BusinessException(GlobalErrorCode.DATA_ALREADY_EXISTS, message);
    }

    /**
     * 快捷方法：创建操作不允许异常
     *
     * @param message 提示消息
     * @return 业务异常
     */
    public static BusinessException operationNotAllowed(String message) {
        return new BusinessException(GlobalErrorCode.OPERATION_NOT_ALLOWED, message);
    }

    /**
     * 快捷方法：创建权限不足异常
     *
     * @return 业务异常
     */
    public static BusinessException accessDenied() {
        return new BusinessException(GlobalErrorCode.ACCESS_DENIED);
    }

    /**
     * 快捷方法：创建参数校验异常
     *
     * @param message 校验失败消息
     * @return 业务异常
     */
    public static BusinessException validationError(String message) {
        return new BusinessException(GlobalErrorCode.VALIDATION_ERROR, message);
    }

    /**
     * 快捷方法：通用失败异常
     *
     * @param message 错误消息
     * @return 业务异常
     */
    public static BusinessException fail(String message) {
        // 使用 VALIDATION_ERROR 或类似的通用错误码，这里假设 VALIDATION_ERROR 适合
        // 或者如果有 INTERNAL_ERROR 也可以
        return new BusinessException(GlobalErrorCode.VALIDATION_ERROR, message);
    }
}

