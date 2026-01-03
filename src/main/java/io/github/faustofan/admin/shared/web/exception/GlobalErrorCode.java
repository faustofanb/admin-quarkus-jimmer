package io.github.faustofan.admin.shared.web.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码枚举
 * <p>
 * 定义系统级、认证级和参数校验级的错误码
 * </p>
 *
 * @author fausto
 */
@Getter
@AllArgsConstructor
public enum GlobalErrorCode implements ErrorCode {

    // ========== 成功 ==========
    SUCCESS(0, "成功"),

    // ========== 系统级错误 1xxx ==========
    INTERNAL_SERVER_ERROR(1000, "服务器内部错误"),
    SERVICE_UNAVAILABLE(1001, "服务不可用"),
    REQUEST_TIMEOUT(1002, "请求超时"),
    DATABASE_ERROR(1003, "数据库错误"),
    CACHE_ERROR(1004, "缓存错误"),
    MESSAGE_QUEUE_ERROR(1005, "消息队列错误"),
    SERIALIZATION_ERROR(1006, "序列化错误"),
    DESERIALIZATION_ERROR(1007, "反序列化错误"),
    RESOURCE_NOT_FOUND(1008, "资源不存在"),
    METHOD_NOT_ALLOWED(1009, "请求方法不允许"),
    TOO_MANY_REQUESTS(1010, "请求过于频繁"),

    // ========== 认证授权错误 2xxx ==========
    UNAUTHORIZED(2000, "未授权，请先登录"),
    TOKEN_EXPIRED(2001, "令牌已过期"),
    TOKEN_INVALID(2002, "令牌无效"),
    TOKEN_MISSING(2003, "令牌缺失"),
    ACCESS_DENIED(2004, "权限不足"),
    ACCOUNT_DISABLED(2005, "账号已被禁用"),
    ACCOUNT_LOCKED(2006, "账号已被锁定"),
    PASSWORD_ERROR(2007, "密码错误"),
    USER_NOT_FOUND(2008, "用户不存在"),
    TENANT_DISABLED(2009, "租户已被禁用"),
    TENANT_EXPIRED(2010, "租户已过期"),

    // ========== 参数校验错误 3xxx ==========
    BAD_REQUEST(3000, "请求参数错误"),
    PARAM_MISSING(3001, "必填参数缺失"),
    PARAM_TYPE_ERROR(3002, "参数类型错误"),
    PARAM_FORMAT_ERROR(3003, "参数格式错误"),
    PARAM_VALUE_INVALID(3004, "参数值无效"),
    PARAM_VALUE_TOO_LONG(3005, "参数值超出长度限制"),
    PARAM_VALUE_TOO_SHORT(3006, "参数值长度不足"),
    PARAM_VALUE_DUPLICATE(3007, "参数值重复"),
    VALIDATION_ERROR(3008, "数据校验失败"),

    // ========== 业务通用错误 4xxx ==========
    DATA_NOT_FOUND(4000, "数据不存在"),
    DATA_ALREADY_EXISTS(4001, "数据已存在"),
    DATA_STATUS_INVALID(4002, "数据状态无效"),
    DATA_REFERENCED(4003, "数据被引用，无法删除"),
    OPERATION_NOT_ALLOWED(4004, "操作不允许"),
    CONCURRENT_MODIFICATION(4005, "数据已被其他操作修改"),
    ;

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误消息
     */
    private final String message;
}

