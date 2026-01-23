package io.github.faustofan.admin.shared.messaging.exception;

/**
 * 消息异常类型枚举
 * <p>
 * 定义消息系统可能发生的异常类型
 */
public enum MessagingExceptionType {

    /**
     * 消息发送失败
     */
    SEND_FAILED("MSG_001", "消息发送失败"),

    /**
     * 消息序列化失败
     */
    SERIALIZATION_FAILED("MSG_002", "消息序列化失败"),

    /**
     * 消息反序列化失败
     */
    DESERIALIZATION_FAILED("MSG_003", "消息反序列化失败"),

    /**
     * 通道不可用
     */
    CHANNEL_UNAVAILABLE("MSG_004", "消息通道不可用"),

    /**
     * Pulsar 连接失败
     */
    PULSAR_CONNECTION_FAILED("MSG_005", "Pulsar连接失败"),

    /**
     * 主题不存在
     */
    TOPIC_NOT_FOUND("MSG_006", "Topic不存在"),

    /**
     * 消息超时
     */
    TIMEOUT("MSG_007", "消息发送/接收超时"),

    /**
     * 重试次数耗尽
     */
    RETRY_EXHAUSTED("MSG_008", "重试次数耗尽"),

    /**
     * 处理器执行失败
     */
    HANDLER_FAILED("MSG_009", "消息处理器执行失败"),

    /**
     * 流处理异常
     */
    STREAM_ERROR("MSG_010", "流处理异常"),

    /**
     * 配置错误
     */
    CONFIGURATION_ERROR("MSG_011", "消息配置错误"),

    /**
     * 未知错误
     */
    UNKNOWN("MSG_999", "未知消息错误");

    private final String code;
    private final String description;

    MessagingExceptionType(String code, String description) {
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
