package io.github.faustofan.admin.shared.observable.exception;

/**
 * 可观测性基础设施异常
 */
public class ObservableException extends RuntimeException {

    private final ObservableExceptionType type;

    public ObservableException(ObservableExceptionType type) {
        super(type.toString());
        this.type = type;
    }

    public ObservableException(ObservableExceptionType type, String message) {
        super(String.format("%s: %s", type.toString(), message));
        this.type = type;
    }

    public ObservableException(ObservableExceptionType type, String message, Throwable cause) {
        super(String.format("%s: %s", type.toString(), message), cause);
        this.type = type;
    }

    public ObservableException(ObservableExceptionType type, Throwable cause) {
        super(type.toString(), cause);
        this.type = type;
    }

    public ObservableExceptionType getType() {
        return type;
    }

    public String getErrorCode() {
        return type.getCode();
    }
}
