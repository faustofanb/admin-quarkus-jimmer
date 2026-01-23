/**
 * 可用性基础设施 - Availability Infrastructure
 * <p>
 * 该包提供统一的可用性保护能力，包括：
 * <ul>
 *   <li><b>限流 (Rate Limiting)</b> - 控制请求速率，防止系统过载</li>
 *   <li><b>熔断 (Circuit Breaker)</b> - 快速失败机制，防止级联故障</li>
 *   <li><b>降级 (Degradation)</b> - 服务降级策略，保证核心功能可用</li>
 *   <li><b>回退 (Fallback)</b> - 备选方案，提供兜底响应</li>
 *   <li><b>重试 (Retry)</b> - 失败重试机制，处理临时性故障</li>
 *   <li><b>超时 (Timeout)</b> - 防止请求长时间阻塞</li>
 *   <li><b>隔离 (Bulkhead)</b> - 资源隔离，限制并发执行</li>
 * </ul>
 * <p>
 * 基于 SmallRye Fault Tolerance (MicroProfile Fault Tolerance) 实现，
 * 完全适配 Quarkus 设计哲学。
 * <p>
 * 主要组件：
 * <ul>
 *   <li>{@link io.github.faustofan.admin.shared.avaliable.AvailabilityFacade} - 统一门面API</li>
 *   <li>{@link io.github.faustofan.admin.shared.avaliable.config.AvailabilityConfig} - 配置接口</li>
 *   <li>{@link io.github.faustofan.admin.shared.avaliable.ratelimit.RateLimiter} - 限流器</li>
 *   <li>{@link io.github.faustofan.admin.shared.avaliable.circuit.CircuitBreakerManager} - 熔断器管理</li>
 *   <li>{@link io.github.faustofan.admin.shared.avaliable.fallback.FallbackHandler} - 回退处理器</li>
 * </ul>
 *
 * @since 1.0
 */
package io.github.faustofan.admin.shared.avaliable;
