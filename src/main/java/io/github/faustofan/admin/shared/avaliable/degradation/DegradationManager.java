package io.github.faustofan.admin.shared.avaliable.degradation;

import io.github.faustofan.admin.shared.avaliable.config.AvailabilityConfig;
import io.github.faustofan.admin.shared.avaliable.constants.AvailabilityConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 降级管理器
 * <p>
 * 提供服务降级管理能力，支持全局降级和针对特定资源的降级控制
 */
@ApplicationScoped
public class DegradationManager {

    private static final Logger LOG = Logger.getLogger(DegradationManager.class);

    private final AvailabilityConfig config;

    /**
     * 已降级的资源集合
     */
    private final Set<String> degradedResources = ConcurrentHashMap.newKeySet();

    /**
     * 降级服务实现
     */
    private final Map<String, Supplier<?>> degradedImplementations = new ConcurrentHashMap<>();

    /**
     * 全局降级开关
     */
    private volatile boolean globalDegraded = false;

    @Inject
    public DegradationManager(AvailabilityConfig config) {
        this.config = config;
        this.globalDegraded = config.degradation().forceDegraded();
    }

    /**
     * 检查资源是否已降级
     */
    public boolean isDegraded(String resourceName) {
        if (!config.degradation().enabled()) {
            return false;
        }
        return globalDegraded || degradedResources.contains(resourceName);
    }

    /**
     * 检查是否处于全局降级模式
     */
    public boolean isGloballyDegraded() {
        return globalDegraded;
    }

    /**
     * 启用全局降级
     */
    public void enableGlobalDegradation() {
        globalDegraded = true;
        LOG.warnf("%s Global degradation enabled",
                AvailabilityConstants.LogPrefix.FALLBACK);
    }

    /**
     * 禁用全局降级
     */
    public void disableGlobalDegradation() {
        globalDegraded = false;
        LOG.infof("%s Global degradation disabled",
                AvailabilityConstants.LogPrefix.FALLBACK);
    }

    /**
     * 降级特定资源
     */
    public void degrade(String resourceName) {
        degradedResources.add(resourceName);
        LOG.warnf("%s Resource %s has been degraded",
                AvailabilityConstants.LogPrefix.FALLBACK, resourceName);
    }

    /**
     * 恢复特定资源
     */
    public void recover(String resourceName) {
        degradedResources.remove(resourceName);
        LOG.infof("%s Resource %s has been recovered",
                AvailabilityConstants.LogPrefix.FALLBACK, resourceName);
    }

    /**
     * 注册降级实现
     */
    public <T> void registerDegradedImplementation(String resourceName, Supplier<T> degradedImpl) {
        degradedImplementations.put(resourceName, degradedImpl);
        LOG.infof("%s Registered degraded implementation for: %s",
                AvailabilityConstants.LogPrefix.FALLBACK, resourceName);
    }

    /**
     * 执行操作（如果降级则使用降级实现）
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(String resourceName, Supplier<T> normalSupplier) {
        if (isDegraded(resourceName)) {
            Supplier<?> degradedImpl = degradedImplementations.get(resourceName);
            if (degradedImpl != null) {
                LOG.debugf("%s Using degraded implementation for: %s",
                        AvailabilityConstants.LogPrefix.FALLBACK, resourceName);
                return (T) degradedImpl.get();
            }
            LOG.debugf("%s Resource %s is degraded but no implementation registered",
                    AvailabilityConstants.LogPrefix.FALLBACK, resourceName);
            return null;
        }
        return normalSupplier.get();
    }

    /**
     * 执行操作（如果降级则使用提供的降级实现）
     */
    public <T> T execute(String resourceName, Supplier<T> normalSupplier, Supplier<T> degradedSupplier) {
        if (isDegraded(resourceName)) {
            LOG.debugf("%s Using provided degraded supplier for: %s",
                    AvailabilityConstants.LogPrefix.FALLBACK, resourceName);
            return degradedSupplier.get();
        }
        return normalSupplier.get();
    }

    /**
     * 获取所有已降级的资源
     */
    public Set<String> getDegradedResources() {
        return Set.copyOf(degradedResources);
    }

    /**
     * 恢复所有资源
     */
    public void recoverAll() {
        degradedResources.clear();
        globalDegraded = false;
        LOG.infof("%s All resources have been recovered",
                AvailabilityConstants.LogPrefix.FALLBACK);
    }

    /**
     * 移除降级实现
     */
    public void unregisterDegradedImplementation(String resourceName) {
        degradedImplementations.remove(resourceName);
    }

    /**
     * 获取降级状态概览
     */
    public DegradationStatus getStatus() {
        return new DegradationStatus(
                globalDegraded,
                degradedResources.size(),
                Set.copyOf(degradedResources)
        );
    }

    /**
     * 降级状态
     */
    public record DegradationStatus(
            boolean globallyDegraded,
            int degradedResourceCount,
            Set<String> degradedResources
    ) {}
}
