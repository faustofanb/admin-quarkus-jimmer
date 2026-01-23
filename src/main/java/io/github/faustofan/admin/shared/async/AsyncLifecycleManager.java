package io.github.faustofan.admin.shared.async;

import io.github.faustofan.admin.shared.async.executor.VirtualThreadExecutorFactory;
import io.github.faustofan.admin.shared.async.executor.VirtualThreadScheduler;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

/**
 * 异步基础设施生命周期管理器
 * <p>
 * 负责执行器和调度器的生命周期管理，与 Quarkus 生命周期集成。
 */
@ApplicationScoped
public class AsyncLifecycleManager {

    private static final Logger LOG = Logger.getLogger(AsyncLifecycleManager.class);

    /**
     * 应用启动时初始化
     */
    void onStart(@Observes StartupEvent event) {
        LOG.info("Async infrastructure initialized with JDK21 virtual threads");
    }

    /**
     * 应用关闭时清理资源
     */
    void onStop(@Observes ShutdownEvent event) {
        LOG.info("Shutting down async infrastructure...");

        // 关闭调度器
        VirtualThreadScheduler.shutdown();

        // 关闭默认执行器
        VirtualThreadExecutorFactory.shutdownDefault();

        LOG.info("Async infrastructure shutdown complete");
    }
}
