package io.github.faustofan.admin.shared.avaliable.exception;

/**
 * 可用性异常基类
 * <p>
 * 所有可用性相关异常的基类
 */
public abstract class AvailabilityException extends RuntimeException {

    private final String resourceName;

    protected AvailabilityException(String message) {
        super(message);
        this.resourceName = null;
    }

    protected AvailabilityException(String message, String resourceName) {
        super(message);
        this.resourceName = resourceName;
    }

    protected AvailabilityException(String message, String resourceName, Throwable cause) {
        super(message, cause);
        this.resourceName = resourceName;
    }

    /**
     * 获取资源名称
     */
    public String getResourceName() {
        return resourceName;
    }

    /**
     * 获取异常类型名称
     */
    public abstract String getExceptionType();
}
