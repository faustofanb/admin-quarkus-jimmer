package io.github.faustofan.admin.shared.avaliable.exception;

/**
 * 隔离舱满异常
 * <p>
 * 当隔离舱达到最大并发数且等待队列已满时抛出
 */
public class BulkheadFullException extends AvailabilityException {

    private static final String EXCEPTION_TYPE = "BULKHEAD_FULL";
    private static final String DEFAULT_MESSAGE = "系统繁忙，请稍后重试";

    private final int maxConcurrentCalls;
    private final int waitingTaskQueue;

    public BulkheadFullException() {
        super(DEFAULT_MESSAGE);
        this.maxConcurrentCalls = 0;
        this.waitingTaskQueue = 0;
    }

    public BulkheadFullException(String resourceName) {
        super(DEFAULT_MESSAGE, resourceName);
        this.maxConcurrentCalls = 0;
        this.waitingTaskQueue = 0;
    }

    public BulkheadFullException(String resourceName, int maxConcurrentCalls, int waitingTaskQueue) {
        super(DEFAULT_MESSAGE, resourceName);
        this.maxConcurrentCalls = maxConcurrentCalls;
        this.waitingTaskQueue = waitingTaskQueue;
    }

    public BulkheadFullException(String resourceName, int maxConcurrentCalls, int waitingTaskQueue, String message) {
        super(message, resourceName);
        this.maxConcurrentCalls = maxConcurrentCalls;
        this.waitingTaskQueue = waitingTaskQueue;
    }

    /**
     * 获取最大并发调用数
     */
    public int getMaxConcurrentCalls() {
        return maxConcurrentCalls;
    }

    /**
     * 获取等待任务队列大小
     */
    public int getWaitingTaskQueue() {
        return waitingTaskQueue;
    }

    @Override
    public String getExceptionType() {
        return EXCEPTION_TYPE;
    }
}
