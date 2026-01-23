package io.github.faustofan.admin.shared.distributed.lock;

import io.github.faustofan.admin.shared.distributed.constants.LockType;

import java.time.Instant;

/**
 * 锁上下文
 * <p>
 * 包含锁的元信息，用于释放锁时验证身份
 */
public class LockContext {

    /**
     * 锁的唯一标识
     */
    private final String lockKey;

    /**
     * 锁的持有者标识（线程ID + UUID）
     */
    private final String holderId;

    /**
     * 锁类型
     */
    private final LockType lockType;

    /**
     * 获取锁的时间
     */
    private final Instant acquiredAt;

    /**
     * 锁的过期时间
     */
    private final Instant expiresAt;

    /**
     * 锁是否有效
     */
    private volatile boolean valid;

    private LockContext(Builder builder) {
        this.lockKey = builder.lockKey;
        this.holderId = builder.holderId;
        this.lockType = builder.lockType;
        this.acquiredAt = builder.acquiredAt;
        this.expiresAt = builder.expiresAt;
        this.valid = true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getLockKey() {
        return lockKey;
    }

    public String getHolderId() {
        return holderId;
    }

    public LockType getLockType() {
        return lockType;
    }

    public Instant getAcquiredAt() {
        return acquiredAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isValid() {
        return valid;
    }

    /**
     * 标记锁为无效（已释放）
     */
    public void invalidate() {
        this.valid = false;
    }

    /**
     * 检查锁是否已过期
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * 获取锁的持有时长（毫秒）
     */
    public long getHoldDurationMs() {
        return Instant.now().toEpochMilli() - acquiredAt.toEpochMilli();
    }

    @Override
    public String toString() {
        return "LockContext{" +
                "lockKey='" + lockKey + '\'' +
                ", holderId='" + holderId + '\'' +
                ", lockType=" + lockType +
                ", acquiredAt=" + acquiredAt +
                ", expiresAt=" + expiresAt +
                ", valid=" + valid +
                '}';
    }

    public static class Builder {
        private String lockKey;
        private String holderId;
        private LockType lockType;
        private Instant acquiredAt;
        private Instant expiresAt;

        public Builder lockKey(String lockKey) {
            this.lockKey = lockKey;
            return this;
        }

        public Builder holderId(String holderId) {
            this.holderId = holderId;
            return this;
        }

        public Builder lockType(LockType lockType) {
            this.lockType = lockType;
            return this;
        }

        public Builder acquiredAt(Instant acquiredAt) {
            this.acquiredAt = acquiredAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public LockContext build() {
            return new LockContext(this);
        }
    }
}
