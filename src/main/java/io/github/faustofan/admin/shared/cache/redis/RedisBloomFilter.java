package io.github.faustofan.admin.shared.cache.redis;

import io.github.faustofan.admin.shared.cache.config.CacheConfig;
import io.github.faustofan.admin.shared.cache.constants.CacheConstants;
import io.github.faustofan.admin.shared.cache.exception.CacheException;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.bitmap.BitMapCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;

/**
 * Redis 布隆过滤器实现
 * <p>
 * 用于防止缓存穿透，判断某个元素是否可能存在。
 * <p>
 * 特性：
 * <ul>
 *   <li>基于 Redis Bitmap 实现</li>
 *   <li>支持自定义误判率</li>
 *   <li>高性能的存在性判断</li>
 *   <li>分布式共享</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 添加元素
 * bloomFilter.add(CacheConstants.BloomFilterName.USER_ID, "12345");
 * 
 * // 批量添加
 * bloomFilter.addAll(CacheConstants.BloomFilterName.USER_ID, Arrays.asList("1", "2", "3"));
 *
 * // 检查元素是否可能存在
 * boolean mayExist = bloomFilter.mightContain(CacheConstants.BloomFilterName.USER_ID, "12345");
 * if (!mayExist) {
 *     // 100%确定不存在，直接返回
 *     return null;
 * }
 * // 可能存在，继续查询数据库
 * }</pre>
 */
@ApplicationScoped
public class RedisBloomFilter {

    private static final Logger LOG = Logger.getLogger(RedisBloomFilter.class);

    private final RedisDataSource redisDataSource;
    private final CacheConfig cacheConfig;
    private final BitMapCommands<String> bitmapCommands;

    @Inject
    public RedisBloomFilter(RedisDataSource redisDataSource, CacheConfig cacheConfig) {
        this.redisDataSource = redisDataSource;
        this.cacheConfig = cacheConfig;
        this.bitmapCommands = redisDataSource.bitmap(String.class);
    }

    // ===========================
    // 添加操作
    // ===========================

    /**
     * 添加元素到布隆过滤器
     *
     * @param filterName 过滤器名称
     * @param element    要添加的元素
     */
    public void add(String filterName, String element) {
        if (!isEnabled() || element == null) {
            return;
        }

        try {
            String key = buildRedisKey(filterName);
            int[] positions = getPositions(element);
            
            for (int position : positions) {
                bitmapCommands.setbit(key, position, 1);
            }
            
            LOG.debugv("Added element to bloom filter: {0}/{1}", filterName, element);
        } catch (Exception e) {
            LOG.warnv(e, "Failed to add element to bloom filter: {0}/{1}", filterName, element);
            throw CacheException.bloomFilterFailed(filterName, e);
        }
    }

    /**
     * 批量添加元素到布隆过滤器
     *
     * @param filterName 过滤器名称
     * @param elements   要添加的元素列表
     */
    public void addAll(String filterName, Iterable<String> elements) {
        if (!isEnabled() || elements == null) {
            return;
        }

        for (String element : elements) {
            add(filterName, element);
        }
    }

    // ===========================
    // 检查操作
    // ===========================

    /**
     * 检查元素是否可能存在于布隆过滤器
     * <p>
     * 返回值含义：
     * <ul>
     *   <li>false：元素一定不存在（100%准确）</li>
     *   <li>true：元素可能存在（存在误判率）</li>
     * </ul>
     *
     * @param filterName 过滤器名称
     * @param element    要检查的元素
     * @return true表示可能存在，false表示一定不存在
     */
    public boolean mightContain(String filterName, String element) {
        if (!isEnabled() || element == null) {
            return true; // 不启用时默认认为可能存在
        }

        try {
            String key = buildRedisKey(filterName);
            int[] positions = getPositions(element);
            
            for (int position : positions) {
                long bit = bitmapCommands.getbit(key, position);
                if (bit == 0) {
                    LOG.debugv("Element definitely not in bloom filter: {0}/{1}", filterName, element);
                    return false; // 有任何一个位为0，则一定不存在
                }
            }
            
            LOG.debugv("Element might be in bloom filter: {0}/{1}", filterName, element);
            return true; // 所有位都为1，可能存在
        } catch (Exception e) {
            LOG.warnv(e, "Failed to check bloom filter, returning true: {0}/{1}", filterName, element);
            return true; // 出错时保守处理，认为可能存在
        }
    }

    // ===========================
    // 删除操作
    // ===========================

    /**
     * 删除布隆过滤器
     * <p>
     * 注意：布隆过滤器不支持删除单个元素，只能删除整个过滤器
     *
     * @param filterName 过滤器名称
     */
    public void delete(String filterName) {
        if (!isEnabled()) {
            return;
        }

        try {
            String key = buildRedisKey(filterName);
            redisDataSource.key().del(key);
            LOG.infov("Deleted bloom filter: {0}", filterName);
        } catch (Exception e) {
            LOG.warnv(e, "Failed to delete bloom filter: {0}", filterName);
            throw CacheException.bloomFilterFailed(filterName, e);
        }
    }

    // ===========================
    // 内部方法
    // ===========================

    /**
     * 计算元素应该设置的位位置
     *
     * @param element 元素值
     * @return 位位置数组
     */
    private int[] getPositions(String element) {
        long expectedInsertions = cacheConfig.bloomFilter().expectedInsertions();
        double falsePositiveRate = cacheConfig.bloomFilter().falsePositiveRate();
        
        // 计算布隆过滤器所需的位数
        int numBits = optimalNumOfBits(expectedInsertions, falsePositiveRate);
        // 计算哈希函数个数
        int numHashFunctions = optimalNumOfHashFunctions(expectedInsertions, numBits);
        
        int[] positions = new int[numHashFunctions];
        long hash64 = hash64(element);
        
        for (int i = 0; i < numHashFunctions; i++) {
            long combinedHash = hash64 + i * hash64;
            positions[i] = (int) ((combinedHash & Long.MAX_VALUE) % numBits);
        }
        
        return positions;
    }

    /**
     * 计算最优的位数
     */
    private int optimalNumOfBits(long n, double p) {
        if (p == 0) {
            p = Double.MIN_VALUE;
        }
        return (int) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    /**
     * 计算最优的哈希函数个数
     */
    private int optimalNumOfHashFunctions(long n, long m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    /**
     * 使用 MurmurHash3 算法生成64位哈希值
     */
    private long hash64(String element) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(element.getBytes(StandardCharsets.UTF_8));
            long result = 0;
            for (int i = 0; i < 8; i++) {
                result |= ((long) (hash[i] & 0xFF)) << (8 * i);
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            // Fallback to hashCode
            return element.hashCode();
        }
    }

    /**
     * 构建Redis Key
     */
    private String buildRedisKey(String filterName) {
        return CacheConstants.KeyPrefix.BLOOM_FILTER + filterName;
    }

    /**
     * 检查布隆过滤器是否启用
     */
    public boolean isEnabled() {
        return cacheConfig.enabled() 
            && cacheConfig.redis().enabled() 
            && cacheConfig.bloomFilter().enabled();
    }
}
