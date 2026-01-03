package io.github.faustofan.admin.shared.messaging.constants;

/**
 * 消息通道类型枚举
 * <p>
 * 定义事件总线支持的消息通道类型
 */
public enum ChannelType {

    /**
     * 本地事件 - 基于 CDI Events，仅在当前JVM内传播
     */
    LOCAL("local", "本地事件"),

    /**
     * Pulsar 消息队列 - 分布式消息，跨服务传播
     */
    PULSAR("pulsar", "Pulsar消息队列"),

    /**
     * 流式通道 - 用于响应式流处理
     */
    STREAM("stream", "响应式流"),

    /**
     * 广播 - 同时发送到本地和远程
     */
    BROADCAST("broadcast", "广播模式"),

    /**
     * 自动选择 - 根据配置和事件类型自动选择通道
     */
    AUTO("auto", "自动选择");

    private final String code;
    private final String description;

    ChannelType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ChannelType fromCode(String code) {
        for (ChannelType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return AUTO;
    }
}
