package io.github.faustofan.admin.shared.persistence.trigger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.jboss.logging.Logger;

/**
 * Jimmer 触发器配置
 * <p>
 * 在应用启动时配置 Jimmer 的 Transaction Trigger，
 * 为所有实体注册全局监听器。
 * <p>
 * <b>触发器类型说明：</b>
 * <ul>
 *     <li><b>TRANSACTION_ONLY</b>: 事务内触发器，在 SQL 执行后立即触发，适用于本应用场景</li>
 *     <li><b>BINLOG_ONLY</b>: BinLog 触发器，需要额外配置数据库 BinLog 监听</li>
 *     <li><b>BOTH</b>: 同时支持两种触发器</li>
 * </ul>
 * <p>
 * 本配置使用 TRANSACTION_ONLY 模式，在事务内同步发布事件。
 *
 * @see org.babyfish.jimmer.sql.runtime.JSqlClientImplementor
 */
@ApplicationScoped
public class JimmerTriggerConfiguration {

    private static final Logger LOG = Logger.getLogger(JimmerTriggerConfiguration.class);

    private final JSqlClient sqlClient;
    private final AuditEntityEventListener entityEventListener;

    @Inject
    public JimmerTriggerConfiguration(JSqlClient sqlClient,
                                       AuditEntityEventListener entityEventListener) {
        this.sqlClient = sqlClient;
        this.entityEventListener = entityEventListener;
    }

    /**
     * 应用启动时注册触发器
     */
    void onStart(@Observes StartupEvent event) {
        registerEntityTriggers();
        LOG.info("Jimmer entity triggers registered successfully");
    }

    /**
     * 注册全局实体触发器
     * <p>
     * 使用 {@code sqlClient.getTriggers(true)} 获取 Transaction Trigger，
     * 并注册一个全局的实体监听器。
     */
    private void registerEntityTriggers() {
        try {
            // 获取 Transaction Trigger（参数 true 表示获取事务内触发器）
            // 注意：quarkus-jimmer 需要配置 quarkus.jimmer.trigger-type=TRANSACTION_ONLY 或 BOTH
            sqlClient.getTriggers(true)
                    .addEntityListener(entityEventListener::onEntityChanged);

            LOG.debug("Registered global entity listener for all entities");

        } catch (Exception e) {
            LOG.error("Failed to register Jimmer entity triggers. " +
                    "Please ensure quarkus.jimmer.trigger-type is set to TRANSACTION_ONLY or BOTH", e);
        }
    }
}
