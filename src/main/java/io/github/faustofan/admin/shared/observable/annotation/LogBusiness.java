package io.github.faustofan.admin.shared.observable.annotation;

import io.github.faustofan.admin.shared.observable.constants.LogLevel;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务日志注解
 * <p>
 * 标注在业务方法或类上，拦截器会自动记录业务日志，包括模块、操作、耗时、异常等信息。
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * @LogBusiness(module = "User", operation = "CreateUser", level = LogLevel.INFO)
 * public void createUser(UserDto dto) {
 *     // 业务实现
 * }
 * }
 * </pre>
 */
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogBusiness {

    /**
     * 业务模块名称
     * <p>
     * 默认使用配置中的 defaultModule
     */
    String module() default "";

    /**
     * 业务操作名称
     * <p>
     * 默认使用方法名
     */
    String operation() default "";

    /**
     * 日志级别
     * <p>
     * 默认 INFO
     */
    LogLevel level() default LogLevel.INFO;

    /**
     * 是否记录入参
     */
    boolean logParams() default false;

    /**
     * 是否记录返回值
     */
    boolean logResult() default false;
}
