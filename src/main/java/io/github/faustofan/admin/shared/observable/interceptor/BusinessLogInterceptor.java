package io.github.faustofan.admin.shared.observable.interceptor;

import io.github.faustofan.admin.shared.observable.ObservableFacade;
import io.github.faustofan.admin.shared.observable.annotation.LogBusiness;
import io.github.faustofan.admin.shared.observable.config.ObservableConfig;
import io.github.faustofan.admin.shared.observable.constants.LogLevel;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.util.Arrays;
import java.util.Map;

/**
 * 业务日志拦截器
 * <p>
 * 拦截标注了 {@link LogBusiness} 注解的方法，自动记录业务日志。
 * <p>
 * 记录内容包括：
 * <ul>
 *   <li>模块名称</li>
 *   <li>操作名称</li>
 *   <li>执行耗时</li>
 *   <li>入参（可选）</li>
 *   <li>返回值（可选）</li>
 *   <li>异常信息（如有）</li>
 * </ul>
 */
@LogBusiness
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class BusinessLogInterceptor {

    @Inject
    ObservableFacade observableFacade;

    @Inject
    ObservableConfig config;

    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        // 获取注解（方法级优先，类级次之）
        LogBusiness annotation = ctx.getMethod().getAnnotation(LogBusiness.class);
        if (annotation == null) {
            annotation = ctx.getTarget().getClass().getAnnotation(LogBusiness.class);
        }
        if (annotation == null) {
            return ctx.proceed();
        }

        // 解析模块和操作名称
        String module = annotation.module();
        if (module == null || module.isEmpty()) {
            module = config.log().business().defaultModule();
        }
        String operation = annotation.operation();
        if (operation == null || operation.isEmpty()) {
            operation = ctx.getMethod().getName();
        }
        LogLevel level = annotation.level();

        // 构建入参日志
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("Executing ").append(operation);
        if (annotation.logParams() && ctx.getParameters() != null && ctx.getParameters().length > 0) {
            messageBuilder.append(", params=").append(Arrays.toString(ctx.getParameters()));
        }

        long start = System.currentTimeMillis();
        try {
            Object result = ctx.proceed();
            long duration = System.currentTimeMillis() - start;

            // 构建成功日志
            StringBuilder successMessage = new StringBuilder();
            successMessage.append(operation).append(" completed, duration=").append(duration).append("ms");
            if (annotation.logResult() && result != null) {
                successMessage.append(", result=").append(result);
            }

            observableFacade.logBusiness(module, operation, level, successMessage.toString());
            
            // 自动收集业务指标 - 成功
            Map<String, String> tags = Map.of(
                "module", module,
                "operation", operation,
                "status", "success"
            );
            observableFacade.incrementCounter(
                io.github.faustofan.admin.shared.observable.constants.ObservableConstants.BusinessMetric.OPERATION_TOTAL,
                tags
            );
            observableFacade.recordTimer(
                io.github.faustofan.admin.shared.observable.constants.ObservableConstants.BusinessMetric.OPERATION_DURATION,
                duration,
                tags
            );
            
            return result;
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;
            // 记录异常日志（使用 ERROR 级别）
            String errorMessage = String.format("%s failed after %dms, error=%s: %s",
                    operation, duration, ex.getClass().getSimpleName(), ex.getMessage());
            observableFacade.logBusiness(module, operation, LogLevel.ERROR, errorMessage);
            
            // 自动收集业务指标 - 失败
            Map<String, String> failureTags = Map.of(
                "module", module,
                "operation", operation,
                "status", "failure",
                "error", ex.getClass().getSimpleName()
            );
            observableFacade.incrementCounter(
                io.github.faustofan.admin.shared.observable.constants.ObservableConstants.BusinessMetric.OPERATION_TOTAL,
                failureTags
            );
            observableFacade.incrementCounter(
                io.github.faustofan.admin.shared.observable.constants.ObservableConstants.BusinessMetric.OPERATION_FAILURE,
                failureTags
            );
            
            throw ex;
        }
    }
}
