package io.github.faustofan.admin.domain.constants;

/**
 * 操作类型枚举
 */
public enum OperateType {

    /**
     * 查询
     */
    QUERY("查询"),

    /**
     * 新增
     */
    CREATE("新增"),

    /**
     * 修改
     */
    UPDATE("修改"),

    /**
     * 删除
     */
    DELETE("删除"),

    /**
     * 导出
     */
    EXPORT("导出"),

    /**
     * 导入
     */
    IMPORT("导入"),

    /**
     * 授权
     */
    GRANT("授权"),

    /**
     * 强制退出
     */
    FORCE_LOGOUT("强制退出"),

    /**
     * 登录
     */
    LOGIN("登录"),

    /**
     * 登出
     */
    LOGOUT("登出"),

    /**
     * 其他
     */
    OTHER("其他");

    private final String label;

    OperateType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
