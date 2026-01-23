package io.github.faustofan.admin.shared.web.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页请求参数
 * <p>
 * 所有分页查询的请求参数都应继承此类。
 * </p>
 *
 * @author fausto
 */
@Data
public class PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 默认页码
     */
    public static final int DEFAULT_PAGE_NO = 1;

    /**
     * 默认每页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 最大每页大小
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 页码，从 1 开始
     */
    @QueryParam("pageNo")
    @DefaultValue("1")
    @Min(value = 1, message = "页码最小为 1")
    private int pageNo = DEFAULT_PAGE_NO;

    /**
     * 每页大小，默认 10，最大 100
     */
    @QueryParam("pageSize")
    @DefaultValue("10")
    @Min(value = 1, message = "每页大小最小为 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页大小最大为 " + MAX_PAGE_SIZE)
    private int pageSize = DEFAULT_PAGE_SIZE;

    /**
     * 获取偏移量（用于数据库分页查询）
     *
     * @return 偏移量
     */
    public int getOffset() {
        return (pageNo - 1) * pageSize;
    }

    /**
     * 创建分页请求
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @return 分页请求
     */
    public static PageRequest of(int pageNo, int pageSize) {
        PageRequest request = new PageRequest();
        request.setPageNo(pageNo);
        request.setPageSize(Math.min(pageSize, MAX_PAGE_SIZE));
        return request;
    }
}

