/**
 * 异步基础设施模块
 * <p>
 * 基于 JDK21 虚拟线程的异步执行框架，提供：
 * <ul>
 *   <li><b>上下文透传</b>：自动透传 MDC 诊断上下文和应用上下文</li>
 *   <li><b>统一API</b>：通过 {@link io.github.faustofan.admin.shared.async.AsyncExecutor} 对外暴露统一接口</li>
 *   <li><b>流式操作</b>：通过 {@link io.github.faustofan.admin.shared.async.AsyncResult} 支持链式调用</li>
 *   <li><b>生命周期管理</b>：与 Quarkus 生命周期集成</li>
 * </ul>
 *
 * <h2>核心组件</h2>
 * <table border="1">
 *   <tr><th>组件</th><th>描述</th></tr>
 *   <tr><td>{@link io.github.faustofan.admin.shared.async.AsyncExecutor}</td><td>统一对外暴露的异步API门面</td></tr>
 *   <tr><td>{@link io.github.faustofan.admin.shared.async.AsyncResult}</td><td>流式API的异步结果封装</td></tr>
 *   <tr><td>{@link io.github.faustofan.admin.shared.common.AppContext}</td><td>应用上下文（用户、租户、请求信息）</td></tr>
 *   <tr><td>{@link io.github.faustofan.admin.shared.async.context.AsyncContext}</td><td>完整异步上下文（MDC + AppContext）</td></tr>
 *   <tr><td>{@link io.github.faustofan.admin.shared.common.AppContextHolder}</td><td>上下文捕获/恢复/清理工具</td></tr>
 * </table>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 1. 设置应用上下文（通常在请求入口处理）
 * AppContext appContext = AppContext.builder()
 *     .userId(1L)
 *     .username("admin")
 *     .tenantId(100L)
 *     .requestId(UUID.randomUUID().toString())
 *     .clientIp("192.168.1.1")
 *     .build();
 * AppContextHolder.setAppContext(appContext);
 *
 * // 2. 简单异步执行（上下文自动透传）
 * AsyncExecutor.runAsync(() -> {
 *     // 可以直接获取用户信息
 *     Long userId = AppContextHolder.getUserId().orElse(null);
 *     Long tenantId = AppContextHolder.getTenantId().orElse(null);
 *     log.info("Processing for user: {}, tenant: {}", userId, tenantId);
 * });
 *
 * // 3. 有返回值的异步执行
 * CompletableFuture<User> future = AsyncExecutor.supplyAsync(() -> userService.findById(userId));
 *
 * // 4. 批量并发处理
 * List<User> users = AsyncExecutor.mapAsync(userIds, userService::findById);
 *
 * // 5. 流式API
 * AsyncResult<Order> result = AsyncResult.of(() -> orderService.createOrder(request))
 *     .onSuccess(order -> notificationService.sendOrderCreatedNotification(order))
 *     .onFailure(ex -> log.error("Failed to create order", ex))
 *     .timeout(Duration.ofSeconds(30));
 *
 * Order order = result.get();
 *
 * // 6. 延迟执行
 * AsyncExecutor.schedule(() -> cacheService.cleanup(), Duration.ofMinutes(5));
 * }</pre>
 *
 * @see io.github.faustofan.admin.shared.async.AsyncExecutor
 * @see io.github.faustofan.admin.shared.async.AsyncResult
 * @see io.github.faustofan.admin.shared.common.AppContext
 * @see io.github.faustofan.admin.shared.common.AppContextHolder
 */
package io.github.faustofan.admin.shared.async;
