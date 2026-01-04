package io.github.faustofan.admin.shared.avaliable.constants;

/**
 * 回退类型枚举
 * <p>
 * 定义各种失败场景下的回退策略
 */
public enum FallbackType {

    /**
     * 返回空值
     */
    EMPTY("返回空值", "返回null或空集合"),

    /**
     * 返回默认值
     */
    DEFAULT_VALUE("返回默认值", "返回预设的默认值"),

    /**
     * 返回缓存值
     */
    CACHED("返回缓存值", "返回上一次成功的缓存结果"),

    /**
     * 降级服务
     */
    DEGRADED("降级服务", "切换到降级版本的服务"),

    /**
     * 抛出特定异常
     */
    THROW_EXCEPTION("抛出异常", "抛出特定的业务异常"),

    /**
     * 重定向
     */
    REDIRECT("重定向", "重定向到备用服务");

    private final String name;
    private final String description;

    FallbackType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * 获取类型名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取类型描述
     */
    public String getDescription() {
        return description;
    }
}
