package io.github.faustofan.admin.shared.web.exception;

/**
 * 错误码常量接口
 * <p>
 * 错误码采用模块化设计：
 * - 0: 成功
 * - 1xxx: 系统级错误
 * - 2xxx: 认证授权错误
 * - 3xxx: 参数校验错误
 * - 1xxxx: 租户模块错误
 * - 2xxxx: 用户模块错误
 * - 3xxxx: 角色模块错误
 * - 4xxxx: 菜单模块错误
 * - 5xxxx: 部门模块错误
 * - 6xxxx: 字典模块错误
 * - 7xxxx: 岗位模块错误
 * </p>
 *
 * @author fausto
 */
public interface ErrorCode {

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    int getCode();

    /**
     * 获取错误消息
     *
     * @return 错误消息
     */
    String getMessage();
}

