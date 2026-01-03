/**
 * 异步上下文管理模块
 * <p>
 * 提供上下文的捕获、恢复、清理功能，支持：
 * <ul>
 *   <li>MDC 诊断上下文透传</li>
 *   <li>应用上下文透传（强类型的 {@link io.github.faustofan.admin.shared.common.AppContext}）</li>
 * </ul>
 *
 * <h2>核心类</h2>
 * <ul>
 *   <li>{@link io.github.faustofan.admin.shared.common.AppContext} - 应用上下文（用户、租户、请求信息）</li>
 *   <li>{@link io.github.faustofan.admin.shared.async.context.AsyncContext} - 完整异步上下文（MDC + AppContext）</li>
 *   <li>{@link io.github.faustofan.admin.shared.common.AppContextHolder} - 上下文持有者（捕获/恢复/清理）</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 设置应用上下文
 * AppContext appContext = AppContext.builder()
 *     .userId(1L)
 *     .username("admin")
 *     .tenantId(100L)
 *     .requestId(UUID.randomUUID().toString())
 *     .build();
 * AppContextHolder.setAppContext(appContext);
 *
 * // 异步任务会自动透传上下文
 * AsyncExecutor.runAsync(() -> {
 *     // 上下文自动透传
 *     Long userId = AppContextHolder.getUserId().orElse(null);
 *     Long tenantId = AppContextHolder.getTenantId().orElse(null);
 * });
 * }</pre>
 *
 * @see io.github.faustofan.admin.shared.common.AppContext
 * @see io.github.faustofan.admin.shared.async.context.AsyncContext
 * @see io.github.faustofan.admin.shared.common.AppContextHolder
 */
package io.github.faustofan.admin.shared.async.context;
