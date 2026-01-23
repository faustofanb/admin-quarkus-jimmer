package io.github.faustofan.admin.shared.persistence.event;

/**
 * 实体事件类型枚举
 * <p>
 * 定义实体生命周期中的事件类型
 */
public enum EntityEventType {

    /**
     * 实体插入
     */
    INSERTED("inserted", "实体新增"),

    /**
     * 实体更新
     */
    UPDATED("updated", "实体更新"),

    /**
     * 实体删除
     */
    DELETED("deleted", "实体删除");

    private final String code;
    private final String description;

    EntityEventType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
