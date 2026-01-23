package io.github.faustofan.admin.shared.web.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页响应结果
 * <p>
 * 用于封装分页查询的结果数据。
 * </p>
 *
 * @param <T> 数据类型
 * @author fausto
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 数据列表
     */
    private List<T> list;

    /**
     * 创建空的分页响应
     *
     * @param <T> 数据类型
     * @return 空的分页响应
     */
    public static <T> PageResponse<T> empty() {
        return new PageResponse<>(0L, Collections.emptyList());
    }

    /**
     * 创建分页响应
     *
     * @param total 总记录数
     * @param list  数据列表
     * @param <T>   数据类型
     * @return 分页响应
     */
    public static <T> PageResponse<T> of(long total, List<T> list) {
        return new PageResponse<>(total, list);
    }

    /**
     * 创建分页响应（从另一个分页响应转换）
     *
     * @param source    源分页响应
     * @param converter 转换函数
     * @param <S>       源数据类型
     * @param <T>       目标数据类型
     * @return 转换后的分页响应
     */
    public static <S, T> PageResponse<T> of(PageResponse<S> source, Function<S, T> converter) {
        List<T> targetList = source.getList().stream()
                .map(converter)
                .collect(Collectors.toList());
        return new PageResponse<>(source.getTotal(), targetList);
    }

    /**
     * 转换数据类型
     *
     * @param converter 转换函数
     * @param <R>       目标数据类型
     * @return 转换后的分页响应
     */
    public <R> PageResponse<R> map(Function<T, R> converter) {
        List<R> targetList = this.list.stream()
                .map(converter)
                .collect(Collectors.toList());
        return new PageResponse<>(this.total, targetList);
    }

    /**
     * 判断是否有数据
     *
     * @return 是否有数据
     */
    public boolean hasData() {
        return this.list != null && !this.list.isEmpty();
    }

    /**
     * 获取总页数
     *
     * @param pageSize 每页大小
     * @return 总页数
     */
    public int getTotalPages(int pageSize) {
        if (pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) this.total / pageSize);
    }
}

