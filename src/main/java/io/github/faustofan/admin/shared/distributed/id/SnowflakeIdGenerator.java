package io.github.faustofan.admin.shared.distributed.id;

import io.github.faustofan.admin.shared.distributed.config.DistributedConfig;
import io.github.faustofan.admin.shared.distributed.constants.DistributedConstants;
import io.github.faustofan.admin.shared.distributed.exception.DistributedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * 雪花算法ID生成器
 * <p>
 * 生成64位分布式唯一ID，结构如下：
 * <pre>
 * +------+----------------------+-------------+-------------+------------------+
 * | 1bit |        41bit         |    5bit     |    5bit     |      12bit       |
 * +------+----------------------+-------------+-------------+------------------+
 * | 符号  |       时间戳          | 数据中心ID  |   机器ID    |      序列号       |
 * +------+----------------------+-------------+-------------+------------------+
 * </pre>
 * <p>
 * 特性：
 * <ul>
 *   <li>趋势递增，适合作为数据库主键</li>
 *   <li>高性能，本地生成无网络开销</li>
 *   <li>支持每毫秒4096个ID</li>
 *   <li>可反解析获取时间戳、机器ID等信息</li>
 * </ul>
 */
@ApplicationScoped
public class SnowflakeIdGenerator implements IdGenerator {

    private static final Logger LOG = Logger.getLogger(SnowflakeIdGenerator.class);

    /**
     * 起始时间戳
     */
    private final long epoch;

    /**
     * 数据中心ID
     */
    private final long datacenterId;

    /**
     * 机器ID
     */
    private final long workerId;

    /**
     * 序列号
     */
    private long sequence = 0L;

    /**
     * 上次生成ID的时间戳
     */
    private long lastTimestamp = -1L;

    /**
     * 机器ID左移位数
     */
    private final long workerIdShift = DistributedConstants.SEQUENCE_BITS;

    /**
     * 数据中心ID左移位数
     */
    private final long datacenterIdShift = DistributedConstants.SEQUENCE_BITS + DistributedConstants.WORKER_ID_BITS;

    /**
     * 时间戳左移位数
     */
    private final long timestampShift = DistributedConstants.SEQUENCE_BITS + DistributedConstants.WORKER_ID_BITS + DistributedConstants.DATACENTER_ID_BITS;

    @Inject
    public SnowflakeIdGenerator(DistributedConfig config) {
        this.epoch = config.idGenerator().epoch();
        this.datacenterId = config.idGenerator().datacenterId();
        this.workerId = config.idGenerator().workerId();

        // 验证参数
        if (datacenterId > DistributedConstants.MAX_DATACENTER_ID || datacenterId < 0) {
            throw DistributedException.configurationError(
                    "Datacenter ID must be between 0 and " + DistributedConstants.MAX_DATACENTER_ID);
        }
        if (workerId > DistributedConstants.MAX_WORKER_ID || workerId < 0) {
            throw DistributedException.configurationError(
                    "Worker ID must be between 0 and " + DistributedConstants.MAX_WORKER_ID);
        }

        LOG.infov("Snowflake ID Generator initialized: datacenterId={0}, workerId={1}, epoch={2}",
                datacenterId, workerId, epoch);
    }

    @Override
    public synchronized long nextId() {
        long currentTimestamp = currentTimeMillis();

        // 时钟回拨检测
        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 5) {
                // 小于5ms的回拨，等待追上
                try {
                    Thread.sleep(offset << 1);
                    currentTimestamp = currentTimeMillis();
                    if (currentTimestamp < lastTimestamp) {
                        throw DistributedException.clockMovedBackwards(lastTimestamp, currentTimestamp);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw DistributedException.idGenerationFailed(e);
                }
            } else {
                throw DistributedException.clockMovedBackwards(lastTimestamp, currentTimestamp);
            }
        }

        // 同一毫秒内
        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & DistributedConstants.MAX_SEQUENCE;
            // 序列号溢出，等待下一毫秒
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 新的毫秒，序列号归零
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        // 组装ID
        return ((currentTimestamp - epoch) << timestampShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << workerIdShift)
                | sequence;
    }

    @Override
    public String nextIdStr() {
        return String.valueOf(nextId());
    }

    @Override
    public long[] nextIds(int count) {
        if (count <= 0) {
            return new long[0];
        }

        long[] ids = new long[count];
        for (int i = 0; i < count; i++) {
            ids[i] = nextId();
        }
        return ids;
    }

    @Override
    public long parseTimestamp(long id) {
        return (id >> timestampShift) + epoch;
    }

    @Override
    public long parseDatacenterId(long id) {
        return (id >> datacenterIdShift) & DistributedConstants.MAX_DATACENTER_ID;
    }

    @Override
    public long parseWorkerId(long id) {
        return (id >> workerIdShift) & DistributedConstants.MAX_WORKER_ID;
    }

    @Override
    public long parseSequence(long id) {
        return id & DistributedConstants.MAX_SEQUENCE;
    }

    /**
     * 等待下一毫秒
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 获取当前时间戳
     */
    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 获取数据中心ID
     */
    public long getDatacenterId() {
        return datacenterId;
    }

    /**
     * 获取机器ID
     */
    public long getWorkerId() {
        return workerId;
    }
}
