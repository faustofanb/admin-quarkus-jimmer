package io.github.faustofan.admin.shared.messaging.constants;

/**
 * 消息投递模式枚举
 * <p>
 * 定义消息的投递语义
 */
public enum DeliveryMode {

    /**
     * 即发即忘 - 不关心消息是否送达
     */
    FIRE_AND_FORGET("fire_and_forget", "即发即忘"),

    /**
     * 至少一次 - 保证消息至少被消费一次（可能重复）
     */
    AT_LEAST_ONCE("at_least_once", "至少一次"),

    /**
     * 至多一次 - 消息最多被消费一次（可能丢失）
     */
    AT_MOST_ONCE("at_most_once", "至多一次"),

    /**
     * 精确一次 - 保证消息精确被消费一次
     */
    EXACTLY_ONCE("exactly_once", "精确一次"),

    /**
     * 同步等待 - 等待消息处理完成
     */
    SYNC("sync", "同步等待");

    private final String code;
    private final String description;

    DeliveryMode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static DeliveryMode fromCode(String code) {
        for (DeliveryMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
                return mode;
            }
        }
        return AT_LEAST_ONCE;
    }
}
