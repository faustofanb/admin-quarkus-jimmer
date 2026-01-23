package io.github.faustofan.admin.shared.persistence;

import org.babyfish.jimmer.sql.*;
import java.time.Instant;

/**
 * 审计基础实体（无租户字段）
 * <p>
 * 适用于不含 tenant_id 的表（如 system_menu/system_dict_type 等）。
 * <ul>
 * <li>id: PostgreSQL 序列/IDENTITY 自增</li>
 * <li>creator/updater: String（用户名或操作者标识）</li>
 * <li>create_time/update_time: Instant（时间戳）</li>
 * <li>deleted: int（0=未删除，1=已删除）</li>
 * </ul>
 */
@MappedSuperclass
public interface AuditEntity {

    /**
     * 主键ID
     * <p>
     * 使用 IDENTITY 策略，由数据库序列/默认值自动生成（PostgreSQL nextval）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id();

    /**
     * 创建者（用户名/操作者标识）
     */
    String creator();

    /**
     * 创建时间
     */
    Instant createTime();

    /**
     * 更新者（用户名/操作者标识）
     */
    String updater();

    /**
     * 更新时间
     */
    Instant updateTime();

    /**
     * 逻辑删除标记
     * <p>
     * 0 = 未删除，1 = 已删除
     */
    @LogicalDeleted("1")
    int deleted();
}
