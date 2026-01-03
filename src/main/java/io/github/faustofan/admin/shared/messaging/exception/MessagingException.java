package io.github.faustofan.admin.shared.messaging.exception;

import io.github.faustofan.admin.shared.messaging.constants.ChannelType;

/**
 * 消息基础设施统一异常
 * <p>
 * 所有消息相关的异常都应该抛出这个异常或其子类
 */
public class MessagingException extends RuntimeException {

    private final MessagingExceptionType type;
    private final ChannelType channelType;
    private final String topic;

    public MessagingException(MessagingExceptionType type, String message) {
        super(buildMessage(type, message));
        this.type = type;
        this.channelType = null;
        this.topic = null;
    }

    public MessagingException(MessagingExceptionType type, String message, Throwable cause) {
        super(buildMessage(type, message), cause);
        this.type = type;
        this.channelType = null;
        this.topic = null;
    }

    public MessagingException(MessagingExceptionType type, ChannelType channelType, String topic, String message) {
        super(buildDetailMessage(type, channelType, topic, message));
        this.type = type;
        this.channelType = channelType;
        this.topic = topic;
    }

    public MessagingException(MessagingExceptionType type, ChannelType channelType, String topic, String message, Throwable cause) {
        super(buildDetailMessage(type, channelType, topic, message), cause);
        this.type = type;
        this.channelType = channelType;
        this.topic = topic;
    }

    public MessagingExceptionType getType() {
        return type;
    }

    public ChannelType getChannelType() {
        return channelType;
    }

    public String getTopic() {
        return topic;
    }

    public String getErrorCode() {
        return type.getCode();
    }

    private static String buildMessage(MessagingExceptionType type, String message) {
        return String.format("[%s] %s: %s", type.getCode(), type.getDescription(), message);
    }

    private static String buildDetailMessage(MessagingExceptionType type, ChannelType channelType, String topic, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(type.getCode()).append("] ").append(type.getDescription());
        if (channelType != null) {
            sb.append(" (channel=").append(channelType.getCode());
        }
        if (topic != null) {
            sb.append(", topic=").append(topic);
        }
        if (channelType != null) {
            sb.append(")");
        }
        sb.append(": ").append(message);
        return sb.toString();
    }

    // ===========================
    // 工厂方法
    // ===========================

    /**
     * 发送失败
     */
    public static MessagingException sendFailed(String topic, String reason) {
        return new MessagingException(MessagingExceptionType.SEND_FAILED,
                String.format("Failed to send message to topic '%s': %s", topic, reason));
    }

    public static MessagingException sendFailed(String topic, Throwable cause) {
        return new MessagingException(MessagingExceptionType.SEND_FAILED,
                String.format("Failed to send message to topic '%s'", topic), cause);
    }

    /**
     * 序列化失败
     */
    public static MessagingException serializationFailed(String reason, Throwable cause) {
        return new MessagingException(MessagingExceptionType.SERIALIZATION_FAILED, reason, cause);
    }

    /**
     * 反序列化失败
     */
    public static MessagingException deserializationFailed(String reason, Throwable cause) {
        return new MessagingException(MessagingExceptionType.DESERIALIZATION_FAILED, reason, cause);
    }

    /**
     * 通道不可用
     */
    public static MessagingException channelUnavailable(ChannelType channelType, String reason) {
        return new MessagingException(MessagingExceptionType.CHANNEL_UNAVAILABLE, channelType, null,
                String.format("Channel '%s' is unavailable: %s", channelType.getCode(), reason));
    }

    /**
     * Pulsar 连接失败
     */
    public static MessagingException pulsarConnectionFailed(String serviceUrl, Throwable cause) {
        return new MessagingException(MessagingExceptionType.PULSAR_CONNECTION_FAILED,
                String.format("Failed to connect to Pulsar at '%s'", serviceUrl), cause);
    }

    /**
     * 超时
     */
    public static MessagingException timeout(String topic, long timeoutMs) {
        return new MessagingException(MessagingExceptionType.TIMEOUT,
                String.format("Operation on topic '%s' timed out after %dms", topic, timeoutMs));
    }

    /**
     * 重试耗尽
     */
    public static MessagingException retryExhausted(String topic, int attempts, Throwable lastError) {
        return new MessagingException(MessagingExceptionType.RETRY_EXHAUSTED,
                String.format("All %d retry attempts exhausted for topic '%s'", attempts, topic), lastError);
    }

    /**
     * 处理器失败
     */
    public static MessagingException handlerFailed(String handlerName, Throwable cause) {
        return new MessagingException(MessagingExceptionType.HANDLER_FAILED,
                String.format("Handler '%s' execution failed", handlerName), cause);
    }

    /**
     * 流错误
     */
    public static MessagingException streamError(String reason, Throwable cause) {
        return new MessagingException(MessagingExceptionType.STREAM_ERROR, reason, cause);
    }

    /**
     * 配置错误
     */
    public static MessagingException configurationError(String reason) {
        return new MessagingException(MessagingExceptionType.CONFIGURATION_ERROR, reason);
    }
}
