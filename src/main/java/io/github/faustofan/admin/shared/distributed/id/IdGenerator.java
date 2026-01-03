package io.github.faustofan.admin.shared.distributed.id;

/**
 * ID生成器接口
 * <p>
 * 定义统一的ID生成API
 */
public interface IdGenerator {

    /**
     * 生成下一个唯一ID（Long型）
     *
     * @return 唯一ID
     */
    long nextId();

    /**
     * 生成下一个唯一ID（字符串形式）
     *
     * @return 唯一ID字符串
     */
    String nextIdStr();

    /**
     * 批量生成ID
     *
     * @param count 生成数量
     * @return ID数组
     */
    long[] nextIds(int count);

    /**
     * 解析ID的时间戳部分（如果支持）
     *
     * @param id ID
     * @return 时间戳（毫秒），如果不支持返回-1
     */
    long parseTimestamp(long id);

    /**
     * 解析ID的数据中心ID部分（如果支持）
     *
     * @param id ID
     * @return 数据中心ID，如果不支持返回-1
     */
    long parseDatacenterId(long id);

    /**
     * 解析ID的机器ID部分（如果支持）
     *
     * @param id ID
     * @return 机器ID，如果不支持返回-1
     */
    long parseWorkerId(long id);

    /**
     * 解析ID的序列号部分（如果支持）
     *
     * @param id ID
     * @return 序列号，如果不支持返回-1
     */
    long parseSequence(long id);
}
