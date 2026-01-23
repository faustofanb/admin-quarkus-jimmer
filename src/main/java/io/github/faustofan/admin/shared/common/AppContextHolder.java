package io.github.faustofan.admin.shared.common;

import io.github.faustofan.admin.shared.async.constants.AsyncConstants;
import io.github.faustofan.admin.shared.async.context.AsyncContext;
import org.jboss.logging.Logger;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;

/**
 * 异步上下文持有者
 * <p>
 * 提供上下文的捕获、恢复和清理功能。
 * 使用 ThreadLocal 存储当前线程的上下文信息。
 */
public final class AppContextHolder {

    private static final Logger LOG = Logger.getLogger(AppContextHolder.class);

    /**
     * 当前线程的应用上下文
     */
    private static final ThreadLocal<AppContext> APP_CONTEXT = new InheritableThreadLocal<>();

    private AppContextHolder() {
        // 禁止实例化
    }

    // ===========================
    // 上下文捕获
    // ===========================

    /**
     * 捕获当前线程的完整上下文
     * <p>
     * 包括 MDC 上下文和应用上下文
     *
     * @return 当前上下文快照
     */
    public static AsyncContext capture() {
        Map<String, String> mdcCopy = MDC.getCopyOfContextMap();
        AppContext appContext = APP_CONTEXT.get();

        LOG.debugv("Captured async context: mdc={0}, appContext={1}",
                mdcCopy != null ? mdcCopy.keySet() : "null",
                appContext);

        return AsyncContext.of(mdcCopy, appContext);
    }

    /**
     * 仅捕获 MDC 上下文
     *
     * @return MDC上下文快照
     */
    public static AsyncContext captureMdc() {
        return AsyncContext.fromMdc(MDC.getCopyOfContextMap());
    }

    /**
     * 仅捕获应用上下文
     *
     * @return 应用上下文快照
     */
    public static AsyncContext captureApp() {
        return AsyncContext.fromApp(APP_CONTEXT.get());
    }

    // ===========================
    // 上下文恢复
    // ===========================

    /**
     * 恢复上下文到当前线程
     *
     * @param context 要恢复的上下文
     */
    public static void restore(AsyncContext context) {
        if (context == null || context.isEmpty()) {
            LOG.trace("Skipping restore of empty/null context");
            return;
        }

        // 恢复 MDC
        Map<String, String> mdcContext = context.getMdcContext();
        if (!mdcContext.isEmpty()) {
            MDC.setContextMap(mdcContext);
            LOG.debugv("Restored MDC context: {0}", mdcContext.keySet());
        }

        // 恢复应用上下文
        AppContext appContext = context.getAppContext();
        if (appContext != null && !appContext.isEmpty()) {
            APP_CONTEXT.set(appContext);
            LOG.debugv("Restored app context: {0}", appContext);
        }
    }

    // ===========================
    // 上下文清理
    // ===========================

    /**
     * 清理当前线程的所有上下文
     */
    public static void clear() {
        MDC.clear();
        APP_CONTEXT.remove();
        LOG.trace("Cleared all async context");
    }

    /**
     * 清理当前线程的 MDC 上下文
     */
    public static void clearMdc() {
        MDC.clear();
    }

    /**
     * 清理当前线程的应用上下文
     */
    public static void clearAppContext() {
        APP_CONTEXT.remove();
    }

    // ===========================
    // 应用上下文操作
    // ===========================

    /**
     * 设置应用上下文
     *
     * @param appContext 应用上下文
     */
    public static void setAppContext(AppContext appContext) {
        if (appContext != null) {
            APP_CONTEXT.set(appContext);
        }
    }

    /**
     * 获取应用上下文
     *
     * @return Optional<AppContext>
     */
    public static Optional<AppContext> getAppContext() {
        return Optional.ofNullable(APP_CONTEXT.get());
    }

    /**
     * 获取应用上下文（必须存在）
     *
     * @return AppContext
     * @throws IllegalStateException 如果上下文不存在
     */
    public static AppContext requireAppContext() {
        return getAppContext()
                .orElseThrow(() -> new IllegalStateException("AppContext is required but not present"));
    }

    // ===========================
    // MDC 便捷操作
    // ===========================

    /**
     * 设置 MDC 值
     */
    public static void setMdc(String key, String value) {
        if (key != null && value != null) {
            MDC.put(key, value);
        }
    }

    /**
     * 获取 MDC 值
     */
    public static Optional<String> getMdc(String key) {
        return Optional.ofNullable(MDC.get(key));
    }

    /**
     * 移除 MDC 值
     */
    public static void removeMdc(String key) {
        if (key != null) {
            MDC.remove(key);
        }
    }

    // ===========================
    // 常用快捷方法（从AppContext读取）
    // ===========================

    /**
     * 获取当前用户ID
     */
    public static Optional<Long> getUserId() {
        return getAppContext().flatMap(AppContext::getUserId);
    }

    /**
     * 获取当前用户名
     */
    public static Optional<String> getUsername() {
        return getAppContext().flatMap(AppContext::getUsername);
    }

    /**
     * 获取当前租户ID
     */
    public static Optional<Long> getTenantId() {
        return getAppContext().flatMap(AppContext::getTenantId);
    }

    /**
     * 获取当前请求ID
     */
    public static Optional<String> getRequestId() {
        return getAppContext().flatMap(AppContext::getRequestId);
    }

    /**
     * 判断当前是否已认证
     */
    public static boolean isAuthenticated() {
        return getAppContext().map(AppContext::isAuthenticated).orElse(false);
    }

    // ===========================
    // MDC Key 快捷方法
    // ===========================

    /**
     * 获取 TraceId（从MDC）
     */
    public static Optional<String> getTraceId() {
        return getMdc(AsyncConstants.MdcKeys.TRACE_ID);
    }

    /**
     * 设置 TraceId（到MDC）
     */
    public static void setTraceId(String traceId) {
        setMdc(AsyncConstants.MdcKeys.TRACE_ID, traceId);
    }

    /**
     * 同步 AppContext 到 MDC
     * <p>
     * 将 AppContext 中的关键信息同步到 MDC，便于日志输出
     */
    public static void syncAppContextToMdc() {
        getAppContext().ifPresent(ctx -> {
            ctx.getUserId().ifPresent(id -> setMdc(AsyncConstants.MdcKeys.USER_ID, String.valueOf(id)));
            ctx.getTenantId().ifPresent(id -> setMdc(AsyncConstants.MdcKeys.TENANT_ID, String.valueOf(id)));
            ctx.getRequestId().ifPresent(id -> setMdc(AsyncConstants.MdcKeys.TRACE_ID, id));
            ctx.getClientIp().ifPresent(ip -> setMdc(AsyncConstants.MdcKeys.CLIENT_IP, ip));
            ctx.getRequestUri().ifPresent(uri -> setMdc(AsyncConstants.MdcKeys.REQUEST_URI, uri));
            ctx.getRequestMethod().ifPresent(method -> setMdc(AsyncConstants.MdcKeys.REQUEST_METHOD, method));
        });
    }
}
