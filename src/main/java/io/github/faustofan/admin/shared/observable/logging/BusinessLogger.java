package io.github.faustofan.admin.shared.observable.logging;

import io.github.faustofan.admin.shared.observable.config.ObservableConfig;
import io.github.faustofan.admin.shared.observable.constants.LogCategory;
import io.github.faustofan.admin.shared.observable.constants.LogLevel;
import io.github.faustofan.admin.shared.observable.constants.ObservableConstants;
import io.github.faustofan.admin.shared.observable.context.TraceContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 业务日志记录器
 * <p>
 * 提供结构化的业务日志记录能力，支持：
 * <ul>
 *   <li>多级日志</li>
 *   <li>上下文自动注入（trace, user, tenant等）</li>
 *   <li>模块化日志</li>
 *   <li>性能监控日志</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * @Inject
 * BusinessLogger businessLogger;
 *
 * // 简单日志
 * businessLogger.info("user", "login", "User logged in successfully");
 *
 * // 带数据的日志
 * businessLogger.info("order", "create", "Order created", Map.of("orderId", orderId));
 *
 * // 操作计时日志
 * businessLogger.logOperation("payment", "process", () -> paymentService.process(order));
 * }</pre>
 */
@ApplicationScoped
public class BusinessLogger {

    private static final Logger LOG = Logger.getLogger(BusinessLogger.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final ObservableConfig config;

    @Inject
    public BusinessLogger(ObservableConfig config) {
        this.config = config;
    }

    // ===========================
    // 基础日志方法
    // ===========================

    /**
     * 记录TRACE级别日志
     */
    public void trace(String module, String operation, String message) {
        log(LogLevel.TRACE, LogCategory.BUSINESS, module, operation, message, null, null);
    }

    /**
     * 记录DEBUG级别日志
     */
    public void debug(String module, String operation, String message) {
        log(LogLevel.DEBUG, LogCategory.BUSINESS, module, operation, message, null, null);
    }

    /**
     * 记录DEBUG级别日志（带数据）
     */
    public void debug(String module, String operation, String message, Map<String, Object> data) {
        log(LogLevel.DEBUG, LogCategory.BUSINESS, module, operation, message, data, null);
    }

    /**
     * 记录INFO级别日志
     */
    public void info(String module, String operation, String message) {
        log(LogLevel.INFO, LogCategory.BUSINESS, module, operation, message, null, null);
    }

    /**
     * 记录INFO级别日志（带数据）
     */
    public void info(String module, String operation, String message, Map<String, Object> data) {
        log(LogLevel.INFO, LogCategory.BUSINESS, module, operation, message, data, null);
    }

    /**
     * 记录WARN级别日志
     */
    public void warn(String module, String operation, String message) {
        log(LogLevel.WARN, LogCategory.BUSINESS, module, operation, message, null, null);
    }

    /**
     * 记录WARN级别日志（带异常）
     */
    public void warn(String module, String operation, String message, Throwable throwable) {
        log(LogLevel.WARN, LogCategory.BUSINESS, module, operation, message, null, throwable);
    }

    /**
     * 记录ERROR级别日志
     */
    public void error(String module, String operation, String message) {
        log(LogLevel.ERROR, LogCategory.BUSINESS, module, operation, message, null, null);
    }

    /**
     * 记录ERROR级别日志（带异常）
     */
    public void error(String module, String operation, String message, Throwable throwable) {
        log(LogLevel.ERROR, LogCategory.BUSINESS, module, operation, message, null, throwable);
    }

    /**
     * 记录ERROR级别日志（带数据和异常）
     */
    public void error(String module, String operation, String message, Map<String, Object> data, Throwable throwable) {
        log(LogLevel.ERROR, LogCategory.BUSINESS, module, operation, message, data, throwable);
    }

    // ===========================
    // 分类日志方法
    // ===========================

    /**
     * 记录安全日志
     */
    public void security(LogLevel level, String operation, String message) {
        log(level, LogCategory.SECURITY, ObservableConstants.MdcKey.MODULE, operation, message, null, null);
    }

    /**
     * 记录安全日志（带数据）
     */
    public void security(LogLevel level, String operation, String message, Map<String, Object> data) {
        log(level, LogCategory.SECURITY, ObservableConstants.MdcKey.MODULE, operation, message, data, null);
    }

    /**
     * 记录审计日志
     */
    public void audit(String operation, String message, Map<String, Object> data) {
        log(LogLevel.INFO, LogCategory.AUDIT, config.log().business().defaultModule(), operation, message, data, null);
    }

    /**
     * 记录性能日志
     */
    public void performance(String module, String operation, long durationMs, Map<String, Object> data) {
        Map<String, Object> perfData = data != null ? new java.util.HashMap<>(data) : new java.util.HashMap<>();
        perfData.put("durationMs", durationMs);
        
        LogLevel level = durationMs > ObservableConstants.SLOW_OPERATION_THRESHOLD_MS ? LogLevel.WARN : LogLevel.DEBUG;
        log(level, LogCategory.PERFORMANCE, module, operation, "Operation completed", perfData, null);
    }

    // ===========================
    // 操作计时日志
    // ===========================

    /**
     * 执行操作并记录日志（有返回值）
     */
    public <T> T logOperation(String module, String operation, Supplier<T> supplier) {
        long startTime = System.currentTimeMillis();
        try {
            T result = supplier.get();
            long duration = System.currentTimeMillis() - startTime;
            logOperationSuccess(module, operation, duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logOperationFailure(module, operation, duration, e);
            throw e;
        }
    }

    /**
     * 执行操作并记录日志（无返回值）
     */
    public void logOperation(String module, String operation, Runnable runnable) {
        long startTime = System.currentTimeMillis();
        try {
            runnable.run();
            long duration = System.currentTimeMillis() - startTime;
            logOperationSuccess(module, operation, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logOperationFailure(module, operation, duration, e);
            throw e;
        }
    }

    private void logOperationSuccess(String module, String operation, long durationMs) {
        Map<String, Object> data = Map.of(
                "durationMs", durationMs,
                "result", ObservableConstants.ResultValue.SUCCESS
        );
        
        LogLevel level = durationMs > ObservableConstants.SLOW_OPERATION_THRESHOLD_MS ? LogLevel.WARN : LogLevel.INFO;
        String message = durationMs > ObservableConstants.SLOW_OPERATION_THRESHOLD_MS 
                ? "Slow operation completed" 
                : "Operation completed";
        
        log(level, LogCategory.BUSINESS, module, operation, message, data, null);
    }

    private void logOperationFailure(String module, String operation, long durationMs, Throwable throwable) {
        Map<String, Object> data = Map.of(
                "durationMs", durationMs,
                "result", ObservableConstants.ResultValue.FAILURE,
                "error", throwable.getClass().getSimpleName()
        );
        log(LogLevel.ERROR, LogCategory.BUSINESS, module, operation, "Operation failed", data, throwable);
    }

    // ===========================
    // 核心日志方法
    // ===========================

    /**
     * 核心日志方法
     */
    public void log(LogLevel level, LogCategory category, String module, String operation, 
                    String message, Map<String, Object> data, Throwable throwable) {
        
        if (!config.log().business().enabled()) {
            return;
        }

        // 设置MDC上下文
        try {
            MDC.put(ObservableConstants.MdcKey.LOG_CATEGORY, category.getCode());
            if (module != null) {
                MDC.put(ObservableConstants.MdcKey.MODULE, module);
            }
            if (operation != null) {
                MDC.put(ObservableConstants.MdcKey.OPERATION, operation);
            }

            // 构建日志消息
            String formattedMessage = formatMessage(category, module, operation, message, data);

            // 记录日志
            switch (level) {
                case TRACE -> LOG.trace(formattedMessage);
                case DEBUG -> LOG.debug(formattedMessage);
                case INFO -> LOG.info(formattedMessage);
                case WARN -> {
                    if (throwable != null) {
                        LOG.warn(formattedMessage, throwable);
                    } else {
                        LOG.warn(formattedMessage);
                    }
                }
                case ERROR, FATAL -> {
                    if (throwable != null) {
                        LOG.error(formattedMessage, throwable);
                    } else {
                        LOG.error(formattedMessage);
                    }
                }
            }
        } finally {
            MDC.remove(ObservableConstants.MdcKey.LOG_CATEGORY);
            MDC.remove(ObservableConstants.MdcKey.MODULE);
            MDC.remove(ObservableConstants.MdcKey.OPERATION);
        }
    }

    /**
     * 格式化日志消息
     */
    private String formatMessage(LogCategory category, String module, String operation,
                                  String message, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        
        // 添加类别图标
        sb.append(category.getIcon()).append(" ");
        
        // 添加模块和操作
        if (module != null) {
            sb.append("[").append(module).append("]");
        }
        if (operation != null) {
            sb.append("[").append(operation).append("]");
        }
        sb.append(" ");
        
        // 添加消息
        sb.append(message);
        
        // 添加数据
        if (data != null && !data.isEmpty()) {
            sb.append(" | ");
            data.forEach((key, value) -> sb.append(key).append("=").append(value).append(", "));
            // 移除最后的逗号和空格
            sb.setLength(sb.length() - 2);
        }
        
        // 添加trace上下文
        if (config.log().business().includeContext()) {
            String traceId = TraceContext.currentTraceId();
            if (traceId != null) {
                sb.append(" | traceId=").append(traceId);
            }
        }
        
        return sb.toString();
    }

    // ===========================
    // 便捷方法
    // ===========================

    /**
     * 创建带模块的日志器
     */
    public ModuleLogger forModule(String module) {
        return new ModuleLogger(this, module);
    }

    /**
     * 模块日志器 - 固定模块名的便捷包装
     */
    public static class ModuleLogger {
        private final BusinessLogger logger;
        private final String module;

        ModuleLogger(BusinessLogger logger, String module) {
            this.logger = logger;
            this.module = module;
        }

        public void trace(String operation, String message) {
            logger.trace(module, operation, message);
        }

        public void debug(String operation, String message) {
            logger.debug(module, operation, message);
        }

        public void debug(String operation, String message, Map<String, Object> data) {
            logger.debug(module, operation, message, data);
        }

        public void info(String operation, String message) {
            logger.info(module, operation, message);
        }

        public void info(String operation, String message, Map<String, Object> data) {
            logger.info(module, operation, message, data);
        }

        public void warn(String operation, String message) {
            logger.warn(module, operation, message);
        }

        public void warn(String operation, String message, Throwable throwable) {
            logger.warn(module, operation, message, throwable);
        }

        public void error(String operation, String message) {
            logger.error(module, operation, message);
        }

        public void error(String operation, String message, Throwable throwable) {
            logger.error(module, operation, message, throwable);
        }

        public <T> T logOperation(String operation, Supplier<T> supplier) {
            return logger.logOperation(module, operation, supplier);
        }

        public void logOperation(String operation, Runnable runnable) {
            logger.logOperation(module, operation, runnable);
        }
    }
}
