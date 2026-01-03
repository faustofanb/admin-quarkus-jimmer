/**
 * Distributed infrastructure package.
 *
 * 提供分布式锁、唯一ID生成、幂等检查等基础设施，
 * 通过 {@link io.github.faustofan.admin.shared.distributed.DistributedFacade}
 * 对外统一暴露 API，避免业务层直接依赖底层实现。
 */
package io.github.faustofan.admin.shared.distributed;
