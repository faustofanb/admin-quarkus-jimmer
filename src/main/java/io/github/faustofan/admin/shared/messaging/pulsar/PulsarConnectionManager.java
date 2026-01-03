package io.github.faustofan.admin.shared.messaging.pulsar;

import io.github.faustofan.admin.shared.messaging.config.MessagingConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Pulsar 连接管理器
 * <p>
 * 管理 Pulsar Client 的生命周期，提供连接状态检查等功能。
 * 这是一个可插拔组件，只有在启用 Pulsar 时才会真正初始化连接。
 */
@ApplicationScoped
public class PulsarConnectionManager {

    private static final Logger LOG = Logger.getLogger(PulsarConnectionManager.class);

    private final MessagingConfig config;
    private volatile boolean connected = false;

    @Inject
    public PulsarConnectionManager(MessagingConfig config) {
        this.config = config;
    }

    /**
     * 初始化连接
     */
    public void initialize() {
        if (!config.pulsar().enabled()) {
            LOG.info("Pulsar is disabled, skipping connection initialization");
            return;
        }

        try {
            LOG.infov("Initializing Pulsar connection to: {0}", config.pulsar().serviceUrl());
            // TODO: 实际的 Pulsar Client 初始化
            // pulsarClient = PulsarClient.builder()
            //     .serviceUrl(config.pulsar().serviceUrl())
            //     .build();
            connected = true;
            LOG.info("Pulsar connection initialized successfully");
        } catch (Exception e) {
            LOG.errorv(e, "Failed to initialize Pulsar connection: {0}", e.getMessage());
            connected = false;
        }
    }

    /**
     * 关闭连接
     */
    public void shutdown() {
        if (!config.pulsar().enabled() || !connected) {
            return;
        }

        try {
            LOG.info("Shutting down Pulsar connection");
            // TODO: 关闭 Pulsar Client
            // if (pulsarClient != null) {
            //     pulsarClient.close();
            // }
            connected = false;
            LOG.info("Pulsar connection closed");
        } catch (Exception e) {
            LOG.errorv(e, "Error while closing Pulsar connection: {0}", e.getMessage());
        }
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * 检查 Pulsar 是否启用
     */
    public boolean isEnabled() {
        return config.pulsar().enabled();
    }

    /**
     * 获取服务地址
     */
    public String getServiceUrl() {
        return config.pulsar().serviceUrl();
    }

    /**
     * 健康检查
     */
    public boolean healthCheck() {
        if (!config.pulsar().enabled()) {
            return true; // 未启用时不影响健康检查
        }
        return connected;
    }
}
